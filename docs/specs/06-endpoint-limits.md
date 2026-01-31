# Endpoint-Specific Limits

## Requirements
- `/api/search`: 5 req/s
- `/api/users`: 20 req/s
- `/api/health`: unlimited

## Design
We use an explicit endpoint-aware interface and a dispatcher:
- `EndpointRateLimiter` exposes `(clientId, endpoint)` methods.
- `EndpointTieredRateLimiter` routes to a `TieredRateLimiter` per endpoint.

## Implementation
- `EndpointRateLimiter` interface
- `EndpointTieredRateLimiter`
- `UnlimitedRateLimiter` for `/api/health`

## Key-Namespacing
Endpoint-specific limiters are instantiated with unique namespaces to separate token buckets by endpoint.
