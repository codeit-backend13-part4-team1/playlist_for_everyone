-- 썸네일이 없는 콘텐츠도 저장할 수 있도록 변경
ALTER TABLE contents
    ALTER COLUMN thumbnail_url DROP NOT NULL;