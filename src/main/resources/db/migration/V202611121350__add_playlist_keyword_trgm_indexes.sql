-- 플레이리스트 키워드 검색 인덱스 (제목·설명 부분 일치 검색용)
-- 목록 조회의 키워드 조건이 lower(title)/lower(description) LIKE '%키워드%' 형태라
-- 일반 B-tree 인덱스를 쓸 수 없어 전체 스캔이 발생한다. trigram GIN 인덱스로 대체한다.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX "IDX_PLAYLISTS_TITLE_TRGM"
    ON "playlists" USING gin (lower("title") gin_trgm_ops);

CREATE INDEX "IDX_PLAYLISTS_DESCRIPTION_TRGM"
    ON "playlists" USING gin (lower("description") gin_trgm_ops);