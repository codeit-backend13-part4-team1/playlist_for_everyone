# 팀원 개발 가이드

로컬 실행, 공통코드 사용법, 테스트 방법을 정리한 문서입니다. 프로젝트 소개는 [README](../README.md)에서 관리합니다.

## 현재 개발 상태

현재는 도메인 기능 개발에 사용할 공통 기반을 준비한 단계입니다.

| 구분 | 현재 상태 |
| --- | --- |
| 공통 기반 | 예외 처리, 응답 DTO, 엔티티 기반 클래스, JPA Auditing, Querydsl 설정, MDC 로그, 비밀번호 인코더, OpenAPI 설정 구현 |
| 도메인 예외 | 사용자·콘텐츠·대화방·팔로우·알림·플레이리스트·리뷰의 미존재 예외 7종을 작성 예시로 제공 |
| 도메인 기능 | 회원·콘텐츠·플레이리스트 등의 실제 API와 서비스 구현 전 |
| 로컬 인프라 | Docker Compose에 PostgreSQL과 Redis 서비스 정의 |
| 개발용 보안 | `dev` 프로필에서 전체 요청을 인증 없이 허용하고 CSRF 검사를 비활성화하는 임시 설정 |
| 후속 기능 | JWT 인증, Redis 기반 캐시·세션, 배치 작업, S3 연동, SSE·WebSocket 기능 등은 별도 구현 필요 |

라이브러리 의존성과 로컬 인프라를 준비한 상태와 기능 구현 완료를 구분합니다. Redis 서비스는 Compose에 포함되어 있으며, 이를 사용하는 캐시·세션 기능은 구현 전입니다. Kafka와 Elasticsearch는 현재 Compose에 포함되어 있지 않습니다.

패키지 기준 경로는 `src/main/java/com/codeit/sb13/playlist_for_everyone`입니다.

| 위치 | 역할 |
| --- | --- |
| `domain/{도메인}` | 팀원이 담당하는 도메인 기능을 구현할 위치 |
| `global/config` | 애플리케이션 공통 설정 |
| `global/domain` | 공통 엔티티 기반 클래스와 정렬 방향 enum |
| `global/dto` | 공통 요청·응답 DTO |
| `global/exception`, `global/controller` | 공통 예외 계층, 오류 코드, 글로벌 예외 핸들러 |
| `src/test/java` | 공통코드 테스트와 `testsupport`의 테스트 전용 객체 |

## 개발 환경

| 항목 | 저장소 설정 |
| --- | --- |
| Java | JDK 17 |
| Spring Boot | 4.0.8 |
| Gradle | Wrapper 9.8.1 사용 |
| PostgreSQL | Compose 이미지 `postgres:16` |
| Redis | Compose 이미지 `redis:7.4-alpine` |
| Docker | Docker 엔진과 Docker Compose v2 필요 |
| 테스트 DB | H2 메모리 DB |

버전의 기준은 [build.gradle](../build.gradle), [Gradle Wrapper 설정](../gradle/wrapper/gradle-wrapper.properties), [compose.yaml](../compose.yaml)입니다. 별도 Gradle 설치 없이 저장소의 Wrapper를 사용합니다. 최초 실행 시 의존성과 컨테이너 이미지를 다운로드할 네트워크 연결이 필요합니다.

## 로컬 실행

명령어는 `build.gradle`, `gradlew`, `compose.yaml`이 있는 **프로젝트 루트**에서 실행합니다. 아래 셸 예시는 macOS/Linux 기준입니다. Windows PowerShell에서는 파일 복사에 `Copy-Item .env.example .env.dev`, Gradle 실행에 `.\gradlew.bat`를 사용합니다.

### 1. 실행 환경 준비

JDK 17을 설치하고 Docker Desktop 또는 Docker 엔진을 실행합니다.

```bash
java -version
docker compose version
```

### 2. 개인 환경변수 파일 생성

처음 설정할 때 [환경변수 예시](../.env.example)를 복사합니다. `.env.dev`가 이미 있으면 기존 값을 확인하고 유지합니다.

```bash
cp .env.example .env.dev
```

`.env.dev`에서 DB 접속 정보와 사용할 포트를 확인합니다. 기본 애플리케이션 포트는 `8080`, PostgreSQL 호스트 포트는 `5432`, Redis 호스트 포트는 `6379`입니다. 포트가 이미 사용 중이면 `EVERYONE_SERVER_PORT`, `EVERYONE_DB_PORT`, `EVERYONE_REDIS_PORT`를 변경합니다. `.env.dev`는 Git 제외 대상이며 실제 개인 설정은 커밋하지 않습니다.

Spring 설정의 파일 import는 `optional`이지만, Compose 실행에는 `--env-file .env.dev`를 전달하므로 기본 로컬 실행 절차에서는 이 파일을 준비해야 합니다.

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

프로필을 별도로 지정하지 않으면 `dev`가 적용됩니다. 현재 설정에서는 Spring Boot가 Compose를 통해 PostgreSQL과 Redis를 실행하고 연결합니다. 기본 절차에서는 `docker compose up`을 먼저 실행할 필요가 없습니다.

Compose 연동은 컨테이너에 매핑된 호스트 포트를 자동으로 인식합니다. 예를 들어 `EVERYONE_REDIS_PORT=16379`로 변경하면 애플리케이션은 호스트의 `16379` 포트로 연결하고, 컨테이너 내부 Redis 포트는 `6379`로 유지됩니다. 자동 연동이 켜져 있으면 이렇게 발견한 서비스 연결 정보가 YAML의 데이터소스 및 Redis 연결 속성보다 우선합니다. [Spring Boot의 서비스 연결](https://docs.spring.io/spring-boot/reference/features/dev-services.html)

애플리케이션 실행 후 다른 터미널에서 컨테이너 상태와 Redis 응답을 확인합니다.

```bash
docker compose --env-file .env.dev ps
docker compose --env-file .env.dev exec redis redis-cli ping
```

`ps`에서 PostgreSQL과 Redis가 실행 중이고 상태 점검 결과가 `healthy`인지 확인합니다. 두 번째 명령의 기대 응답은 `PONG`입니다. 이 명령은 컨테이너 내부에서 실행하므로 호스트 포트를 변경해도 동일하게 사용합니다.

이미 일부 Compose 서비스가 실행 중인 상태에서 Redis 같은 서비스를 새로 추가했다면, 애플리케이션을 종료하고 다음 명령으로 변경된 구성을 반영합니다. 이후 `ps`와 `PING`을 확인하고 애플리케이션을 다시 실행합니다.

```bash
docker compose --env-file .env.dev up -d
```

애플리케이션은 실행 터미널에서 `Ctrl+C`로 종료합니다. Spring Boot가 시작한 Compose 서비스는 기본 종료 동작에 따라 함께 정지합니다. 이미 실행 중이던 서비스를 사용한 경우에는 Spring Boot가 그 서비스를 정지하지 않습니다. 직접 시작한 서비스의 정지가 필요하면 다음 명령을 사용합니다. [Spring Boot의 Compose 동작](https://docs.spring.io/spring-boot/reference/features/dev-services.html)

```bash
docker compose --env-file .env.dev stop
```

Compose의 자동 실행·연결을 사용하지 않으려면 PostgreSQL과 Redis를 직접 준비하고, `.env.dev`의 기존 항목을 다음과 같이 수정합니다. Redis 호스트와 포트는 직접 준비한 서버에 맞게 변경하고, `EVERYONE_DB_*` 항목도 실제 DB 접속 정보에 맞춥니다.

```properties
EVERYONE_DOCKER_COMPOSE_ENABLED=false
EVERYONE_REDIS_HOST=localhost
EVERYONE_REDIS_PORT=6379
```

`dev` 프로필은 `.env.dev`를 읽으며, 개발 YAML이 위 Redis 값을 `spring.data.redis.host`와 `spring.data.redis.port`에 연결합니다. Compose 연동을 꺼도 이 값이 적용되므로 다음 명령으로 실행합니다.

```bash
./gradlew bootRun
```

### 4. API 문서 접근

기본 포트 기준 주소입니다. `EVERYONE_SERVER_PORT`를 변경했다면 주소의 포트도 변경합니다.

| 문서 | 주소 |
| --- | --- |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |

현재 [SecurityConfig](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/config/SecurityConfig.java)는 `@Profile("dev")`가 적용된 개발 초기 임시 설정입니다. `dev`에서는 정적 리소스, Swagger UI, OpenAPI JSON 및 API 요청을 인증 없이 허용하고 CSRF 검사를 비활성화합니다. `STATELESS` 정책으로 인증 정보를 HTTP 세션에 저장하지 않으며, JWT 인증·인가 기능은 아직 구현 전입니다.

`dev`가 활성화되지 않은 환경에는 이 임시 설정이 적용되지 않습니다. 현재 `test` 또는 `prod` 프로필만 사용하는 경우에는 Spring Boot의 기본 보안 자동 설정이 적용되어 Swagger 접근에도 인증이 필요할 수 있습니다. [Spring Boot 기본 Security 설정](https://docs.spring.io/spring-boot/reference/web/spring-security.html)

현재는 도메인 컨트롤러가 없으므로 API 문서 설정이 있어도 실제 도메인 API 목록은 아직 제공되지 않습니다. 설정 경로는 [application.yaml](../src/main/resources/application.yaml)과 [OpenApiConfig](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/config/OpenApiConfig.java)를 참고합니다.

## 환경변수와 프로필

아래 값은 `.env.example`에 있는 로컬 예시입니다. 운영 환경의 기본값으로 간주하지 않습니다.

| 환경변수 | 로컬 예시 값 | 용도 |
| --- | --- | --- |
| `EVERYONE_SERVER_PORT` | `8080` | 애플리케이션 포트 |
| `EVERYONE_DB_HOST` | `localhost` | 직접 데이터소스를 설정할 때의 DB 호스트 |
| `EVERYONE_DB_PORT` | `5432` | PostgreSQL 호스트 포트와 데이터소스 포트 |
| `EVERYONE_DB_NAME` | `everyone` | 데이터베이스 이름 |
| `EVERYONE_DB_USERNAME` | `everyone` | DB 사용자 |
| `EVERYONE_DB_PASSWORD` | `change-me` | 개인 환경에 맞게 변경할 예시 비밀번호 |
| `EVERYONE_REDIS_HOST` | `localhost` | Spring Redis 접속 호스트. Compose 자동 연동 시 발견한 연결 정보가 우선 |
| `EVERYONE_REDIS_PORT` | `6379` | Compose Redis의 호스트 포트 매핑과 Spring Redis 접속 포트. 컨테이너 내부 포트는 `6379` |
| `EVERYONE_MULTIPART_MAX_FILE_SIZE` | `10MB` | 업로드 파일 하나의 최대 크기 |
| `EVERYONE_MULTIPART_MAX_REQUEST_SIZE` | `12MB` | 파일과 폼 데이터 등을 포함한 multipart 요청 전체의 최대 크기 |
| `EVERYONE_JPA_DDL_AUTO` | `validate` | 엔티티와 DB 스키마 일치 여부 검증 |
| `EVERYONE_JPA_FORMAT_SQL` | `true` | 개발 환경 SQL 포맷 설정 |
| `EVERYONE_JPA_SHOW_SQL` | `false` | 개발 환경 JPA SQL 출력 설정 |
| `EVERYONE_FLYWAY_ENABLED` | `true` | Flyway 활성화 |
| `EVERYONE_DOCKER_COMPOSE_ENABLED` | `true` | 개발 환경 Compose 자동 연동 |
| `EVERYONE_OPENAPI_SERVER_URL` | `https://your-domain.example.com` | `prod`의 OpenAPI 서버 URL 예시. `dev`는 로컬 주소 사용 |
| `EVERYONE_AWS_REGION` | `ap-northeast-2` | 향후 S3 연동에 사용할 리전 설정 |
| `EVERYONE_AWS_ENDPOINT` | 빈 값 | 향후 S3 연동에 사용할 엔드포인트 설정 |
| `EVERYONE_S3_BUCKET` | 빈 값 | 향후 S3 연동에 사용할 버킷 설정 |

S3 관련 값은 현재 공통 설정에 선언되어 있지만 실제 S3 연동 기능은 구현 전입니다.

| 프로필 | 데이터베이스 | JPA DDL | Flyway | Compose | 설정 입력 |
| --- | --- | --- | --- | --- | --- |
| `dev` | PostgreSQL | 기본 `validate` | 기본 활성화 | 기본 활성화 | `.env.dev` 및 외부 환경변수 |
| `test` | H2 메모리 DB | `create-drop` | 비활성화 | 비활성화 | 테스트 리소스 |
| `prod` | PostgreSQL | 기본 `validate` | 기본 활성화 | 비활성화 | 외부 환경변수 |

설정 파일: [개발](../src/main/resources/application-dev.yaml), [테스트](../src/test/resources/application-test.yaml), [운영](../src/main/resources/application-prod.yaml).

`dev`는 `.env.dev`와 외부 환경변수를 통해 Redis 접속 정보를 받습니다. Redis 호스트와 포트를 생략하면 개발 YAML의 기본값인 `localhost:6379`를 사용하며, Compose 자동 연동이 켜져 있으면 발견한 연결 정보가 우선합니다.

테스트는 [테스트용 application.properties](../src/test/resources/application.properties)에서 `test` 프로필을 활성화합니다.

운영에서는 배포 환경에 `SPRING_PROFILES_ACTIVE=prod`를 지정합니다. `prod`는 `.env.dev`를 자동으로 읽지 않으므로 DB 정보와 `EVERYONE_OPENAPI_SERVER_URL` 등을 외부 환경변수로 공급해야 합니다. Redis의 `EVERYONE_REDIS_HOST`와 `EVERYONE_REDIS_PORT`도 기본값 없이 필수로 받으므로 실제 운영 Redis 주소와 포트를 반드시 지정합니다.

파일 업로드 제한은 [공통 application.yaml](../src/main/resources/application.yaml)에 정의되어 dev와 prod 모두에 적용됩니다. `EVERYONE_MULTIPART_MAX_FILE_SIZE`와 `EVERYONE_MULTIPART_MAX_REQUEST_SIZE`를 생략하면 각각 `10MB`, `12MB`를 사용합니다. 개발에서는 `.env.dev`, 운영에서는 배포 환경의 환경변수로 조정할 수 있습니다. 요청 전체 크기에는 파일뿐 아니라 폼 데이터와 multipart 구분자 등 부가 정보도 포함됩니다. 파일당 제한과 요청 전체 제한은 별도로 적용되며, 여러 파일을 한 번에 받는 경우에도 요청 전체 제한을 넘을 수 없습니다.

현재 [초기 스키마](../src/main/resources/db/migration/V202610220902__init_schema.sql)에 13개 테이블과 PK·FK가 정의되어 있으며, 실제 도메인 엔티티는 구현 전입니다. 엔티티를 추가할 때 초기 스키마의 컬럼·타입·제약조건과 매핑을 맞추고, 이후 스키마 변경은 새 Flyway 마이그레이션으로 작성합니다. 작성 규칙과 적용 절차는 [Flyway 마이그레이션 가이드](flyway-migration-guide.md)를 참고합니다. H2 테스트 통과만으로 PostgreSQL 마이그레이션까지 검증한 것으로 보지 않습니다.

### 운영 배포 시 보안 설정 전환

운영 배포를 시작할 때 개발용 임시 보안 설정을 다음 순서로 전환합니다.

1. JWT 인증·인가를 구현하고, 전체 요청을 허용하는 정책을 공개 경로와 인증·권한이 필요한 경로별 정책으로 교체합니다.
2. CSRF 검사를 활성화하고 `XSRF-TOKEN` 쿠키와 `X-XSRF-TOKEN` 헤더를 사용하는 쿠키 기반 CSRF 정책을 적용합니다.
3. 운영 보안 정책 적용을 완료한 뒤 `SecurityConfig`의 `@Profile("dev")` 제한을 제거합니다.

제거 대상은 Security 설정의 프로필 제한입니다. `application-dev.yaml`은 로컬 개발 환경 설정으로 유지합니다.

### 실행 중 확인할 항목

| 증상 | 확인할 내용 |
| --- | --- |
| Docker에 연결할 수 없음 | Docker 엔진 실행 여부와 현재 Docker 컨텍스트 |
| `.env.dev`를 찾지 못함 | 파일 생성 여부와 터미널의 프로젝트 루트 위치 |
| 포트 충돌 | `EVERYONE_SERVER_PORT`, `EVERYONE_DB_PORT`, `EVERYONE_REDIS_PORT`에 지정한 포트를 사용 중인 다른 프로세스 |
| `Redis health check failed` 또는 Redis 연결 거부 | Redis 컨테이너 실행 상태와 `PING` 응답, 호스트 포트, 설정 변경 후 애플리케이션 재시작 여부. Compose 연동을 끈 경우 직접 지정한 Redis 접속 정보 |
| 엔티티 추가 후 스키마 검증 실패 | DB 테이블과 마이그레이션 적용 상태 |
| Swagger 로그인 화면 또는 401 응답 | 활성 프로필과 `SecurityConfig` 로드 여부. `dev`에서는 인증 없이 접근하며, `dev`가 활성화되지 않으면 현재는 기본 보안 자동 설정 적용 |

DB 파일은 Compose의 `postgres-data` 볼륨에 저장됩니다. 기존 볼륨을 사용하는 경우 환경변수의 DB 계정 정보를 바꿔도 이미 초기화된 계정이 자동 변경되는 것은 아니므로 실제 DB 설정을 함께 확인합니다.

현재 Redis 서비스에는 별도의 이름 있는 볼륨이나 명시적인 AOF 설정이 없습니다. 컨테이너 재생성 후에도 데이터를 유지해야 하는 기능을 구현할 때 Redis 영속화 방식과 볼륨 구성을 함께 결정합니다.

## 공통코드 사용법

### 엔티티와 Auditing

필요한 필드에 맞춰 기반 클래스를 상속합니다.

| 클래스 | 제공하는 필드 |
| --- | --- |
| [BaseEntity](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/domain/BaseEntity.java) | 자동 생성 UUID `id` |
| [CreatedAtEntity](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/domain/CreatedAtEntity.java) | `id`, 생성 시각 `createdAt` |
| [UpdatedAtEntity](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/domain/UpdatedAtEntity.java) | `id`, `createdAt`, 수정 시각 `updatedAt` |

생성·수정 시각은 `LocalDateTime`이며 [JpaAuditingConfig](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/config/JpaAuditingConfig.java)에서 JPA Auditing을 활성화합니다. 실제 사용 방식은 [테스트용 엔티티](../src/test/java/com/codeit/sb13/testsupport/persistence/AuditedTestEntity.java)와 [JPA 공통 설정 테스트](../src/test/java/com/codeit/sb13/playlist_for_everyone/global/config/JpaCommonConfigTest.java)를 참고합니다.

### Hibernate 배치 설정

[공통 application.yaml](../src/main/resources/application.yaml)의 `spring.jpa.properties`에 다음 Hibernate 설정을 정의합니다. 현재 네 값은 고정값으로 관리하며 dev·test·prod에 공통 적용되므로, 별도 환경변수를 추가할 필요는 없습니다.

| 설정 | 현재 값 | 역할 |
| --- | --- | --- |
| `hibernate.default_batch_fetch_size` | `100` | 지연 로딩 대상 엔티티·컬렉션의 배치 조회 크기 |
| `hibernate.jdbc.batch_size` | `50` | JDBC 쓰기 배치의 최대 크기 |
| `hibernate.order_inserts` | `true` | INSERT 순서를 정렬해 배치 처리 기회 증가 |
| `hibernate.order_updates` | `true` | UPDATE 순서를 정렬해 배치 처리 기회 증가 |

배치 조회는 지연 로딩 대상들을 묶어 조회하여 N+1 문제를 완화합니다. JDBC 쓰기 배치는 flush 과정에서 같은 SQL 형태의 호환되는 쓰기 작업을 최대 50개씩 묶어 처리합니다. 실제 배치에는 최대 크기보다 적은 작업이 포함될 수 있습니다. 각 설정의 정의는 Hibernate의 [조회 배치 설정](https://docs.hibernate.org/orm/7.2/javadocs/org/hibernate/cfg/FetchSettings.html)과 [JDBC 배치 설정](https://docs.hibernate.org/orm/7.2/javadocs/org/hibernate/cfg/BatchSettings.html)을 참고합니다.

dev·test에서는 `org.hibernate.orm.jdbc.batch` 로그 수준이 `trace`로 설정되어 있습니다. 엔티티 저장·수정 후 flush할 때 출력되는 `Executing JDBC batch` 로그에서 쓰기 배치의 실행 여부와 크기를 확인할 수 있습니다.

배치 설정의 효과는 연관관계 매핑과 조회 방식, 처리할 데이터량에 따라 달라집니다. 도메인 구현 후 실제 쿼리 수와 처리 시간을 측정해 N+1 감소 및 쓰기 성능을 검증하고 배치 크기를 조정합니다.

### Querydsl

[QueryDslConfig](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/config/QueryDslConfig.java)가 `JPAQueryFactory`를 빈으로 제공합니다. 도메인 저장소에서 생성자 주입으로 사용합니다.

```java
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ExampleQueryRepository {
    private final JPAQueryFactory queryFactory;
}
```

위 코드는 주입 방식의 예시입니다. 실제 조회 조건은 도메인 저장소에서 구현합니다. 메인 소스의 Q 클래스는 `./gradlew compileJava` 실행 시 생성되며, 생성된 소스를 직접 작성하거나 수정하지 않습니다.

### 예외와 오류 응답

[EveryOneException](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/exception/EveryOneException.java)은 오류 코드, 상세 정보, 원인 예외를 전달하는 공통 기반입니다. [ApiErrorCode](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/exception/ApiErrorCode.java)에 HTTP 상태와 메시지를 정의하고, [GlobalExceptionHandler](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/controller/GlobalExceptionHandler.java)가 응답으로 변환합니다.

예를 들어 사용자 미존재를 판단한 서비스 위치에서 다음과 같이 사용할 수 있습니다. `userId`는 해당 메서드가 조회한 사용자 식별자입니다.

```java
throw new UserNotFoundException(Map.of("userId", userId));
```

이 예외의 HTTP 상태는 404이며, [ApiErrorResponse](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/dto/response/ApiErrorResponse.java)의 JSON 형태는 다음과 같습니다.

```json
{
  "exceptionName": "UserNotFoundException",
  "message": "사용자를 찾을 수 없습니다.",
  "details": {
    "userId": "00000000-0000-0000-0000-000000000001"
  }
}
```

- 상세 정보가 `null`이면 공통 예외에서 빈 객체로 처리합니다.
- 요청 본문의 Bean Validation 오류는 필드명 또는 객체명별 메시지 배열을 `details`에 담습니다.
- 예상하지 못한 예외는 500 상태와 공통 서버 오류 메시지로 응답합니다.
- `details`는 응답에 전달할 정보만 담습니다. 원인 예외는 별도의 `cause` 인자로 전달합니다.

현재 도메인 예외 7종은 **작성 예시**입니다. 담당 도메인의 요구사항에 맞게 오류 코드를 확정하고 확장합니다. 예시의 매핑 검증은 [DomainExceptionExamplesTest](../src/test/java/com/codeit/sb13/playlist_for_everyone/global/exception/DomainExceptionExamplesTest.java), 공통 동작 검증은 [EveryOneExceptionTest](../src/test/java/com/codeit/sb13/playlist_for_everyone/global/exception/EveryOneExceptionTest.java)로 분리되어 있습니다.

### 커서 기반 목록 응답

[CursorResponse](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/dto/response/CursorResponse.java)는 목록 조회 결과를 전달하는 공통 record입니다.

| 필드 | Java 타입 | 용도 |
| --- | --- | --- |
| `data` | `List<T>` | 도메인별 응답 DTO 목록 |
| `nextCursor` | `String` | 다음 조회에 전달할 커서 |
| `nextIdAfter` | `UUID` | 다음 조회에 사용할 보조 식별자 |
| `hasNext` | `boolean` | 다음 페이지 존재 여부 |
| `totalCount` | `long` | 집계 결과. 집계 범위는 각 API 명세에서 확정 |
| `sortBy` | `String` | 적용한 정렬 기준 |
| `sortDirection` | `SortDirection` | `ASCENDING` 또는 `DESCENDING` |

[SortDirection](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/domain/SortDirection.java)은 프로젝트에서 정의한 enum입니다. 다음 JSON은 빈 페이지의 형태를 설명하는 예시이며, 아직 구현된 목록 API의 응답은 아닙니다.

```json
{
  "data": [],
  "nextCursor": null,
  "nextIdAfter": null,
  "hasNext": false,
  "totalCount": 0,
  "sortBy": "createdAt",
  "sortDirection": "DESCENDING"
}
```

DTO에는 커서 계산이나 값 보정 로직이 없습니다. 목록 API 구현 시 빈 목록을 `[]`로 반환하는 규칙, 마지막 페이지의 커서 값, 동일 정렬값 처리, `totalCount`의 필터 적용 범위와 정렬 허용값을 해당 API 명세와 대조해야 합니다. 위 예시만으로 그 규칙이 확정되거나 자동 보장되는 것은 아닙니다.

### 로그와 비밀번호 인코더

[MDCLoggingInterceptor](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/config/MDCLoggingInterceptor.java)는 MVC 요청에 `requestId`, `requestMethod`, `requestUri`, `requestIp`를 기록하고 요청 완료 후 정리합니다. 현재 구현과 테스트는 동기 MVC 요청 기준입니다. SSE·WebSocket 등 비동기 처리의 로그 컨텍스트는 기능 구현 시 별도로 검토합니다.

[Logback 설정](../src/main/resources/logback-spring.xml)은 `dev`·`prod`에서 콘솔과 파일에, `test`에서 콘솔에 로그를 출력합니다. 기본 파일 위치는 `.logs/everyone.log`이며 `LOG_PATH`로 로그 디렉터리를 변경할 수 있습니다.

[PasswordEncoderConfig](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/config/PasswordEncoderConfig.java)는 BCrypt 기반 `PasswordEncoder`를 제공합니다. 비밀번호 저장 시 `encode`, 확인 시 `matches`를 사용합니다. 회원가입과 로그인 흐름은 도메인 기능에서 구현합니다.

## 테스트와 커버리지

Docker Desktop 또는 Docker 엔진을 실행한 뒤 프로젝트 루트에서 실행합니다.

```bash
./gradlew test jacocoTestReport
```

공통 테스트는 `test` 프로필과 H2를 사용합니다. 알림 통합 테스트는 Testcontainers로 별도의 PostgreSQL 16 컨테이너를 실행하고 실제 Flyway 마이그레이션을 적용합니다. 전체 테스트와 빌드에는 Docker가 필요하며, 로컬 PostgreSQL이나 `.env.dev`는 필요하지 않습니다. 최초 실행 시 테스트용 컨테이너 이미지 다운로드가 필요할 수 있습니다.

제목 말줄임 단위 테스트만 확인하려면 Docker 없이 다음 명령을 사용할 수 있습니다.

```bash
./gradlew test --tests '*NotificationCommandTest'
```

빌드까지 확인할 때는 다음 명령을 사용합니다.

```bash
./gradlew build
```

보고서는 실행 후 로컬 `build` 디렉터리에 생성됩니다. 생성물은 Git에 포함하지 않습니다.

| 결과 | 로컬 경로 |
| --- | --- |
| 테스트 HTML | `build/reports/tests/test/index.html` |
| 테스트 XML | `build/test-results/test/` |
| JaCoCo HTML | `build/reports/jacoco/test/html/index.html` |
| JaCoCo XML | `build/reports/jacoco/test/jacocoTestReport.xml` |

프로젝트의 테스트 커버리지 목표는 80% 이상입니다. 현재 JaCoCo 설정은 자동 생성된 Q 클래스를 제외하며, 직접 작성한 `QueryDslConfig` 등 공통코드는 측정 대상에 포함합니다. `test` 실행 후에도 보고서를 생성하도록 연결되어 있습니다. 커버리지 미달 시 빌드를 실패시키는 80% 검증 규칙과 CI 배지는 아직 구성 전입니다.

### 테스트 작성과 현재 검증 범위

- 테스트 클래스와 메서드에 한글 `@DisplayName`으로 대상과 기대 결과를 설명합니다. 파라미터화 테스트는 개별 사례도 구분할 수 있게 이름을 지정합니다.
- 공통 예외 테스트에는 [TestEveryOneException](../src/test/java/com/codeit/sb13/testsupport/exception/TestEveryOneException.java)을 사용합니다. 도메인 예외 예시의 변경이 공통 동작 테스트에 영향을 주지 않도록 분리합니다.
- 기존 테스트는 애플리케이션 컨텍스트, 예외 응답, 검증 오류, MDC, JPA Auditing, Querydsl 조회, 비밀번호 인코더, OpenAPI 설정을 확인합니다.
- 알림 테스트는 제목 말줄임, 입력 검증, 커밋 후 저장, 롤백·트랜잭션 외 발행 시 미저장, 저장 실패 시 원 업무 보존 및 후속 정상 저장을 확인합니다. 통합 테스트는 `TransactionTemplate`으로 실제 커밋·롤백을 수행합니다.
- MVC 테스트는 Security 필터를 제외해 핸들러와 인터셉터를 검증합니다. 이 테스트의 통과는 인증·인가 동작 검증을 의미하지 않습니다.

알림 통합 테스트는 PostgreSQL에서 테스트 프로필의 애플리케이션 컨텍스트 기동과 Flyway 마이그레이션을 확인합니다. 실제 Redis 연결과 캐시·세션 동작, Swagger 접속 및 커서 목록 API의 응답은 해당 결과에 포함되지 않습니다. 최신 결과와 수치는 각자의 코드에서 명령을 실행해 보고서로 확인합니다.

## 협업과 후속 작업

기능 작업에는 [이슈 템플릿](../.github/ISSUE_TEMPLATE/feature.md)을, 변경 공유에는 [PR 템플릿](../.github/PULL_REQUEST_TEMPLATE.md)을 사용합니다. PR에는 변경 이유, 실행한 테스트와 결과, 후속 작업을 기록합니다. 공통 응답이나 설정을 변경하면 관련 테스트와 이 문서도 함께 확인합니다.

브랜치명과 커밋 메시지 형식은 팀에서 합의한 규칙을 따릅니다. 현재 이 문서에서 별도 형식을 정하지 않습니다.

다음 항목은 후속 작업으로 관리합니다.

- 도메인별 API·서비스·엔티티와 PostgreSQL/Flyway 검증
- JWT 인증·인가 및 쿠키 기반 CSRF 정책 적용, 운영 배포 시 개발용 Security 프로필 제한 제거, 관리자 초기화 및 인증 실패 응답 처리
- 목록 API의 커서 규칙 확정과 직렬화·페이징 테스트
- Redis 기반 캐시·세션 기능 구현과 데이터 보관 정책 결정
- 배치, S3, SSE·WebSocket 등 기능별 구현과 필요한 인프라 추가
- GitHub Actions 테스트 자동화, 커버리지 80% 검증, README 커버리지 배지 및 배포 파이프라인
