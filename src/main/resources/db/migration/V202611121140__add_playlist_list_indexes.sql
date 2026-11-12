-- ============================================================
-- 플레이리스트 목록 조회 인덱스 (playlists)
-- ============================================================
-- 기본 정렬(updatedAt)과 커서 조건
CREATE INDEX "IDX_PLAYLISTS_UPDATED_AT_ID"
    ON "playlists" ("updated_at" DESC, "id" DESC);

-- 소유자 필터 + updatedAt 정렬
CREATE INDEX "IDX_PLAYLISTS_OWNER_UPDATED_AT_ID"
    ON "playlists" ("owner_id", "updated_at" DESC, "id" DESC);

-- 구독자 수 정렬(subscribeCount)과 커서 조건
CREATE INDEX "IDX_PLAYLISTS_SUBSCRIBER_COUNT_ID"
    ON "playlists" ("subscriber_count" DESC, "id" DESC);


-- ============================================================
-- 구독자 기준 구독 조회 인덱스 (subscriptions)
-- ============================================================
-- 구독자 필터와 구독 여부 확인. 기존 UNIQUE는 (playlist_id, subscriber_id) 순서라
-- subscriber_id 기준 조회에는 사용할 수 없다.
CREATE INDEX "IDX_SUBSCRIPTIONS_SUBSCRIBER_PLAYLIST"
    ON "subscriptions" ("subscriber_id", "playlist_id");