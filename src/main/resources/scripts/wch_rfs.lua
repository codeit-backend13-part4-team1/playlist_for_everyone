
-- refresh.lua

-- KEYS[1]: watcher:session:{sessionId}
-- ARGV[1]: sessionId
-- ARGV[2]: 콘텐츠 키 prefix
-- ARGV[3]: TTL milliseconds
-- ARGV[4]: 컨텐츠 - 구독 키 구분자

local subscriptions = redis.call('HGETALL', KEYS[1])

-- 세션 구독 정보가 없으면 아무것도 갱신하지 않음
if #subscriptions == 0 then
    return 0
end

local ttl = tonumber(ARGV[3])
local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)
local expiresAt = now + ttl

redis.call('PEXPIRE', KEYS[1], ttl)


-- HGETALL 은 필드,밸류,필드,밸류...
for i = 1, #subscriptions, 2 do
    local subscriptionId = subscriptions[i]
    local contentId = subscriptions[i + 1]

    local contentKey = ARGV[2] .. contentId
    local member = ARGV[1] .. ARGV[4] .. subscriptionId

    redis.call('ZADD', contentKey, expiresAt, member)
end

return 1
