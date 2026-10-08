redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'context', ARGV[2], 'attempts', 0)
redis.call('EXPIRE', KEYS[1], 300)
return 1
