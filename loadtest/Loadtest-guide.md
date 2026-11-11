# loadtest

JMeter로 API 부하테스트를 재현하기 위한 시드 데이터, 시나리오, 스크립트 모음입니다.
도메인별로 어느 API가 어느 부하에서 느려지는지 찾는 것이 목적이며, 측정 결과는 [`loadtest-results.md`](loadtest-results.md)와 관련 PR(이슈 #163)에 정리합니다.

## 현재 범위

| 도메인 | 시드 | 시나리오 | 측정 대상 |
|---|---|---|---|
| 플레이리스트 | `seed_playlist.sql` | `playlist-smoke.jmx` | 목록·생성·단건 조회·수정·구독·구독 취소·삭제 |
| 플레이리스트 목록 조건별 | `seed_playlist.sql` | `playlist-list-variants.jmx` | 정렬·키워드·소유자·구독자 필터·커서 페이지 |
| 플레이리스트 콘텐츠 | `seed_content.sql` | `playlist-content-scenario.jmx` | 콘텐츠 추가·삭제 |
| 사용자 인증·보안 | `seed_playlist.sql` | `login-scenario.jmx`, `user-scenario.jmx` | 로그인, 회원가입, 계정 잠금·해제, 권한 변경 |
| 팔로우 | `seed_follow.sql` | `follow-scenario.jmx` | 팔로워 수, 팔로우 여부, 팔로우, 언팔로우 |
| 알림 | `seed_notification.sql` | `notification-scenario.jmx` | 목록(첫 페이지·cursor·오래된순), 삭제 |
| 콘텐츠 | `seed_content.sql` | `content-scenario.jmx` | 단건 조회, 삭제 (생성·수정은 S3가 필요해 제외) |

## 파일

| 파일 | 설명 | Git |
|---|---|---|
| `seed_playlist.sql` | 더미 데이터 생성 (사용자 10,000 / 플레이리스트 200,000 / 구독 약 100만) | 포함 |
| `seed_follow.sql` | 팔로우 약 290,000건 | 포함 |
| `seed_notification.sql` | 알림 약 1,380,000건 | 포함 |
| `seed_content.sql` | 콘텐츠 11,000건 · 리뷰 50,000건 · 태그 · 플레이리스트 연결, `loadtest1~200`을 ADMIN으로 승격 | 포함 |
| `*.jmx` | 도메인별 JMeter 시나리오 (위 표) | 포함 |
| `update_csv_200.py` | 현재 DB 기준으로 `users.csv`를 생성 (`playlist-smoke.jmx`용) | 포함 |
| `update_list_params.py`, `update_follow_params.py`, `update_notification_params.py`, `update_content_params.py`, `update_plcontent_params.py`, `update_user_params.py` | 시나리오별 파라미터 CSV 생성 (사용자·대상 ID는 DB마다 달라서 실행 때마다 생성) | 포함 |
| `explain_list.sql`, `explain_notification.sql` | 느린 쿼리의 실행 계획 확인용 | 포함 |
| `loadtest-results.md` | 측정 결과와 원인 확인 | 포함 |
| `users.csv`, `*_params.csv` | 가상 사용자별 계정·대상 ID | 제외 (생성물) |
| `jmeter.log`, `results_*.jtl`, `report_*/` | 실행 결과물 | 제외 (생성물) |

결과물과 생성된 CSV는 `.gitignore`에 들어 있어야 합니다.

```
loadtest/*_params.csv
loadtest/jmeter.log
```

## 준비물

- Docker, JDK 17
- JMeter 5.6.3 (macOS: `brew install jmeter`)
- Python 3
- 프로젝트 루트의 `.env.dev`

모든 명령은 **프로젝트 루트**에서 실행합니다.

## 재현 순서

### 1. DB 준비 (최초 1회)

```bash
docker compose --env-file .env.dev up -d postgres
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d everyone -c "CREATE DATABASE loadtest;"
```

`loadtest` DB는 개발 DB(`everyone`)와 같은 컨테이너 안의 별도 DB입니다. 이미 있다고 나오면 그대로 다음 단계로 갑니다.
`.env.dev`에서 DB 사용자 이름을 바꿨다면 `-U` 값도 바꿔 주세요. Redis도 필요하므로 `docker compose --env-file .env.dev up -d`로 함께 띄웁니다.

### 2. 더미 데이터 생성

`seed_playlist.sql`을 가장 먼저 실행합니다. 다른 시드는 이 데이터(사용자, 플레이리스트)를 전제로 합니다.

```bash
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -v ON_ERROR_STOP=1 < loadtest/seed_playlist.sql
```

- 현재 DB가 `loadtest`가 아니면 스크립트가 즉시 중단됩니다.
- 다시 실행하면 이전 시드를 지우고 새로 만듭니다. 몇 분 걸릴 수 있습니다.
- 테스트 계정은 `loadtest1@example.com` ~ `loadtest10000@example.com`이고, 비밀번호는 `seed_playlist.sql` 머리말 주석에 있습니다.

다른 도메인을 측정하려면 해당 시드를 이어서 실행합니다. 파일 이름만 바꿉니다.

```bash
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -v ON_ERROR_STOP=1 < loadtest/seed_follow.sql
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -v ON_ERROR_STOP=1 < loadtest/seed_notification.sql
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -v ON_ERROR_STOP=1 < loadtest/seed_content.sql
```

- `seed_follow.sql`, `seed_notification.sql`은 다시 실행하면 자기 데이터만 지우고 다시 만듭니다.
- `seed_content.sql`은 이 스크립트가 만든 콘텐츠(`lt-c-*`, `LT-DEL-*`)만 지우고 다시 만듭니다. 삭제 시나리오는 대상을 실제로 지우므로 실행 전마다 다시 실행합니다.
- `seed_content.sql`은 `loadtest1~200`을 ADMIN으로 올립니다. 콘텐츠 삭제와 권한 변경 API가 ADMIN 전용이기 때문입니다. 로컬 `loadtest` DB에만 해당합니다.
- 다른 시드를 만든 뒤 `seed_playlist.sql`을 다시 실행하면 이 사용자들을 참조하는 데이터(팔로우, 알림, 리뷰 등) 때문에 삭제가 막힐 수 있습니다. 이 경우에는 `loadtest` DB를 지우고 처음부터 다시 만드는 것이 안전합니다. (재실행해서 확인하지는 않았습니다)

### 3. 서버 실행

```bash
EVERYONE_DOCKER_COMPOSE_ENABLED=false EVERYONE_DB_NAME=loadtest JWT_ACCESS_TOKEN_VALIDITY=3h ./gradlew bootRun --args='--logging.level.root=warn --logging.level.org.hibernate=warn --logging.level.org.hibernate.SQL=off --logging.level.org.hibernate.orm.jdbc.batch=warn --logging.level.com.codeit.sb13.playlist_for_everyone=warn'
```

- 반드시 이 명령 그대로 터미널에서 실행합니다. IntelliJ의 Run 버튼으로 띄우면 환경변수가 빠집니다.
- `EVERYONE_DB_NAME=loadtest`가 없으면 서버가 `everyone` DB에 연결되어 테스트 계정이 없고 401이 쏟아집니다. `.env.dev`의 값을 환경변수가 덮어쓰는 구조입니다.
- `JWT_ACCESS_TOKEN_VALIDITY=3h`는 테스트 중 액세스 토큰이 만료되지 않게 하기 위한 값입니다.
- 로그 레벨 옵션은 로그 출력이 측정에 영향을 주지 않게 줄이는 설정입니다. `application-dev.yaml`이 Hibernate batch 로그를 TRACE로 켜 두기 때문에 `org.hibernate.orm.jdbc.batch=warn`까지 필요합니다.
- 로그 레벨을 warn으로 올렸으므로 `Started ...` 같은 줄이 나오지 않습니다. 로그가 조용해지면 기동된 것입니다(30초 정도 기다립니다).
- DB 커넥션 풀은 HikariCP 기본값(10개)을 씁니다.

### 4. 측정 (플레이리스트 기본 시나리오)

```bash
python3 loadtest/update_csv_200.py
rm -rf report results.jtl
jmeter -n -t loadtest/playlist-smoke.jmx -l results.jtl -e -o report
open report/index.html
```

- `update_csv_200.py`는 현재 `loadtest` DB에서 구독 대상 플레이리스트를 뽑아 `loadtest/users.csv`를 만듭니다. **시드를 다시 실행했다면 반드시 다시 실행하세요.** 플레이리스트 ID가 새로 만들어지므로 예전 CSV로 돌리면 구독 요청이 전부 404가 됩니다. JMeter는 이 파일을 읽기만 하고 만들어 주지 않습니다.
- 반드시 CLI(`-n`)로 실행합니다. GUI로 부하를 걸면 JMeter 자체가 느려져 결과가 왜곡됩니다.
- 첫 리포트의 `Statistics` 표에서 API별 평균, 95th, 99th, Error %를 확인합니다.
- `-Jthreads=사용자수 -Jramp=접속시간(초)`로 사용자 수와 접속 시간을 바꿀 수 있습니다. 기본값은 200명, 60초입니다.

## 측정 조건

| 구분 | 옵션 | 의미 |
|---|---|---|
| 평시 | `-Jthreads=200 -Jramp=60` | 200명이 60초에 걸쳐 접속. 실제 동시 활동은 1~4명 |
| 부하 | `-Jthreads=200 -Jramp=5` | 200명이 5초 안에 몰림. 동시 활동 약 200명 |

- 각 조건을 **두 번** 실행하고 두 번째 값을 기록합니다. 첫 실행은 워밍업입니다.
- JMeter는 결과 폴더(`-o`)가 이미 있으면 실행되지 않습니다. 새 이름을 쓰거나 이전 결과를 지웁니다.

### 결과 표 보기 (요청별 평균·p95·p99·에러)

```bash
python3 - results_이름.jtl <<'EOF'
import csv,sys
d={}
for r in csv.DictReader(open(sys.argv[1])):
    d.setdefault(r['label'],[]).append((int(r['elapsed']),r['success'],r['responseCode']))
for k,v in sorted(d.items()):
    e=sorted(x for x,_,_ in v); n=len(e)
    codes={}
    for _,ok,c in v:
        if ok!='true': codes[c]=codes.get(c,0)+1
    print(k,n,'avg',round(sum(e)/n),'p95',e[int(n*.95)-1],'p99',e[int(n*.99)-1],'max',e[-1],'err',codes)
EOF
```

## 도메인별 실행

아래 함수는 JMeter 실행을 줄여 줍니다. 터미널에 한 번 붙여 넣어 등록합니다. 등록만 하면 아무 출력도 없는 것이 정상입니다.

```bash
runj() { (cd loadtest && rm -rf ../results_$1.jtl ../report_$1 && jmeter -n -t $2 -Jthreads=$3 -Jramp=$4 -l ../results_$1.jtl -e -o ../report_$1); }
```

사용법: `runj 결과이름 시나리오.jmx 사용자수 접속시간(초)`

### 플레이리스트 목록 조건별

```bash
python3 loadtest/update_list_params.py
runj list_calm playlist-list-variants.jmx 200 60
runj list_burst playlist-list-variants.jmx 200 5
```

### 플레이리스트 콘텐츠 추가·삭제

`seed_content.sql`을 실행한 상태에서 씁니다. 추가한 콘텐츠를 마지막에 삭제하므로 DB가 원래대로 돌아옵니다.

```bash
python3 loadtest/update_plcontent_params.py
runj plc_calm playlist-content-scenario.jmx 200 60
runj plc_burst playlist-content-scenario.jmx 200 5
```

### 사용자 인증·보안

로그인과 CSRF는 모든 시나리오 안에 포함됩니다. 로그인만 따로 보려면 다음을 씁니다.

```bash
python3 loadtest/update_notification_params.py
runj login_low login-scenario.jmx 10 10
runj login_burst login-scenario.jmx 200 5
```

회원가입, 계정 잠금·해제, 권한 변경은 사용자 시나리오입니다. 실행할 때마다 이전에 가입된 테스트 계정(`lt-new-*`)을 지우고, 변경 대상(`loadtest1001~1200`)을 USER·잠금 해제 상태로 되돌립니다.

```bash
runu() { python3 loadtest/update_user_params.py && runj $1 user-scenario.jmx $2 $3; }
runu user_calm 200 60
runu user_burst 200 5
```

### 팔로우

```bash
python3 loadtest/update_follow_params.py
runj follow_calm follow-scenario.jmx 200 60
runj follow_burst follow-scenario.jmx 200 5
```

이전 실행으로 팔로우 상태가 어긋나 "행 수가 200이 아니에요"가 나오면 `seed_follow.sql`을 다시 실행합니다.

### 알림

```bash
python3 loadtest/update_notification_params.py
runj noti_calm notification-scenario.jmx 200 60
runj noti_burst notification-scenario.jmx 200 5
```

### 콘텐츠

삭제 시나리오가 대상 콘텐츠를 실제로 지우므로 아래 함수는 매번 시드를 다시 실행합니다.

```bash
runc() { docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -v ON_ERROR_STOP=1 < loadtest/seed_content.sql && python3 loadtest/update_content_params.py && runj $1 content-scenario.jmx $2 $3; }
runc content_calm 200 60
runc content_burst 200 5
```

콘텐츠 생성·수정은 S3(`EVERYONE_S3_BUCKET`)가 필요해서 측정하지 않았습니다. 썸네일은 URL 문자열만 사용합니다.

## 느린 이유 확인 방법

### 쿼리 실행 계획 (EXPLAIN)

```bash
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest < loadtest/explain_list.sql
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest < loadtest/explain_notification.sql
```

`Seq Scan`이 보이면 인덱스를 못 쓰는 쿼리입니다. 인덱스는 후보만 제안하며 적용 후 재측정은 하지 않았습니다.

### 커넥션 풀 고갈 확인

서버 로그에 다음 줄이 나오면 풀이 바닥난 것입니다.

```
HikariPool-1 - Connection is not available, request timed out after 30000ms (total=10, active=10, idle=0, waiting=N)
```

### 스레드 덤프로 누가 커넥션을 기다리는지 확인

부하를 거는 동안 다른 터미널에서 실행합니다.

```bash
jcmd $(jps -l | grep -iE "playlist|Application" | grep -v Jps | head -1 | cut -d' ' -f1) Thread.print > /tmp/dump.txt
awk 'BEGIN{RS=""} /"http-nio/ && /HikariPool.getConnection/' /tmp/dump.txt | head -80
```

- 호출 경로에서 어떤 코드가 연결을 기다리는지, 그 위에 이미 연결을 쥔 트랜잭션이 있는지 봅니다.
- 풀을 일부러 줄여 재현하려면 서버 실행 옵션에 `--spring.datasource.hikari.maximum-pool-size=2`를 추가합니다. 1개는 Flyway가 시작할 때 연결이 2개 필요해서 서버가 뜨지 않습니다.

## 측정할 때 주의

- **서버를 막 띄운 직후의 첫 실행은 워밍업(JVM, 커넥션 풀) 영향이 큽니다.** 같은 서버로 한 번 더 돌린 결과를 기준으로 삼으세요.
- 측정 중에는 IDE 빌드나 영상 같은 무거운 작업을 닫고 전원을 연결해 두세요.
- 평시 조건은 200명을 60초에 걸쳐 순차 투입하므로 동시 활성 스레드는 소수입니다. 절대 처리량이 아니라 API별 응답시간 비교용입니다.
- 앱, DB, JMeter가 같은 컴퓨터에서 돌기 때문에 절대 수치보다 도메인·API 간 상대 비교로 보세요.
- 같은 조건도 실행마다 편차가 있었습니다(알림). 한 번의 값으로 결론 내리지 마세요.

## 인덱스 전후 비교 방법

같은 서버를 켜 둔 채 인덱스만 바꿔서 두 번 측정합니다.

```bash
# 인덱스 없음
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -c "DROP INDEX idx_playlists_updated_at_id;"
jmeter -n -t loadtest/playlist-smoke.jmx -l results_noidx.jtl -e -o report_noidx

# 인덱스 복원
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -c "CREATE INDEX idx_playlists_updated_at_id ON playlists (updated_at DESC, id DESC);"
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -c "ANALYZE playlists;"
```

`loadtest` DB에서만 실행하세요. 개발 DB에는 실행하지 마세요.

## 정리

```bash
docker compose --env-file .env.dev exec -T postgres psql -U everyone -d everyone -c "DROP DATABASE loadtest;"
```

데이터가 수백만 건이라 디스크를 차지합니다. 다 쓴 뒤에는 `loadtest` DB를 지워도 됩니다.
시드의 구독 데이터는 `loadtest` 계정 전체에 해당하므로, 테스트 후 정리용으로 `subscriptions`를 계정 기준으로 지우지 마세요(시드가 같이 사라집니다). 필요하면 시드를 다시 실행하세요.