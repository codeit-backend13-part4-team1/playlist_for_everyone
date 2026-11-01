
-- unregister.lua

-- KEYS[1]: watcher:session:{sessionId}
-- ARGV[1]: subscriptionId
-- ARGV[2]: ZSET member
-- ARGV[3]: 콘텐츠 키 prefix

local content = redis.call('HGET', KEYS[1], ARGV[1])

if not content then
    return 0
end

local contentKey = ARGV[3] .. content

-- session 등록 취소
redis.call('HDEL', KEYS[1], ARGV[1])

-- content 별 구독 리스트에서 구독 키 삭제
redis.call('ZREM', contentKey, ARGV[2])

return 1
