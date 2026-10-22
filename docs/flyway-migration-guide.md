# Flyway 마이그레이션 가이드

모두의 플리 프로젝트에서 DB 스키마를 Flyway로 관리하는 방법을 정리합니다. 로컬 개발 환경 전체 설정은 [팀원 개발 가이드](DEVELOPMENT.md)를 참고합니다.

## 현재 스키마

SQL 파일은 `src/main/resources/db/migration/`에 둡니다. 현재 초기 파일은 [V202610220902__init_schema.sql](../src/main/resources/db/migration/V202610220902__init_schema.sql)입니다.

- 초기 파일에는 테이블 13개, 컬럼 75개, PK 13개, FK 18개가 정의되어 있습니다.
- 각 테이블의 `CREATE TABLE` 안에 PK·FK를 선언하고, 바로 아래에 컬럼 주석을 배치합니다.
- PK 외의 UNIQUE 제약조건, 별도 조회 인덱스, CHECK, DB 기본값, 명시적인 삭제 정책은 아직 없습니다.
- 실제 도메인 엔티티는 구현 전입니다. 엔티티를 작성할 때 이 SQL의 타입·길이·NULL 조건과 매핑을 맞춥니다.

이 문서의 UNIQUE·인덱스 예시는 이후 직접 작성할 때 참고하는 코드이며, 현재 초기 스키마에는 적용되어 있지 않습니다.

## 핵심 원칙과 파일명

- 초기 공통 스키마는 현재 초기 파일 하나에서 관리합니다.
- 공유되었거나 DB에 적용된 파일은 수정하지 않고, 이후 변경은 새 버전의 마이그레이션으로 추가합니다.
- 개발·운영 환경에서 스키마 변경은 Flyway가 담당하고, Hibernate는 기본 `ddl-auto: validate`로 엔티티 매핑을 검증합니다.
- 엔티티의 테이블·컬럼·제약조건·인덱스를 변경하면 필요한 Flyway SQL도 함께 작성합니다.

Flyway는 적용한 버전과 체크섬을 이력에 기록합니다. 적용된 파일의 SQL이나 주석·공백을 변경하면 체크섬 불일치가 발생할 수 있으므로, 가독성 정리도 공유 전에 완료합니다. [Flyway 버전 마이그레이션](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html)

파일명은 다음 형식을 사용합니다.

```text
VyyyyMMddHHmm__description.sql
```

현재 파일과 이후 변경 파일의 예시입니다. 두 번째와 세 번째 파일은 아직 존재하지 않는 작성 예시입니다.

```text
V202610220902__init_schema.sql
V202610221030__add_unique_constraints.sql
V202610221100__add_direct_messages_index.sql
```

버전과 설명 사이는 밑줄 두 개(`__`), 설명은 영어 소문자와 밑줄을 사용합니다. 새 버전은 기존 최신 버전보다 크게 정하고, 같은 분에 여러 팀원이 작업할 때는 버전이 겹치지 않는지 확인합니다. PR을 올리기 전에 기준 브랜치의 최신 마이그레이션과도 대조합니다.

## 테이블 생성 순서

현재 파일은 참조 대상 테이블이 먼저 생성되도록 아래 순서로 구성되어 있습니다. 새 테이블을 추가할 때도 FK가 참조하는 테이블과 PK가 먼저 존재해야 합니다.

| 순서 | 테이블 | 역할 |
| --- | --- | --- |
| 1 | `users` | 사용자 계정 |
| 2 | `profiles` | 프로필 파일 정보 |
| 3 | `temporary_passwords` | 임시 비밀번호 |
| 4 | `contents` | 콘텐츠 |
| 5 | `content_tags` | 콘텐츠 태그 |
| 6 | `reviews` | 콘텐츠 리뷰 |
| 7 | `playlists` | 플레이리스트 |
| 8 | `playlist_contents` | 플레이리스트에 포함된 콘텐츠 |
| 9 | `subscriptions` | 플레이리스트 구독 |
| 10 | `follows` | 사용자 팔로우 |
| 11 | `conversations` | 사용자 간 대화 |
| 12 | `direct_messages` | 다이렉트 메시지 |
| 13 | `notifications` | 사용자 알림 |

## 테이블 작성 형태

하나의 테이블은 **구분 주석 → 컬럼 → PK·FK → 컬럼 주석** 순서로 작성합니다. 아래는 현재 `profiles` 정의이며, 참조 대상인 `users`가 먼저 생성되어 있어야 합니다.

```sql
-- ============================================================
-- 프로필 파일 (profiles)
-- ============================================================
CREATE TABLE "profiles" (
    "id"              UUID         NOT NULL,
    "user_id"         UUID         NOT NULL,
    "origin_filename" VARCHAR(255) NOT NULL,
    "content_type"    VARCHAR(100) NOT NULL,
    "size"            BIGINT       NOT NULL,
    "created_at"      TIMESTAMP    NOT NULL,

    CONSTRAINT "PK_PROFILES" PRIMARY KEY ("id"),
    CONSTRAINT "FK_users_TO_profiles_1"
        FOREIGN KEY ("user_id") REFERENCES "users" ("id")
);

COMMENT ON COLUMN "profiles"."id" IS '프로필ID';
COMMENT ON COLUMN "profiles"."user_id" IS '사용자ID';
COMMENT ON COLUMN "profiles"."origin_filename" IS '기존 파일 명칭';
COMMENT ON COLUMN "profiles"."content_type" IS '컨텐츠타입';
COMMENT ON COLUMN "profiles"."size" IS '파일사이즈';
COMMENT ON COLUMN "profiles"."created_at" IS '생성일자';
```

- 들여쓰기는 공백 4칸을 사용하고, 같은 테이블의 컬럼명·타입·NULL 조건을 정렬합니다.
- SQL 키워드와 타입은 대문자로 작성합니다.
- 테이블·컬럼명은 `lower_snake_case`로 작성하고, 현재 SQL처럼 큰따옴표로 감쌉니다.
- 기존 제약조건 이름은 `"PK_PROFILES"`, `"FK_users_TO_profiles_1"`처럼 대소문자를 포함해 그대로 사용합니다.
- 마지막 컬럼과 첫 제약조건 사이에는 빈 줄을 두고, 각 선언은 쉼표로 구분합니다. 마지막 제약조건 뒤에는 쉼표를 붙이지 않습니다.
- `COMMENT ON COLUMN`은 테이블 생성문 아래에 모으며, 실제 컬럼명과 일치시킵니다.

이 형태는 초기 스키마와 새 테이블 생성에 사용합니다. 이미 존재하는 테이블에 제약조건이나 컬럼을 추가할 때는 새 버전 파일에서 `ALTER TABLE`을 사용합니다.

## UUID와 컬럼 타입

공통 [BaseEntity](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/domain/BaseEntity.java)는 JPA의 `GenerationType.UUID`로 ID를 생성합니다.

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

현재 SQL의 PK는 `"id" UUID NOT NULL`이며, DB에서 UUID를 생성하는 기본값은 없습니다. JPA를 거치지 않고 직접 INSERT할 때는 ID도 명시해야 합니다. FK에는 참조할 기존 행의 ID를 넣습니다.

| 대상 | 현재 SQL 기준 |
| --- | --- |
| PK와 FK | `UUID` |
| 생성일시 | `created_at`이 있는 모든 테이블에서 `TIMESTAMP NOT NULL` |
| 수정일시 | `updated_at`이 있는 테이블에서 `TIMESTAMP NULL` |
| 프로필 파일 크기 | `profiles.size BIGINT NOT NULL` |
| 리뷰 평점 | `reviews.rating DOUBLE PRECISION NOT NULL` |
| DM 읽음 여부 | `direct_messages.is_read BOOLEAN NOT NULL` |
| 콘텐츠 출처 | `contents.provider VARCHAR(30) NOT NULL` |
| 출처의 콘텐츠 ID | `contents.provider_item_id VARCHAR(255) NOT NULL` |

[CreatedAtEntity](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/domain/CreatedAtEntity.java)와 [UpdatedAtEntity](../src/main/java/com/codeit/sb13/playlist_for_everyone/global/domain/UpdatedAtEntity.java)는 `LocalDateTime`과 JPA Auditing을 사용합니다. 현재 DB에는 생성일시 기본값도 없으므로 직접 INSERT하는 경우 필수 생성일시를 함께 넣어야 합니다. `content_tags`에는 생성·수정일시 컬럼이 없습니다.

`provider_item_id`는 내부 PK와 별개인 외부 출처의 ID입니다. 출처마다 숫자·문자열 형식이 다를 수 있으므로 UUID 타입이나 UUID 문자열 길이로 제한하지 않습니다. 서로 다른 출처에서 같은 값이 나올 수 있어 이 컬럼 단독으로 UNIQUE를 지정하지 않습니다.

## FK 관계

현재 초기 파일에 선언된 FK 18개입니다.

| 참조하는 컬럼 | 참조 대상 |
| --- | --- |
| `profiles.user_id` | `users.id` |
| `temporary_passwords.user_id` | `users.id` |
| `content_tags.content_id` | `contents.id` |
| `reviews.content_id` | `contents.id` |
| `reviews.author_id` | `users.id` |
| `playlists.owner_id` | `users.id` |
| `playlist_contents.playlist_id` | `playlists.id` |
| `playlist_contents.content_id` | `contents.id` |
| `subscriptions.playlist_id` | `playlists.id` |
| `subscriptions.subscriber_id` | `users.id` |
| `follows.followee_id` | `users.id` |
| `follows.follower_id` | `users.id` |
| `conversations.user_a_id` | `users.id` |
| `conversations.user_b_id` | `users.id` |
| `direct_messages.conversation_id` | `conversations.id` |
| `direct_messages.sender_id` | `users.id` |
| `direct_messages.receiver_id` | `users.id` |
| `notifications.receiver_id` | `users.id` |

현재 FK에는 `ON DELETE`가 없어 기본 `NO ACTION`이 적용됩니다. 참조하는 행이 남아 있으면 부모 행을 삭제할 수 없으므로, 삭제 기능을 구현할 때 연관 데이터 처리 정책도 정합니다. FK는 참조하는 쪽 컬럼에 인덱스를 자동 생성하지 않습니다. [PostgreSQL FK 문서](https://www.postgresql.org/docs/16/ddl-constraints.html#DDL-CONSTRAINTS-FK)

## UNIQUE와 인덱스 작성 예시

### UNIQUE 제약조건

아래는 **현재 미적용된 예시**입니다. 반영할 때는 새 마이그레이션 파일에 작성하고, 기존 데이터에 중복이 없는지 먼저 확인합니다.

```sql
-- 동일한 이메일로 중복 가입 방지
ALTER TABLE "users"
    ADD CONSTRAINT "UQ_USERS_EMAIL"
    UNIQUE ("email");

-- 같은 플레이리스트에 같은 콘텐츠 중복 추가 방지
ALTER TABLE "playlist_contents"
    ADD CONSTRAINT "UQ_PLAYLIST_CONTENTS_PLAYLIST_CONTENT"
    UNIQUE ("playlist_id", "content_id");

-- 같은 사용자의 동일 플레이리스트 중복 구독 방지
ALTER TABLE "subscriptions"
    ADD CONSTRAINT "UQ_SUBSCRIPTIONS_PLAYLIST_SUBSCRIBER"
    UNIQUE ("playlist_id", "subscriber_id");

-- 동일한 사용자 간 중복 팔로우 방지
ALTER TABLE "follows"
    ADD CONSTRAINT "UQ_FOLLOWS_FOLLOWER_FOLLOWEE"
    UNIQUE ("follower_id", "followee_id");

-- 같은 콘텐츠에 같은 태그 중복 등록 방지
ALTER TABLE "content_tags"
    ADD CONSTRAINT "UQ_CONTENT_TAGS_CONTENT_TAG"
    UNIQUE ("content_id", "tag");
```

복합 UNIQUE는 컬럼 값의 조합을 제한합니다. `UNIQUE ("playlist_id", "subscriber_id")`를 선언해도 여러 사용자가 같은 플레이리스트를 구독하는 것은 허용됩니다.

엔티티에도 제약조건을 표현한다면 이름과 대상 컬럼을 SQL과 맞춥니다. 아래는 향후 사용자 엔티티의 `@Table` 작성 예시입니다.

```java
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_USERS_EMAIL", columnNames = "email")
    }
)
```

제약조건 이름을 명확히 관리하려면 `@Column(unique = true)`보다 이름을 지정한 `@UniqueConstraint`를 사용합니다. `ddl-auto: validate`에서는 이 어노테이션을 추가하는 것만으로 DB에 제약조건이 생성되지 않습니다.

### 조회 인덱스

다음도 **현재 미적용된 예시**입니다. 대화방별로 생성일시와 ID를 기준으로 메시지를 조회하는 쿼리에 맞춘 후보이며, 실제 필터·정렬 조건과 실행 계획을 확인해 반영합니다.

```sql
CREATE INDEX "IDX_DIRECT_MESSAGES_CONVERSATION_CREATED_ID"
    ON "direct_messages" ("conversation_id", "created_at", "id");
```

엔티티에 `@Index`를 선언한다면 SQL의 인덱스 이름과 컬럼 순서를 맞춥니다. PK·UNIQUE가 자동 생성하는 인덱스와 중복되는 인덱스를 추가하지 않도록 확인합니다. [PostgreSQL 제약조건 문서](https://www.postgresql.org/docs/16/ddl-constraints.html)

## 변경 작업 흐름

1. 변경할 엔티티 매핑과 DB 컬럼·제약조건을 정리합니다.
2. 최신 마이그레이션과 버전 중복 여부를 확인하고 새 SQL 파일을 추가합니다.
3. 기존 테이블은 `ALTER TABLE`, 새 테이블은 앞서 설명한 `CREATE TABLE` 형태로 작성합니다.
4. 데이터가 있는 테이블에 UNIQUE나 NOT NULL을 추가한다면 기존 중복·NULL 데이터 처리도 검토합니다.
5. 별도의 PostgreSQL 검증 DB에서 마이그레이션 적용과 엔티티 매핑을 확인합니다.
6. PR에 관련 엔티티·SQL 변경과 실제 검증 결과를 함께 기록합니다.

공유·적용된 초기 파일의 내용을 되돌리거나 파일명을 바꾸는 방식으로 변경 이력을 정리하지 않습니다.

## 로컬 실행

명령은 프로젝트 루트에서 실행합니다. JDK 17과 Docker 엔진을 준비하고, 처음 설정할 때 [.env.example](../.env.example) 전체를 `.env.dev`로 복사합니다. 이미 파일이 있으면 기존 값을 유지합니다.

macOS/Linux:

```bash
if [ ! -f .env.dev ]; then
    cp .env.example .env.dev
fi
```

Windows PowerShell:

```powershell
if (-not (Test-Path .env.dev)) {
    Copy-Item .env.example .env.dev
}
```

아래는 예시 파일 중 DB·마이그레이션 관련 값입니다. `.env.dev` 전체를 이 항목만으로 대체하지 않고, 개인 환경에 맞춰 값을 확인합니다.

```properties
EVERYONE_DB_HOST=localhost
EVERYONE_DB_PORT=5432
EVERYONE_DB_NAME=everyone
EVERYONE_DB_USERNAME=everyone
EVERYONE_DB_PASSWORD=change-me
EVERYONE_JPA_DDL_AUTO=validate
EVERYONE_FLYWAY_ENABLED=true
EVERYONE_DOCKER_COMPOSE_ENABLED=true
```

애플리케이션 실행:

```bash
./gradlew bootRun
```

```powershell
.\gradlew.bat bootRun
```

기본 프로필은 `dev`입니다. 현재 개발 설정은 Spring Boot의 Compose 연동으로 PostgreSQL 16을 사용하며, Flyway가 애플리케이션 시작 시 마이그레이션을 적용합니다. 빌드에는 Flyway Gradle 플러그인이 없어 별도의 `flywayMigrate` 태스크를 사용하는 구성은 아닙니다. [Spring Boot의 Flyway 실행](https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)

기존 PostgreSQL에 직접 연결하려면 `EVERYONE_DOCKER_COMPOSE_ENABLED=false`와 해당 DB 접속 정보를 설정합니다. 초기 마이그레이션을 처음 검증할 때는 별도의 빈 DB를 사용합니다. 기존 테이블이나 데이터가 있는 DB에서 충돌이 나면 접속 대상과 적용 이력을 먼저 확인하며, 데이터 삭제나 Docker 볼륨 제거를 기본 실행 절차로 사용하지 않습니다.

DB 클라이언트에서 적용 이력을 확인할 때는 다음 조회를 사용할 수 있습니다.

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

빈 DB에 초기 파일만 적용했다면 버전 `202610220902`의 성공 기록을 확인합니다. 같은 DB로 다시 시작했을 때 기존 마이그레이션이 중복 적용되지 않고 정상 기동하는지도 확인합니다.

## 프로필과 검증 범위

| 프로필 | DB | JPA DDL | Flyway | Compose |
| --- | --- | --- | --- | --- |
| `dev` | PostgreSQL | 기본 `validate` | 기본 활성화 | 기본 활성화 |
| `prod` | PostgreSQL | 기본 `validate` | 기본 활성화 | 비활성화 |
| `test` | H2 메모리 DB | `create-drop` | 비활성화 | 비활성화 |

설정 기준은 [개발 설정](../src/main/resources/application-dev.yaml), [운영 설정](../src/main/resources/application-prod.yaml), [테스트 설정](../src/test/resources/application-test.yaml)입니다. 운영 프로필은 `.env.dev`를 자동으로 읽지 않으며, 접속 정보는 배포 환경에서 공급합니다.

기본 테스트는 [테스트 프로필 지정](../src/test/resources/application.properties)에 따라 `test` 프로필을 사용합니다.

```bash
./gradlew test
```

```powershell
.\gradlew.bat test
```

현재 H2 테스트 통과는 PostgreSQL 마이그레이션 검증을 의미하지 않습니다. `ddl-auto: validate` 성공만으로 UNIQUE·FK·인덱스까지 모두 검증했다고 간주하지 않습니다.

PostgreSQL 검증에서는 다음을 확인합니다.

- 초기 파일만 적용하는 경우 13개 테이블과 PK 13개·FK 18개가 생성되는지 확인합니다.
- 이후 변경 파일이 있다면 새 제약조건·인덱스와 기존 데이터에 미치는 영향까지 확인합니다.
- 같은 DB로 재시작했을 때 체크섬 오류나 중복 적용이 없는지 확인합니다.
- UNIQUE를 추가한 경우 중복 입력이 거절되고, 서로 다른 조합은 허용되는지 확인합니다.

현재 PostgreSQL Testcontainers 의존성은 준비되어 있지만, Flyway를 실행하는 통합 테스트는 아직 없습니다. 필요할 때 별도의 PostgreSQL 통합 테스트에서 Flyway를 활성화해 검증합니다. 문서의 실행 예시는 검증 절차를 안내하는 것으로, 마이그레이션 실행 성공을 보증하는 결과 기록은 아닙니다.

## .gitkeep

현재 마이그레이션 디렉터리에는 실제 초기 SQL 파일이 있으므로 `.gitkeep`은 필요하지 않습니다.
