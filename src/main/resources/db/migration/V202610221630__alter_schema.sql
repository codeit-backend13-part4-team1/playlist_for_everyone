-- ============================================================
-- contents 테이블 컬럼 변경
-- ============================================================
ALTER TABLE contents
    ALTER COLUMN description TYPE TEXT,
    ALTER COLUMN description SET NOT NULL,
    ALTER COLUMN thumbnail_url TYPE VARCHAR(2048),
    ALTER COLUMN thumbnail_url SET NOT NULL,
    ALTER COLUMN provider_item_id DROP NOT NULL;

-- ============================================================
-- 콘텐츠 중복 방지
-- ============================================================
ALTER TABLE contents
    ADD CONSTRAINT uk_content_provider_type_item_id
        UNIQUE (provider, type, provider_item_id);

-- ============================================================
-- 태그 중복 방지
-- ============================================================
ALTER TABLE content_tags
    ADD CONSTRAINT uk_content_tag
        UNIQUE (content_id, tag);