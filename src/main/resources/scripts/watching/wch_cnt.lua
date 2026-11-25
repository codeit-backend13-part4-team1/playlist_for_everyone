-- keys
local content_id = KEYS[1]

-- argv
local content_count_prefix = ARGV[1]

local content_count_key = content_count_prefix .. content_id

local count = redis.call(redis.call('GET',content_count_key))
return count