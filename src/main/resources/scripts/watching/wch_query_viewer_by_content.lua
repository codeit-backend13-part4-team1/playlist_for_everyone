-- 컨텐츠에 해당하는 현재 시청자 유저 id 를 리스트로 반환하는 스크립트
-- key
local content_id = KEYS[1]
-- argv
local content_key_prefix = ARGV[1]
local user_key_prefix = ARGV[2]
local session_key_prefix = ARGV[3]
local content_counter_key_prefix = ARGV[4]
local delimiter = ARGV[5]

-- 기타
local content_key = content_key_prefix .. content_id

-- [UTIL] 구분자로 결합된 키 페어 분리.
-- 무조건 구분자로 결합되었던 2개의 문자열을 반환한다.
-- 구분자가 없거나, 값이 이상하면 에러 반환.
local function parse_key_pair(_combined)
    -- 구분자 시작 위치 추출 및 검증 => 없다면 false(nil) 반환.
    local pos = string.find(_combined, delimiter, 1, true)
    if not pos then
        error("구분자를 찾을 수 없습니다.")
    end

    -- 분리된 키 페어 검증
    local pre = string.sub(_combined, 1, pos - 1)
    local post = string.sub(_combined, pos + #delimiter)
    if pre == '' or post == '' then
        error("추출된 키 값이 비어있습니다.")
    end

    return pre, post
end

-- [UTIL] session 키를 바탕으로 content id 와 user id 를 반환하는 로직
-- 특정 구독이 존재하는가 에 따라서 값이 반환되므로, 현재 활성 상태인 구독을 체크하는 메서드 이다.
-- 에러가 발생하면 nil,nil 반환
local function query_content_user_pair_by_hash(_session_key, _subscription_id)
    -- hash 에서 content id 와 user id 반환. 없을 시 false 반환
    local content_user_pair = redis.call("HGET", _session_key, _subscription_id)
    if not content_user_pair then
        -- 키페어가 존재하지 않으면 nil 세트 반환.
        return nil, nil
    end

    -- 추출 후 반환값 검정 로직.(형식 불량 시 에러 반환)
    local result, content_id, user_id = pcall(parse_key_pair,content_user_pair)
    if not result then
        redis.log(redis.LOG_WARNING,"구독 데이터 형식 파싱 에러!", _session_key, _subscription_id, content_user_pair)
        return nil, nil
    end
    return content_id, user_id
end

-- [UTIL] for 문 내부의 유사 continue 기능을 위한 래퍼 함수
-- continue 로직에선 nil, 값이 존재하면 값(컨텐츠 id) 반환.
local function pseudo_continue_function_in_repeat_syntax(_session_subscription_pair)
    -- 세션, 구독 키 추출 및 검증( 파싱 불가시 에러 발생. )
    local result, session_id, subscription_id = pcall(parse_key_pair,_session_subscription_pair)
    -- content 에 저장된 구독 값 이상시 핸들링.
    if not result then
        return nil
    end

    -- 파싱 성공시 로직
    local session_key = session_key_prefix .. session_id
    local queried_content_id, user_id = query_content_user_pair_by_hash(session_key, subscription_id)

    -- 현재 컨텐츠에 속한 게 맞는 지 확인.
    if queried_content_id ~= content_id then
        return nil
    end

    return user_id
end

-- [UTIL] 컨텐츠에 존재하는 구독을 유저 id 로서반환.
local function query_subscription_by_content(_content_key)
    -- 컨텐츠에 존재하는 구독 요소 반환.
    -- 존재하지 않는 (= 요소가 0 인) Set 은 Get => false(lua), nil(redis) / SEMEBERS => {}(lua), empty array(redis)
    local subscription_by_content = redis.call('SMEMBERS', _content_key)
    -- 결과 반환용 테이블
    local user_id_table = {}
    -- 중복 검사용 테이블
    local unique_user_id_index = {}

    -- 빈 테이블 이라면 바로 빈 테이블 반환. => 빈 테이블 반복문 진입 안함
--     if #subscription_by_content == 0 then
--         return user_id_table
--     end

    -- 순회하면서 현재 존재하는 hash(실시간 구독 리스트) 와 비교.
    for _, session_subscription_pair in ipairs(subscription_by_content) do
        local user_id = pseudo_continue_function_in_repeat_syntax(session_subscription_pair)
        if user_id then
            if not unique_user_id_index[user_id] then
                table.insert(user_id_table, user_id)
                unique_user_id_index[user_id] = true
            end
        end
    end
    return user_id_table
end


-- logic
return query_subscription_by_content(content_key)





