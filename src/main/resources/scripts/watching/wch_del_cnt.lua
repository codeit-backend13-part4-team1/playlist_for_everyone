
-- 컨텐츠 키를 기반으로 연관된 세션 기록을 검색해서, 세션을 전부 삭제하는 스크립트

-- KEYS[1]: contentId
-- ARGV[1]: content prefix
-- ARGV[2]: session prefix
-- ARGV[3]: delimiter



-- 컨텐츠에 연결된 모든 세션-구독 쌍(member)
local contentKey = ARGV[1] .. KEYS[1]
local members = redis.call('ZRANGE', contentKey, 0, -1)

-- lua 배열은 1 부터
for i = 1, #members do
    local member = members[i]
    local regex = "^([^" .. ARGV[3] .. "]+)" .. ARGV[3] .. "(.+)"
    local sessionId, subscriptionId = string.match(member,regex)

    if sessionId then
        local key = ARGV[2] .. sessionId
        -- hash 키 삭제
        redis.call('HDEL',key,subscriptionId)
    end
end

-- content 단위라 Zset 자체 삭제
redis.call('DEL', contentKey)

return 1

