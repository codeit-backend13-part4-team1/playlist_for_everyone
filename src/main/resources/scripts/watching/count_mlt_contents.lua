-- 불 특정 갯수의 contentId 의 리스트를 가지고, contentId 별 시청자 수를 반환하는 스크립트

-- KEYS : contentId 를 담고있는 리스트
-- ARGV[1] : contentPrefix

local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2]/1000)
local result = {}

for i, contentId in ipairs(KEYS) do
    local key = ARGV[1] .. contentId
    local count = redis.call('ZCOUNT', key, '(' .. tostring(now), '+inf')
    redis.log(redis.LOG_NOTICE, contentId, key, count)
    result[i] = {contentId, count}
end

return result
