-- 알림 목록 쿼리 실행 계획 확인 (읽기 전용, loadtest DB)
-- 실행: docker compose --env-file .env.dev exec -T postgres psql -U everyone -d loadtest < loadtest/explain_notification.sql > explain_notification_result.txt
-- 사용자: loadtest1 (알림 2,000건). 쿼리는 앱이 보내는 SQL과 같은 모양으로 맞춘 근사값이다.
\pset pager off
\echo ===== 인덱스 현황 =====
select indexname, indexdef from pg_indexes where tablename = 'notifications';
\echo ===== 행 수 =====
select count(*) as total, count(*) filter (where receiver_id = 'e2d5c034-857f-43e4-bf1f-3f0582e873bb') as loadtest1 from notifications;

\echo
\echo ===== N1 목록 첫 페이지(최신순) =====
\o /dev/null
explain (analyze, buffers) select n1_0.id, n1_0.content, n1_0.created_at, n1_0.level, n1_0.receiver_id, n1_0.title from notifications n1_0 where n1_0.receiver_id = 'e2d5c034-857f-43e4-bf1f-3f0582e873bb' order by n1_0.created_at desc, n1_0.id desc fetch first 21 rows only;
\o
explain (analyze, buffers) select n1_0.id, n1_0.content, n1_0.created_at, n1_0.level, n1_0.receiver_id, n1_0.title from notifications n1_0 where n1_0.receiver_id = 'e2d5c034-857f-43e4-bf1f-3f0582e873bb' order by n1_0.created_at desc, n1_0.id desc fetch first 21 rows only;

\echo
\echo ===== N1/N2/N3 공통 건수 count =====
\o /dev/null
explain (analyze, buffers) select count(n1_0.id) from notifications n1_0 where n1_0.receiver_id = 'e2d5c034-857f-43e4-bf1f-3f0582e873bb';
\o
explain (analyze, buffers) select count(n1_0.id) from notifications n1_0 where n1_0.receiver_id = 'e2d5c034-857f-43e4-bf1f-3f0582e873bb';

\echo
\echo ===== N2 목록 2페이지(cursor) =====
\o /dev/null
explain (analyze, buffers) select n1_0.id, n1_0.content, n1_0.created_at, n1_0.level, n1_0.receiver_id, n1_0.title from notifications n1_0 where (n1_0.created_at < now() - interval '45 days' or (n1_0.created_at = now() - interval '45 days' and n1_0.id < 'ffffffff-ffff-ffff-ffff-ffffffffffff')) and n1_0.receiver_id = 'e2d5c034-857f-43e4-bf1f-3f0582e873bb' order by n1_0.created_at desc, n1_0.id desc fetch first 21 rows only;
\o
explain (analyze, buffers) select n1_0.id, n1_0.content, n1_0.created_at, n1_0.level, n1_0.receiver_id, n1_0.title from notifications n1_0 where (n1_0.created_at < now() - interval '45 days' or (n1_0.created_at = now() - interval '45 days' and n1_0.id < 'ffffffff-ffff-ffff-ffff-ffffffffffff')) and n1_0.receiver_id = 'e2d5c034-857f-43e4-bf1f-3f0582e873bb' order by n1_0.created_at desc, n1_0.id desc fetch first 21 rows only;
