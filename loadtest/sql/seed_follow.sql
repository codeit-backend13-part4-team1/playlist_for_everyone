-- 팔로우 도메인 부하테스트 시드 (loadtest DB 전용)
-- 전제: seed_playlist.sql 로 loadtest1~10000 사용자가 이미 있어야 한다.
-- 실행: docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -v ON_ERROR_STOP=1 < loadtest/sql/seed_follow.sql
-- 다시 실행하면 follows 를 비우고 다시 만든다. (follows 외 테이블은 건드리지 않는다)
--
-- 만들어지는 데이터
--   1) 일반 팔로우 약 30만 건: 팔로워는 loadtest201~10000 중 무작위, 팔로우 대상은 앞번호 쪽으로 치우친 분포
--      (loadtest1 이 가장 인기 있는 계정 = 팔로워가 가장 많다)
--   2) 테스터(loadtest1~200) 한 명당 팔로우 1건: loadtest(i) -> loadtest(9800+i)  (followed-by-me / 언팔로우 대상)
--   3) 테스터가 새로 팔로우할 대상은 loadtest(9600+i) 이며, 시드에는 이 관계가 없다.

\set ON_ERROR_STOP on

DO $$
BEGIN
  IF current_database() <> 'loadtest' THEN
    RAISE EXCEPTION '이 스크립트는 loadtest DB 에서만 실행해야 해요. 현재 DB: %', current_database();
  END IF;
  IF (SELECT count(*) FROM users WHERE email ~ '^loadtest[0-9]+@example\.com$') < 10000 THEN
    RAISE EXCEPTION 'loadtest 사용자가 10000명 미만이에요. 먼저 seed_playlist.sql 을 실행하세요.';
  END IF;
END $$;

DELETE FROM follows;

CREATE TEMP TABLE lt_users AS
SELECT id, substring(email from '^loadtest([0-9]+)@')::int AS n
FROM users
WHERE email ~ '^loadtest[0-9]+@example\.com$';
CREATE UNIQUE INDEX ON lt_users (n);

-- 1) 일반 팔로우. 같은 쌍은 중복되므로 ON CONFLICT 로 건너뛴다. 자기 자신 팔로우는 제외한다.
INSERT INTO follows (id, follower_id, followee_id, created_at)
SELECT gen_random_uuid(), f.id, t.id, now() - (random() * interval '180 days')
FROM (
  SELECT 201 + floor(random() * 9800)::int AS fn,
         1 + floor(10000 * power(random(), 3))::int AS tn
  FROM generate_series(1, 300000)
) g
JOIN lt_users f ON f.n = g.fn
JOIN lt_users t ON t.n = g.tn
WHERE g.fn <> g.tn
ON CONFLICT (follower_id, followee_id) DO NOTHING;

-- 2) 테스터 한 명당 기존 팔로우 1건
INSERT INTO follows (id, follower_id, followee_id, created_at)
SELECT gen_random_uuid(), f.id, t.id, now()
FROM lt_users f
JOIN lt_users t ON t.n = f.n + 9800
WHERE f.n BETWEEN 1 AND 200
ON CONFLICT (follower_id, followee_id) DO NOTHING;

ANALYZE follows;

SELECT 'follows' AS table_name, count(*) AS total FROM follows;
SELECT u.email, count(*) AS followers
FROM follows f JOIN users u ON u.id = f.followee_id
GROUP BY u.email ORDER BY followers DESC LIMIT 3;
