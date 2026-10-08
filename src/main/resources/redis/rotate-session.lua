if redis.call('GET', KEYS[2]) ~= ARGV[3] then return 0 end
local current = redis.call('HGET', KEYS[1], 'current')
if not current then return 0 end
if current ~= ARGV[1] then
    redis.call('DEL', KEYS[1])
    return -1
end
local expires = redis.call('HGET', KEYS[1], 'expires')
if tonumber(expires) <= tonumber(redis.call('TIME')[1]) then
    redis.call('DEL', KEYS[1])
    return 0
end
redis.call('HSET', KEYS[1], 'current', ARGV[2])
redis.call('SET', KEYS[3], ARGV[3], 'EXAT', expires)
return 1
