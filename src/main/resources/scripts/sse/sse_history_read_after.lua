-- KEYS[1]: 사용자별 Stream 키
-- KEYS[2]: 사용자별 이벤트 UUID → Stream ID 매핑키
--
-- ARGV[1]: 기준 Stream ID. 없으면 빈 문자열
-- ARGV[2]: 보관기간(밀리초)
--
-- 반환값:
-- {
--     gapDetected,
--     {
--         {Stream ID, 이벤트 JSON},
--         ...
--     }
-- }
--
-- gapDetected: 1이면 주어진 기준 위치를 보관 이력에서 확인할 수 없다. 실제 미수신 유실의 확정은 아니다.
-- 기준 위치가 없어도 그보다 뒤에 남은 이력은 반환한다. 기준 위치가 없거나 오래됐다는 이유로 실패하지 않는다.
-- 기준 위치를 주지 않으면 남은 전체 이력과 gapDetected=0을 반환한다.
-- 조회 과정에서 키의 만료시간은 갱신하지 않는다.

local streamKey = KEYS[1]
local mappingKey = KEYS[2]

local afterPosition = ARGV[1]
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

-- 전체 만료 시 빈 키를 다시 만들지 않는다. 연결 유지와 이후 새 이벤트 처리는 호출부의 책임이다.
if redis.call('XLEN', streamKey) == 0 then
    redis.call('DEL', streamKey, mappingKey)

    if afterPosition == '' then
        return {0, {}}
    end

    return {1, {}}
end

local gapDetected = 0

if afterPosition ~= '' then
    local cursorEntries = redis.call('XRANGE', streamKey, afterPosition, afterPosition)

    if #cursorEntries == 0 then
        gapDetected = 1
    end
end

local startPosition = '-'
if afterPosition ~= '' then
    startPosition = '(' .. afterPosition
end

local records = redis.call('XRANGE', streamKey, startPosition, '+')
local entries = {}
for _, entry in ipairs(records) do
    table.insert(entries, {entry[1], getEventJson(entry)})
end

return {gapDetected, entries}
