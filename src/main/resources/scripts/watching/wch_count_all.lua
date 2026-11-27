-- 불 특정 갯수의 contentId 의 리스트를 가지고, contentId 별 시청자 수를 반환하는 스크립트
-- ARGV
local content_key_prefix = ARGV[1]
local content_counter_key = content_key_prefix .. "counter"

local CURSOR = 2

local result = {}

if #ARGV < CURSOR then
    return result
end

for i = CURSOR, #ARGV do
    local content_id = ARGV[i]
    local r, count = pcall(redis.call, "HGET", content_counter_key, content_id)

    if not r or not count then
        count = 0
    end

    result[#result + 1] = {content_id, tonumber(count)}
end


return result

