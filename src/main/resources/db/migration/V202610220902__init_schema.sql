-- ============================================================
-- 사용자 계정 (users)
-- ============================================================
CREATE TABLE "users" (
    "id"         UUID         NOT NULL,
    "email"      VARCHAR(255) NOT NULL,
    "nickname"   VARCHAR(100) NULL,
    "password"   VARCHAR(255) NULL,
    "role"       VARCHAR(10)  NOT NULL,
    "enabled"    BOOLEAN      NOT NULL,
    "created_at" TIMESTAMP    NOT NULL,
    "updated_at" TIMESTAMP    NULL,

    CONSTRAINT "PK_USERS" PRIMARY KEY ("id")
);

COMMENT ON COLUMN "users"."id" IS '사용자ID';
COMMENT ON COLUMN "users"."email" IS '이메일';
COMMENT ON COLUMN "users"."nickname" IS '이름';
COMMENT ON COLUMN "users"."password" IS '비밀번호';
COMMENT ON COLUMN "users"."role" IS '권한';
COMMENT ON COLUMN "users"."enabled" IS '계정잠금상태';
COMMENT ON COLUMN "users"."created_at" IS '생성일자';
COMMENT ON COLUMN "users"."updated_at" IS '수정일자';


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


-- ============================================================
-- 임시 비밀번호 (temporary_passwords)
-- ============================================================
CREATE TABLE "temporary_passwords" (
    "id"         UUID         NOT NULL,
    "user_id"    UUID         NOT NULL,
    "password"   VARCHAR(255) NOT NULL,
    "expired_at" TIMESTAMP    NOT NULL,
    "status"     VARCHAR(10)  NOT NULL,
    "created_at" TIMESTAMP    NOT NULL,
    "updated_at" TIMESTAMP    NULL,

    CONSTRAINT "PK_TEMPORARY_PASSWORDS" PRIMARY KEY ("id"),
    CONSTRAINT "FK_users_TO_temporary_passwords_1"
        FOREIGN KEY ("user_id") REFERENCES "users" ("id")
);

COMMENT ON COLUMN "temporary_passwords"."id" IS '임시 비밀번호 ID';
COMMENT ON COLUMN "temporary_passwords"."user_id" IS '사용자ID';
COMMENT ON COLUMN "temporary_passwords"."password" IS '임시 비밀번호';
COMMENT ON COLUMN "temporary_passwords"."expired_at" IS '만료시간';
COMMENT ON COLUMN "temporary_passwords"."status" IS '사용상태';
COMMENT ON COLUMN "temporary_passwords"."created_at" IS '생성시간';
COMMENT ON COLUMN "temporary_passwords"."updated_at" IS '수정시간';


-- ============================================================
-- 콘텐츠 (contents)
-- ============================================================
CREATE TABLE "contents" (
    "id"               UUID         NOT NULL,
    "type"             VARCHAR(10)  NOT NULL,
    "title"            VARCHAR(100) NOT NULL,
    "description"      VARCHAR(255) NULL,
    "thumbnail_url"    VARCHAR(255) NULL,
    "provider"         VARCHAR(30)  NOT NULL,
    "provider_item_id" VARCHAR(255) NOT NULL,
    "created_at"       TIMESTAMP    NOT NULL,
    "updated_at"       TIMESTAMP    NULL,

    CONSTRAINT "PK_CONTENTS" PRIMARY KEY ("id")
);

COMMENT ON COLUMN "contents"."id" IS '콘텐츠ID';
COMMENT ON COLUMN "contents"."type" IS '콘텐츠타입';
COMMENT ON COLUMN "contents"."title" IS '제목';
COMMENT ON COLUMN "contents"."description" IS '설명';
COMMENT ON COLUMN "contents"."thumbnail_url" IS '썸네일URL';
COMMENT ON COLUMN "contents"."provider" IS '출처';
COMMENT ON COLUMN "contents"."provider_item_id" IS '출처ID';
COMMENT ON COLUMN "contents"."created_at" IS '생성일시';
COMMENT ON COLUMN "contents"."updated_at" IS '수정일시';


-- ============================================================
-- 콘텐츠 태그 (content_tags)
-- ============================================================
CREATE TABLE "content_tags" (
    "id"         UUID        NOT NULL,
    "content_id" UUID        NOT NULL,
    "tag"        VARCHAR(50) NOT NULL,

    CONSTRAINT "PK_CONTENT_TAGS" PRIMARY KEY ("id"),
    CONSTRAINT "FK_contents_TO_content_tags_1"
        FOREIGN KEY ("content_id") REFERENCES "contents" ("id")
);

COMMENT ON COLUMN "content_tags"."id" IS '콘텐츠태그ID';
COMMENT ON COLUMN "content_tags"."content_id" IS '콘텐츠ID';
COMMENT ON COLUMN "content_tags"."tag" IS '태그';


-- ============================================================
-- 콘텐츠 리뷰 (reviews)
-- ============================================================
CREATE TABLE "reviews" (
    "id"         UUID             NOT NULL,
    "content_id" UUID             NOT NULL,
    "author_id"  UUID             NOT NULL,
    "text"       TEXT             NOT NULL,
    "rating"     DOUBLE PRECISION NOT NULL,
    "created_at" TIMESTAMP        NOT NULL,
    "updated_at" TIMESTAMP        NULL,

    CONSTRAINT "PK_REVIEWS" PRIMARY KEY ("id"),
    CONSTRAINT "FK_contents_TO_reviews_1"
        FOREIGN KEY ("content_id") REFERENCES "contents" ("id"),
    CONSTRAINT "FK_users_TO_reviews_1"
        FOREIGN KEY ("author_id") REFERENCES "users" ("id")
);

COMMENT ON COLUMN "reviews"."id" IS 'id';
COMMENT ON COLUMN "reviews"."content_id" IS '콘텐츠ID';
COMMENT ON COLUMN "reviews"."author_id" IS '사용자ID';
COMMENT ON COLUMN "reviews"."text" IS '리뷰 내용';
COMMENT ON COLUMN "reviews"."rating" IS '평점';
COMMENT ON COLUMN "reviews"."created_at" IS '작성 시간';
COMMENT ON COLUMN "reviews"."updated_at" IS '수정 시간';


-- ============================================================
-- 플레이리스트 (playlists)
-- ============================================================
CREATE TABLE "playlists" (
    "id"          UUID         NOT NULL,
    "owner_id"    UUID         NOT NULL,
    "title"       VARCHAR(100) NOT NULL,
    "description" VARCHAR(255) NOT NULL,
    "created_at"  TIMESTAMP    NOT NULL,
    "updated_at"  TIMESTAMP    NULL,

    CONSTRAINT "PK_PLAYLISTS" PRIMARY KEY ("id"),
    CONSTRAINT "FK_users_TO_playlists_1"
        FOREIGN KEY ("owner_id") REFERENCES "users" ("id")
);

COMMENT ON COLUMN "playlists"."id" IS '플레이리스트ID';
COMMENT ON COLUMN "playlists"."owner_id" IS '사용자ID';
COMMENT ON COLUMN "playlists"."title" IS '제목';
COMMENT ON COLUMN "playlists"."description" IS '설명';
COMMENT ON COLUMN "playlists"."created_at" IS '생성일시';
COMMENT ON COLUMN "playlists"."updated_at" IS '수정일시';


-- ============================================================
-- 플레이리스트에 포함된 콘텐츠 (playlist_contents)
-- ============================================================
CREATE TABLE "playlist_contents" (
    "id"          UUID      NOT NULL,
    "playlist_id" UUID      NOT NULL,
    "content_id"  UUID      NOT NULL,
    "created_at"  TIMESTAMP NOT NULL,

    CONSTRAINT "PK_PLAYLIST_CONTENTS" PRIMARY KEY ("id"),
    CONSTRAINT "FK_playlists_TO_playlist_contents_1"
        FOREIGN KEY ("playlist_id") REFERENCES "playlists" ("id"),
    CONSTRAINT "FK_contents_TO_playlist_contents_1"
        FOREIGN KEY ("content_id") REFERENCES "contents" ("id")
);

COMMENT ON COLUMN "playlist_contents"."id" IS '플레이리스트콘텐츠 ID';
COMMENT ON COLUMN "playlist_contents"."playlist_id" IS '플레이리스트 ID';
COMMENT ON COLUMN "playlist_contents"."content_id" IS '콘텐츠ID';
COMMENT ON COLUMN "playlist_contents"."created_at" IS '생성일시';


-- ============================================================
-- 플레이리스트 구독 (subscriptions)
-- ============================================================
CREATE TABLE "subscriptions" (
    "id"            UUID      NOT NULL,
    "playlist_id"   UUID      NOT NULL,
    "subscriber_id" UUID      NOT NULL,
    "created_at"    TIMESTAMP NOT NULL,

    CONSTRAINT "PK_SUBSCRIPTIONS" PRIMARY KEY ("id"),
    CONSTRAINT "FK_playlists_TO_subscriptions_1"
        FOREIGN KEY ("playlist_id") REFERENCES "playlists" ("id"),
    CONSTRAINT "FK_users_TO_subscriptions_1"
        FOREIGN KEY ("subscriber_id") REFERENCES "users" ("id")
);

COMMENT ON COLUMN "subscriptions"."id" IS '구독 ID';
COMMENT ON COLUMN "subscriptions"."playlist_id" IS '플레이리스트ID';
COMMENT ON COLUMN "subscriptions"."subscriber_id" IS '구독한 사용자 ID';
COMMENT ON COLUMN "subscriptions"."created_at" IS '구독 생성일자';


-- ============================================================
-- 사용자 팔로우 (follows)
-- ============================================================
CREATE TABLE "follows" (
    "id"          UUID      NOT NULL,
    "followee_id" UUID      NOT NULL,
    "follower_id" UUID      NOT NULL,
    "created_at"  TIMESTAMP NOT NULL,

    CONSTRAINT "PK_FOLLOWS" PRIMARY KEY ("id"),
    CONSTRAINT "FK_users_TO_follows_1"
        FOREIGN KEY ("followee_id") REFERENCES "users" ("id"),
    CONSTRAINT "FK_users_TO_follows_2"
        FOREIGN KEY ("follower_id") REFERENCES "users" ("id")
);

COMMENT ON COLUMN "follows"."id" IS '팔로우 ID';
COMMENT ON COLUMN "follows"."followee_id" IS '팔로우 대상';
COMMENT ON COLUMN "follows"."follower_id" IS '팔로워';
COMMENT ON COLUMN "follows"."created_at" IS '팔로우 생성일자';


-- ============================================================
-- 사용자 간 대화 (conversations)
-- ============================================================
CREATE TABLE "conversations" (
    "id"         UUID      NOT NULL,
    "user_a_id"  UUID      NOT NULL,
    "user_b_id"  UUID      NOT NULL,
    "created_at" TIMESTAMP NOT NULL,

    CONSTRAINT "PK_CONVERSATIONS" PRIMARY KEY ("id"),
    CONSTRAINT "FK_users_TO_conversations_1"
        FOREIGN KEY ("user_a_id") REFERENCES "users" ("id"),
    CONSTRAINT "FK_users_TO_conversations_2"
        FOREIGN KEY ("user_b_id") REFERENCES "users" ("id")
);

COMMENT ON COLUMN "conversations"."id" IS '대화방 ID';
COMMENT ON COLUMN "conversations"."user_a_id" IS '사용자 a ID';
COMMENT ON COLUMN "conversations"."user_b_id" IS '사용자 b ID';
COMMENT ON COLUMN "conversations"."created_at" IS '생성일시';


-- ============================================================
-- 다이렉트 메시지 (direct_messages)
-- ============================================================
CREATE TABLE "direct_messages" (
    "id"              UUID      NOT NULL,
    "conversation_id" UUID      NOT NULL,
    "sender_id"       UUID      NOT NULL,
    "receiver_id"     UUID      NOT NULL,
    "content"         TEXT      NOT NULL,
    "is_read"         BOOLEAN   NOT NULL,
    "created_at"      TIMESTAMP NOT NULL,

    CONSTRAINT "PK_DIRECT_MESSAGES" PRIMARY KEY ("id"),
    CONSTRAINT "FK_conversations_TO_direct_messages_1"
        FOREIGN KEY ("conversation_id") REFERENCES "conversations" ("id"),
    CONSTRAINT "FK_users_TO_direct_messages_1"
        FOREIGN KEY ("sender_id") REFERENCES "users" ("id"),
    CONSTRAINT "FK_users_TO_direct_messages_2"
        FOREIGN KEY ("receiver_id") REFERENCES "users" ("id")
);

COMMENT ON COLUMN "direct_messages"."id" IS '다이렉트메시지 ID';
COMMENT ON COLUMN "direct_messages"."conversation_id" IS '대화방 ID';
COMMENT ON COLUMN "direct_messages"."sender_id" IS '발신자';
COMMENT ON COLUMN "direct_messages"."receiver_id" IS '수신자';
COMMENT ON COLUMN "direct_messages"."content" IS '내용';
COMMENT ON COLUMN "direct_messages"."is_read" IS '읽음여부';
COMMENT ON COLUMN "direct_messages"."created_at" IS '생성일시';


-- ============================================================
-- 사용자 알림 (notifications)
-- ============================================================
CREATE TABLE "notifications" (
    "id"          UUID         NOT NULL,
    "receiver_id" UUID         NOT NULL,
    "title"       VARCHAR(255) NOT NULL,
    "content"     TEXT         NOT NULL,
    "level"       VARCHAR(20)  NOT NULL,
    "created_at"  TIMESTAMP    NOT NULL,

    CONSTRAINT "PK_NOTIFICATIONS" PRIMARY KEY ("id"),
    CONSTRAINT "FK_users_TO_notifications_1"
        FOREIGN KEY ("receiver_id") REFERENCES "users" ("id")
);

COMMENT ON COLUMN "notifications"."id" IS '알림ID';
COMMENT ON COLUMN "notifications"."receiver_id" IS '사용자ID';
COMMENT ON COLUMN "notifications"."title" IS '제목';
COMMENT ON COLUMN "notifications"."content" IS '내용';
COMMENT ON COLUMN "notifications"."level" IS '알림레벨';
COMMENT ON COLUMN "notifications"."created_at" IS '생성일시';
