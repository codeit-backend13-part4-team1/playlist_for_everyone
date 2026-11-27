# 배포 가이드

운영 서버를 띄우는 데 필요한 설정과 방법을 정리합니다.
로컬 개발 환경은 [DEVELOPMENT.md](DEVELOPMENT.md)를 참고합니다.

## 배포 구조

| 항목 | 내용 |
| --- |---|
| 배포 브랜치 | `main` (dev → main 릴리스 PR이 머지되면 배포) |
| 프로필 | `prod` (Dockerfile의 `SPRING_PROFILES_ACTIVE=prod`) |
| 실행 환경 | AWS ECS Fargate, 태스크 2개 ([다중 인스턴스 운영](#다중-인스턴스-운영)) |
| 진입 경로 | CloudFront → ALB → ECS 태스크 |
| 이미지 저장소 | Amazon ECR |
| 비밀 값 | SSM Parameter Store에 저장하고 태스크 정의의 `valueFrom`으로 주입 |
| 헬스 체크 | `GET /actuator/health` (인증 없이 허용, 나머지 Actuator는 인증 필요) |
| 로그 | 콘솔 출력을 CloudWatch Logs로 수집. 태스크마다 로그 스트림이 따로 생성 |


## 배포 흐름

`main`에 push되면 [ci.yml](../.github/workflows/ci.yml)이 아래 순서로 실행합니다. 앞 단계가 실패하면 다음 단계는 실행하지 않습니다.

| 단계 | 작업 | 내용 |
|---|---|---|
| 1 | `build` | Gradle 빌드·테스트와 라인 커버리지 80% 검증. PR에서도 같은 작업을 실행합니다 |
| 2 | `push-image` | Docker 이미지를 빌드해 ECR에 커밋 SHA 태그와 `latest` 태그로 push. 빌드 캐시는 GitHub Actions 캐시를 사용합니다 |
| 3 | `deploy` | [deploy.yml](../.github/workflows/deploy.yml)을 호출해 ECS 서비스를 갱신합니다 |

`deploy.yml`은 다음 순서로 배포합니다.
1. 배포할 커밋이 `main`의 최신 커밋인지 확인합니다. 예전 실행을 재실행해 운영이 이전 커밋으로 돌아가는 것을 막습니다.
2. 최신 태스크 정의 개정을 가져와 이미지만 새 SHA 태그로 바꿉니다. 환경변수와 SSM 참조는 콘솔에서 관리하는 값을 그대로 사용합니다.
3. 새 개정을 등록하고 서비스에 적용한 뒤, 새 태스크가 안정 상태가 될 때까지 최대 15분 기다립니다.

AWS 접근은 GitHub OIDC로 발급한 임시 자격 증명을 사용합니다. 저장소에는 AWS 액세스 키를 두지 않고, 역할 ARN만 `AWS_ROLE_ARN` 시크릿에 둡니다. OIDC 토큰은 `main` push의 이미지·배포 작업에서만 발급하며 PR 빌드는 AWS에 접근할 수 없습니다.

## 다중 인스턴스 운영
ECS 서비스는 태스크 2개로 운영합니다. ALB가 두 태스크에 요청을 번갈아 보냅니다.

### 설정

| 항목 | 값 | 이유 |
| --- | --- | --- |
| 원하는 태스크 수 | 2 | 한 대가 내려가도 서비스가 유지됩니다 |
| 서브넷 | 2a, 2c | 두 태스크가 서로 다른 가용 영역에 뜹니다 |
| 최소 정상 / 최대 비율 | 100% / 200% | 새 태스크가 정상이 된 뒤 기존 태스크를 내립니다. 배포 중 최대 4개가 뜹니다 |
| 상태 검사 유예 기간 | 180초 | 앱이 뜨기 전에 헬스 체크 실패로 내려가지 않게 합니다 |
| 고정 세션(sticky) | 끔 | 어느 태스크로 요청이 가도 같은 결과가 나오도록 상태를 서버 밖에 둡니다 |
| 등록 취소 지연 | 30초 | 내려갈 태스크가 처리 중인 요청을 마무리합니다. ECS 종료 대기 시간(기본 30초)과 맞춥니다 |

### 서버 간 상태 공유

| 기능 | 공유 방식 |
| --- | --- |
| 로그인 | JWT로 인증하고, 로그인 세션과 Refresh Token은 Redis에 둡니다 |
| 소셜 로그인 | 로그인 시작과 제공자 콜백 사이의 요청 정보(state)를 Redis 세션에 둡니다. 콜백이 다른 태스크로 가도 로그인이 이어집니다 |
| SSE 알림 | Redis 채널로 발행하면 연결을 가진 태스크가 전송합니다. 놓친 알림은 Redis 이력으로 복구합니다 |
| 강제 로그아웃 | Redis 채널로 세션 폐기를 알립니다 |
| 알림 중복 제한 | Redis 키로 60초 동안 같은 알림을 막습니다 |
| 시청방 채팅·DM·시청자 수 | Redis 채널과 Redis 집계를 사용합니다 |
| 콘텐츠 검색 캐시 | Redis 캐시를 함께 사용합니다 |
| 콘텐츠 수집 배치 | PostgreSQL advisory lock으로 한 태스크만 실행합니다 |

### 배포 중 동작


- 기존 태스크가 내려갈 때 SSE와 WebSocket 연결이 끊기고, 클라이언트가 새 태스크로 다시 연결합니다.
- Redis 키 구조가 바뀌는 배포에서는 이전 태스크와 새 태스크가 잠시 함께 뜨는 동안 시청자 수가 어긋날 수 있습니다. 배포가 끝나면 정상으로 돌아옵니다.

### 확인 방법

1. EC2 → 대상 그룹 → 대상 탭에서 대상 2개가 모두 `healthy`인지 확인합니다.
2. CloudWatch Logs Insights에서 요청이 두 태스크로 나뉘는지 확인합니다.

   ```
   fields @logStream
   | filter @message like /\| (GET|POST|PATCH|PUT|DELETE) \|/
   | stats count(*) by @logStream
   ```

3. 브라우저 두 개로 로그인 유지, 팔로우 알림, 권한 변경 강제 로그아웃을 확인합니다. 팔로우 알림은 60초 중복 제한이 있으므로 1분 이상 간격을 둡니다.

### 태스크 수 되돌리기

ECS 서비스 업데이트에서 원하는 태스크 수를 `1`로 바꿉니다. 코드나 이미지 변경 없이 바로 적용됩니다.

## 운영 환경변수

### 필수

운영 배포 시 아래 변수를 주입합니다. OAuth 리다이렉트 기준 주소와 WebSocket 허용 Origin에는 사용자가 접속하는 서비스 주소를 사용합니다.

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
| `EVERYONE_WEBSOCKET_ALLOWED_ORIGINS` | WebSocket·SockJS 연결을 허용할 프런트엔드 Origin. 여러 값은 쉼표로 구분하며 경로와 마지막 슬래시는 제외 |
| `EVERYONE_OAUTH2_REDIRECT_BASE_URL` | OAuth 콜백과 로그인 결과 이동에 사용할 공개 서비스 주소. 제공자 콘솔에 등록한 콜백의 기준 URL입니다. 주소 끝에 `/`를 붙이지 않습니다. |
| `EVERYONE_OAUTH2_GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID |
| `EVERYONE_OAUTH2_GOOGLE_CLIENT_SECRET` | Google OAuth 클라이언트 시크릿 |
| `EVERYONE_OAUTH2_KAKAO_CLIENT_ID` | Kakao OAuth 클라이언트 ID |
| `EVERYONE_OAUTH2_KAKAO_CLIENT_SECRET` | Kakao OAuth 클라이언트 시크릿 |

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

## IAM 역할

| 역할 | 사용 주체 | 필요한 권한 |
| --- | --- | --- |
| 태스크 실행 역할 | ECS 에이전트 | ECR 이미지 pull, CloudWatch Logs 쓰기, SSM 파라미터 읽기 |
| 태스크 역할 | 애플리케이션 | S3 버킷 객체 읽기·쓰기·삭제, `ecs:DescribeTasks` (수집 배치 복구 시 이전 태스크 상태 확인) |
| 배포 역할 | GitHub Actions (OIDC) | ECR push, 태스크 정의 조회·등록, 서비스 갱신·조회, 위 두 역할에 대한 `iam:PassRole` |

## 롤백

| 상황 | 방법 |
| --- | --- |
| 코드 문제 | 문제 커밋을 되돌리는 PR을 `main`에 머지합니다. 일반 배포와 같은 경로로 테스트를 거쳐 배포됩니다 |
| 긴급 복구 | ECS 서비스 업데이트에서 이전 태스크 정의 개정을 선택합니다. 다음 `main` 배포는 최신 개정을 기준으로 다시 진행합니다 |
| 다중 인스턴스 문제 | [태스크 수 되돌리기](#태스크-수-되돌리기)를 따릅니다 |

GitHub Actions에서 예전 배포 실행을 재실행하면 최신 커밋 확인 단계에서 중단됩니다.

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
