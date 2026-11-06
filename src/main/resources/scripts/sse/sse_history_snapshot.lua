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
    redis.call('HDEL', mappingKey, lastEventId)
    return {upperBound, {}}
end

local records = redis.call('XRANGE', streamKey, '('.. cursorPosition, upperBound)
local entries = {}

for _, entry in ipairs(records) do
    table.insert(entries, {entry[1], getEventJson(entry)})
end

return {upperBound, entries}
