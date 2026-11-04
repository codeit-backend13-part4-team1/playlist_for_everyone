# 7. Redis SSE 다중 서버 실행

이 문서는 같은 PostgreSQL·Redis를 공유하는 애플리케이션 두 개를 실행하고 종료하는 절차입니다.
실행 후에는 [실제 수신 검증](08-redis-verification.md)으로 이어갑니다. 명령은 모두 프로젝트 루트에서 실행합니다.

## 준비 조건

- JDK 17과 Docker Compose v2를 준비하고 Docker 엔진을 실행합니다.
- `.env.dev`를 준비합니다. 처음 준비하는 경우에는 [팀원 개발 가이드](../DEVELOPMENT.md#로컬-실행)를 참고합니다.
- TMDB 조회 없이 SSE만 검증할 때는 `EVERYONE_TMDB_ACCESS_TOKEN=dummy`를 설정할 수 있습니다.
- 호스트 포트 `8081`, `8082`와 `.env.dev`에 지정한 DB·Redis 포트가 사용 가능한지 확인합니다.

기존 `.env.dev`와 DB 접속 정보는 그대로 사용합니다. 각 서버의 JWT 설정도 동일한 환경에서 공급합니다.
Spring의 Compose 자동 실행은 컨테이너 안에서 비활성화되며, 아래 CLI 명령으로 서비스를 관리합니다.

## 두 서버가 사용하는 주소

| 대상 | 호스트에서 접근 | 애플리케이션 컨테이너에서 접근 |
| --- | --- | --- |
| 서버 A | `http://localhost:8081` | 애플리케이션 포트 `8080` |
| 서버 B | `http://localhost:8082` | 애플리케이션 포트 `8080` |
| PostgreSQL | `.env.dev`의 `EVERYONE_DB_PORT` | `postgres:5432` |
| Redis | `.env.dev`의 `EVERYONE_REDIS_PORT` | `redis:6379` |

[compose.sse.yml](../../compose.sse.yml)은 두 서버에 `dev` 프로필, Redis 전송, SSE 자동 구독을 지정합니다.
두 서버는 같은 bootJar를 읽고 같은 채널을 구독합니다. 채널은 `.env.dev`의 설정 또는 기본값을 사용합니다.
호스트 포트를 변경해도 컨테이너끼리는 서비스 이름과 내부 포트를 사용합니다.

## 빌드와 실행

먼저 JAR를 만듭니다. 이 명령은 애플리케이션 패키징이며 테스트 실행 결과를 의미하지 않습니다.

```bash
./gradlew bootJar --no-daemon --console=plain
```

생성된 `build/libs/playlist_for_everyone-0.0.1-SNAPSHOT.jar`를 Java 17 컨테이너에 읽기 전용으로 마운트합니다.
JAR가 없으면 디렉터리를 대신 만들지 않도록 설정되어 있습니다.

```bash
docker compose --env-file .env.dev \
  -f compose.yaml -f compose.sse.yml config --quiet

docker compose --env-file .env.dev \
  -f compose.yaml -f compose.sse.yml up -d app-a app-b
```

첫 번째 명령은 병합된 Compose 설정을 검증합니다. 두 번째 명령은 PostgreSQL·Redis의 상태 점검이 성공한 뒤 두 앱을 시작합니다.
기존 PostgreSQL 데이터 볼륨을 공유하므로 앱의 Flyway도 그 DB를 대상으로 동작합니다.

```bash
docker compose --env-file .env.dev \
  -f compose.yaml -f compose.sse.yml ps

docker compose --env-file .env.dev \
  -f compose.yaml -f compose.sse.yml logs --tail=60 app-a app-b
```

컨테이너의 `running` 상태만으로 앱 준비 완료를 판단하지 않습니다. 두 서버의 기동 완료 로그를 확인한 뒤 다음 응답이 모두 `200`인지 확인합니다.

```bash
curl --silent --show-error --max-time 10 --output /dev/null \
  --write-out '%{http_code}\n' http://localhost:8081/v3/api-docs
curl --silent --show-error --max-time 10 --output /dev/null \
  --write-out '%{http_code}\n' http://localhost:8082/v3/api-docs
```

코드 변경 후에는 JAR를 다시 빌드하고 실행 중인 두 앱을 다시 생성합니다. 아래 명령은 DB·Redis가 이미 실행 중일 때 사용합니다.

```bash
docker compose --env-file .env.dev \
  -f compose.yaml -f compose.sse.yml up -d --no-deps --force-recreate app-a app-b
```

## 검증과 종료

두 서버가 준비되면 [로그인·SSE 연결·샘플 발행](08-redis-verification.md)을 진행합니다.
검증이 끝나면 SSE를 연결한 각 터미널에서 `Ctrl+C`로 클라이언트를 종료하고 두 앱을 중지합니다.

```bash
docker compose --env-file .env.dev \
  -f compose.yaml -f compose.sse.yml stop app-a app-b
```

이 명령은 공유 PostgreSQL·Redis와 데이터 볼륨을 유지합니다. 검증에 사용한 사용자 계정도 DB에 남습니다.
문서의 검증 절차에는 볼륨 삭제가 필요하지 않습니다.

---

[← 이전: Redis 전송 구조와 설정](06-redis-transport.md) · [목차](README.md) · [다음: 실제 수신 검증 →](08-redis-verification.md)
