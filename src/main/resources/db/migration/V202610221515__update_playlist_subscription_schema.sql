-- ============================================================
-- 플레이리스트 구독자 수 (playlists)
-- ============================================================
ALTER TABLE "playlists"
    ADD COLUMN "subscriber_count" BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN "playlists"."subscriber_count" IS '구독자 수';


-- ============================================================
-- 같은 플레이리스트에 같은 콘텐츠 중복 추가 방지 (playlist_contents)
-- ============================================================
ALTER TABLE "playlist_contents"
    ADD CONSTRAINT "UQ_PLAYLIST_CONTENTS_PLAYLIST_CONTENT"
        UNIQUE ("playlist_id", "content_id");


-- ============================================================
-- 같은 사용자의 동일 플레이리스트 중복 구독 방지 (subscriptions)
-- ============================================================
ALTER TABLE "subscriptions"
    ADD CONSTRAINT "UQ_SUBSCRIPTIONS_PLAYLIST_SUBSCRIBER"
        UNIQUE ("playlist_id", "subscriber_id");