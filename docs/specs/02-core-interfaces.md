# Core Interfaces

## RateLimiter
`src/main/java/com/meta/ratelimiter/RateLimiter.java`

Responsibilities:
- Decide if a request is allowed for a client.
- Track remaining requests and reset time.
- Expose configured limit for headers.

Methods:
```java
boolean allowRequest(String clientId);
int getRemainingRequests(String clientId);
int getLimit(String clientId);
void reset(String clientId);
long getResetTimeMillis(String clientId);
```

## EndpointRateLimiter
`src/main/java/com/meta/ratelimiter/EndpointRateLimiter.java`

Responsibilities:
- Same as `RateLimiter` but scoped to `(clientId, endpoint)`.

Methods:
```java
boolean allowRequest(String clientId, String endpoint);
int getRemainingRequests(String clientId, String endpoint);
int getLimit(String clientId, String endpoint);
void reset(String clientId, String endpoint);
long getResetTimeMillis(String clientId, String endpoint);
```

## Configuration
`RateLimitConfig` includes:
- `maxRequests`: steady-state refill rate (per window).
- `windowSizeMillis`: window size for refill rate.
- `burstCapacity`: maximum tokens allowed (burst size).
- `strategy`: token bucket/sliding/fixed window.

## Tiering
`ClientTier`, `ClientTierResolver`, `TieredRateLimitConfig`, `TieredRateLimiter` provide tier-based dispatch.
