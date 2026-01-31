# Algorithms & Strategies

## Token Bucket
- **State:** `tokens`, `lastRefillTimestamp`.
- **Refill rate:** `maxRequests / windowSizeMillis` (tokens per ms).
- **Burst:** `burstCapacity` caps tokens.
- **Allow:** if `tokens >= 1`, decrement and allow.

Implemented in:
- `TokenBucketRateLimiter`
- `DistributedTokenBucketRateLimiter`

## Sliding Window
- **State:** list of request timestamps.
- **Allow:** purge timestamps < `now - window`, check size < limit, append timestamp.

Implemented in:
- `SlidingWindowRateLimiter`
- `DistributedSlidingWindowRateLimiter`

## Fixed Window
- **State:** count, windowStartMillis.
- **Allow:** reset window when expired, increment if under limit.

Implemented in:
- `FixedWindowRateLimiter`
- `DistributedFixedWindowRateLimiter`

## Strategy Selection
`RateLimitStrategy` defines: `TOKEN_BUCKET`, `SLIDING_WINDOW`, `FIXED_WINDOW`.
