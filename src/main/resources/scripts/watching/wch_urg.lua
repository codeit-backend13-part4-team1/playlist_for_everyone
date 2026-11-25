-- unregister.lua
-- 세션 구독 등록 취소 자료구조
-- ALL or Nothing 이라 가정하고 작성한다.

-- KEYS
local session_id = KEYS[1]
local subscription_id = KEYS[2]

-- ARGV
local content_key_prefix = ARGV[1]
local user_key_prefix = ARGV[2]
local session_key_prefix = ARGV[3]
local content_counter_key_prefix = ARGV[4]
local delimiter = ARGV[5]

-- redis 키
local session_key = session_key_prefix .. session_id

-- 저장용 값 페어
local session_subscription_pair = session_id .. delimiter .. subscription_id

-- ttl 변수들
local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)

-- 기타 변수들
local update_flag = false


--------------
---- UTIL ----
--------------

-- [UTIL] delimiter 를 기준으로 저장된 문자열을 분리한다.
local function parse_contained_string(_string)

    local pos = string.find(_string, delimiter, 1, true)

    if not pos then
        error("delimiter 을 찾을 수 없음.")
    end

    local first = string.sub(_string, 1, pos - 1)
    local second = string.sub(_string, pos + #delimiter)

    if first == "" or second == "" then
        error("파싱 결과가 비어있음")
    end

    return first, second
end


-- 등록 취소할 구독의 content_id, user_id 조회
local content_user_pair = redis.call('HGET', session_key, subscription_id)

-- 이미 제거되었거나 존재하지 않는 구독
if not content_user_pair then
    return 0
end

local content_id, user_id = parse_contained_string(content_user_pair)

-- 조회 결과를 기반으로 Redis 키 조립
local content_key = content_key_prefix .. content_id
local user_key = user_key_prefix .. user_id
local content_counter_key = content_counter_key_prefix .. content_id


-- [UTIL] key_pair 에 연결된 content_id, user_id 반환
local function query_key_pair_to_content(
    _session_subscription_pair
)

    local session_id, subscription_id =
        parse_contained_string(
            _session_subscription_pair
        )

    local session_key =
        session_key_prefix .. session_id

    local content_user_pair = redis.call(
        'HGET', session_key, subscription_id
    )

    local content_id, user_id =
        parse_contained_string(content_user_pair)

    return content_id, user_id
end


-- [UTIL] 해당 유저가 콘텐츠에 활성 구독을
-- 하나 이상 보유하고 있다면 true 반환
local function check_user_existed_in_content(_user_key, _content_id)

    for _, session_subscription_pair in ipairs(
        redis.call('SMEMBERS', _user_key)
    ) do

        local content_id, user_id =
            query_key_pair_to_content(
                session_subscription_pair
            )

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


-- [UTIL] 콘텐츠에 속한 순수 시청자 수 업데이트
local function count_current_viewing_user_update(
    _content_id
)

    local uniqueUsers = {}
    local count = 0

    -- 현재 콘텐츠에 등록된 활성 구독 반환
    local subscriptions_in_content = redis.call(
        'ZRANGEBYSCORE',
        content_key,
        '(' .. now,
        '+inf'
    )

    -- 구독별 사용자, 콘텐츠 조회
    for _, session_subscription_pair in ipairs(
        subscriptions_in_content
    ) do

        local found_content_id, found_user_id =
            query_key_pair_to_content(
                session_subscription_pair
            )

        -- 같은 콘텐츠라면 중복되지 않게 유저 카운팅
        if found_content_id == _content_id then

            if found_user_id ~= ""
                and not uniqueUsers[found_user_id] then

                uniqueUsers[found_user_id] = true
                count = count + 1
            end
        end
    end

    -- 현재 콘텐츠 시청자 수 대입
    redis.call(
        'SET',
        content_counter_key,
        count
    )
end


--------------
---- USER ----
--------------

-- [USER] 유저별 구독 SET 에서 구독 제거
local function remove_subscription_by_user()
    redis.call('SREM', user_key, session_subscription_pair)
end


-----------------
---- CONTENT ----
-----------------

-- [CONTENT ZSET] 콘텐츠별 구독 제거
local function remove_subscription_by_content_key()

    -- 만료된 구독 제거
    local remove_counter = redis.call('ZREMRANGEBYSCORE', content_key, '-inf', now)

    if remove_counter > 0 then
        update_flag = true
    end

    -- 현재 구독 제거
    redis.call('ZREM', content_key, session_subscription_pair)
end


--------------
---- HASH ----
--------------

-- [HASH] 세션에서 해당 구독 정보 제거
local function remove_subscription_by_session_key()
    redis.call('HDEL', session_key, subscription_id)
end


---------------
---- CHECK ----
---------------

-- [CHECK] 등록 취소 대상 구독의 활성 여부 확인
local function check_subscription_active()

    local score = redis.call(
        'ZSCORE',
        content_key,
        session_subscription_pair
    )

    if score and tonumber(score) > now then
        return true
    end

    return false
end

------------
-- 로직 부분
------------

-- 1. 등록 취소하기 전 해당 구독의 활성 여부 확인
local was_active = check_subscription_active()

-- 2. 유저 - 구독 제거
remove_subscription_by_user()

-- 3. 콘텐츠 - 구독 제거 및 만료 구독 정리
remove_subscription_by_content_key()

-- 4. 세션 - 구독 HASH 제거
remove_subscription_by_session_key()

-- 5. 해당 유저가 여전히 콘텐츠를 시청 중인지 확인
local res, existed = pcall(
    check_user_existed_in_content,
    user_key,
    content_id
)

if not res then
    return redis.error_reply(
        "ERR 사용자 구독 조회 로직에서 에러 발생"
    )
end

-- 6. 마지막 활성 구독이 제거되었다면
-- 시청자 수 업데이트 플래그 설정
if was_active and not existed then
    update_flag = true
end

-- 7. 시청자 수 갱신이 필요한 경우 재집계
if update_flag then
    count_current_viewing_user_update(content_id)
end

-- 8. 모든 작업이 정상적으로 완료되면 0 반환
return 0