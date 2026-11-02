-- ============================================================
-- 파일 정보 (file_infos)
-- ============================================================
CREATE TABLE file_infos (
                            id UUID PRIMARY KEY,
                            key VARCHAR(255) NOT NULL,
                            original_filename VARCHAR(255),
                            content_type VARCHAR(255),
                            size BIGINT,
                            created_at TIMESTAMP NOT NULL
);

ALTER TABLE file_infos
    ADD CONSTRAINT uk_file_infos_key UNIQUE (key);
