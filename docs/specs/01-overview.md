# Overview

## Goal
Provide a production-style rate limiting service that supports multiple algorithms, distributed state, tiered limits, endpoint-specific limits, rate-limit headers, burst handling, and performance validation.

## Deliverables
- Core rate limiter interfaces and strategy implementations.
- Distributed store abstraction + in-memory simulation.
- Tiered and endpoint-aware rate limiting.
- HTTP header support for rate-limit metadata.
- Burst allowance for token bucket.
- Comprehensive unit and performance tests.

## Architecture (High-Level)
1. **API Layer**: `RateLimiterApp` exposes `/api/check-limit` and auxiliary endpoints.
2. **Rate Limiter Core**: `RateLimiter` and `EndpointRateLimiter` interfaces.
3. **Strategies**: Token bucket, sliding window, fixed window (local + distributed).
4. **State Storage**: `ClientRateLimitStore` (local) and `DistributedRateLimitStore` (shared).
5. **Cross-Cutting Features**:
   - Tiering (`TieredRateLimiter`)
   - Endpoint limits (`EndpointTieredRateLimiter`)
   - Headers (`X-RateLimit-*`)
   - Burst capacity

## Key Files
- `src/main/java/com/meta/ratelimiter/RateLimiterApp.java`
- `src/main/java/com/meta/ratelimiter/RateLimiter.java`
- `src/main/java/com/meta/ratelimiter/EndpointRateLimiter.java`
- `src/main/java/com/meta/ratelimiter/RateLimitConfig.java`
