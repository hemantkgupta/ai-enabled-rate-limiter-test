# Burst Allowance

## Goal
Allow temporary bursts above steady-state rate (e.g., 10 req/s with burst 20).

## Configuration
`RateLimitConfig` now includes:
- `maxRequests`: steady refill rate.
- `burstCapacity`: maximum tokens stored.

## Behavior
- Tokens refill at `maxRequests / windowSizeMillis`.
- Bucket capacity is `burstCapacity`.
- Requests are allowed while tokens >= 1.

## Implementation
- `TokenBucketRateLimiter`
- `DistributedTokenBucketRateLimiter`
