# 플레이리스트 목록 조회 인덱스 개선 비교 (이슈 #22)

개선 전후를 같은 데이터, 같은 쿼리, 같은 워밍업 절차로 측정한 결과다. 파라미터는 L4(희귀 키워드)의 JMeter 값만 전후가 다르다(3장 참고).

## 1. 측정 환경

| 항목 | 내용 |
|---|---|
| 앱 / DB | 로컬 Spring Boot 4.0.8 (Java 17), PostgreSQL 16 (Docker) |
| 코드 기준 | 최신 `origin/dev` 머지 후 (개선 전후 동일 코드) |
| 데이터 | playlists 200,000 / subscriptions 995,613 / users 10,001 (`seed_playlist.sql`) |
| 통계 | 각 상태에서 `ANALYZE` 실행 후 측정 |
| 부하 도구 | JMeter, `playlist-list-variants.jmx` |
| 파라미터 | `update_list_params.py`로 생성 (200행). L4 키워드는 전후가 다름 (3장 참고) |
| 환경 한계 | 앱, DB, JMeter가 한 노트북에서 함께 동작하므로 절대값이 아닌 상대 비교용 |

## 2. 변경 내용 (추가 인덱스)

| 인덱스 | 대상 | 목적 |
|---|---|---|
| `IDX_PLAYLISTS_OWNER_UPDATED_AT_ID (owner_id, updated_at DESC, id DESC)` | playlists | 소유자 필터 |
| `IDX_PLAYLISTS_SUBSCRIBER_COUNT_ID (subscriber_count DESC, id DESC)` | playlists | 구독자 수 정렬 |
| `IDX_SUBSCRIPTIONS_SUBSCRIBER_PLAYLIST (subscriber_id, playlist_id)` | subscriptions | 구독 필터 |
| `IDX_PLAYLISTS_TITLE_TRGM` (GIN, `lower(title) gin_trgm_ops`) | playlists | 제목 키워드 검색 |
| `IDX_PLAYLISTS_DESCRIPTION_TRGM` (GIN, `lower(description) gin_trgm_ops`) | playlists | 설명 키워드 검색 |

- 기존 `IDX_PLAYLISTS_UPDATED_AT_ID (updated_at DESC, id DESC)`는 개선 전후 모두 존재한다.
- 트라이그램 인덱스는 `pg_trgm` 확장이 필요하다 (`V202611121350__add_playlist_keyword_trgm_indexes.sql`).

## 3. 측정 방법

**개선 전 상태 만들기:** 위 신규 인덱스 5개를 `DROP INDEX`한 뒤 `ANALYZE`했다. 개선 후는 인덱스를 복원하고 `ANALYZE`했다.

**JMeter**
- 20 users / ramp-up 120초
- 상태마다 서버 재시작 → 워밍업 1회 → 기록 1회 (워밍업 결과는 버림)
- 각 상태 1회 측정이며 평균 응답시간(ms)을 비교했다. 에러는 전후 모두 0건이다.
- 파라미터: 사용자와 조건은 전후 동일하다. 단, L4(희귀 키워드)의 JMeter 키워드는 <br>`update_list_params.py`가 실행마다 제목을 무작위(`random()`, 시드 없음)로 뽑아 전후가 서로 다르다. <br>같은 키워드로 비교한 증거는 5장의 EXPLAIN(`여행 플레이리스트 78369`)이며, JMeter의 L4 값은 보조 자료로 본다.

**EXPLAIN (ANALYZE, BUFFERS)**
- 같은 파라미터: 사용자 `b034b160-e9c0-44e4-bf88-0019d360412a`, 희귀 키워드 `여행 플레이리스트 78369`, 흔한 키워드 `운동`
- 각 쿼리를 2번 실행하고 첫 번째는 버렸다 (캐시 상태 통일).
- 개선 전은 `BEGIN; DROP INDEX ...; EXPLAIN ...; ROLLBACK;`으로 신규 인덱스를 일시적으로 제거한 상태에서 측정했다.
- 쿼리는 앱이 보내는 SQL 그대로가 아니라 같은 모양의 근사 쿼리다.

## 4. 응답시간 비교 (JMeter, 평균 ms)

| 라벨 | 개선 전 | 개선 후 | 비고 |
|---|---|---|---|
| CSRF 토큰 (대조군) | 22 | 27 | 인덱스와 무관 |
| 로그인 (대조군) | 289 | 288 | 인덱스와 무관 |
| L1 updatedAt DESC | 187 | 159 | 기존 인덱스 사용, 1회 측정이라 해석 보류 |
| L2 subscribeCount DESC | 357 | 57 | `IDX_PLAYLISTS_SUBSCRIBER_COUNT_ID` |
| L3 키워드(흔한 단어) | 307 | 188 | 트라이그램 효과 대상 아님, 해석 보류 |
| L4 키워드(희귀, 제목 전체) | 1218 | 136 | GIN 트라이그램 (키워드가 전후로 다름, 5장 EXPLAIN 참고) |
| L5 소유자 필터 | 274 | 38 | `IDX_PLAYLISTS_OWNER_UPDATED_AT_ID` |
| L6 구독자 필터 | 1281 | 44 | `IDX_SUBSCRIPTIONS_SUBSCRIBER_PLAYLIST` |
| L7 2페이지 cursor | 178 | 124 | 인덱스 대상 아님, 해석 보류 |

- 인덱스와 무관한 대조군(CSRF, 로그인)과 처리량(1.5/s)이 전후로 같아 부하 조건이 동일했다고 판단했다.
- 인덱스가 겨냥한 L2, L4, L5, L6만 개선으로 해석한다. L1, L3, L7의 변화는 단일 측정이라 개선으로 주장하지 않는다.

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
| L5 소유자 필터 | 후 | `IDX_PLAYLISTS_OWNER_UPDATED_AT_ID` | Bitmap Index → Bitmap Heap Scan | Index Cond `owner_id =` | quicksort 유지 | 20 | hit 13 | 0.09 ms |
| L5 count | 전 → 후 | Seq Scan → Index Only Scan | | | | | hit 8,291 → 5 | 17.5 → 0.02 ms |
| L6 구독 필터 | 전 | `IDX_PLAYLISTS_UPDATED_AT_ID` + `UQ_SUBSCRIPTIONS_PLAYLIST_SUBSCRIBER` | playlists 전체를 updated_at 순으로 걷고 구독 여부를 건건이 조회 (loops 200,042) | Index Cond `playlist_id, subscriber_id` | 없음 (인덱스 순서 + Limit) | 21 | hit 798,405 / read 3,048 | 360.6 ms |
| L6 구독 필터 | 후 | `IDX_SUBSCRIPTIONS_SUBSCRIBER_PLAYLIST` | Index Only Scan Backward → PK 조회 | Index Cond `subscriber_id =` | top-N heapsort 추가 | 102개 중 21 반환 | hit 719 | 0.72 ms |
| L6 count | 전 → 후 | Seq Scan(subscriptions) → Index Only Scan | | | | | hit 412 + read 10,177 → 317 | 35.1 → 0.19 ms |

- 원문: [explain_before.txt](./explain/explain_before.txt), [explain_after.txt](./explain/explain_after.txt)
- 측정하지 않은 쿼리: L3의 본 쿼리(count만 측정), L1(기본 정렬), L7(커서 2페이지). L1과 L7은 이번 인덱스의 대상이 아니라서 제외했다.
- Buffers에 `read`가 일부 남아 있어 완전히 캐시에 올라간 상태는 아니다. 비교는 `hit`/`read` 합계와 실행시간을 함께 본다.

## 6. 인덱스별 적절성 판단과 한계

- **L2:** 인덱스 순서와 정렬이 일치해 정렬이 사라지고, 정렬 순서대로 21건만 읽는다.
- **L4:** 긴 키워드는 GIN 트라이그램을 사용한다. 개선 전후 검색 결과 건수가 동일함을 확인했다 (111 = 111). 정렬(quicksort)은 남아 있지만 결과가 1건이라 비용이 거의 없다.
- **L5:** 정렬(quicksort)은 그대로 남는다. 대신 해당 사용자의 20건만 읽어서 비용이 작아졌다.
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

## 8. 배포 시 확인 사항

- 트라이그램 인덱스 마이그레이션은 `CREATE EXTENSION IF NOT EXISTS pg_trgm`을 포함한다. <br>운영 DB 계정에 권한이 없으면 첫 배포의 Flyway가 실패할 수 있어, 배포 담당자 박경석 님이 첫 배포 시 Flyway 로그에서 해당 마이그레이션 적용 여부를 확인하기로 했다.