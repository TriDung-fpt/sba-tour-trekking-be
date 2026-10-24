-- A delayed mail failure must not remove a newer delivery's challenge.
if redis.call('HGET', KEYS[1], 'hash') == ARGV[1] then
    redis.call('DEL', KEYS[1])
end
-- Keep cooldown/hour reservations even when mail delivery fails.
return 1
