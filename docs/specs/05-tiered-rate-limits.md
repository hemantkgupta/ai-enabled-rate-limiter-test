# Tiered Rate Limiting

## Concepts
- Clients are grouped into subscription tiers.
- Each tier has its own `RateLimitConfig`.

## Tier Model
Files:
- `ClientTier` enum
- `ClientTierResolver`
- `InMemoryClientTierResolver`
- `TieredRateLimitConfig`

## TieredRateLimiter
`src/main/java/com/meta/ratelimiter/TieredRateLimiter.java`

Behavior:
- Resolves client tier from `ClientTierResolver`.
- Delegates to tier-specific rate limiter.
- Exposes `getLimit` for headers.

## Default Tier Config
`TieredRateLimitConfig.defaultPerSecond()`:
- FREE: 10 req/s
- PREMIUM: 100 req/s
- ENTERPRISE: 1000 req/s
