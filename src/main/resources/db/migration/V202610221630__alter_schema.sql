-- ============================================================
-- contents 테이블 컬럼 제약조건 변경
-- ============================================================
ALTER TABLE contents
ALTER COLUMN description TYPE TEXT,
    ALTER COLUMN description SET NOT NULL,
    ALTER COLUMN thumbnail_url TYPE VARCHAR(2048),
    ALTER COLUMN thumbnail_url SET NOT NULL,
    ALTER COLUMN provider_item_id DROP NOT NULL;

-- ============================================================
-- 외부 API 콘텐츠 ID 필수
-- ============================================================
ALTER TABLE contents
    ADD CONSTRAINT chk_content_provider_item_id
        CHECK (
            provider = 'ADMIN'
                OR provider_item_id IS NOT NULL
            );

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