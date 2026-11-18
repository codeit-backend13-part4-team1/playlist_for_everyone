-- key
-- 1. content id

local time = redis.call('TIME')
-- 반올림 미릴초 단위
local now = time[1] * 1000 + math.floor(time[2]/1000)

local count = redis.call(
    'ZCOUNT',
    KEYS[1],
    '(' .. tostring(now),
    '+inf'
)

return count