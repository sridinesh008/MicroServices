-- Token Bucket — atomic rate limit check via Redis Lua script.
--
-- Why Lua? Redis executes Lua scripts atomically — no other command runs
-- on the server between HMGET and HSET. This is equivalent to
-- ConcurrentHashMap.compute() in InMemoryRateLimitStore.
--
-- KEYS[1] = hash key  e.g. "rl:tb:r1:IP:10.0.0.1:/api"
-- ARGV[1] = capacity        (integer  — max tokens the bucket can hold)
-- ARGV[2] = refill_rate     (float    — tokens added per second)
-- ARGV[3] = cost            (integer  — tokens consumed per request, usually 1)
-- ARGV[4] = now_ms          (integer  — current epoch time in milliseconds)
-- ARGV[5] = ttl_seconds     (integer  — key expiry; prevents stale keys from idle clients)
--
-- Returns: { allowed (0|1), floor(remaining_tokens), retry_after_seconds }

local key         = KEYS[1]
local capacity    = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local cost        = tonumber(ARGV[3])
local now_ms      = tonumber(ARGV[4])
local ttl         = tonumber(ARGV[5])

-- Read stored bucket state. Returns nil for both fields on first request.
local data          = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
local stored_tokens = tonumber(data[1])
local last_refill   = tonumber(data[2])

-- First request for this key: initialize bucket to full capacity.
-- Example: capacity=5 → tokens=5, last_refill_ms=now
if stored_tokens == nil then
    stored_tokens = capacity
    last_refill   = now_ms
end

-- Lazy refill: calculate tokens earned since the last request.
-- elapsed   = (now_ms - last_refill_ms) / 1000.0   (milliseconds → seconds)
-- new_tokens = min(capacity, stored + elapsed × rate)
--
-- Example: stored=1.0, last=2000ms, now=5000ms, rate=1.0/sec
--   elapsed   = (5000 - 2000) / 1000 = 3.0 sec
--   new_tokens = min(5, 1.0 + 3.0 × 1.0) = min(5, 4.0) = 4.0
local elapsed    = (now_ms - last_refill) / 1000.0
local new_tokens = math.min(capacity, stored_tokens + elapsed * refill_rate)

local allowed     = 0
local retry_after = 0

if new_tokens >= cost then
    -- Allow: deduct cost from bucket.
    -- Example: new_tokens=4.0, cost=1 → remaining=3.0
    new_tokens  = new_tokens - cost
    allowed     = 1
    retry_after = 0
else
    -- Deny: tell client how many seconds to wait for enough tokens.
    -- retry_after = ceil((cost - available) / rate)
    -- Example: need=1, have=0.2, rate=1.0 → ceil(0.8 / 1.0) = 1 second
    retry_after = math.ceil((cost - new_tokens) / refill_rate)
    allowed     = 0
end

-- Persist updated state back to Redis.
redis.call('HSET', key, 'tokens', tostring(new_tokens), 'last_refill_ms', tostring(now_ms))
-- Reset TTL on every access so idle clients' keys eventually expire.
redis.call('EXPIRE', key, ttl)

-- Return array → Java receives List<Long>:
--   [0] allowed (0 or 1)
--   [1] floor(remaining tokens) — fractional part dropped for the response header
--   [2] retry_after_seconds     — 0 when allowed
return { allowed, math.floor(new_tokens), retry_after }
