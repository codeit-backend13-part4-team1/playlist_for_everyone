-- 지정한 세션에서 전달받은 구독과 연관된 리소스를 삭제한다.
--
-- KEYS[1]: sessionId
--
-- ARGV[1]: 콘텐츠 ZSet 키 접두사
-- ARGV[2]: 사용자 Set 키 접두사
-- ARGV[3]: 세션 Hash 키 접두사
-- ARGV[4]: 콘텐츠별 시청자 수 키 접두사
-- ARGV[5]: delimiter
-- ARGV[6...]: 삭제할 subscriptionId 목록
--
-- 세션 Hash:    subscriptionId -> contentId .. delimiter .. userId
-- 사용자 Set:   sessionId .. delimiter .. subscriptionId
-- 콘텐츠 ZSet:  sessionId .. delimiter .. subscriptionId -> 만료 시각

if #ARGV < 6 then
    return 0
end

local session_id = KEYS[1]
local content_key_prefix = ARGV[1]
local user_key_prefix = ARGV[2]
local session_key_prefix = ARGV[3]
local content_counter_key_prefix = ARGV[4]
local delimiter = ARGV[5]
local ttl = tonumber(ARGV[6])

if not session_id or session_id == '' then
    error('sessionId가 비어 있습니다.')
end

if delimiter == '' then
    error('delimiter가 비어 있습니다.')
end

local session_key = session_key_prefix .. session_id

-- 모든 구독의 활성 여부를 같은 시각으로 판단한다.
local redis_time = redis.call('TIME')
local now = tonumber(redis_time[1]) * 1000
    + math.floor(tonumber(redis_time[2]) / 1000)

-- 키는 재집계가 필요한 contentId다.
local update_flags = {}

local function session_subscription_pair(subscription_id)
    return session_id .. delimiter .. subscription_id
end

local function parse_contained_string(value)
    local pos = string.find(value, delimiter, 1, true)
    if not pos then
        error('저장된 값에서 delimiter를 찾을 수 없습니다.')
    end

    local first = string.sub(value, 1, pos - 1)
    local second = string.sub(value, pos + #delimiter)

    if first == '' or second == '' then
        error('delimiter로 분리한 값이 비어 있습니다.')
    end

    return first, second
end

-- 구독이 속한 세션의 Hash에서 콘텐츠와 사용자를 조회한다.
-- 재집계 대상에는 다른 세션의 구독도 포함된다.
local function query_key_pair_to_content(pair)
    local member_session_id, subscription_id = parse_contained_string(pair)
    local value = redis.call(
        'HGET',
        session_key_prefix .. member_session_id,
        subscription_id
    )

    if not value then
        return nil, nil
    end

    return parse_contained_string(value)
end

local function check_subscription_active(content_id, subscription_id)
    local score = redis.call(
        'ZSCORE',
        content_key_prefix .. content_id,
        session_subscription_pair(subscription_id)
    )

    return score and tonumber(score) > now
end

-- 사용자 Set에 남은 구독 중 이 콘텐츠에서 활성인 구독이 있는지 확인한다.
local function check_user_existed_in_content(user_id, content_id)
    local user_key = user_key_prefix .. user_id
    local content_key = content_key_prefix .. content_id

    for _, pair in ipairs(redis.call('SMEMBERS', user_key)) do
        local score = redis.call('ZSCORE', content_key, pair)
        if score and tonumber(score) > now then
            return true
        end
    end

    return false
end

local function remove_subscription_by_user(user_id, subscription_id)
    redis.call(
        'SREM',
        user_key_prefix .. user_id,
        session_subscription_pair(subscription_id)
    )
end

local function remove_subscription_by_content(content_id, subscription_id)
    local content_key = content_key_prefix .. content_id

    local expired_count = redis.call(
        'ZREMRANGEBYSCORE', content_key, '-inf', now
    )

    if expired_count > 0 then
        update_flags[content_id] = true
    end

    redis.call('ZREM', content_key, session_subscription_pair(subscription_id))
end

local function remove_subscription_by_session(subscription_id)
    -- HGET으로 조회할 때와 같은 필드를 삭제한다.
    redis.call('HDEL', session_key, subscription_id)
end

local function count_current_viewing_user_update(content_id)
    local content_key = content_key_prefix .. content_id
    local content_counter_key = content_counter_key_prefix .. content_id
    local unique_users = {}
    local count = 0

    local active_subscriptions = redis.call(
        'ZRANGEBYSCORE', content_key, '(' .. now, '+inf'
    )

    for _, pair in ipairs(active_subscriptions) do
        local found_content_id, user_id = query_key_pair_to_content(pair)

        if found_content_id == content_id and not unique_users[user_id] then
            unique_users[user_id] = true
            count = count + 1
        end
    end
    redis.call('SET', content_counter_key, count, 'PX', ttl)
end

for i = 7, #ARGV do
    local subscription_id = ARGV[i]
    local value = redis.call('HGET', session_key, subscription_id)

    -- 이미 삭제된 구독이나 중복으로 전달된 ID는 건너뛴다.
    if value then
        local content_id, user_id = parse_contained_string(value)
        local was_active = check_subscription_active(content_id, subscription_id)

        remove_subscription_by_user(user_id, subscription_id)
        remove_subscription_by_content(content_id, subscription_id)
        remove_subscription_by_session(subscription_id)

        -- 이미 재집계할 콘텐츠라면 남은 구독을 다시 조회할 필요가 없다.
        if was_active and not update_flags[content_id] then
            local ok, still_active = pcall(
                check_user_existed_in_content, user_id, content_id
            )

            if not ok or not still_active then
                update_flags[content_id] = true
            end
        end
    end
end

for content_id in pairs(update_flags) do
    count_current_viewing_user_update(content_id)
end

return 0