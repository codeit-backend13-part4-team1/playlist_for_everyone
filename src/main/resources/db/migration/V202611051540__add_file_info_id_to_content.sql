-- ============================================================
-- 콘텐츠 썸네일 파일 정보 연관관계 추가
-- ============================================================
ALTER TABLE contents
    ADD COLUMN file_info_id UUID;

ALTER TABLE contents
    ADD CONSTRAINT fk_contents_file_info
        FOREIGN KEY (file_info_id)
            REFERENCES file_infos(id);

ALTER TABLE contents
    ADD CONSTRAINT uk_contents_file_info
        UNIQUE (file_info_id);