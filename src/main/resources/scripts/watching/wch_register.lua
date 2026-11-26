-- 시청자 세션 정보를 저장하는 스크립트
-- 인자는 전부 java 단에서 이상적으로 검증하고 들어오는 것으로 가정.
-- 자료 구조는 다음과 같다.
-- 세션 --
-- 시청 세션(hash session_storage_key(static) session_id - info(session_id:ctime:content_id:user_id))
-- 세션 수명 저장소 (zset session_life_key(static) - session_id : expire - session_id)
-- 유저 --
-- 유저 저장소 (zset user_key - expire : session_id)
-- 활성 유저 정보(hash user_active_key(static) - user_id - session_id)
-- 컨텐츠 --
-- 컨텐츠 저장소 (zset content_key - expire : session_id)
-- 컨텐츠 시청자 카운터 (hash counter_key(static) - content_id : int)

local SCRIPT_NAME = "WatchingSessionRegister"

-- KEYS
local session_id = KEYS[1] -- subscription.uuid()
local content_id = KEYS[2]
local user_id = KEYS[3]

-- ARGV
--- key prefix
local session_key_prefix = ARGV[1]
local content_key_prefix = ARGV[2]
local user_key_prefix = ARGV[3]
--- argv option value
local delimiter = ARGV[4]
local ttl = tonumber(ARGV[5])
--- ctime
local session_ctime = ARGV[6]

-- global variable
local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)
local expire = now + ttl
--- redis keys
local session_storage_key = session_key_prefix .. "store" -- tmp
local session_life_key = session_key_prefix .. "life" -- tmp
local user_active_key = user_key_prefix .. "active" -- tmp
local content_counter_key = content_key_prefix .. "counter"

local fresh_user_flag = false

------------------------------------- UTIL -----------------------------------------

-- [UTIL] 구분자로 구분된 문자열을 각각의 인자로 반환하는 함수
local function parse(_combined)
    local result = {}
    local cursor = 1 -- 문자열에서의 현재 출발 지점
    while true do
        local s, e = string.find(_combined, delimiter, cursor, true)

        -- s(start) 가 없다면 => 구분자가 없는 끝 문자.
        if not s then
            local last = false
            if cursor <= #_combined then
                last = string.sub(_combined, cursor)
            end
            table.insert(result, last)
            break
        end
        -- s 가 있다면, -> 구분된 문자
        -- s 검증
        if s == cursor then
            -- 1. 구분자가 1이면, 전방 문자열 없음.
            table.insert(result, false)
        else
            -- 2. 구분자 인덱스가 1보다 크면, 전방 문자열이 인자가 됨.
            table.insert(result,string.sub(_combined, cursor, s - 1))
        end
        -- 현재 커서 구분자 뒤 인덱스로 위치
        cursor = e + 1
    end

    -- 수집한 테이블 각 인자로 분리해서 반환.
    return unpack(result)
end

local function combine(_argv_table)
    local result = ""
    for i, v in ipairs(_argv_table) do
        if i < #_argv_table then
            result = result .. v .. delimiter
        else
            result = result .. v
        end
    end
    return result
end



--------------------------------- session -------------------------------------
-- 세션 관련 매서드. 세션 관련 스토리지 키는 전역 변수로 사용.

-- [recover] 등록된 세션 storage (hash) 에서 세션 정보를 제거하는 함수. 에러시 세션 잔존 경고 로그.
local function remove_session_info(_session_id)
    local result = pcall(redis.call, 'HDEL', session_storage_key, _session_id)
    if not result then
        redis.log(redis.LOG_WARNING, SCRIPT_NAME, "Error: session_info delete error : ", _session_id)
        error(SCRIPT_NAME .. " error : 세션 info 삭제 완료 불가 에러.")
    end
end

-- [check] 세션 충돌을 검사하고, 에러 및 충돌은 즉시 에러 반환.
local function check_session(_session_id, _info)
    local life, old_expire = pcall(redis.call, 'ZSCORE', session_life_key, _session_id)
    if life then
        if old_expire and tonumber(old_expire) > now then
            local info, old = pcall(redis.call, 'HGET', session_storage_key, _session_id)
            -- 구독 조회가 실패하거나, 같은 구독이 아니라면 스크립트 중단.
            if not info or old ~= _info then
                -- 기존 구독 조회가 실패하면 등록 로직 실패
                redis.log(redis.LOG_WARNING, SCRIPT_NAME, "세션 수명 조회 검정 실패. ", _session_id)
                error(SCRIPT_NAME .. " error: 구독 충돌 검증 실패")
            end
        end
    else
        -- 기존 구독 조회가 실패하면 등록 로직 실패
        redis.log(redis.LOG_WARNING, SCRIPT_NAME, "세션 수명 조회 검정 실패. ", _session_id)
        error(SCRIPT_NAME .. " error: 구독 충돌 검증 실패")
    end
end


-- [session] 세션 저장소에 세션 정보를 등록하는 매서드.
-- 실패시, 등록 하려 했던 정보 정리. 후 에러 반환.
local function set_session_info(_session_id, _info)
    -- 과거 레코드 검정. 수명이 다하지 않은 레코드가 있다면, 구독 충돌 검정
    check_session(_session_id, _info)

    local storage = pcall(redis.call, 'HSET', session_storage_key, _session_id, _info)
    local life = pcall(redis.call, 'ZADD', session_life_key, expire, _session_id)
    if not storage or not life then
        if not life then
            remove_session_info(_session_id)
        end
        error(SCRIPT_NAME .. " error: 세션 정보 저장 실패")
    end
end


-- [recover] 등록된 세션 life 정리. 에러시 세션 잔존 경고 로그.
local function remove_session_life(_session_id)
    local result = pcall(redis.call, 'ZREM', session_life_key, _session_id)
    if not result then
        redis.log(redis.LOG_WARNING, SCRIPT_NAME, "Error: session_life delete error : ", _session_id)
        error(SCRIPT_NAME .. " error : 세션 life 삭제 완료 불가 에러.")
    end
end

------------------------------------ USER -------------------------------------------


-- [recover] 등록된 유저 정보 정리. 에러시 로그.
local function remove_user_index(_user_key, _session_id)
   local result = pcall(redis.call, 'ZREM', _user_key, _session_id)
   if not result then
       redis.log(redis.LOG_WARNING, SCRIPT_NAME, "Error: user_index delete error : ", _session_id)
       error(SCRIPT_NAME .. " error : 유저 index 삭제 완료 불가 에러.")
   end
end

------------------------------- USER / CONTENT -------------------------------------------------

-- [add] 유저 / 컨텐츠 저장소에 세션 정보 추가 이후, 활성 유저 정보 교체. 에러 반환.
local function add_to_index(_key, _session_id)
    -- 유저 별 세션 리스트에 세션 추가
    local container = pcall(redis.call, 'ZADD', _key, expire, _session_id)
    if not container then
        error(SCRIPT_NAME .. " error: 세션 정보 저장 실패.")
    end
end


-- [update] 유저, 혹은 컨텐츠 저장소의 세션 정보를 업데이트 하는 함수. 에러 반환
local function session_sync(_key)
    -- 정리되지 않은 저장소에 대한 모든 세션 기록
    local result, pre_session_list = pcall(redis.call, 'ZRANGE', _key, 0, -1, 'WITHSCORES')
    -- 조회 에러 시 동기화 실패로 간주, 로그 남김
    if not result then
        redis.log(redis.LOG_WARNING, SCRIPT_NAME, "Error : 유저 저장소 동기화 실패 - key : ", _key)
        error(SCRIPT_NAME .. " error : 유저 저장소 동기화 실패")
    end

    for i = 1, #pre_session_list, 2 do
        local pre_session_id = pre_session_list[i]
        local pre_session_expire = tonumber(pre_session_list[i + 1])

        -- 현재 세션 수명 저장소에 존재하는 세션수명으로 검사. 에러 발생 시, 넘김
        local q_result, session_expire = pcall(redis.call, "ZSCORE", session_life_key, pre_session_id)

        if q_result then
            -- expire 갱신. 존재하지 않거나, 수명이 지낸 세션은 삭제.
            local r = true
            if not session_expire or tonumber(session_expire) <= now then
                r = pcall(redis.call, 'ZREM', _key, pre_session_id)
            elseif tonumber(session_expire) > pre_session_expire then
                r = pcall(redis.call, 'ZADD', _key, session_expire, pre_session_id)
            end
            -- 갱신 에러 시 로그 남기고 속행
            if not r then
                redis.log(redis.LOG_WARNING, "수명 갱신 실패 - key : ", _key, " session_id : ", pre_session_id)
            end
        end
    end
end

--------------------------------------- CONTENT ----------------------------------------
-- [update] 현재 컨텐츠 zset 에 포함된 세션의 실제 유저 수가 몇 명인지 조회, 설정하는 함수.
local function content_counter_update(_content_key, _content_id)
    -- 반환용 테이블
    local user_table = {}
    local unique_table = {}
    -- 만료 시간이 현재보다 미래인 객체 조회
    local result, session_in_content_list = pcall(redis.call, 'ZRANGEBYSCORE', _content_key, '(' .. now, '+inf')

    if not result then
        redis.log(redis.LOG_WARNING, "컨텐츠 시청자 수 업데이트 에러", _content_id)
        error("시청자 수 업데이트 에러")
    end

    for _, session_id_in in ipairs(session_in_content_list) do
        -- 세션 정보 저장소에서 세션 정보 반환.
        local r, info = pcall(redis.call, 'HGET', session_storage_key, session_id_in)

        if not r or not info then
            -- 세션 정보 저장소 반환 에러. 로그 남기고 집계 넘김
            redis.log(redis.LOG_NOTICE, "세션 정보 조회 에러. 카운팅 스킵 - ", session_id_in)
        else
            -- 정보 파싱
            local _, _, content_id, user_id = parse(info)

            if content_id == _content_id and user_id and user_id ~= "" then
                if not unique_table[user_id] then
                    table.insert(user_table,user_id)
                    unique_table[user_id] = true
                end
                else
                    -- 형식, 혹은 파싱 오류로 nil 반환시 로그만 남김
                    redis.log(redis.LOG_NOTICE, "유저 정보 검정 에러. 카운팅 스킵 - ", info)
                end
        end
    end

    local set_r = pcall(redis.call, 'HSET', content_counter_key, _content_id, #user_table)
    if not set_r then
        redis.log(redis.LOG_WARNING, SCRIPT_NAME, "컨텐츠 시청자 수 자료구조 갱신 에러. content : ", _content_id)
        error(SCRIPT_NAME .. " error: 시청자 수 세팅 시 에러.")
    end
end

-------------------- sub logic --------------------
-- [USER] 세션 정보가 저장 된 후, 유저 정보 조정.
-- 에러시 활성 유저 정보 조정 필요.
local function update_user_container(_user_id, _session_id)
    local user_key = user_key_prefix .. _user_id
    -- 유저 - 세션 동기화.
    session_sync(user_key)

    -- 유저 정보 체크(처음 접속한 사용라 라면, true 반환)
    local check, check_list = pcall(redis.call, 'ZRANGE', user_key, 0, -1)
    if not check or #check_list < 1 then
        fresh_user_flag = true
    end

    -- 유저 정보 추가, 활성 유저 추가.
    add_to_index(user_key, _session_id)

    -- 활성 유저 인덱스에 유저 정보 추가. 실패시 데이터 복구 시도.
    local active = pcall(redis.call, 'HSET', user_active_key, _user_id, _session_id)
    if not active then
        remove_user_index(user_key, _session_id)
    end
end

-- [CONTENT] 세션, 유저 정보가 저장된 후, 컨텐츠 저장소 정보 갱신.
local function update_content_container(_content_id, _session_id)
    local content_key = content_key_prefix .. _content_id

    -- 컨텐츠 - 세션 동기화
    session_sync(content_key)

    -- 컨텐츠 정보 추가
    add_to_index(content_key, _session_id)

    -- 컨텐츠 유저 카운터 업데이트
    content_counter_update(content_key, _content_id)
end




-------------------- main logic ------------------
-- 1. 주입 된 인자들로 저장 객체 조립. -> info(session_id:ctime:content_id:user_id)
local session_info = combine({session_id, session_ctime, content_id, user_id})

-- 2. hash 저장.
set_session_info(session_id, session_info)

-- 3. 유저 zset 업데이트.
update_user_container(user_id, session_id)

-- 4. 컨텐츠 zset 업데이트
update_content_container(content_id, session_id)

-- 5. 만약 하나라도 에러가 난다면, 이전 데이터로 복구
-- 당장은 시간이 없으므로,스크립트 내 로직에서 데이터 복구 로직은 구성하지 않는다.
-- 현재 작성된 복구 로직만 있는걸로 간주한다.

-- 사용자가 새로 들어온 것이가에 따라 체크 간주 필요.
local res = 0
if fresh_user_flag then
    res = 1
end
return res