-- KEYS[1]: 사용자별 Stream 키
-- KEYS[2]: 사용자별 이벤트 UUID → Stream ID 매핑키
--
-- ARGV[1]: 이벤트 UUID 문자열
-- ARGV[2]: 필드 순서를 정렬하여 직렬화한 SseEvent 전체 JSON
-- ARGV[3]: 보관기간(밀리초)
-- ARGV[4]: 사용자별 최대 보관 건수
--
-- 반환값: {처리 결과, Stream ID, 저장된 이벤트 JSON}
-- 처리 결과: CREATED / EXISTING / CONFLICT
--
-- 두 키는 같은 사용자 해시 태그를 사용한다. 다른 Redis 명령이 중간에 끼어들지 않게
-- 정리 → 중복 확인 → 저장 → 건수 제한 → TTL 설정을 한 호출로 수행한다.
-- Lua 원자성은 실행 중 오류에 대한 롤백을 뜻하지 않는다.

local streamKey = KEYS[1]
local mappingKey = KEYS[2]

local eventId = ARGV[1]
local eventJson = ARGV[2]
local retentionMillis = tonumber(ARGV[3])
local maxEventsPerUser = tonumber(ARGV[4])

if not retentionMillis
        or retentionMillis < 1
        or retentionMillis % 1 ~= 0 then
    return redis.error_reply('보관기간은 1밀리초이상의 정수여야 합니다.')
end

if not maxEventsPerUser
        or maxEventsPerUser < 1
        or maxEventsPerUser % 1 ~= 0 then
    return redis.error_reply('최대 보관 건수는 1이상의 정수여야 합니다.')
end

local function getEventJson(entry)
    local fields = entry[2]

    for index =1, #fields, 2 do
        if fields[index] == 'event' then
            return fields[index + 1]
        end
    end

    error('저장 된 SSE 이력에 event 필드가 없습니다.')
end

local function removeHistoryEntries(entries)
    for _, entry in ipairs(entries) do
        local position = entry[1]
        local storedEvent = cjson.decode(getEventJson(entry))

        redis.call('HDEL', mappingKey, storedEvent.eventId)
        redis.call('XDEL', streamKey, position)
    end
end

-- 앱 서버 간 시계 차이를 피하려고 Redis 시간을 기준으로 최초 저장 시점이 오래된 항목을 정리한다.
local serverTime = redis.call('TIME')
local nowMillis = tonumber(serverTime[1]) * 1000 + math.floor(tonumber(serverTime[2]) / 1000)
local cutoffMillis = nowMillis - retentionMillis

if cutoffMillis >= 0 then
    local expiredEntries = redis.call('XRANGE', streamKey, '-', string.format('%.0f', cutoffMillis))
    removeHistoryEntries(expiredEntries)
end

local existingPosition = redis.call('HGET', mappingKey, eventId)

if existingPosition then
    local existingEntries = redis.call('XRANGE', streamKey, existingPosition, existingPosition)
    if #existingEntries > 0 then
        local existingJson = getEventJson(existingEntries[1])
        -- 중복·충돌이면 새 위치를 만들거나 키의 만료시각을 연장하지 않는다.
        if existingJson == eventJson then
            return {'EXISTING', existingPosition, existingJson}
        end
        return {'CONFLICT', existingPosition, existingJson}
    end
    redis.call('HDEL', mappingKey, eventId)
end

local position = redis.call('XADD', streamKey, '*', 'event', eventJson)

redis.call('HSET', mappingKey, eventId, position)

-- 이 Stream은 한 사용자의 이력이다. 전체 사용자 수가 늘면 총 메모리도 별도로 증가한다.
local overflowCount = redis.call('XLEN', streamKey) - maxEventsPerUser

if overflowCount > 0 then
    local oldestEntries = redis.call('XRANGE', streamKey, '-', '+', 'COUNT', overflowCount)
    removeHistoryEntries(oldestEntries)
end

-- 신규 저장만 키 TTL을 갱신한다. 기존 이벤트의 저장 시각은 그대로이므로 개별 보관기간은 늘지 않는다.
-- 계속 활동하는 키의 오래된 이벤트는 저장·조회 시 정리하고, 비활성 키는 TTL로 제거한다.
local expiresAt = string.format('%.0f', nowMillis + retentionMillis)

redis.call('PEXPIREAT', streamKey, expiresAt)
redis.call('PEXPIREAT', mappingKey, expiresAt)

return {'CREATED', position, eventJson}
