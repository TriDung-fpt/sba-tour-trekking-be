if redis.call('HGET', KEYS[1], 'hash') == ARGV[1] then redis.call('DEL', KEYS[1]) end
return 1
