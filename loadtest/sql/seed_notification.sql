-- 알림 도메인 부하테스트 시드 (loadtest DB 전용)
-- 전제: seed_playlist.sql 로 loadtest1~10000 사용자가 이미 있어야 한다.
-- 실행: docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest -v ON_ERROR_STOP=1 < loadtest/sql/seed_notification.sql
-- 다시 실행하면 notifications 를 비우고 다시 만든다. (notifications 외 테이블은 건드리지 않는다)
--
-- 만들어지는 데이터 (총 약 138만 건)
--   - 테스터(loadtest1~200): 사용자당 2,000건  (알림이 많이 쌓인 사용자)
--   - 나머지(loadtest201~10000): 사용자당 100건
--   - 생성 시각은 최근 90일 안에서 무작위, 레벨은 INFO/WARNING/ERROR 무작위

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

DELETE FROM notifications;

CREATE TEMP TABLE lt_users AS
SELECT id, substring(email from '^loadtest([0-9]+)@')::int AS n
FROM users
WHERE email ~ '^loadtest[0-9]+@example\.com$';

INSERT INTO notifications (id, receiver_id, title, content, level, created_at)
SELECT gen_random_uuid(),
       u.id,
       '부하테스트 알림 ' || g,
       repeat('알림 내용입니다. ', 6) || g,
       (ARRAY['INFO', 'WARNING', 'ERROR'])[1 + floor(random() * 3)::int],
       now() - (random() * interval '90 days')
FROM lt_users u
CROSS JOIN LATERAL generate_series(1, CASE WHEN u.n <= 200 THEN 2000 ELSE 100 END) AS g;

ANALYZE notifications;

SELECT 'notifications' AS table_name, count(*) AS total FROM notifications;
SELECT receiver_id, count(*) AS cnt FROM notifications GROUP BY receiver_id ORDER BY cnt DESC LIMIT 3;
