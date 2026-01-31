# Rate Limit Headers

## Headers
- `X-RateLimit-Limit`: configured maximum requests for the client/endpoint.
- `X-RateLimit-Remaining`: requests remaining in the current window.
- `X-RateLimit-Reset`: Unix timestamp (seconds) when limit resets.

## Reset Timestamp Calculation
`getResetTimeMillis()` returns milliseconds **until reset**.

Conversion:
```
long currentSeconds = System.currentTimeMillis() / 1000;
long resetAfterSeconds = (long) Math.ceil(resetAfterMillis / 1000.0);
long resetUnixSeconds = currentSeconds + Math.max(0, resetAfterSeconds);
```

## Implementation
`RateLimiterApp.checkRateLimit()` applies headers using endpoint-aware limiter:
- `limit = endpointRateLimiter.getLimit(clientId, endpoint)`
- `remaining = endpointRateLimiter.getRemainingRequests(...)`
- `resetAfterMs = endpointRateLimiter.getResetTimeMillis(...)`
