# Bug Fixes Summary

## Overview
This document summarizes all bugs found and fixed in the rate limiter implementation.

---

## Bug #1: Integer Division Precision Loss in TokenBucketRateLimiter ✅ FIXED

**Location:** `TokenBucketRateLimiter.java` - `refillTokens()` method

**Original Code:**
```java
double refillRate = config.getMaxRequests() / config.getWindowSizeMillis();
```

**Issue:**
- Integer division (e.g., 10/1000 = 0) resulted in 0, causing tokens to never refill
- After initial tokens were exhausted, the rate limiter would permanently block all requests

**Fix:**
```java
double refillRate = (double) config.getMaxRequests() / config.getWindowSizeMillis();
```

**Impact:** Critical - Made token bucket algorithm completely non-functional after initial tokens were used

---

## Bug #2: Thread-Safety Issues ✅ FIXED

### 2a. Non-Thread-Safe HashMap in ClientRateLimitStore

**Location:** `ClientRateLimitStore.java`

**Original Code:**
```java
private Map<String, TokenBucketState> tokenBucketStates = new HashMap<>();
private Map<String, SlidingWindowState> slidingWindowStates = new HashMap<>();
```

**Issue:**
- `HashMap` is not thread-safe
- Concurrent access caused race conditions and potential `ConcurrentModificationException`

**Fix:**
```java
private Map<String, TokenBucketState> tokenBucketStates = new ConcurrentHashMap<>();
private Map<String, SlidingWindowState> slidingWindowStates = new ConcurrentHashMap<>();
```

### 2b. Check-Then-Act Race Condition

**Location:** `ClientRateLimitStore.java` - `getOrCreateTokenBucketState()` and `getOrCreateSlidingWindowState()`

**Original Code:**
```java
public TokenBucketState getOrCreateTokenBucketState(String clientId, double initialTokens) {
    TokenBucketState state = tokenBucketStates.get(clientId);
    if (state == null) {
        state = new TokenBucketState(initialTokens, System.currentTimeMillis());
        tokenBucketStates.put(clientId, state);
    }
    return state;
}
```

**Issue:**
- Check-then-act pattern creates race condition
- Multiple threads could create duplicate state objects

**Fix:**
```java
public TokenBucketState getOrCreateTokenBucketState(String clientId, double initialTokens) {
    return tokenBucketStates.computeIfAbsent(clientId, 
        k -> new TokenBucketState(initialTokens, System.currentTimeMillis()));
}
```

### 2c. Unsynchronized State Mutations

**Location:** `TokenBucketRateLimiter.java` - `allowRequest()`, `getRemainingRequests()`, `getResetTimeMillis()`

**Issue:**
- Multiple threads could read and modify `TokenBucketState` fields simultaneously
- Led to race conditions where more requests were allowed than the rate limit
- Token counts could be corrupted

**Fix:**
Added `synchronized` blocks around all state access and modification:
```java
@Override
public boolean allowRequest(String clientId) {
    ClientRateLimitStore.TokenBucketState state = 
        store.getOrCreateTokenBucketState(clientId, config.getMaxRequests());

    synchronized (state) {
        long currentTime = System.currentTimeMillis();
        refillTokens(state, currentTime);

        if (state.tokens >= 1.0) {
            state.tokens -= 1.0;
            return true;
        }
        return false;
    }
}
```

**Impact:** Critical - In concurrent environments, rate limits were not enforced correctly

---

## Bug #3: Memory Leak in SlidingWindowRateLimiter ✅ FIXED

**Location:** `SlidingWindowRateLimiter.java` - `allowRequest()`, `getRemainingRequests()`, `getResetTimeMillis()`

**Original Code:**
```java
@Override
public boolean allowRequest(String clientId) {
    ClientRateLimitStore.SlidingWindowState state = 
        store.getOrCreateSlidingWindowState(clientId);

    long currentTime = System.currentTimeMillis();
    long windowStart = currentTime - config.getWindowSizeMillis();

    // Count requests in current window
    int requestCount = 0;
    for (Long timestamp : state.requestTimestamps) {
        if (timestamp >= windowStart) {
            requestCount++;
        }
    }

    if (requestCount < config.getMaxRequests()) {
        state.requestTimestamps.add(currentTime);
        return true;
    }
    return false;
}
```

**Issue:**
- Old timestamps were counted but never removed from the list
- The `requestTimestamps` list grew unbounded over time
- Caused memory leak and degraded performance as list size increased

**Fix:**
```java
@Override
public boolean allowRequest(String clientId) {
    ClientRateLimitStore.SlidingWindowState state = 
        store.getOrCreateSlidingWindowState(clientId);

    synchronized (state) {
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - config.getWindowSizeMillis();

        // Remove old timestamps to prevent memory leak
        state.requestTimestamps.removeIf(timestamp -> timestamp < windowStart);
        
        // Count requests in current window
        int requestCount = state.requestTimestamps.size();

        if (requestCount < config.getMaxRequests()) {
            state.requestTimestamps.add(currentTime);
            return true;
        }
        return false;
    }
}
```

**Additional Fix:** Added synchronization to prevent concurrent modification issues

**Impact:** High - Memory usage would grow indefinitely, eventually causing OutOfMemoryError

---

## Test Results

All tests now pass successfully:

```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Concurrency Tests
- ✅ `testConcurrentRequests`: Verified rate limit is enforced under concurrent load (100 requests allowed out of 200 attempts)
- ✅ `testConcurrentMultipleClients`: Each client independently rate-limited (50 requests per client)
- ✅ `testHighConcurrencyStressTest`: 50 threads, 500 total requests, only 100 allowed

### Token Bucket Tests
- ✅ Basic rate limiting works correctly
- ✅ Token refill mechanism works properly
- ✅ Multiple clients are isolated

### Sliding Window Tests
- ✅ Basic rate limiting works correctly
- ✅ Memory leak is fixed (old timestamps are cleaned up)
- ✅ Multiple clients are isolated

---

## Summary

**Total Bugs Fixed:** 3 major bugs with multiple sub-issues

1. **Integer division bug** - Prevented token refill
2. **Thread-safety issues** - Multiple race conditions in concurrent access
3. **Memory leak** - Unbounded list growth in sliding window implementation

All bugs have been fixed and verified with comprehensive test coverage.
