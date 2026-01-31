# Testing Matrix

## Core Strategy Tests
- `TokenBucketTest`
- `SlidingWindowTest`
- `FixedWindowRateLimiter` tests embedded in distributed tests

## Distributed Strategy Tests
- `DistributedTokenBucketTest`
- `DistributedSlidingWindowTest`
- `DistributedFixedWindowTest`

## Tiering
- `TieredRateLimiterTest`

## Endpoint Limits
- `EndpointRateLimiterTest`

## Headers
- `RateLimiterHeaderTest`

## Burst Allowance
- `TokenBucketBurstTest`
