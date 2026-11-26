-- 인자로 주입 받은 구독 Id 에 해당하는 세션 수명을 갱신한다.
-- 인자는 전부 java 단에서 이상적으로 검증하고 들어오는 것으로 가정.이상적으로
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

local SCRIPT_NAME = "WatchingSessionRefresh"

-- ARGV
--- key prefix
local session_key_prefix = ARGV[1]
local content_key_prefix = ARGV[2]
local user_key_prefix = ARGV[3]
--- argv option value
local delimiter = ARGV[4]
local ttl = tonumber(ARGV[5])

-- global variable
local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)
local expire = now + ttl

--- redis keys
local session_storage_key = session_key_prefix .. "store" -- tmp
local session_life_key = session_key_prefix .. "life" -- tmp
local user_active_key = user_key_prefix .. "active" -- tmp
local content_counter_key = content_key_prefix .. "counter"

-- ARGV 키 마지막 index
local CURSOR = 6

local session_id


-- 업데이트 용 인덱스
local update_user_id_flag = {}
local update_content_id_flag = {}

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



------------------------------ USER --------------------------------------

-- [UTIL] 현재 user container 에서 가장 최근 접속한 세션을 체크하고, 활성 사용자로 변경
local function select_next_user_to_active(_user_key, _user_id)
    -- user index 에서 최근 사용자 조회 => 배열
    local next, next_active = pcall(redis.call, 'ZREVRANGE', _user_key, 0, 0)

    if not next then
        redis.log(redis.LOG_WARNING, SCRIPT_NAME, " : 새 활성 사용자 설정 실패! - ")
        -- 활성 유저 정보 삭제.
        pcall(redis.call, 'HDEL', user_active_key, _user_id)
    else
        if #next_active < 1 then
            -- 이제 유저가 존재하지 않은경우, 활성 사용자 삭제.
            pcall(redis.call, 'HDEL', user_active_key, _user_id)
        else
            -- 유저가 존재하면, 활성 사용자로 세팅
            pcall(redis.call, 'HSET', user_active_key, _user_id, next_active[1])
        end
    end
end

-- [UTIl] 활성 사용자를 업데이트 하는 유틸리티 함수.
-- 세션 갱신이므로 활성 사용자는 항상 바꾼다.
local function update_active_user(_user_key, _user_id)
    select_next_user_to_active(_user_key,_user_id)
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




----------------------------- USER / CONTAINER -----------------------------
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


-------------------------------- sub logic ---------------------------

-- 반복문 continue 를 구현하기 위한 래필 함수
local function pseudo_continue_logic_function(_session_id)
    local result, info = pcall(redis.call, 'HGET', session_storage_key, _session_id)

    if not result then
        redis.log(redis.LOG_WARNING, SCRIPT_NAME, " : 초기 세션 정보 조회 오류! 조기 에러", _session_id)
        error("초기 세션 조회 에러!")
    end

    -- 세션 정보가 없다면 레코드 삭제
    local r_update
    if not info then
        r_update = pcall(redis.call, 'ZREM', session_life_key, _session_id)
        -- ttl 갱신 실패 시, 로그에 기록, 다음 작업으로.
        if not r_update then
            redis.log(redis.LOG_WARNING, SCRIPT_NAME, " : 만료 세션 수명 정보 삭제 실패 - ", _session_id)
        end
        return
    end


    local _, _, content_id, user_id = parse(info)

    if not content_id or not user_id then
        redis.log(redis.LOG_WARNING, SCRIPT_NAME, " : 초기 세션 정보 형식 오류! 조기 에러", info)
        error("세션 정보에 콘텐츠 ID 또는 사용자 ID가 없습니다.")
    end

    -- 세션 정보가 존재하면, ttl 갱신.
    r_update = pcall(redis.call, 'ZADD', session_life_key, expire, _session_id)

    -- ttl 갱신 실패 시, 로그에 기록, 다음 작업으로.
    if not r_update then
        redis.log(redis.LOG_WARNING, SCRIPT_NAME, " : 세션 수명 갱신 실패 - ", _session_id)
        return
    end

    -- 변경된 유저 id 체크
    if not update_user_id_flag[user_id] then
        update_user_id_flag[user_id] = _session_id
    end
    -- 변경된 컨텐츠 id 체크
    if not update_content_id_flag[content_id] then
        update_content_id_flag[content_id] = _session_id
    end
end

local function update_user_container(_user_id)
    local user_key = user_key_prefix .. _user_id
    -- 2. 세션 업데이트
    session_sync(user_key)
    -- 3. 활성 유저 업데이트
    update_active_user(user_key, _user_id)
end

local function update_content_container(_content_id, _session_id)
   local content_key = content_key_prefix .. _content_id

   -- 2. 세션 업데이트
   session_sync(content_key)
   -- 3. 카운터 업데이트
   content_counter_update(content_key, _content_id)
end

-------------------------------- main logic -------------------------
-- 세션 정보가 없다면 0 반환.
if #ARGV < CURSOR then
    return 0
end

-- 세션 정보를 먼저 업데이트
for i = CURSOR, #ARGV do
    session_id = ARGV[i]
    pseudo_continue_logic_function(session_id)
end

-- 문자열 순회는 pairs
for _user_id, _session_id in pairs(update_user_id_flag) do
    update_user_container(_user_id)
end

for _content_id, _session_id in pairs(update_content_id_flag) do
    update_content_container(_content_id, _session_id)
end

-- 당장은 시간이 없으므로,스크립트 내 로직에서 데이터 복구 로직은 구성하지 않는다.
-- 현재 작성된 복구 로직만 있는걸로 간주한다.


return 0