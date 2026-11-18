-- 단일 세션, 컨텐츠에 대해 복수개의 구독을 삭제하는 스크립트

-- KEYS[1]: session Hash key
-- KEYS[2]: content ZSet key
-- KEYS[3]: delimiter
--
-- ARGV[1]: contentId
-- ARGV[2]: sessionId
-- ARGV[3...]: subscriptionIds

local contentId = ARGV[1]
local sessionId = ARGV[2]

for i = 3, #ARGV do
    local subscriptionId = ARGV[i]

    local oldContent = redis.call(
        'HGET', KEYS[1], subscriptionId
    )

    if not oldContent or oldContent == contentId then
        redis.call(
            'HDEL', KEYS[1], subscriptionId
        )

        redis.call(
            'ZREM',
            KEYS[2],
            sessionId .. KEYS[3] .. subscriptionId
        )
    end
end

return 1