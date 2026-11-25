-- refresh.lua
--
-- KEYS[1]: sessionId
--
-- ARGV[1]: 콘텐츠 ZSet 키 접두사
-- ARGV[2]: 사용자 Set 키 접두사
-- ARGV[3]: 세션 Hash 키 접두사
-- ARGV[4]: 콘텐츠별 시청자 수 키 접두사
-- ARGV[5]: delimiter
-- ARGV[6]: TTL (밀리초 단위 양의 정수)
--
-- 세션 Hash:   subscriptionId -> contentId .. delimiter .. userId
-- 사용자 Set:  sessionId .. delimiter .. subscriptionId
-- 콘텐츠 ZSet: sessionId .. delimiter .. subscriptionId -> 만료 시각(ms)
--
-- 반환값: 세션이 없으면 0, 갱신하면 1

-- keys
local session_id = KEYS[1]

-- args
local content_key_prefix = ARGV[1]
local user_key_prefix = ARGV[2]
local session_key_prefix = ARGV[3]
local content_counter_key_prefix = ARGV[4]
local delimiter = ARGV[5]
local ttl = tonumber(ARGV[6])

local session_key = session_key_prefix .. session_id

local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)
local expires_at = now + ttl

local function parse_pair(value)
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

-- 여러 세션이 공유하는 키의 기존 TTL은 필요한 경우에만 연장한다.
-- PTTL이 -1인 키는 영구 키이므로 그대로 둔다.
local function extend_shared_key_ttl(key)
    local remaining = redis.call('PTTL', key)
    if remaining >= 0 and remaining < ttl then
        redis.call('PEXPIRE', key, ttl)
    end
end

-- 현재 시청자 수를 갱신하는 함수
local function count_current_viewing_user_update(_content_id, now)
    local content_key = content_key_prefix .. _content_id
    local active_session_sub_pair = redis.call('ZRANGEBYSCORE', content_key, '(' .. now, '+inf')

    local existed_user = {}
    local count = 0

    for _, session_sub_pair in ipairs(active_session_sub_pair) do
        local session_id, subscription_id = parse_pair(session_sub_pair)
        local content_user_pair = redis.call('HGET', session_key_prefix .. session_id, subscription_id)

        if content_user_pair then
            local content_id, user_id = parse_pair(content_user_pair)
            if content_id == _content_id and not existed_user[user_id] then
                existed_user[user_id] = true
                count = count + 1
            end
        end
    end

    local counter_key = content_counter_key_prefix .. _content_id
    redis.call('SET', counter_key, count, 'PX', ttl)
end


-- logic

local subscriptions = redis.call('HGETALL', session_key)

if #subscriptions == 0 then
    return 0
end

-- HGETALL 결과는 [subscriptionId, contentId .. delimiter .. userId, ...].
-- 쓰기를 시작하기 전에 세션 레코드를 파싱하고 콘텐츠별로 묶는다.
local members_by_content = {}
local user_keys = {}


-- Hash 이므로 키, 값, 키2, 값2, ...
for i = 1, #subscriptions, 2 do
    local subscription_id = subscriptions[i]
    local content_id, user_id = parse_pair(subscriptions[i + 1])

    local member = session_id .. delimiter .. subscription_id
    local user_key = user_key_prefix .. user_id

    -- content 안에 { 세션 - 구독 id 페어, 유저 키 }
    if not members_by_content[content_id] then
        members_by_content[content_id] = {}
    end

    table.insert(members_by_content[content_id], {
        member = member,
        user_key = user_key
    })

    user_keys[user_key] = true
end



redis.call('PEXPIRE', session_key, ttl)

for content_id, records in pairs(members_by_content) do
    local content_key = content_key_prefix .. content_id

    -- score <= now 인 구독을 이 콘텐츠에서 한 번만 정리한다.
    local expired_count = redis.call('ZREMRANGEBYSCORE', content_key, '-inf', now)
    local needs_recount = expired_count > 0

    for _, record in ipairs(records) do

        -- 세션 Hash에 남아 있는 구독의 사용자 인덱스도 복구한다.
        redis.call('SADD', record.user_key, record.member)

        -- 기존 멤버는 score만 갱신하며 0을 반환한다.
        -- 멤버가 없었거나 위에서 만료로 정리됐다면 1을 반환한다.
        local added = redis.call('ZADD', content_key, expires_at, record.member)

        if added > 0 then
            needs_recount = true
        end
    end

    extend_shared_key_ttl(content_key)

    local counter_key = content_counter_key_prefix .. content_id

    if needs_recount then
        -- 활성 구독 구성이 바뀌었으므로 값을 다시 계산하고 TTL도 설정한다.
        count_current_viewing_user_update(content_id, now)
    elseif redis.call('PEXPIRE', counter_key, ttl) == 0 then
        -- 카운터가 이미 만료됐다면 활성 구독을 기준으로 다시 생성한다.
        count_current_viewing_user_update(content_id, now)
    end
end

for user_key in pairs(user_keys) do
    extend_shared_key_ttl(user_key)
end

return 1