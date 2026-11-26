-- keys
local content_id = KEYS[1]

-- argv
local content_count_prefix = ARGV[1]

local content_count_key = content_count_prefix .. content_id

local count = redis.call('GET',content_count_key)

if not count then
    return 0
end
return count