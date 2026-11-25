-- 불 특정 갯수의 contentId 의 리스트를 가지고, contentId 별 시청자 수를 반환하는 스크립트

-- KEYS : contentId 를 담고있는 리스트
-- ARGV[1] : contentPrefix

-- ARGV
local content_counter_prefix

local result = {}

for i, content_id in ipairs(KEYS) do
    local content_counter_key = content_counter_prefix .. content_id
    local viewer = redis.call('GET',content_counter_key)
    result[i] = {content_id, viewer}
end

return result

