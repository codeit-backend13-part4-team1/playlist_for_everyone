-- 반환 형식: {{session_id, ctime, content_id, user_id}, ...}

-- KEYS
local content_id = KEYS[1]

-- ARGV
local session_key_prefix = ARGV[1]
local content_key_prefix = ARGV[2]
local user_key_prefix = ARGV[3]
local delimiter = ARGV[4]

if not delimiter or delimiter == "" then
    error("세션 정보 구분자가 비어 있습니다.")
end

local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)

local content_key = content_key_prefix .. content_id
local session_storage_key = session_key_prefix .. "store"
local session_life_key = session_key_prefix .. "life"
local user_active_key = user_key_prefix .. "active"

local function parse(_combined)
    local result = {}
    local cursor = 1

    while true do
        local s, e = string.find(_combined, delimiter, cursor, true)

        if not s then
            local last = false
            if cursor <= #_combined then
                last = string.sub(_combined, cursor)
            end
            result[#result + 1] = last
            break
        end

        if s == cursor then
            result[#result + 1] = false
        else
            result[#result + 1] = string.sub(_combined, cursor, s - 1)
        end

        cursor = e + 1
    end

    return unpack(result)
end

local function get_session_info(_session_id)
    local info = redis.call('HGET', session_storage_key, _session_id)
    if not info then
        return
    end

    local stored_session_id, ctime, stored_content_id, user_id = parse(info)
    if stored_session_id ~= _session_id
        or stored_content_id ~= content_id
        or not ctime
        or not user_id
    then
        return
    end

    -- 콘텐츠 인덱스에 오래된 항목이 남아 있어도 중앙 만료시간을 확인한다.
    local expire = tonumber(redis.call('ZSCORE', session_life_key, _session_id))
    if not expire or expire <= now then
        return
    end

    return {stored_session_id, ctime, stored_content_id, user_id}
end

local function update_active_user(_user_id, _session_id)
    redis.call('HSET', user_active_key, _user_id, _session_id)
end

local function get_active_user(_user_id, candidate, sessions_by_id)
    local active_session_id = redis.call('HGET', user_active_key, _user_id)

    if active_session_id and active_session_id ~= "" then
        local active_info = sessions_by_id[active_session_id]

        if active_info and active_info[4] == _user_id then
            return active_info
        end
    end

    update_active_user(_user_id, candidate[1])
    return candidate
end

local session_ids = redis.call(
    'ZREVRANGEBYSCORE', content_key, '+inf', '(' .. now
)

local sessions_by_id = {}
local candidate_by_user = {}
local user_ids = {}

for _, session_id in ipairs(session_ids) do
    local info = get_session_info(session_id)

    if info then
        local user_id = info[4]
        sessions_by_id[session_id] = info

        -- 콘텐츠 인덱스에서 만료 점수가 가장 높은 유효 세션을 후보로 삼는다.
        if not candidate_by_user[user_id] then
            candidate_by_user[user_id] = info
            user_ids[#user_ids + 1] = user_id
        end
    end
end

local result = {}

for _, user_id in ipairs(user_ids) do
    result[#result + 1] = get_active_user(
        user_id, candidate_by_user[user_id], sessions_by_id
    )
end

return result