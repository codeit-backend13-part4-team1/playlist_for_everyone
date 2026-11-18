-- ============================================================
-- 부하테스트용 더미 데이터 (플레이리스트 계열) - 중 규모
--   users 10,000 / playlists 200,000 / subscriptions 약 100만
--
-- 사용법 (프로젝트 루트에서):
--   docker compose --env-file .env.dev exec -T postgres \
--     psql -U everyone -d loadtest -v ON_ERROR_STOP=1 < loadtest/sql/seed_playlist.sql
--
-- 테스트 계정: loadtest1@example.com ~ loadtest10000@example.com
--             비밀번호(공통): Loadtest1234!   (부하테스트 전용 값)
-- 다시 실행해도 안전합니다(이전 loadtest 계정 데이터를 지우고 새로 만듭니다).
-- ============================================================

-- 안전장치: loadtest DB가 아니면 즉시 중단 (개발 DB 보호)
DO $$
BEGIN
  IF current_database() <> 'loadtest' THEN
    RAISE EXCEPTION 'loadtest DB에서만 실행할 수 있습니다. 현재 DB: %', current_database();
END IF;
END $$;

\timing on

-- 1) 이전 시드 데이터 제거 (이메일이 loadtest로 시작하는 계정 기준)
DELETE FROM subscriptions
WHERE subscriber_id IN (SELECT id FROM users WHERE email LIKE 'loadtest%@example.com')
   OR playlist_id   IN (SELECT id FROM playlists
                        WHERE owner_id IN (SELECT id FROM users WHERE email LIKE 'loadtest%@example.com'));
DELETE FROM playlists
WHERE owner_id IN (SELECT id FROM users WHERE email LIKE 'loadtest%@example.com');
DELETE FROM users WHERE email LIKE 'loadtest%@example.com';

-- 2) 사용자 10,000명 (비밀번호 해시는 모두 동일: BCrypt)
CREATE TEMP TABLE seed_users AS
SELECT g AS n, gen_random_uuid() AS id
FROM generate_series(1, 10000) g;

INSERT INTO users (id, email, nickname, password, role, enabled, created_at, updated_at)
SELECT id,
       'loadtest' || n || '@example.com',
       'user' || n,
       '$2b$10$gODKrWEeiWhG.B0Wmo/zKuBE81n/mKP0rfsnWzbBZ0Aw9tnIQD1Uu',
       'USER',
       true,
       now() - random() * interval '365 days',
    NULL
FROM seed_users;

-- 3) 플레이리스트 200,000개
--    소유자는 사용자에게 고르게 분배(사용자당 20개), 제목·설명에는 검색용 키워드를 섞는다.
CREATE TEMP TABLE seed_playlists AS
SELECT g AS n,
       gen_random_uuid() AS id,
       (g % 10000) + 1 AS owner_n
FROM generate_series(1, 200000) g;

INSERT INTO playlists (id, owner_id, title, description, created_at, updated_at, subscriber_count)
SELECT p.id,
       u.id,
       (ARRAY['출근길','퇴근길','드라이브','운동','집중','공부','새벽감성','비오는날','카페','여행',
        '영화음악','발라드','힙합','재즈','인디','클래식','게임','휴식','주말','파티'])[1 + (p.n % 20)]
         || ' 플레이리스트 ' || p.n,
       (ARRAY['잔잔한','신나는','감성적인','경쾌한','차분한','몽환적인','묵직한','따뜻한','시원한','깊은'])[1 + (p.n % 10)]
         || ' 분위기로 모은 추천 목록 ' || p.n,
       t.upd - random() * interval '30 days',
       t.upd,
       0
FROM seed_playlists p
    JOIN seed_users u ON u.n = p.owner_n
    CROSS JOIN LATERAL (SELECT now() - random() * interval '180 days' AS upd) t;

-- 4) 구독 약 100만 건(중복 쌍 제거 후): 일부 플레이리스트에 구독이 몰리도록 편향시킨다.
--    (중복 쌍과 본인 플레이리스트 구독은 제외)
CREATE TEMP TABLE seed_sub_candidates AS
SELECT 1 + floor(200000 * power(random(), 2.5))::int AS pn,
    1 + floor(10000 * random())::int              AS un
FROM generate_series(1, 1000000);

INSERT INTO subscriptions (id, playlist_id, subscriber_id, created_at)
SELECT gen_random_uuid(), p.id, u.id, now() - random() * interval '120 days'
FROM (SELECT DISTINCT pn, un FROM seed_sub_candidates) c
    JOIN seed_playlists p ON p.n = c.pn
    JOIN seed_users u     ON u.n = c.un
WHERE p.owner_n <> c.un;

-- 삽입 직후 통계를 갱신한다. 재실행 시 낡은 통계로 아래 UPDATE가 느려지는 것을 막는다.
ANALYZE subscriptions;
ANALYZE playlists;

-- 5) 플레이리스트의 구독자 수를 실제 구독 행 수와 맞춘다.
UPDATE playlists p
SET subscriber_count = s.cnt
    FROM (SELECT playlist_id, count(*) AS cnt FROM subscriptions GROUP BY playlist_id) s
WHERE p.id = s.playlist_id;

-- 6) 통계 갱신 (실행 계획이 실제 데이터 크기를 보고 세워지도록)
ANALYZE users;
ANALYZE playlists;
ANALYZE subscriptions;

-- 7) 결과 요약
SELECT (SELECT count(*) FROM users WHERE email LIKE 'loadtest%@example.com') AS users,
       (SELECT count(*) FROM playlists)    AS playlists,
       (SELECT count(*) FROM subscriptions) AS subscriptions,
       (SELECT max(subscriber_count) FROM playlists) AS max_subscribers,
       (SELECT count(*) FROM playlists WHERE subscriber_count = 0) AS playlists_without_subscribers;