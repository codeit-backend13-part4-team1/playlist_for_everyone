\pset pager off
\echo ===== 인덱스 현황 =====
select tablename, indexname, indexdef from pg_indexes where tablename in ('playlists','subscriptions','users','file_infos') order by tablename, indexname;
\echo ===== 테이블 크기(대략) =====
select relname, reltuples::bigint as approx_rows from pg_class where relname in ('playlists','subscriptions','users','file_infos');
\echo
\echo ===== 공통) 전체 건수 count (모든 목록 요청이 실행) =====
\o /dev/null
explain (analyze, buffers) select count(p1_0.id) from playlists p1_0;
\o
explain (analyze, buffers) select count(p1_0.id) from playlists p1_0;
\echo
\echo ===== L2 subscribeCount 정렬 - 본 쿼리 =====
\o /dev/null
explain (analyze, buffers) select p1_0.id,p1_0.created_at,p1_0.description,p1_0.owner_id,o1_0.id,o1_0.created_at,o1_0.email,o1_0.enabled,o1_0.nickname,o1_0.password,pi1_0.id,pi1_0.content_type,pi1_0.created_at,pi1_0.file_key,pi1_0.original_filename,pi1_0.size,o1_0.role,o1_0.updated_at,p1_0.subscriber_count,p1_0.title,p1_0.updated_at from playlists p1_0 join users o1_0 on o1_0.id=p1_0.owner_id left join file_infos pi1_0 on pi1_0.id=o1_0.profile_image_id order by p1_0.subscriber_count desc,p1_0.id desc fetch first 21 rows only;
\o
explain (analyze, buffers) select p1_0.id,p1_0.created_at,p1_0.description,p1_0.owner_id,o1_0.id,o1_0.created_at,o1_0.email,o1_0.enabled,o1_0.nickname,o1_0.password,pi1_0.id,pi1_0.content_type,pi1_0.created_at,pi1_0.file_key,pi1_0.original_filename,pi1_0.size,o1_0.role,o1_0.updated_at,p1_0.subscriber_count,p1_0.title,p1_0.updated_at from playlists p1_0 join users o1_0 on o1_0.id=p1_0.owner_id left join file_infos pi1_0 on pi1_0.id=o1_0.profile_image_id order by p1_0.subscriber_count desc,p1_0.id desc fetch first 21 rows only;
\echo
\echo ===== L3 키워드(흔한 단어) - count =====
\o /dev/null
explain (analyze, buffers) select count(p1_0.id) from playlists p1_0 where lower(p1_0.title) like '%운동%' escape '!' or lower(p1_0.description) like '%운동%' escape '!';
\o
explain (analyze, buffers) select count(p1_0.id) from playlists p1_0 where lower(p1_0.title) like '%운동%' escape '!' or lower(p1_0.description) like '%운동%' escape '!';
\echo
\echo ===== L4 키워드(희귀) - 본 쿼리 =====
\o /dev/null
explain (analyze, buffers) select p1_0.id,p1_0.created_at,p1_0.description,p1_0.owner_id,o1_0.id,o1_0.created_at,o1_0.email,o1_0.enabled,o1_0.nickname,o1_0.password,pi1_0.id,pi1_0.content_type,pi1_0.created_at,pi1_0.file_key,pi1_0.original_filename,pi1_0.size,o1_0.role,o1_0.updated_at,p1_0.subscriber_count,p1_0.title,p1_0.updated_at from playlists p1_0 join users o1_0 on o1_0.id=p1_0.owner_id left join file_infos pi1_0 on pi1_0.id=o1_0.profile_image_id where lower(p1_0.title) like '%게임 플레이리스트 151716%' escape '!' or lower(p1_0.description) like '%게임 플레이리스트 151716%' escape '!' order by p1_0.updated_at desc,p1_0.id desc fetch first 21 rows only;
\o
explain (analyze, buffers) select p1_0.id,p1_0.created_at,p1_0.description,p1_0.owner_id,o1_0.id,o1_0.created_at,o1_0.email,o1_0.enabled,o1_0.nickname,o1_0.password,pi1_0.id,pi1_0.content_type,pi1_0.created_at,pi1_0.file_key,pi1_0.original_filename,pi1_0.size,o1_0.role,o1_0.updated_at,p1_0.subscriber_count,p1_0.title,p1_0.updated_at from playlists p1_0 join users o1_0 on o1_0.id=p1_0.owner_id left join file_infos pi1_0 on pi1_0.id=o1_0.profile_image_id where lower(p1_0.title) like '%게임 플레이리스트 151716%' escape '!' or lower(p1_0.description) like '%게임 플레이리스트 151716%' escape '!' order by p1_0.updated_at desc,p1_0.id desc fetch first 21 rows only;
\echo
\echo ===== L4 키워드(희귀) - count =====
\o /dev/null
explain (analyze, buffers) select count(p1_0.id) from playlists p1_0 where lower(p1_0.title) like '%게임 플레이리스트 151716%' escape '!' or lower(p1_0.description) like '%게임 플레이리스트 151716%' escape '!';
\o
explain (analyze, buffers) select count(p1_0.id) from playlists p1_0 where lower(p1_0.title) like '%게임 플레이리스트 151716%' escape '!' or lower(p1_0.description) like '%게임 플레이리스트 151716%' escape '!';
\echo
\echo ===== L5 소유자 필터 - 본 쿼리 =====
\o /dev/null
explain (analyze, buffers) select p1_0.id,p1_0.created_at,p1_0.description,p1_0.owner_id,o1_0.id,o1_0.created_at,o1_0.email,o1_0.enabled,o1_0.nickname,o1_0.password,pi1_0.id,pi1_0.content_type,pi1_0.created_at,pi1_0.file_key,pi1_0.original_filename,pi1_0.size,o1_0.role,o1_0.updated_at,p1_0.subscriber_count,p1_0.title,p1_0.updated_at from playlists p1_0 join users o1_0 on o1_0.id=p1_0.owner_id left join file_infos pi1_0 on pi1_0.id=o1_0.profile_image_id where o1_0.id='e2d5c034-857f-43e4-bf1f-3f0582e873bb' order by p1_0.updated_at desc,p1_0.id desc fetch first 21 rows only;
\o
explain (analyze, buffers) select p1_0.id,p1_0.created_at,p1_0.description,p1_0.owner_id,o1_0.id,o1_0.created_at,o1_0.email,o1_0.enabled,o1_0.nickname,o1_0.password,pi1_0.id,pi1_0.content_type,pi1_0.created_at,pi1_0.file_key,pi1_0.original_filename,pi1_0.size,o1_0.role,o1_0.updated_at,p1_0.subscriber_count,p1_0.title,p1_0.updated_at from playlists p1_0 join users o1_0 on o1_0.id=p1_0.owner_id left join file_infos pi1_0 on pi1_0.id=o1_0.profile_image_id where o1_0.id='e2d5c034-857f-43e4-bf1f-3f0582e873bb' order by p1_0.updated_at desc,p1_0.id desc fetch first 21 rows only;
\echo
\echo ===== L5 소유자 필터 - count =====
\o /dev/null
explain (analyze, buffers) select count(p1_0.id) from playlists p1_0 where p1_0.owner_id='e2d5c034-857f-43e4-bf1f-3f0582e873bb';
\o
explain (analyze, buffers) select count(p1_0.id) from playlists p1_0 where p1_0.owner_id='e2d5c034-857f-43e4-bf1f-3f0582e873bb';
\echo
\echo ===== L6 구독 필터 - 본 쿼리 =====
\o /dev/null
explain (analyze, buffers) select p1_0.id,p1_0.created_at,p1_0.description,p1_0.owner_id,o1_0.id,o1_0.created_at,o1_0.email,o1_0.enabled,o1_0.nickname,o1_0.password,pi1_0.id,pi1_0.content_type,pi1_0.created_at,pi1_0.file_key,pi1_0.original_filename,pi1_0.size,o1_0.role,o1_0.updated_at,p1_0.subscriber_count,p1_0.title,p1_0.updated_at from playlists p1_0 join users o1_0 on o1_0.id=p1_0.owner_id left join file_infos pi1_0 on pi1_0.id=o1_0.profile_image_id where exists(select 1 from subscriptions s1_0 where s1_0.playlist_id=p1_0.id and s1_0.subscriber_id='e2d5c034-857f-43e4-bf1f-3f0582e873bb') order by p1_0.updated_at desc,p1_0.id desc fetch first 21 rows only;
\o
explain (analyze, buffers) select p1_0.id,p1_0.created_at,p1_0.description,p1_0.owner_id,o1_0.id,o1_0.created_at,o1_0.email,o1_0.enabled,o1_0.nickname,o1_0.password,pi1_0.id,pi1_0.content_type,pi1_0.created_at,pi1_0.file_key,pi1_0.original_filename,pi1_0.size,o1_0.role,o1_0.updated_at,p1_0.subscriber_count,p1_0.title,p1_0.updated_at from playlists p1_0 join users o1_0 on o1_0.id=p1_0.owner_id left join file_infos pi1_0 on pi1_0.id=o1_0.profile_image_id where exists(select 1 from subscriptions s1_0 where s1_0.playlist_id=p1_0.id and s1_0.subscriber_id='e2d5c034-857f-43e4-bf1f-3f0582e873bb') order by p1_0.updated_at desc,p1_0.id desc fetch first 21 rows only;
\echo
\echo ===== L6 구독 필터 - count =====
\o /dev/null
explain (analyze, buffers) select count(p1_0.id) from playlists p1_0 where exists(select 1 from subscriptions s1_0 where s1_0.playlist_id=p1_0.id and s1_0.subscriber_id='e2d5c034-857f-43e4-bf1f-3f0582e873bb');
\o
explain (analyze, buffers) select count(p1_0.id) from playlists p1_0 where exists(select 1 from subscriptions s1_0 where s1_0.playlist_id=p1_0.id and s1_0.subscriber_id='e2d5c034-857f-43e4-bf1f-3f0582e873bb');
