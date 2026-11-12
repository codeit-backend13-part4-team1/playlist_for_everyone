-- 콘텐츠 도메인 부하테스트 시드 (loadtest DB 전용)
-- 전제: seed_playlist.sql 로 loadtest1~10000 사용자와 플레이리스트가 이미 있어야 한다.
-- 실행: docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -v ON_ERROR_STOP=1 < loadtest/sql/seed_content.sql
-- 다시 실행하면 이 스크립트가 만든 콘텐츠(lt-c-*, LT-DEL-*)만 지우고 다시 만든다. 다른 데이터는 건드리지 않는다.
-- (삭제 테스트는 대상 콘텐츠를 지우므로, 삭제 테스트 실행 전마다 이 스크립트를 다시 실행한다.)
--
-- 만들어지는 데이터
--   - 조회용 콘텐츠 10,000건 (lt-c-1 ~ lt-c-10000), 태그 3개씩
--       lt-c-1        : 리뷰 5,000건 (리뷰가 많은 콘텐츠)
--       lt-c-2~2001   : 리뷰 20건씩 (일반 콘텐츠)
--       lt-c-2~2001   : 플레이리스트 3개에 담김
--   - 삭제용 콘텐츠 1,000건 (LT-DEL-1 ~ LT-DEL-1000): 리뷰 5건, 플레이리스트 3개, 태그 3개씩
--   - loadtest1~200 사용자를 ADMIN 으로 올린다. (콘텐츠 삭제는 ADMIN 권한 필요)
--   - 썸네일은 file_info 없이 thumbnail_url 문자열만 사용한다. (S3 를 쓰지 않음)

\set ON_ERROR_STOP on

DO $$
BEGIN
  IF current_database() <> 'loadtest' THEN
    RAISE EXCEPTION '이 스크립트는 loadtest DB 에서만 실행해야 해요. 현재 DB: %', current_database();
  END IF;
  IF (SELECT count(*) FROM users WHERE email ~ '^loadtest[0-9]+@example\.com$') < 10000 THEN
    RAISE EXCEPTION 'loadtest 사용자가 10000명 미만이에요. 먼저 seed_playlist.sql 을 실행하세요.';
  END IF;
  IF (SELECT count(*) FROM playlists) < 3000 THEN
    RAISE EXCEPTION '플레이리스트가 3000개 미만이에요. 먼저 seed_playlist.sql 을 실행하세요.';
  END IF;
END $$;

-- 1) 이전에 만든 시드 삭제
CREATE TEMP TABLE old_c AS
SELECT id FROM contents WHERE provider_item_id LIKE 'lt-c-%' OR title LIKE 'LT-DEL-%';
DELETE FROM reviews WHERE content_id IN (SELECT id FROM old_c);
DELETE FROM playlist_contents WHERE content_id IN (SELECT id FROM old_c);
DELETE FROM content_tags WHERE content_id IN (SELECT id FROM old_c);
DELETE FROM contents WHERE id IN (SELECT id FROM old_c);

-- 2) 테스트 사용자 loadtest1~200 을 ADMIN 으로
UPDATE users SET role = 'ADMIN' WHERE email ~ '^loadtest([1-9]|[1-9][0-9]|1[0-9][0-9]|200)@example\.com$';

CREATE TEMP TABLE lt_users AS
SELECT id, substring(email from '^loadtest([0-9]+)@')::int AS n
FROM users WHERE email ~ '^loadtest[0-9]+@example\.com$';

CREATE TEMP TABLE lt_pl AS
SELECT id, row_number() OVER (ORDER BY id) AS rn FROM (SELECT id FROM playlists ORDER BY id LIMIT 3000) p;

-- 3) 조회용 콘텐츠 10,000건
INSERT INTO contents (id, type, title, description, thumbnail_url, provider, provider_item_id, created_at)
SELECT gen_random_uuid(),
       (ARRAY['movie', 'tvSeries', 'sport'])[1 + (g % 3)],
       '부하테스트 콘텐츠 ' || g,
       repeat('콘텐츠 설명입니다. ', 10) || g,
       'https://example.com/thumbnails/' || g || '.jpg',
       'TMDB', 'lt-c-' || g, now()
FROM generate_series(1, 10000) g;

CREATE TEMP TABLE lt_c AS
SELECT id, substring(provider_item_id from 'lt-c-([0-9]+)')::int AS n FROM contents WHERE provider_item_id LIKE 'lt-c-%';

INSERT INTO content_tags (id, content_id, tag)
SELECT gen_random_uuid(), c.id, 'tag' || t
FROM lt_c c CROSS JOIN generate_series(1, 3) t;

-- lt-c-1: 리뷰 5,000건
INSERT INTO reviews (id, content_id, author_id, text, rating, created_at)
SELECT gen_random_uuid(), c.id, u.id, '부하테스트 리뷰 ' || u.n, 1 + floor(random() * 9) / 2.0, now()
FROM lt_c c JOIN lt_users u ON u.n <= 5000
WHERE c.n = 1;

-- lt-c-2 ~ 2001: 리뷰 20건씩
INSERT INTO reviews (id, content_id, author_id, text, rating, created_at)
SELECT gen_random_uuid(), c.id, u.id, '부하테스트 리뷰 ' || u.n, 1 + floor(random() * 9) / 2.0, now()
FROM lt_c c JOIN lt_users u ON u.n BETWEEN c.n AND c.n + 19
WHERE c.n BETWEEN 2 AND 2001;

-- lt-c-2 ~ 2001: 플레이리스트 3개에 담김
INSERT INTO playlist_contents (id, playlist_id, content_id, created_at)
SELECT gen_random_uuid(), p.id, c.id, now()
FROM lt_c c JOIN lt_pl p ON p.rn IN (c.n, c.n + 1000, c.n + 2000) WHERE c.n BETWEEN 2 AND 1000
UNION ALL
SELECT gen_random_uuid(), p.id, c.id, now()
FROM lt_c c JOIN lt_pl p ON p.rn IN (c.n - 1000, c.n, c.n + 1000) WHERE c.n BETWEEN 1001 AND 2000;

-- 4) 삭제용 콘텐츠 1,000건 (ADMIN 직접 등록 형태)
INSERT INTO contents (id, type, title, description, thumbnail_url, provider, provider_item_id, created_at)
SELECT gen_random_uuid(), 'movie', 'LT-DEL-' || g, repeat('삭제 대상 설명입니다. ', 10) || g,
       'https://example.com/thumbnails/del' || g || '.jpg', 'ADMIN', NULL, now()
FROM generate_series(1, 1000) g;

CREATE TEMP TABLE lt_d AS
SELECT id, substring(title from 'LT-DEL-([0-9]+)')::int AS n FROM contents WHERE title LIKE 'LT-DEL-%';

INSERT INTO content_tags (id, content_id, tag)
SELECT gen_random_uuid(), d.id, 'tag' || t FROM lt_d d CROSS JOIN generate_series(1, 3) t;

INSERT INTO reviews (id, content_id, author_id, text, rating, created_at)
SELECT gen_random_uuid(), d.id, u.id, '삭제 대상 리뷰', 4.0, now()
FROM lt_d d JOIN lt_users u ON u.n BETWEEN d.n AND d.n + 4;

INSERT INTO playlist_contents (id, playlist_id, content_id, created_at)
SELECT gen_random_uuid(), p.id, d.id, now()
FROM lt_d d JOIN lt_pl p ON p.rn IN (d.n + 1000, d.n + 2000, d.n + 1999);

ANALYZE contents; ANALYZE reviews; ANALYZE content_tags; ANALYZE playlist_contents;

SELECT (SELECT count(*) FROM contents) AS contents,
       (SELECT count(*) FROM reviews) AS reviews,
       (SELECT count(*) FROM content_tags) AS tags,
       (SELECT count(*) FROM playlist_contents) AS playlist_contents,
       (SELECT count(*) FROM users WHERE role = 'ADMIN') AS admins;
