# 배포 가이드

운영 서버를 띄우는 데 필요한 설정과 방법을 정리합니다.
로컬 개발 환경은 [DEVELOPMENT.md](DEVELOPMENT.md)를 참고합니다.

## 배포 구조

| 항목 | 내용 |
| --- | --- |
| 배포 브랜치 | `main` (dev → main 릴리스 PR이 머지되면 배포) |
| 프로필 | `prod` (Dockerfile의 `SPRING_PROFILES_ACTIVE=prod`) |
| 실행 환경 | AWS ECS Fargate |
| 이미지 저장소 | Amazon ECR |
| 헬스 체크 | `GET /actuator/health` (인증 없이 허용, 나머지 Actuator는 인증 필요) |
| 로그 | 콘솔 출력을 CloudWatch Logs로 수집 |


## 운영 환경변수

### 필수
`application-prod.yaml`은 아래 변수에 기본값을 두지 않습니다. 하나라도 비어 있으면 서버가 시작되지 않습니다.

| 변수 | 설명 |
| --- | --- |
| `EVERYONE_OPENAPI_SERVER_URL` | Swagger에 표시할 운영 서버 주소 |
| `EVERYONE_DB_HOST` | RDS 엔드포인트 |
| `EVERYONE_DB_NAME` | DB 이름 |
| `EVERYONE_DB_USERNAME` | DB 사용자 |
| `EVERYONE_DB_PASSWORD` | DB 비밀번호 |
| `EVERYONE_REDIS_HOST` | ElastiCache 엔드포인트 |
| `EVERYONE_REDIS_PORT` | Redis 포트 |
| `EVERYONE_S3_BUCKET` | 파일 저장 버킷 |
| `EVERYONE_TMDB_ACCESS_TOKEN` | TMDB API 토큰 |
| `JWT_SECRET_KEY` | 운영 전용 JWT 서명 키 (32바이트 이상, 로컬 키 재사용 금지) |

### 콘텐츠 정기 수집

기본값은 비활성화입니다. 첫 배포는 꺼 둔 상태로 서버 안정화를 확인한 뒤 켭니다.
설정 방법과 동작은 [DEVELOPMENT.md](DEVELOPMENT.md)의 콘텐츠 정기 수집 절을 참고합니다.

| 변수 | 운영 값 |
| --- |-------|
| `EVERYONE_COLLECTION_SCHEDULE_ENABLED` | 첫 배포는 `false` |
| `EVERYONE_COLLECTION_SCHEDULE_ZONE` | `Asia/Seoul` |
| `EVERYONE_COLLECTION_SCHEDULE_TMDB_CRON` | `0 0 0 * * *` (예: 매일 자정) |
| `EVERYONE_COLLECTION_SCHEDULE_TMDB_MAX_PAGES` | 1~500 |
| `EVERYONE_COLLECTION_SCHEDULE_SPORTSDB_CRON` | `0 30 0 * * *` (예: 매일 0시 30분) |
| `EVERYONE_COLLECTION_SCHEDULE_SPORTSDB_LEAGUE_ID` | 스포츠 수집을 켤 때 필수 |
| `EVERYONE_COLLECTION_SCHEDULE_SPORTSDB_SEASON` | 스포츠 수집을 켤 때 필수 |

## Docker 이미지

### 구성

| 결정 | 이유 |
| --- | --- |
| 2단계 빌드 (JDK로 빌드 → JRE로 실행) | 최종 이미지에 빌드 도구와 소스가 남지 않습니다 |
| 이미지 빌드 시 테스트 제외 (`-x test`) | 테스트는 PR CI에서 통과한 코드만 `main`에 들어옵니다 |
| non-root 사용자(`app`)로 실행 | 앱이 침해되어도 컨테이너 root 권한을 갖지 못합니다 |
| `exec java` | java가 1번 프로세스가 되어 ECS 종료 신호를 직접 받고 정상 종료합니다 |
| `-XX:MaxRAMPercentage=75.0` | 힙을 컨테이너 메모리 한도 안에 둬서 강제 종료를 막습니다 |
| `.dockerignore`에 `.env*` | 비밀 값이 빌드 컨텍스트와 이미지에 들어가지 않습니다 |
| `TZ=Asia/Seoul` | 컨테이너 기본 시간대가 UTC라 `LocalDateTime`이 9시간 이르게 저장·표시됩니다. 서비스 기준 시간대로 고정합니다 |

### 로컬에서 운영 이미지 확인

```bash
docker build -t everyone:local .

docker run --rm -p 8080:8080 \
  --env-file .env.prod \
  --add-host=host.docker.internal:host-gateway \
  -e EVERYONE_DB_HOST=host.docker.internal \
  -e EVERYONE_REDIS_HOST=host.docker.internal \
  everyone:local

curl -i http://localhost:8080/actuator/health   # 200, {"status":"UP"}
```
컨테이너 안에서 `localhost`는 컨테이너 자신이므로, 로컬에서 띄운 DB와 Redis는 `host.docker.internal`로 접근합니다.
`--add-host`는 Linux Docker Engine에서도 이 주소를 쓸 수 있게 합니다. Docker Desktop(macOS, Windows)에서는 없어도 동작합니다.
