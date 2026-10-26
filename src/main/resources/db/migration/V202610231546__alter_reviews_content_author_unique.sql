-- ============================================================
-- 태그 중복 방지
-- ============================================================
ALTER TABLE reviews
    ADD CONSTRAINT uk_review_content_author
        UNIQUE (content_id, author_id);