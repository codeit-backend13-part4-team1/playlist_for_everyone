# 모두의 플리

영화·시리즈·스포츠 경기를 플레이리스트에 모으고, 같은 콘텐츠를 보고 있는 사람들과 실시간으로 대화하는 서비스입니다.

[![main 빌드의 라인 커버리지](https://raw.githubusercontent.com/codeit-backend13-part4-team1/playlist_for_everyone/coverage-badges/coverage.svg)](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/actions/workflows/ci.yml?query=branch%3Amain)

## 주요 기능

- **계정과 프로필** — 이메일로 가입하거나 Google·Kakao 계정으로 로그인할 수 있습니다. 이름·프로필 이미지·비밀번호를 변경할 수 있고, 비밀번호를 잊으면 이메일로 임시 비밀번호를 받을 수 있습니다.
- **콘텐츠 데이터 수집** — TMDB의 인기 영화·TV 시리즈와 TheSportsDB의 지정 리그·시즌 경기를 가져옵니다. 정기 수집을 켜면 설정한 일정에 따라 새 콘텐츠를 추가하고, 기존 콘텐츠의 제목·설명·썸네일·태그가 바뀌면 갱신합니다.
- **콘텐츠 탐색과 리뷰** — 유형·키워드·태그로 검색하거나 현재 시청자가 많은 순으로 둘러볼 수 있습니다. 콘텐츠별 평균 평점을 확인하고 리뷰를 평점순·작성순으로 읽을 수 있습니다. 내 리뷰와 평점은 작성 후 수정하거나 삭제할 수 있습니다.
- **플레이리스트와 구독** — 내 목록을 만들어 콘텐츠를 추가하거나 뺄 수 있습니다. 제목·설명으로 목록을 검색하고 내가 만든 목록이나 구독 중인 목록만 모아 볼 수 있습니다. 다른 사람의 목록을 구독하거나 구독을 취소할 수 있습니다.
- **팔로우와 알림** — 다른 사용자를 팔로우하거나 팔로우를 취소하고 팔로워 수를 확인할 수 있습니다. 팔로우한 사람이 목록을 만들거나 시청을 시작할 때, 구독한 목록에 콘텐츠가 추가될 때 알림을 받습니다. 새 팔로워, 내 목록의 새 구독자, 새 DM, 내 계정의 권한 변경도 알림함에서 확인하고 읽음 처리할 수 있습니다.
- **시청 채팅과 1:1 대화** — 같은 콘텐츠의 시청자와 실시간으로 채팅하거나 개인 메시지(DM)를 주고받을 수 있습니다. 상대방 이름이나 메시지 내용으로 대화방을 찾고, 읽지 않은 메시지를 확인해 읽음 처리할 수 있습니다.
- **관리자 기능** — 관리자는 회원 목록을 조회하고 권한을 변경하거나 계정을 잠글 수 있습니다. 콘텐츠와 썸네일도 등록·수정·삭제할 수 있습니다.

## 기술적 과제

### 구독자 수로 정렬하는 플레이리스트 조회

구독자 수가 같으면 ID로 순서를 정해 다음 페이지에서도 목록이 일정하게 이어지도록 했습니다. PostgreSQL에 이 정렬 순서와 일치하는 인덱스를 추가했습니다. 플레이리스트 20만 건으로 측정한 로컬 JMeter 테스트에서 구독자 수 내림차순 조회의 평균 응답 시간이 **357ms에서 57ms**로 줄었습니다. [측정 조건과 실행 계획](docs/loadtest/playlist-list-index-comparison.md)

### 플레이리스트 생성과 팔로워 알림의 DB 연결 경합

플레이리스트를 생성한 요청 스레드가 커밋 직후 팔로워별 알림을 저장하면서 추가 DB 연결을 기다렸습니다. 알림 저장·발행을 커밋 후 전용 실행기로 옮겼습니다. 팔로워 10명, DB 연결 2개로 요청 25건을 5개씩 동시에 보낸 실험에서 **요청 실패가 15건에서 0건**으로 줄었습니다.

기본 DB 풀에서는 같은 팔로워 조건으로 요청 100건을 순차 전송했을 때 HTTP 응답 시간의 95백분위수(p95)가 208ms에서 46ms로 줄었습니다. 5개씩 동시에 보내면 처리량은 초당 32.77건에서 51.15건으로 늘었고, p95는 168ms에서 202ms로 증가했습니다. [부하 조건별 비교](docs/loadtest/notification/results.md)

### ECS 롤링 배포 중 콘텐츠 수집 재개

배포 중에는 기존 태스크와 새 태스크가 잠시 함께 실행돼 같은 수집 작업을 시작할 수 있습니다. 수집 작업별로 PostgreSQL 세션 잠금을 획득한 태스크만 작업을 시작하거나 복구하도록 했습니다. 다음 예약 시점에 기존 작업이 남아 있으면 기록된 ECS 태스크 상태를 조회합니다. 기존 태스크가 `STOPPED`일 때만 이전 작업의 파라미터와 마지막으로 저장된 읽기 위치를 사용해 재개합니다. [배치 잠금과 재시작 검증](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/pull/259)

### 강제 로그아웃 후 남은 실시간 연결 종료

다른 기기에서 다시 로그인하거나 관리자가 권한을 변경하거나 계정을 잠그면 기존 로그인 세션이 폐기됩니다. 이미 열린 채팅·알림 연결에도 이를 적용하기 위해 폐기된 세션 ID를 Redis로 전달합니다. 각 서버는 사용자 ID와 세션 ID가 모두 일치하는 WebSocket·SSE 연결을 찾아 닫습니다. SSE 서버가 폐기 이벤트를 놓쳐도 다음 하트비트에서 세션을 다시 확인할 수 있습니다. [로그인 세션 폐기](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/pull/161) · [WebSocket 연결 종료](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/pull/194) · [SSE 연결 종료](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/pull/249) · [하트비트 재검증](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/pull/256)

## 팀원과 기여

| 팀원 | 주요 구현 | 추가로 해결한 과제 |
| --- | --- | --- |
| [조혜령(gpfud09)](https://github.com/gpfud09) | 콘텐츠 검색·관리, 리뷰, 공용 S3 파일 저장 | DB 커밋 후에만 S3 파일을 삭제해 롤백 시 원본 보존. 콘텐츠 삭제가 확정되면 시청 세션 정리 이벤트 발행 |
| [이해빈(haebppy)](https://github.com/haebppy) | TMDB·TheSportsDB 수집 배치, 재시도·모니터링, 중단 작업 복구 | 재수집한 콘텐츠가 바뀌면 검색 캐시 무효화. PR 빌드·테스트, 라인 커버리지 80% 검증, README 배지 갱신 자동화 |
| [장준서(jangjunseo518-collab)](https://github.com/jangjunseo518-collab) | 플레이리스트·구독, 생성·구독·콘텐츠 추가 이벤트 발행 | 목록 조회 인덱스 개선. 여러 도메인의 JMeter 테스트 데이터와 시나리오를 작성하고 성능 재측정 |
| [정구영(KooYeoung)](https://github.com/KooYeoung) | 팔로우, 서버 간 DM 전달, 도메인 이벤트를 받아 알림 생성·전송 | 커밋 후 비동기 알림 처리와 Redis 기반 반복 발송 제한. SSE 재연결 시 이벤트 복구, 배포 환경 WebSocket·SockJS Origin 403 해결 |
| [오재건(latte798)](https://github.com/latte798) | STOMP 연결 인증, 콘텐츠별 시청·채팅 구독, 콘텐츠 삭제 시 연결·구독 정리 | 동시 입퇴장 시 세션·구독 상태 동기화와 Redis 오류 재시도. 채팅 송신 경로 수정 및 비동기 전송 |
| [박경석(seok-dev)](https://github.com/seok-dev) | Google·Kakao 로그인, JWT 갱신, 중복 로그인·강제 로그아웃, 관리자 권한 변경·계정 잠금 | ECR 이미지 게시와 ECS 배포 자동화. 운영 소셜 로그인 후 서비스 주소로 돌아오도록 리다이렉트 수정 |

## 기술 스택과 프로젝트 구조

| 영역 | 기술과 용도 |
| --- | --- |
| 백엔드 | Java 17, Spring Boot, Spring MVC로 HTTP API 구현. Spring Security로 JWT 인증과 OAuth 2.0 로그인 처리 |
| 데이터 | PostgreSQL에 서비스 데이터 저장. Spring Data JPA와 Querydsl로 조회하고 Flyway로 스키마 변경 관리 |
| 캐시·파일 저장 | Redis로 콘텐츠 검색 결과와 로그인 세션을 관리하고, Amazon S3에 프로필 이미지와 업로드한 콘텐츠 썸네일 저장 |
| 실시간 통신 | WebSocket/STOMP로 시청 채팅과 DM 전달. SSE로 알림 전송. Redis Pub/Sub으로 서버 간 이벤트 전달 |
| 콘텐츠 수집 | Spring Batch로 TMDB의 영화·시리즈와 TheSportsDB의 경기 데이터 수집 |
| 배포 | GitHub Actions로 Docker 이미지를 Amazon ECR에 게시하고 ECS Fargate에 배포 |
| 테스트 | JUnit과 Testcontainers로 기능·통합 테스트, JMeter로 부하 측정, JaCoCo로 커버리지 확인 |

```text
src/main/
├── java/com/codeit/sb13/playlist_for_everyone/
│   ├── domain/      인증, 콘텐츠, 리뷰, 플레이리스트, 대화 등 도메인별 구현
│   └── global/      공통 설정, 예외 처리, 파일 저장, SSE·WebSocket
└── resources/       프로필별 설정, DB 마이그레이션, 정적 리소스
docs/                개발·배포 가이드, 도메인 설계와 검증 기록
loadtest/            부하 테스트 시나리오와 실행 자료
```

## 로컬 실행

JDK 17을 설치하고 Docker Compose를 사용할 수 있도록 Docker를 실행합니다. 저장소 루트에서 처음 한 번 개발용 설정 파일을 만듭니다.

```bash
cp -n .env.example .env.dev
```

`.env.dev`에서 `EVERYONE_TMDB_ACCESS_TOKEN`에 TMDB API Read Access Token을 입력합니다. 콘텐츠 수집 없이 로컬 서버만 실행할 때는 `dummy`를 입력하면 됩니다.

```bash
./gradlew bootRun
```

기본 `dev` 프로필에서는 Spring Boot가 Docker Compose로 PostgreSQL과 Redis를 시작합니다. 기본 포트는 앱 `8080`, PostgreSQL `5432`, Redis `6379`입니다. 서버가 시작되면 다른 터미널에서 상태를 확인합니다.

```bash
curl -fsS http://localhost:8080/actuator/health
```

응답의 `status`가 `UP`이면 기동이 완료된 상태입니다. API 목록과 요청 형식은 로컬 Swagger UI에서 확인할 수 있습니다. [Swagger UI](http://localhost:8080/swagger-ui.html)

## 관련 문서

| 주제 | 문서 |
| --- | --- |
| 개발과 배포 | [로컬 설정·테스트](docs/DEVELOPMENT.md) · [운영 환경과 ECS 배포](docs/DEPLOYMENT.md) |
| 인증 | [로그인 세션과 JWT](docs/auth/01-login-session.md) · [강제 로그아웃](docs/auth/02-force-logout.md) · [비밀번호 초기화](docs/auth/05-password-reset.md) · [소셜 로그인](docs/auth/06-social-login.md) |
| 수집과 성능 검증 | [콘텐츠 정기 수집 설정](docs/DEVELOPMENT.md#콘텐츠-정기-수집) · [도메인별 부하 테스트 결과](docs/loadtest/loadtest-results.md) |
| 실시간 통신 | [SSE 연결·분산 전송·복구](docs/sse/README.md) · [대화방과 DM](docs/conversation/README.md) |
| 사용자 활동 | [알림 생성과 전달](docs/notification/README.md) · [팔로우와 이벤트](docs/follow/README.md) |
