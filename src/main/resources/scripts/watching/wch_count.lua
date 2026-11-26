-- 자료 구조는 다음과 같다.
-- 세션 --
-- 시청 세션(hash session_storage_key(static) session_id - info(session_id:ctime:content_id:user_id))
-- 세션 수명 저장소 (zset session_life_key(static) - session_id : expire - session_id)
-- 유저 --
-- 유저 저장소 (zset user_key - expire : session_id)
-- 활성 유저 정보(hash user_active_key(static) - user_id - session_id)
-- 컨텐츠 --
-- 컨텐츠 저장소 (zset content_key - expire : session_id)
-- 컨텐츠 시청자 카운터 (hash counter_key(static) - content_id : int)

-- keys
local content_id = KEYS[1]

-- argv
local content_key_prefix = ARGV[1]
local content_counter_key = content_key_prefix .. "counter"

local count = redis.call('HGET', content_count_key, content_id)

if not count then
    return 0
end
return tonumber(count)