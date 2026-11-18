
-- register.lua

-- KEYS[1]: session key
-- KEYS[2]: content key
-- ARGV[1]: subscriptionId
-- ARGV[2]: contentId
-- ARGV[3]: session - content key (sessionId:subscriptionId)
-- ARGV[4]: TTL milliseconds


local oldContent = redis.call('HGET', KEYS[1], ARGV[1])

-- 동일 구독 ID로 다른 콘텐츠 등록 방지
if oldContent and oldContent ~= ARGV[2] then
    return -1
end

local ttl = tonumber(ARGV[4])
local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)
local expiresAt = now + ttl

redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
redis.call('PEXPIRE', KEYS[1], ttl)

redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
redis.call('ZADD', KEYS[2], expiresAt, ARGV[3])
redis.call('PEXPIRE', KEYS[2], ttl)

return 1
