local hash = redis.call('HGET', KEYS[1], 'hash')
if not hash then return 0 end
if redis.call('HGET', KEYS[1], 'context') ~= ARGV[2] then
    redis.call('DEL', KEYS[1])
    return 0
end

local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
if hash == ARGV[1] and attempts <= 5 then
    redis.call('DEL', KEYS[1])
    return 1
end
if attempts >= 5 then redis.call('DEL', KEYS[1]) end
return 0
