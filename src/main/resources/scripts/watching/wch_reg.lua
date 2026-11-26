-- 세션 저장 자료구조
-- 우선 기능 완성을 위해 스크립트 도중 실패에 의한 자료 복구는 고려하지 않는다. ALL or NotThing 이라 가정하고 작성한다.

-- KEYS
local content_id = KEYS[1]
local user_id = KEYS[2]
local session_id = KEYS[3]
local subscription_id = KEYS[4]

-- ARGV
local content_key_prefix = ARGV[1]
local user_key_prefix = ARGV[2]
local session_key_prefix = ARGV[3]
local content_counter_key_prefix = ARGV[4]
local delimiter = ARGV[5]
local ttl = tonumber(ARGV[6])

-- redis 키
local content_key = content_key_prefix .. content_id
local user_key = user_key_prefix .. user_id
local session_key = session_key_prefix .. session_id
local content_counter_key = content_counter_key_prefix .. content_id

-- 저장용 값 페어
local content_user_pair = content_id .. delimiter .. user_id
local session_subscription_pair = session_id .. delimiter .. subscription_id

-- ttl 변수들
local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)
local expiresAt = now + ttl

-- 기타 변수들
local update_flag = false



-- [UTIL] delimiter 를 기준으로 저장된 문자열을 분리한다.
local function parse_contained_string(_string)

    local pos = string.find(_string, delimiter, 1, true)

    if not pos then
        error("delimiter 을 찾을 수 없음.")
    end

    local first = string.sub(_string, 1, pos - 1)
    local second = string.sub(_string, pos + #delimiter) -- "#~" 는 ~ 에 대한 길이 연산자.

    if first == "" or second == "" then
        error("파싱 결과가 비어있음")
    end

    return first, second
end

-- [UTIL] key_pair 을 hash 에 저장중인 content id, user_id 로 반환하는 유틸 함수
local function query_key_pair_to_content(_session_subscription_pair)
    -- session 과 subscription 분리
    local session_id, subscription_id = parse_contained_string(_session_subscription_pair)

    -- 세션 조회용 키 조립
    local session_key = session_key_prefix .. session_id
    local content_user_pair = redis.call('HGET', session_key, subscription_id)

    -- 세션이 만료된 경우
    if not content_user_pair then
        return nil, nil
    end

    local content_id, user_id = parse_contained_string(content_user_pair)

    return content_id, user_id
end


-- [UTIL] user - subscription 자료구조에 현재 등록 에 해당하는 contentId 랑 연결된 구독이 있으면
--  1 을 반환, 아니면 0 반환
local function check_user_existed_in_content(_user_key, _content_id)
    for _, session_subscription_pair in ipairs(redis.call('SMEMBERS', _user_key)) do

        local content_id, user_id = query_key_pair_to_content(session_subscription_pair)

        if _content_id == content_id then
            local score = redis.call(
                            'ZSCORE',
                            content_key_prefix .. _content_id,
                            session_subscription_pair
                        )
            if score and tonumber(score) > now then
                return true
            end
        end
    end
    return false
end

-- [UTIL] content 에 속한 시청자 수 업데이트
local function count_current_viewing_user_update(_content_id)
    local uniqueUsers = {}
    local count = 0

    -- 현재 컨텐츠에 등록된 구독들 반환
    local subscriptions_in_content = redis.call('ZRANGEBYSCORE', content_key,'(' .. now, '+inf')

    -- 구독별로 사용자, 컨텐츠 조회
    for _, session_subscription_pair in ipairs(subscriptions_in_content) do
        local found_content_id, found_user_id = query_key_pair_to_content(session_subscription_pair)

        -- 같은 컨텐츠라면 중복되지 않게 유저 카운팅
        if found_content_id == _content_id then
            if found_user_id ~= "" and not uniqueUsers[found_user_id] then
                uniqueUsers[found_user_id] = true
                count = count + 1
            end
        end
    end

    -- content 시청자 수 카운터에 현재 시청자 수 대입
    redis.call('SET', content_counter_key, count)
    redis.call('PEXPIREAT', content_counter_key, expiresAt)
end


--------------
---- USER ----
--------------

-- [USER] 유저 key 에 대해 subscriptionId 를 등록하고, ttl 을 설정
-- 유저가 컨텐츠에 처음 조인된 유저라면 1, 아니면 0 반환
local function set_subscription_by_user()

    local check_duplicate = redis.call('SISMEMBER', user_key, session_subscription_pair)

    -- 이미 레코드가 존재한다면 아무것도 하지 않고 0 반환.
    if check_duplicate == 1 then
        local score = redis.call(
            'ZSCORE',
            content_key,
            session_subscription_pair
        )

        if score and tonumber(score) > now then
            redis.call('PEXPIREAT', user_key, expiresAt)
            return 0
        end
    end

    -- 존재하지 않으면 첫 조인인지 파악
    local res, existed = pcall(check_user_existed_in_content, user_key, content_id)

    if not res then
        return -1
    end

    -- 리소스 등록 로직
    -- user 별 구독 레코드 추가
    redis.call('SADD', user_key, session_subscription_pair)
    redis.call('PEXPIREAT', user_key, expiresAt)
    if existed then
        -- 사전에 시청중인 컨텐츠라면 별 리소스 조작 없이 반환
        return 0
    else
        update_flag = true
        return 1
    end
end


-----------------
---- CONTENT ----
-----------------

-- [CONTENT SET] content key 에 대해 구독(세션 - 구독) id 를 저장. -> content 에 대해 연결된 구독 확인
local function set_subscription_by_content_key()
    -- 컨텐츠에 구독 레코드 업데이트, 생성 및 수명주기 갱신.
    local remove_counter = redis.call('ZREMRANGEBYSCORE', content_key, '-inf', now)

    if remove_counter > 0 then
        update_flag = true
    end

    redis.call('ZADD', content_key, expiresAt, session_subscription_pair)
    redis.call('PEXPIREAT', content_key, expiresAt)
end

--------------
---- HASH ----
--------------

-- [HASH] 세션 - 구독 - (컨텐츠 + 유저) 저장 자료구조 추가
local function set_subscription_by_session_key()
    redis.call('HSET', session_key, subscription_id, content_user_pair)
    redis.call('PEXPIREAT', session_key, expiresAt)
end


---------------
---- CHECK ----
---------------

-- [CHECK] 특정 세션의 특정 구독에 컨텐츠 + 유저가 존재하는지 확인하는 함수
local function subscription_conflict_check()
    -- 세션 ID - 구독 ID 에 연결된 contentID 반환
    local oldContent = redis.call('HGET', session_key, subscription_id)

    -- 기존 구독이 존재하면서 같은 구독이 아니면 구독 충돌 (~= 는 != 즉, 불일치 연산자)
    if oldContent and oldContent ~= content_user_pair then
        return true
    end
    return false
end

------------
-- 로직 부분
------------

-- 1. 구독 충돌이 존재하면 에러.(-1)
if subscription_conflict_check() then
    return redis.error_reply("구독 충돌 발생")
end

-- 2. 유저 - 구독 등록(리소스 조회가 필요함으로 우선 처리)
local first_join = set_subscription_by_user()

if first_join ~= 0 and first_join ~= 1 then
    return redis.error_reply("리소스 등록 로직에서 에러 발생")
end

-- 3. 세션 - 구독 - 컨텐츠 hash 등록
set_subscription_by_session_key()

-- 4. 컨텐츠 - 구독 등록
set_subscription_by_content_key()

-- 5. 신규 시청자라면 시청자 수 업데이트
if update_flag then
    count_current_viewing_user_update(content_id)
end

return first_join




