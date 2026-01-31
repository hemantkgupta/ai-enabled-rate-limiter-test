# Distributed Store & In-Memory Simulation

## DistributedRateLimitStore
`src/main/java/com/meta/ratelimiter/DistributedRateLimitStore.java`

Capabilities:
- `get`, `set` with TTL.
- `compute` for atomic updates (used by distributed limiters).
- `remove`, `clear` for cleanup.

## InMemoryDistributedRateLimitStore
`src/main/java/com/meta/ratelimiter/InMemoryDistributedRateLimitStore.java`

Behavior:
- Stores entries in `ConcurrentHashMap`.
- Enforces TTL on read/compute.
- Uses `compute` to guarantee atomic update per key.

## Consistency Model
- **Eventually consistent**: simulated in-memory, but intended for Redis-like shared store.
- **Atomic per key**: `compute` ensures safe concurrent updates for a key.
