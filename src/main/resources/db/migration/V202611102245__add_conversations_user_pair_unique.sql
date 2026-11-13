-- ============================================================
-- 대화방 중복 방지
-- ============================================================
ALTER TABLE "conversations"
    ADD CONSTRAINT "uq_conversations_user_pair"
    UNIQUE ("user_a_id", "user_b_id");
