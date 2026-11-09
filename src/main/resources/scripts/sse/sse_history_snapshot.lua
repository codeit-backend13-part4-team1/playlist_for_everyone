-- KEYS[1]: 사용자별 Stream 키
-- KEYS[2]: 사용자별 이벤트 UUID → Stream ID 매핑키
--
-- ARGV[1]: 마지막 수신 이벤트 UUID 문자열. 없으면 빈 문자열
-- ARGV[2]: 보관기간(밀리초)
--
-- 반환값:
-- {
--     upperBound,
--     {
--         {Stream ID, 이벤트 JSON},
--         ...
--     }
-- }
--
-- upperBound: 조회 시점의 마지막 Stream ID. 이력이 없으면 빈 문자열
-- 복구할 이벤트가 없으면 두 번째 값은 빈 배열
-- 커서가 없거나 현재 사용자 이력에 없으면 과거 전체를 보내지 않고 현재 끝 위치만 반환한다.
-- 만료 정리와 끝 위치·복구 목록 확보를 한 호출로 수행하며, 조회가 TTL을 갱신하지 않는다.

local streamKey = KEYS[1]
local mappingKey = KEYS[2]

local lastEventId = ARGV[1]
local retentionMillis = tonumber(ARGV[2])

if not retentionMillis
        or retentionMillis < 1
        or retentionMillis % 1 ~= 0 then
    return redis.error_reply('보관기간은 1밀리초 이상의 정수여야 합니다.')
end

local function getEventJson(entry)
    local fields = entry[2]

    for index =1, #fields, 2 do
        if fields[index] == 'event' then
            return fields[index + 1]
        end
    end

    error('저장된 SSE 이력에 event 필드가 없습니다.')
end

-- 조회 전에 만료된 본문과 UUID 매핑을 함께 정리한다. 읽기 요청이어도 이 정리 과정은 Redis 데이터를 변경한다.
local function removeHistoryEntries(entries)
    for _, entry in ipairs(entries) do
        local position = entry[1]
        local storedEvent = cjson.decode(getEventJson(entry))

        redis.call('HDEL', mappingKey, storedEvent.eventId)
        redis.call('XDEL', streamKey, position)
    end
end

local serverTime = redis.call('TIME')
local nowMillis = tonumber(serverTime[1]) * 1000 + math.floor(tonumber(serverTime[2]) / 1000)
local cutoffMillis = nowMillis - retentionMillis

if cutoffMillis >= 0 then
    local expiredEntries = redis.call('XRANGE', streamKey, '-', string.format('%.0f', cutoffMillis))
    removeHistoryEntries(expiredEntries)
end

local latestEntries = redis.call('XREVRANGE', streamKey, '+', '-', 'COUNT', 1)
if #latestEntries == 0 then
    -- 남은 이력이 없으면 빈 Stream·매핑 키를 함께 제거한다. Java에서는 빈 끝 위치를 null로 해석한다.
    redis.call('DEL', streamKey, mappingKey)
    return{'', {}}
end

local upperBound = latestEntries[1][1]
if lastEventId == '' then
    return {upperBound, {}}
end

local cursorPosition = redis.call('HGET', mappingKey, lastEventId)
if not cursorPosition then
    return {upperBound, {}}
end

local cursorEntries =redis.call('XRANGE', streamKey, cursorPosition, cursorPosition)
if #cursorEntries == 0 then
    -- UUID 매핑만으로 커서가 유효하다고 판단하지 않는다. 실제 항목이 없으면 과거 전체 재전송을 하지 않는다.
    redis.call('HDEL', mappingKey, lastEventId)
    return {upperBound, {}}
end

-- 커서 자신은 제외하고, 이번 조회에서 확보한 upperBound까지만 초기 복구 대상으로 삼는다.
local records = redis.call('XRANGE', streamKey, '('.. cursorPosition, upperBound)
local entries = {}

for _, entry in ipairs(records) do
    table.insert(entries, {entry[1], getEventJson(entry)})
end

return {upperBound, entries}
