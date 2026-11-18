-- ============================================================
-- 팔로우 중복 방지
-- ============================================================
ALTER TABLE "follows"
    ADD CONSTRAINT "uq_follows_follower_followee"
    UNIQUE ("follower_id", "followee_id");
