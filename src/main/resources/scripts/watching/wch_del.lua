
-- delete-all.lua

-- KEYS[1]: watcher:session:{sessionId}
-- ARGV[1]: sessionId
-- ARGV[2]: 콘텐츠 키 prefix

local subscriptions = redis.call('HGETALL', KEYS[1])

for i = 1, #subscriptions, 2 do
    local subscriptionId = subscriptions[i]
    local contentId = subscriptions[i + 1]

    local contentKey = ARGV[2] .. contentId
    local member = ARGV[1] .. ARGV[3] .. subscriptionId

    redis.call('ZREM', contentKey, member)
end

redis.call('DEL', KEYS[1])

return 1
