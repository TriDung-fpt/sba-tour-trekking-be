local now = tonumber(redis.call('TIME')[1])
if tonumber(ARGV[3]) <= now then return 0 end
redis.call('HSET', KEYS[1], 'account', ARGV[1], 'fingerprint', ARGV[2], 'expires', ARGV[3], 'current', ARGV[4])
redis.call('EXPIREAT', KEYS[1], ARGV[3])
redis.call('SET', KEYS[2], ARGV[5], 'EXAT', ARGV[3])
return 1
