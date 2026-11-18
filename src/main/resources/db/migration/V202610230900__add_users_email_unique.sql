-- 동일한 이메일로 중복 가입 방지
ALTER TABLE "users"
    ADD CONSTRAINT "UQ_USERS_EMAIL"
    UNIQUE ("email");

-- enabled는 활성 여부(true = 정상, false = 잠김)
COMMENT ON COLUMN "users"."enabled" IS '계정활성상태 (false = 잠김)';