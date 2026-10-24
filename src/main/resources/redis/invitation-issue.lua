-- Reserve delivery and replace the challenge in one atomic operation.
if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
if tonumber(redis.call('GET', KEYS[3]) or '0') >= 5 then return 0 end

redis.call('SET', KEYS[2], '1', 'EX', 60)
local count = redis.call('INCR', KEYS[3])
if count == 1 then redis.call('EXPIRE', KEYS[3], 3600) end
redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'context', ARGV[2], 'attempts', 0)
redis.call('EXPIRE', KEYS[1], 300)
return 1
