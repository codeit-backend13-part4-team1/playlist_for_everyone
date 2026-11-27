

-- KEYS
local user_id = KEYS[1]

-- ARGV
local session_key_prefix = ARGV[1]
local content_key_prefix = ARGV[2]
local user_key_prefix = ARGV[3]
local delimiter = ARGV[4]

local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)

local session_storage_key = session_key_prefix .. "store"
local session_life_key = session_key_prefix .. "life"
local user_active_key = user_key_prefix .. "active"

-- util
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

-- sub
local function get_session_info(_session_id)
    local info = redis.call('HGET', session_storage_key, _session_id)
    if not info then
        return {}
    end

    local stored_session_id, ctime, content_id, s_user_id = parse(info)
    if stored_session_id ~= _session_id
        or not content_id
        or not ctime
        or s_user_id ~= user_id
    then
        return {}
    end

    -- 콘텐츠 인덱스에 오래된 항목이 남아 있어도 중앙 만료시간을 확인한다.
    local expire = tonumber(redis.call('ZSCORE', session_life_key, _session_id))
    if not expire or expire <= now then
        return {}
    end

    return {stored_session_id, ctime, content_id, user_id}
end

-- main logic
local result, session_id = pcall(redis.call, 'HGET', user_active_key, user_id)

if not result or not session_id then
    return {}
end

return get_session_info(session_id)

