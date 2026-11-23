-- ============================================================
-- 소셜 계정 연결 (social_accounts)
-- ============================================================
CREATE TABLE "social_accounts" (
    "id"               UUID         NOT NULL,
    "user_id"          UUID         NOT NULL,
    "provider"         VARCHAR(20)  NOT NULL,
    "provider_user_id" VARCHAR(255) NOT NULL,
    "created_at"       TIMESTAMP    NOT NULL,

    CONSTRAINT "PK_SOCIAL_ACCOUNTS" PRIMARY KEY ("id"),
    CONSTRAINT "FK_users_TO_social_accounts_1"
        FOREIGN KEY ("user_id") REFERENCES "users" ("id"),
    -- 같은 제공자의 같은 회원 ID는 한 사용자에게만 연결한다.
    CONSTRAINT "uq_social_accounts_provider_user"
        UNIQUE ("provider", "provider_user_id"),
    -- 한 사용자는 제공자마다 하나의 계정만 연결한다.
    CONSTRAINT "uq_social_accounts_user_provider"
        UNIQUE ("user_id", "provider")
);

COMMENT ON COLUMN "social_accounts"."id" IS '소셜 계정 연결 ID';
COMMENT ON COLUMN "social_accounts"."user_id" IS '사용자ID';
COMMENT ON COLUMN "social_accounts"."provider" IS '소셜 제공자 (GOOGLE, KAKAO)';
COMMENT ON COLUMN "social_accounts"."provider_user_id" IS '제공자 회원 고유 ID';
COMMENT ON COLUMN "social_accounts"."created_at" IS '연결일자';