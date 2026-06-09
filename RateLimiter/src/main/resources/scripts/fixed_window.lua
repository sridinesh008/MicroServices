-- Fixed Window — atomic rate limit check via Redis Lua script.
--
-- A fixed window resets the counter at the start of each window boundary.
-- E.g. capacity=100, windowSizeMs=1000 → allow 100 requests per second.
--
-- KEYS[1] = base key         e.g. "rl:fw:r1:IP:10.0.0.1:/api"
-- ARGV[1] = capacity         (integer — max units allowed per window)
-- ARGV[2] = window_size_ms   (integer — window duration in milliseconds)
-- ARGV[3] = now_ms           (integer — current epoch time in milliseconds)
-- ARGV[4] = cost             (integer — units consumed per request, usually 1)
--
-- Returns: { allowed (0|1), remaining, retry_after_seconds }

local base_key   = KEYS[1]
local capacity   = tonumber(ARGV[1])
local window_ms  = tonumber(ARGV[2])
local now_ms     = tonumber(ARGV[3])
local cost       = tonumber(ARGV[4])
local window_id  = math.floor(now_ms / window_ms)
local window_key = base_key .. ":" .. window_id
local window_end = (window_id + 1) * window_ms

-- Read current count for this window (nil → 0 on first request).
local count = tonumber(redis.call("GET", window_key) or "0")

if count + cost <= capacity then
    local new_count = redis.call("INCRBY", window_key, cost)
    if new_count == cost then
        -- First write to this window key: set TTL to two windows so it expires naturally.
        redis.call("PEXPIRE", window_key, window_ms * 2)
    end
    local remaining = capacity - new_count
    if remaining < 0 then remaining = 0 end
    return { 1, remaining, 0 }
else
    local retry_ms  = window_end - now_ms
    local retry_sec = math.ceil(retry_ms / 1000)
    if retry_sec < 1 then retry_sec = 1 end
    return { 0, 0, retry_sec }
end
