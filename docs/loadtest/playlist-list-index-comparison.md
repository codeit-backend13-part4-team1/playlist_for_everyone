# 플레이리스트 목록 조회 인덱스 개선 비교 (이슈 #22)

개선 전후를 같은 데이터, 같은 쿼리, 같은 워밍업 절차로 측정한 결과다. 파라미터는 L4(희귀 키워드)의 JMeter 값만 전후가 다르다(3장 참고). 리뷰 의견을 반영해 소유자 인덱스를 단일 컬럼으로 바꿨으며, 인덱스 구성 검토 내용은 8장에 정리했다.

## 1. 측정 환경

| 항목 | 내용 |
|---|---|
| 앱 / DB | 로컬 Spring Boot 4.0.8 (Java 17), PostgreSQL 16 (Docker) |
| 코드 기준 | 최신 `origin/dev` 머지 후 (개선 전후 동일 코드) |
| 데이터 | playlists 200,000 / subscriptions 995,613 / users 10,001 (`seed_playlist.sql`) |
| 통계 | 각 상태에서 `ANALYZE` 실행 후 측정 |
| 부하 도구 | JMeter, `playlist-list-variants.jmx` |
| 파라미터 | `update_list_params.py`로 생성 (200행). L4 키워드는 전후가 다름 (3장 참고) |
| 인덱스 변경 이력 | 리뷰 반영으로 소유자 인덱스를 `(owner_id)` 단일 컬럼으로 변경 (3장, 8장) |
| 환경 한계 | 앱, DB, JMeter가 한 노트북에서 함께 동작하므로 절대값이 아닌 상대 비교용 |

## 2. 변경 내용 (추가 인덱스)

| 인덱스 | 대상 | 목적 | 크기 (빌드 직후) |
|---|---|---|---|
| `IDX_PLAYLISTS_OWNER_ID (owner_id)` | playlists | 소유자 필터 | 1,616 kB |
| `IDX_PLAYLISTS_SUBSCRIBER_COUNT_ID (subscriber_count DESC, id DESC)` | playlists | 구독자 수 정렬 | 7,960 kB |
| `IDX_SUBSCRIPTIONS_SUBSCRIBER_PLAYLIST (subscriber_id, playlist_id)` | subscriptions | 구독 필터 | 47 MB |
| `IDX_PLAYLISTS_TITLE_TRGM` (GIN, `lower(title) gin_trgm_ops`) | playlists | 제목 키워드 검색 | 6,408 kB |
| `IDX_PLAYLISTS_DESCRIPTION_TRGM` (GIN, `lower(description) gin_trgm_ops`) | playlists | 설명 키워드 검색 | 7,880 kB |

- 위 5개의 합계는 약 70 MB이며, 이 중 구독 인덱스가 47 MB로 가장 크다.
- `IDX_PLAYLISTS_UPDATED_AT_ID (updated_at DESC, id DESC)`(7,960 kB)는 측정의 "전" 상태에도 남겨 두었다. 다만 `origin/dev`의 마이그레이션과 엔티티에는 이 인덱스를 만드는 곳이 없고 `V202611121140`이 만든다. 따라서 이 인덱스를 쓰는 쿼리(공통 count, L1, L6 전, L7)의 "전" 값에는 이 인덱스가 이미 있는 상태가 반영되어 있다. 인덱스가 없는 상태라면 "전"은 더 느려질 것으로 예상하므로 개선 폭을 부풀린 값은 아니다.
- 트라이그램 인덱스는 `pg_trgm` 확장이 필요하다 (`V202611121350__add_playlist_keyword_trgm_indexes.sql`).

## 3. 측정 방법

**개선 전 상태 만들기:** 위 신규 인덱스 5개를 `DROP INDEX`한 뒤 `ANALYZE`했다. 개선 후는 인덱스를 복원하고 `ANALYZE`했다.

**JMeter**
- 20 users / ramp-up 120초
- 상태마다 서버 재시작 → 워밍업 1회 → 기록 1회 (워밍업 결과는 버림)
- 각 상태 1회 측정이며 평균 응답시간(ms)을 비교했다. 에러는 전후 모두 0건이다.
- 파라미터: 사용자와 조건은 전후 동일하다. 단, L4(희귀 키워드)의 JMeter 키워드는 <br>`update_list_params.py`가 실행마다 제목을 무작위(`random()`, 시드 없음)로 뽑아 전후가 서로 다르다. <br>같은 키워드로 비교한 증거는 5장의 EXPLAIN(`여행 플레이리스트 78369`)이며, JMeter의 L4 값은 보조 자료로 본다.
- JMeter의 "개선 후"는 소유자 인덱스를 단일 컬럼으로 바꾸기 전(복합 인덱스 `(owner_id, updated_at DESC, id DESC)`) 상태에서 측정했다. 변경 후에는 JMeter를 재측정하지 않았다 (4장, 8장).

**EXPLAIN (ANALYZE, BUFFERS)**
- 같은 파라미터: 사용자 `b034b160-e9c0-44e4-bf88-0019d360412a`, 희귀 키워드 `여행 플레이리스트 78369`, 흔한 키워드 `운동`
- 각 쿼리를 2번 실행하고 첫 번째는 버렸다 (캐시 상태 통일).
- 개선 전은 `BEGIN; DROP INDEX ...; EXPLAIN ...; ROLLBACK;`으로 신규 인덱스를 일시적으로 제거한 상태에서 측정했다.
- 쿼리는 앱이 보내는 SQL 그대로가 아니라 같은 모양의 근사 쿼리다.
- 소유자 인덱스를 단일 컬럼으로 바꾼 뒤에는 로컬 DB에 마이그레이션을 다시 적용하고 `ANALYZE` 후 같은 스크립트를 다시 실행했다 (`explain_after_owner_id.txt`). 5장의 L5 "후" 행은 이 파일 기준이고, 나머지 행은 변경 전에 측정한 `explain_after.txt` 기준이다.

## 4. 응답시간 비교 (JMeter, 평균 ms)

| 라벨 | 개선 전 | 개선 후 | 비고 |
|---|---|---|---|
| CSRF 토큰 (대조군) | 22 | 27 | 인덱스와 무관 |
| 로그인 (대조군) | 289 | 288 | 인덱스와 무관 |
| L1 updatedAt DESC | 187 | 159 | 기존 인덱스 사용, 1회 측정이라 해석 보류 |
| L2 subscribeCount DESC | 357 | 57 | `IDX_PLAYLISTS_SUBSCRIBER_COUNT_ID` |
| L3 키워드(흔한 단어) | 307 | 188 | 트라이그램 효과 대상 아님, 해석 보류 |
| L4 키워드(희귀, 제목 전체) | 1218 | 136 | GIN 트라이그램 (키워드가 전후로 다름, 5장 EXPLAIN 참고) |
| L5 소유자 필터 | 274 | 38 | 복합 소유자 인덱스 상태에서 측정 (아래 참고) |
| L6 구독자 필터 | 1281 | 44 | `IDX_SUBSCRIPTIONS_SUBSCRIBER_PLAYLIST` |
| L7 2페이지 cursor | 178 | 124 | 인덱스 대상 아님, 해석 보류 |

- 인덱스와 무관한 대조군(CSRF, 로그인)과 처리량(1.5/s)이 전후로 같아 부하 조건이 동일했다고 판단했다.
- 인덱스가 겨냥한 L2, L4, L5, L6만 개선으로 해석한다. L1, L3, L7의 변화는 단일 측정이라 개선으로 주장하지 않는다.
- L5의 개선 후 값(38 ms)은 소유자 인덱스를 단일 컬럼 `IDX_PLAYLISTS_OWNER_ID`로 바꾸기 전에 측정한 값이다. 바꾼 뒤에는 같은 계획이 선택되고 쿼리 비용이 같은 수준임을 EXPLAIN으로 확인했으나(8장) JMeter는 다시 돌리지 않았다.

## 5. 실행계획 비교 (EXPLAIN)

| 쿼리 | 구분 | 사용 인덱스 | 스캔 방식 | Index Cond / Filter | 추가 정렬 | actual rows | Buffers | 실행시간 |
|---|---|---|---|---|---|---|---|---|
| 공통 count | 전 | `IDX_PLAYLISTS_UPDATED_AT_ID` | Parallel Index Only Scan | 없음 | 없음 | 66,681 × 3 | hit 9,530 | 21.4 ms |
| 공통 count | 후 | `IDX_PLAYLISTS_SUBSCRIBER_COUNT_ID` | Parallel Index Only Scan | 없음 | 없음 | 66,681 × 3 | hit 9,462 | 20.0 ms |
| L2 구독자 수 정렬 | 전 | 없음 | Parallel Seq Scan + Hash Join | 없음 | top-N heapsort | 66,681 × 3 읽고 21건 반환 | hit 8,782 / read 318 | 94.9 ms |
| L2 구독자 수 정렬 | 후 | `IDX_PLAYLISTS_SUBSCRIBER_COUNT_ID` | Index Scan | 없음 (정렬 순서 그대로 읽음) | 없음 | 21 | hit 84 | 0.23 ms |
| L3 흔한 키워드 count | 전 | 없음 | Parallel Seq Scan | Filter `lower(title) LIKE '%운동%' OR ...` | 없음 | 3,333 × 3 (제거 63,347) | hit 8,161 / read 130 | 105.9 ms |
| L3 흔한 키워드 count | 후 | 없음 | Parallel Seq Scan | 동일 | 없음 | 3,333 × 3 | hit 7,781 / read 510 | 113.1 ms |
| L4 희귀 키워드 | 전 | 없음 | Parallel Seq Scan | Filter (66,680 제거 × 3) | quicksort | 1 | hit 8,382 | 114.3 ms |
| L4 희귀 키워드 | 후 | `IDX_PLAYLISTS_TITLE_TRGM` + `IDX_PLAYLISTS_DESCRIPTION_TRGM` | BitmapOr → Bitmap Heap Scan | Recheck Cond (LIKE) | quicksort 유지 | 1 | hit 606 | 11.3 ms |
| L4 희귀 키워드 count | 전 → 후 | Seq Scan → 트라이그램 Bitmap | | | | | hit 8,291 → 603 | 157.2 → 10.7 ms |
| L5 소유자 필터 | 전 | 없음 | Parallel Seq Scan | Filter `owner_id =` (66,674 제거 × 3) | quicksort | 20 | hit 8,477 | 18.7 ms |
| L5 소유자 필터 | 후 | `IDX_PLAYLISTS_OWNER_ID` | Bitmap Index → Bitmap Heap Scan | Index Cond `owner_id =` | quicksort 유지 | 21 | hit 12 | 0.07 ms |
| L5 count | 전 → 후 | Seq Scan → Bitmap Heap Scan | | | | | hit 8,291 → 9 | 17.5 → 0.03 ms |
| L6 구독 필터 | 전 | `IDX_PLAYLISTS_UPDATED_AT_ID` + `UQ_SUBSCRIPTIONS_PLAYLIST_SUBSCRIBER` | playlists 전체를 updated_at 순으로 걷고 구독 여부를 건건이 조회 (loops 200,042) | Index Cond `playlist_id, subscriber_id` | 없음 (인덱스 순서 + Limit) | 21 | hit 798,405 / read 3,048 | 360.6 ms |
| L6 구독 필터 | 후 | `IDX_SUBSCRIPTIONS_SUBSCRIBER_PLAYLIST` | Index Only Scan Backward → PK 조회 | Index Cond `subscriber_id =` | top-N heapsort 추가 | 102개 중 21 반환 | hit 719 | 0.72 ms |
| L6 count | 전 → 후 | Seq Scan(subscriptions) → Index Only Scan | | | | | hit 412 + read 10,177 → 317 | 35.1 → 0.19 ms |

- 원문: [explain_before.txt](./explain/explain_before.txt), [explain_after.txt](./explain/explain_after.txt) (소유자 인덱스 변경 전), [explain_after_owner_id.txt](./explain/explain_after_owner_id.txt) (소유자 인덱스 변경 후)
- L5 "후" 행은 `explain_after_owner_id.txt`, 나머지 행은 `explain_after.txt` 기준이다.
- L5 count는 소유자 인덱스를 복합으로 둘 때는 Index Only Scan(hit 5, 0.02 ms)이었다. 앱의 count 쿼리는 `count(id)`인데 단일 컬럼 인덱스에는 `id`가 없어 테이블을 읽는 Bitmap Heap Scan(hit 9, 0.03 ms)으로 바뀌었다. 소유자당 22건 기준 차이는 미미하다.
- 측정하지 않은 쿼리: L3의 본 쿼리(count만 측정), L1(기본 정렬), L7(커서 2페이지). L1과 L7은 이번 인덱스의 대상이 아니라서 제외했다.
- Buffers에 `read`가 일부 남아 있어 완전히 캐시에 올라간 상태는 아니다. 비교는 `hit`/`read` 합계와 실행시간을 함께 본다.

## 6. 인덱스별 적절성 판단과 한계

- **L2:** 인덱스 순서와 정렬이 일치해 정렬이 사라지고, 정렬 순서대로 21건만 읽는다.
- **L4:** 긴 키워드는 GIN 트라이그램을 사용한다. 개선 전후 검색 결과 건수가 동일함을 확인했다 (111 = 111). 정렬(quicksort)은 남아 있지만 결과가 1건이라 비용이 거의 없다.
- **L5:** 정렬(quicksort)은 그대로 남는다. 소유자당 플레이리스트가 약 20건이라 정렬 비용이 거의 없고, 해당 사용자의 행만 읽는다. 인덱스 구성은 8장에서 검토했다.
- **L6:** 개선 전에는 `updated_at` 인덱스 순서를 이용해 정렬을 피했지만 playlists 약 20만 건을 전부 걸으며 구독 여부를 조회했다. <br>개선 후에는 구독 102건만 읽고 top-N 정렬을 새로 하지만 총 비용은 훨씬 작다. 구독 수가 수만 건으로 늘면 이 정렬 비용이 커질 수 있다.
- **공통 count:** 플래너가 다른 인덱스(`SUBSCRIBER_COUNT_ID`)를 골랐을 뿐 시간은 사실상 같다 (21.4 → 20.0 ms, 개선 아님). <br>약 20ms로 병목이 아니라고 판단해 캐싱 등은 하지 않았다.
- **한계 1:** 2글자 이하 키워드(`LIKE '%x%'`, 예: `운동`)는 트라이그램 인덱스를 사용할 수 없어 Seq Scan이 유지된다 (L3 참고).
- **한계 2:** `exists` 서브쿼리는 플래너가 조인으로 변환해서 별도 수정이 필요 없었다.
- **한계 3:** JMeter는 각 상태 1회 측정이므로 작은 차이는 해석하지 않는다.
- **한계 4:** JMeter의 L4 키워드가 전후로 달라 L4의 정확한 비교는 EXPLAIN 기준이다.

## 7. 쓰기 비용

앱 수준 쓰기 측정(`playlist-smoke.jmx`)은 최신 dev의 알림 연동(`NotificationDomainEventListener.handlePlaylistCreated`)에서 HikariCP 커넥션 풀 고갈이 발생했다. <br>인덱스를 제거한 개선 전 상태에서도 같은 현상이 재현되어 이번 변경과 무관하며, 비교에는 사용할 수 없었다.

이를 대신해 DB 수준 마이크로 벤치(트랜잭션 + ROLLBACK, `write_bench.sql`)로 측정했다.

- 구독 INSERT 2,000건
- `subscriber_count` UPDATE 2,000건
- `subscriber_count + updated_at` UPDATE 2,000건

개선 전후 간 구분 가능한 차이가 없었다 (문장당 약 1ms 이하이고 반복 간 노이즈가 더 큼).
인덱스가 늘어난 만큼 쓰기 비용이 0이라는 뜻은 아니며, 이 측정 환경에서는 노이즈 이하였다는 의미다.

이 벤치는 소유자 인덱스를 복합으로 둘 때 측정했고, 단일 컬럼으로 바꾼 뒤에는 다시 측정하지 않았다. `updated_at`이 갱신되어도 소유자 인덱스는 갱신할 필요가 없어졌으므로 쓰기 비용은 같거나 줄었을 것으로 추정하지만 측정한 값은 아니다.

## 8. 인덱스 구성 검토 (리뷰 반영)

리뷰에서 기존 인덱스를 활용하거나 더 작은 인덱스로 같은 성능을 확보할 수 있는지, 특히 소유자 필터를 `owner_id` 단독 인덱스와 비교해 보라는 의견이 있었다. 목록 쿼리와 count 쿼리를 기준으로 비교했다.

**측정 조건**
- loadtest DB(playlists 200,062 / subscriptions 약 100만), 같은 사용자 `b034b160-e9c0-44e4-bf88-0019d360412a`
- 각 비교 전 `VACUUM (ANALYZE)`를 실행해 통계와 가시성 정보를 맞췄다.
- 후보 인덱스는 `BEGIN; DROP INDEX ...; CREATE INDEX ...; EXPLAIN ...; ROLLBACK;`으로 일시 적용했다. 각 쿼리는 여러 번 실행하고 캐시된 상태의 마지막 결과를 기록했다.
- 실행시간은 실행마다 0.1 ~ 5 ms로 흔들려서, 읽은 버퍼 수와 계획 모양을 판단 근거로 삼았다.
- 쿼리는 `playlists`와 `subscriptions`만 쓰는 단순화한 쿼리다 (users 조인 제외).

**소유자 필터: 복합 `(owner_id, updated_at DESC, id DESC)` 대 단일 `(owner_id)`**

| | 복합 | 단일 |
|---|---|---|
| 목록 계획 | Bitmap Index → Bitmap Heap → Sort | 동일 |
| 목록 버퍼 | 10 | 9 |
| count 계획 (앱의 `count(id)` 기준) | Index Only Scan, hit 5 | Bitmap Heap Scan, hit 9 |
| 인덱스 크기 | 약 11 MB | 1,616 kB |

- 복합 인덱스의 `updated_at`, `id` 컬럼은 이 계획에서 정렬에 쓰이지 않았다. 비트맵 스캔은 인덱스 순서를 유지하지 않기 때문에 정렬(Sort)이 남는다.
- 단일 컬럼은 크기가 약 7분의 1이고 목록 성능은 같다. count는 `id`가 인덱스에 없어 테이블을 읽게 되지만 소유자당 22건 기준으로 hit 5 → 9, 0.02 → 0.03 ms 차이다. (`count(*)`로 단순화하면 둘 다 Index Only Scan이 되어 이 차이가 보이지 않는다.)
- **한계:** 시드 데이터는 소유자당 최대 22건으로 고르게 분포해 있다. 플레이리스트를 수천 건 이상 가진 소유자에서 복합 인덱스가 정렬 생략으로 이득을 주는지는 이번 데이터로 측정하지 못했다.
- **결정:** 소유자당 플레이리스트가 적은 현재 사용 패턴에서는 단일 컬럼이 충분하다고 판단해 `IDX_PLAYLISTS_OWNER_ID (owner_id)`로 변경했다.

**구독 필터: 현재 `(subscriber_id, playlist_id)` 대 단일 `(subscriber_id)` 대 새 인덱스 없음**

| | 현재 | 단일 `(subscriber_id)` | 새 인덱스 없음 (기존 UQ만) |
|---|---|---|---|
| 구독 쪽 계획 | Index Only Scan Backward | Bitmap Heap Scan (테이블 102페이지 읽음) | Parallel Seq Scan (구독 약 100만 건) |
| 목록 전체 버퍼 | 429 (구독 21 + PK 408) | 513 (구독 105 + PK 408) | 약 10,800 (hit 954 + read 9,841) |
| count 전체 버퍼 | 328 | 412 | 측정 안 함 |
| 실행시간 (캐시된 상태) | 1.1 ms | 0.8 ms | 50.9 ms (첫 실행, 디스크 읽기 포함) |
| 인덱스 크기 | 47 MB | 7,040 kB | 없음 |

- 기존 `UQ_SUBSCRIPTIONS_PLAYLIST_SUBSCRIBER (playlist_id, subscriber_id)`는 선두 컬럼이 `playlist_id`라서 `subscriber_id`로 찾는 이 쿼리에 쓸 수 없다. 기존 인덱스만으로는 풀 스캔이 된다.
- 단일 컬럼 인덱스는 크기가 약 7분의 1이지만 `playlist_id`를 인덱스에서 읽지 못해 구독 행마다 테이블 페이지를 읽는다. 구독이 100건일 때는 메모리 안에서 시간 차이가 없었으나, 구독이 많은 사용자나 캐시가 비어 있을 때는 읽는 페이지 수가 구독 수에 비례해 늘어난다.
- **결정:** `(subscriber_id, playlist_id)`를 유지한다. 대신 이 인덱스가 이번 변경에서 가장 큰 저장 공간(47 MB)을 차지한다는 점을 비용으로 명시한다.

**그 외 인덱스**
- `IDX_PLAYLISTS_SUBSCRIBER_COUNT_ID`(구독/취소마다 갱신), 제목·설명 GIN 인덱스는 이번 검토에서 대안을 비교하지 않았다. 쓰기 비용은 7장의 DB 수준 벤치에서 구분 가능한 차이가 없었다.
- 사용 빈도: 운영 트래픽 데이터가 없다. 요구사항상 프로필 페이지에서 보유·구독 플레이리스트를 조회하므로 소유자 필터와 구독자 필터가 쓰일 것으로 본다(추정). `pg_stat_user_indexes`의 `idx_scan`은 부하 테스트를 반복하며 쌓인 누적값이라 사용 빈도 근거로 쓰지 않았다.

**소유자 인덱스 변경 후 전체 EXPLAIN 재실행 (`explain_after_owner_id.txt`, 실행시간 ms)**

| 쿼리 | 기존 후 | 재실행 |
|---|---|---|
| 공통 count | 20.0 | 19.5 |
| L2 구독자 수 정렬 | 0.23 | 0.26 |
| L3 흔한 키워드 count | 113.1 | 98.3 |
| L4 희귀 키워드 본 쿼리 / count | 11.3 / 10.7 | 6.7 / 7.5 |
| L5 소유자 필터 본 쿼리 / count | 0.09 / 0.02 | 0.07 / 0.03 |
| L6 구독 필터 본 쿼리 / count | 0.72 / 0.19 | 1.16 / 0.25 |

L5 외 쿼리는 인덱스를 바꾸지 않았고, 값의 차이는 단일 실행의 흔들림 범위다.

## 9. 배포 시 확인 사항

- 트라이그램 인덱스 마이그레이션은 `CREATE EXTENSION IF NOT EXISTS pg_trgm`을 포함한다. <br>운영 DB 계정에 권한이 없으면 첫 배포의 Flyway가 실패할 수 있어, 배포 담당자 박경석 님이 첫 배포 시 Flyway 로그에서 해당 마이그레이션 적용 여부를 확인하기로 했다.