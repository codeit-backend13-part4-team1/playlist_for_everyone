-- remove_by_content.lua

-- keys
local content_id = KEYS[1]

-- argv
local content_key_prefix = ARGV[1]
local user_key_prefix = ARGV[2]
local session_key_prefix = ARGV[3]
local content_counter_key_prefix = ARGV[4]
local delimiter = ARGV[5]

local content_key = content_key_prefix .. content_id
local content_counter_key = content_counter_key_prefix .. content_id

local function parse_pair(value)
    local pos = string.find(value, delimiter, 1, true)
    return string.sub(value, 1, pos - 1),
           string.sub(value, pos + #delimiter)
end

-- 만료 시각이 지난 멤버도 포함해 콘텐츠 ZSet에 남아 있는 구독을 조회한다.
local members = redis.call('ZRANGE', content_key, 0, -1)

for _, member in ipairs(members) do
    local session_id, subscription_id = parse_pair(member)
    local session_key = session_key_prefix .. session_id

    -- Hash 필드는 subscriptionId, 값은 contentId .. delimiter .. userId다.
    local value = redis.call('HGET', session_key, subscription_id)

    if value then
        local stored_content_id, user_id = parse_pair(value)

        -- 같은 구독 ID가 다른 콘텐츠에 재사용됐다면 현재 구독을 보존한다.
        if stored_content_id == content_id then
            redis.call('SREM', user_key_prefix .. user_id, member)
            redis.call('HDEL', session_key, subscription_id)
        end
    end
end

redis.call('DEL', content_key, content_counter_key)

return 1