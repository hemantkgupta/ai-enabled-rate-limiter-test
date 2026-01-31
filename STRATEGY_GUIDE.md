# Rate Limiter Challenge - Strategy Guide

## Quick Reference: Using Claude Effectively

### Phase 1: Bug Discovery (First 15-20 minutes)

#### 1. Understanding the Token Bucket Bug

**Prompt to Claude:**
```
I have a Token Bucket rate limiter implementation. Here's the refill logic:

[Paste the refillTokens method from TokenBucketRateLimiter.java]

The configuration is:
- maxRequests: 10
- windowSizeMillis: 1000 (1 second)

So the refill rate should be 10 tokens per 1000ms = 0.01 tokens per millisecond.

The test expects that after using all 10 tokens and waiting 100ms, 
I should have 1 token available (since 100ms * 0.01 = 1 token).

But the test fails. What's the bug in the refill calculation?
```

**Expected Finding:** Integer division `config.getMaxRequests() / config.getWindowSizeMillis()` results in `10 / 1000 = 0` (not 0.01).

**Fix:** Cast to double: `(double) config.getMaxRequests() / config.getWindowSizeMillis()`

---

#### 2. Understanding the Memory Leak Bug

**Prompt to Claude:**
```
I have a sliding window rate limiter that tracks request timestamps.

[Paste the allowRequest method from SlidingWindowRateLimiter.java]

The problem: after running for a while with many requests, the 
requestTimestamps list grows very large and never shrinks.

What's causing the memory leak and how should I fix it?
```

**Expected Finding:** Old timestamps outside the window are counted but never removed from the list.

**Fix:** Remove expired timestamps before or after checking:
```java
// Remove timestamps outside current window
state.requestTimestamps.removeIf(timestamp -> timestamp < windowStart);
```

---

#### 3. Understanding the Concurrency Bug

**Prompt to Claude:**
```
I have a ClientRateLimitStore that stores state for multiple clients:

[Paste ClientRateLimitStore.java]

When running concurrent tests with multiple threads accessing the same 
client's rate limit state, I'm seeing race conditions where:
- More than the allowed number of requests are permitted
- Requests sometimes fail unexpectedly
- State gets corrupted

What's the concurrency issue and how do I fix it?
```

**Expected Finding:** HashMap is not thread-safe, and the state objects themselves are mutated without synchronization.

**Fixes (multiple options to discuss):**
1. Use `ConcurrentHashMap` instead of `HashMap`
2. Add synchronization around state access
3. Use atomic operations or locks
4. Make state immutable and use CAS operations

---

### Phase 2: Feature Implementation (20-25 minutes)

Choose features based on interview flow. Here are prompts for each:

#### Feature 1: Distributed Rate Limiting

**Prompt to Claude:**
```
I need to extend my rate limiter to work in a distributed environment 
with multiple servers. Currently, I use in-memory storage which won't 
work when scaled horizontally.

Requirements:
- Multiple servers should share rate limit state
- Must be eventually consistent
- Low latency (can't block on network calls)

For this interview, I'll simulate Redis with a simple interface.

Help me design:
1. What interface should I create for the distributed store?
2. How should I handle network failures?
3. What's the trade-off between accuracy and performance?
```

**Implementation Approach:**
```java
// Create interface
public interface DistributedStore {
    Long increment(String key, long amount, long ttlMillis);
    void set(String key, long value, long ttlMillis);
    Long get(String key);
}

// Implement with graceful degradation
```

---

#### Feature 2: Tiered Rate Limits

**Prompt to Claude:**
```
I need to support different rate limits based on client subscription tier:
- FREE: 10 requests/second
- PREMIUM: 100 requests/second
- ENTERPRISE: 1000 requests/second

Current design uses a single RateLimitConfig for all clients.

How should I redesign this to support per-client tiers?
Options I'm considering:
1. Pass tier with each request
2. Store tier mapping separately
3. Create different RateLimiter instances per tier

Which is best for an interview setting? Show me the code structure.
```

---

#### Feature 3: Endpoint-Specific Limits

**Prompt to Claude:**
```
I need different rate limits for different API endpoints:
- /api/search: 5 requests/second (expensive operation)
- /api/users: 20 requests/second
- /api/health: unlimited

Currently allowRequest(clientId) doesn't consider the endpoint.

How should I extend the interface? Should it be:
- allowRequest(clientId, endpoint)
- Separate RateLimiter instance per endpoint
- Composite key: clientId + endpoint

What are trade-offs? Show me the cleanest design.
```

---

#### Feature 4: Rate Limit Headers

**Prompt to Claude:**
```
I need to add standard rate limit headers to HTTP responses:
- X-RateLimit-Limit: maximum requests allowed
- X-RateLimit-Remaining: requests remaining in window
- X-RateLimit-Reset: Unix timestamp when limit resets

My RateLimiter interface currently has:
- getRemainingRequests(clientId) -> int
- getResetTimeMillis(clientId) -> long

How do I convert getResetTimeMillis() (milliseconds until reset) to 
a Unix timestamp (seconds since epoch)?

Show me the code to add these headers in RateLimiterApp.
```

---

#### Feature 5: Burst Allowance

**Prompt to Claude:**
```
I want to allow temporary bursts above the normal rate limit:
- Normal limit: 10 requests/second
- Burst allowance: Can spike to 20 requests, but average must stay at 10

This is like token bucket with a larger bucket than refill rate.

How do I modify TokenBucketRateLimiter to support burst?
Should I:
1. Add a burstCapacity parameter separate from maxRequests?
2. Use maxRequests as burst and add a refillRate parameter?

Show me the cleanest approach.
```

---

### Phase 3: Testing & Validation (10-15 minutes)

#### Testing New Features

**Prompt to Claude:**
```
I just implemented [feature name]. Help me write a comprehensive test.

The feature does: [describe]

What edge cases should I test?
- Happy path
- Boundary conditions
- Error cases
- Concurrent access (if applicable)

Show me a test structure with 3-4 key test cases.
```

---

#### Performance Testing

**Prompt to Claude:**
```
I need to validate my rate limiter's performance:

Requirements:
- Handle 10,000 requests/second
- Latency < 1ms per check
- Support 1,000 concurrent clients

How should I write a performance test?
What metrics should I measure?
What would indicate a performance problem?
```

---

## Bug Fixes - Detailed Solutions

### Bug #1: Token Bucket Refill (Integer Division)

**Location:** `TokenBucketRateLimiter.java`, line ~44

**Current Code:**
```java
double refillRate = config.getMaxRequests() / config.getWindowSizeMillis();
```

**Problem:** 
- `10 / 1000 = 0` (integer division)
- All tokens refill immediately or not at all

**Fix:**
```java
double refillRate = (double) config.getMaxRequests() / config.getWindowSizeMillis();
```

**Verification:**
```java
// With fix: 10.0 / 1000 = 0.01 tokens per millisecond
// After 100ms: 100 * 0.01 = 1 token (correct!)
```

---

### Bug #2: Sliding Window Memory Leak

**Location:** `SlidingWindowRateLimiter.java`, `allowRequest()` method

**Problem:** 
- Timestamps accumulate forever
- List grows to millions of entries
- Performance degrades over time

**Fix - Option 1 (Add cleanup):**
```java
@Override
public boolean allowRequest(String clientId) {
    ClientRateLimitStore.SlidingWindowState state = 
        store.getOrCreateSlidingWindowState(clientId);

    long currentTime = System.currentTimeMillis();
    long windowStart = currentTime - config.getWindowSizeMillis();

    // FIX: Remove expired timestamps
    state.requestTimestamps.removeIf(timestamp -> timestamp < windowStart);

    // Count requests in current window
    int requestCount = state.requestTimestamps.size(); // Can use size now!

    // Check if we're under the limit
    if (requestCount < config.getMaxRequests()) {
        state.requestTimestamps.add(currentTime);
        return true;
    }

    return false;
}
```

**Fix - Option 2 (Use a better data structure):**
```java
// Use a TreeSet or Deque for efficient removal
// In SlidingWindowState:
public Deque<Long> requestTimestamps = new ArrayDeque<>();

// In allowRequest:
while (!state.requestTimestamps.isEmpty() && 
       state.requestTimestamps.peekFirst() < windowStart) {
    state.requestTimestamps.pollFirst();
}
```

---

### Bug #3: Concurrency Issues

**Location:** `ClientRateLimitStore.java`

**Problem:**
- `HashMap` not thread-safe
- Multiple threads can create duplicate state
- State mutations not atomic

**Fix - Option 1 (ConcurrentHashMap + synchronized):**
```java
private ConcurrentHashMap<String, TokenBucketState> tokenBucketStates = new ConcurrentHashMap<>();
private ConcurrentHashMap<String, SlidingWindowState> slidingWindowStates = new ConcurrentHashMap<>();

public synchronized TokenBucketState getOrCreateTokenBucketState(String clientId, double initialTokens) {
    return tokenBucketStates.computeIfAbsent(clientId, 
        k -> new TokenBucketState(initialTokens, System.currentTimeMillis()));
}
```

**Fix - Option 2 (Finer-grained locking):**
```java
private final ConcurrentHashMap<String, TokenBucketState> tokenBucketStates = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

public TokenBucketState getOrCreateTokenBucketState(String clientId, double initialTokens) {
    Object lock = locks.computeIfAbsent(clientId, k -> new Object());
    synchronized (lock) {
        return tokenBucketStates.computeIfAbsent(clientId,
            k -> new TokenBucketState(initialTokens, System.currentTimeMillis()));
    }
}
```

**Fix - Option 3 (Immutable state with AtomicReference):**
```java
// Make state immutable
public static class TokenBucketState {
    public final double tokens;
    public final long lastRefillTimestamp;
    // Constructor, no setters
}

// Use AtomicReference for updates
private ConcurrentHashMap<String, AtomicReference<TokenBucketState>> tokenBucketStates = new ConcurrentHashMap<>();

public void updateTokenBucketState(String clientId, double tokens, long timestamp) {
    tokenBucketStates.computeIfAbsent(clientId, 
        k -> new AtomicReference<>(new TokenBucketState(0, timestamp)))
        .set(new TokenBucketState(tokens, timestamp));
}
```

---

## Discussion Points - E7 Level

### 1. Algorithm Comparison

**Interviewer: "Why did you choose token bucket over sliding window?"**

**Strong Answer:**
"Great question. Let me compare the trade-offs:

**Token Bucket:**
- Pros: Handles bursts gracefully, constant memory (just a counter)
- Cons: Less accurate at window boundaries, approximate enforcement
- Best for: APIs where occasional bursts are acceptable

**Sliding Window:**
- Pros: Precise tracking, no burst allowance unless designed in
- Cons: O(N) memory where N = requests in window, cleanup overhead
- Best for: Strict rate limiting, audit requirements

**Fixed Window:**
- Pros: Simplest implementation, constant memory and time
- Cons: Boundary issues (spike at window edge), less fair
- Best for: Simple use cases, low precision requirements

For this system, I chose token bucket because:
1. Memory efficient - critical for millions of clients
2. Handles legitimate bursts (user refreshing page)
3. Simple to make thread-safe

However, if audit compliance required exact counting, I'd use sliding window with optimizations like circular buffer."

---

### 2. Distributed Systems Challenges

**Interviewer: "How would you scale this across multiple servers?"**

**Strong Answer:**
"Distributed rate limiting faces the CAP theorem trade-off:

**Option 1: Centralized Store (Redis)**
```
Client -> Server 1 -> Redis -> Increment counter
Client -> Server 2 -> Redis -> Increment counter
```
Pros: Accurate, consistent
Cons: Redis is SPOF, network latency, Redis load

**Option 2: Eventually Consistent**
```
Each server maintains local counters
Periodic sync to share state
Allow some over-limit requests during sync lag
```
Pros: Low latency, resilient to network issues
Cons: Can exceed limit by N × (limit per server)

**Option 3: Sticky Sessions**
```
Route client to same server via load balancer
Each server enforces limit for its clients
```
Pros: Simple, no coordination needed
Cons: Uneven load, failover issues

**My Production Recommendation:**
Use Redis with local caching:
1. Check local cache first (fast path)
2. Increment Redis counter (accurate enforcement)
3. Cache result for 100ms
4. On Redis failure, fall back to local enforcement (degraded mode)

This gives 99.9% accuracy with <1ms latency."

---

### 3. Trade-offs Discussion

**Interviewer: "What are the memory vs accuracy trade-offs?"**

**Strong Answer:**
"Let me break this down with actual numbers:

**Memory:**
- Token Bucket: 16 bytes per client (2 longs)
- Sliding Window: 8 bytes × requests in window
  - At 100 req/sec, 1 sec window = 800 bytes per client
  - At 1M clients = 800 MB (manageable)
  - But at 1000 req/sec = 8 GB (problematic)

**Accuracy:**
- Token Bucket: ~95% accurate due to token quantization
- Sliding Window: 100% accurate
- Fixed Window: ~80% accurate at boundaries

**Latency:**
- Token Bucket: O(1) - simple arithmetic
- Sliding Window: O(N) - must scan timestamps
- Fixed Window: O(1) - counter increment

**Production Choice:**
For typical API gateway:
- Use token bucket for most endpoints (memory efficient)
- Use sliding window for sensitive endpoints (billing, auth)
- Monitor actual vs expected rate to tune

The key insight: Perfect accuracy usually isn't needed. Token bucket's 95% accuracy is fine for DDoS protection."

---

### 4. Failure Modes & Resilience

**Interviewer: "What happens when the rate limiter fails?"**

**Strong Answer:**
"Failure modes to consider:

**1. Storage Failure (Redis down):**
```java
public boolean allowRequest(String clientId) {
    try {
        return redis.checkAndIncrement(clientId);
    } catch (RedisException e) {
        metrics.recordFailure("redis_down");
        // Option A: Fail open (allow all) - risky
        // Option B: Fail closed (deny all) - impacts users
        // Option C: Fall back to local limiting
        return localRateLimiter.allowRequest(clientId);
    }
}
```

**2. High Latency:**
- Set timeout on Redis calls (50ms max)
- If timeout, use cached state
- Async updates to reduce blocking

**3. Clock Skew:**
- Token bucket sensitive to time
- Use monotonic clocks (System.nanoTime())
- Or logical counters instead of timestamps

**Production Strategy:**
1. Circuit breaker pattern for Redis
2. Local cache with TTL for fast path
3. Metrics to detect failures (latency, error rate)
4. Graceful degradation: local → allow with warning → deny

The E7 insight: Design for partial failures, not perfect operation."

---

### 5. Monitoring & Observability

**Interviewer: "What metrics would you track?"**

**Strong Answer:**
"I'd track these metrics with P50/P95/P99 percentiles:

**Rate Limiting Metrics:**
```
rate_limit_requests_total{client, endpoint, result=[allowed|denied]}
rate_limit_utilization{client, endpoint} # % of limit used
rate_limit_exceeded_total{client, endpoint}
```

**Performance Metrics:**
```
rate_limit_check_duration_ms # Latency
rate_limit_storage_errors_total
rate_limit_cache_hit_ratio
```

**Business Metrics:**
```
top_rate_limited_clients # Who's hitting limits?
rate_limit_by_endpoint # Which APIs are constrained?
burst_violations # Clients spiking above average
```

**Alerting:**
- Alert if error rate > 1% (storage issues)
- Alert if P99 latency > 10ms (performance)
- Alert if >5% of requests denied (aggressive limiting or attack)

**Dashboards:**
1. Real-time: Requests/sec, allowed/denied ratio
2. Client view: Top clients by usage
3. Endpoint view: Utilization by API path

The key is: Metrics inform both scaling decisions and client support."

---

## Common Pitfalls to Avoid

### 1. Over-Engineering
❌ "I'll implement a distributed consensus algorithm for exact counting"
✅ "I'll use Redis with optimistic caching for good-enough accuracy"

### 2. Ignoring Edge Cases
❌ "This works for typical load"
✅ "Let me test with: zero requests, exactly at limit, burst scenarios"

### 3. Poor Concurrency Handling
❌ "I'll add synchronized to everything"
✅ "I'll use appropriate concurrent data structures and minimize lock scope"

### 4. Not Testing
❌ "The code looks right"
✅ "Let me write a test that fails first, then fix it"

### 5. Unclear Explanations
❌ "This uses a token bucket"
✅ "Token bucket allows bursts while maintaining average rate, which is better for user experience than strict limiting"

---

## Time Management Checklist

**Minutes 0-5:**
- [ ] Read all code files
- [ ] Understand rate limiter algorithms
- [ ] Run existing tests to see baseline

**Minutes 5-15:**
- [ ] Uncomment failing tests
- [ ] Find token bucket refill bug
- [ ] Find sliding window memory leak
- [ ] Run concurrency tests

**Minutes 15-20:**
- [ ] Fix all bugs
- [ ] Verify all tests pass
- [ ] Commit/save working code

**Minutes 20-40:**
- [ ] Choose 2-3 features to implement
- [ ] Implement with tests
- [ ] Ensure clean, documented code

**Minutes 40-50:**
- [ ] Run full test suite
- [ ] Test edge cases
- [ ] Verify no regressions

**Minutes 50-60:**
- [ ] Prepare trade-off discussion
- [ ] Review metrics/monitoring approach
- [ ] Be ready for scaling questions

---

## Success Criteria

**Minimum (Passing):**
- Fix 2+ bugs correctly
- All original tests pass
- Implement 1 feature that works
- Can explain algorithm basics

**Strong (Good Performance):**
- Fix all bugs
- Implement 2-3 features with tests
- Handle concurrency correctly
- Discuss trade-offs clearly
- Production considerations

**Exceptional:**
- All of above
- Elegant, extensible design
- Performance optimizations
- Deep distributed systems discussion
- Multiple solution approaches compared

Remember: Meta values systematic thinking and communication as much as coding ability!
