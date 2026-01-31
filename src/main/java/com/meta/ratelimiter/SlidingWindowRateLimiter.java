package com.meta.ratelimiter;

import java.util.List;

/**
 * Sliding Window Rate Limiter Implementation
 * 
 * Algorithm:
 * - Tracks timestamps of all requests in a sliding time window
 * - When new request comes, count requests in last N milliseconds
 * - If count < limit, allow request and add timestamp
 * - Otherwise, deny request
 * 
 * WARNING: This implementation has a memory leak!
 * Old timestamps are never cleaned up.
 */
public class SlidingWindowRateLimiter implements RateLimiter {
    private final RateLimitConfig config;
    private final ClientRateLimitStore store;

    public SlidingWindowRateLimiter(RateLimitConfig config, ClientRateLimitStore store) {
        this.config = config;
        this.store = store;
    }

    @Override
    public boolean allowRequest(String clientId) {
        ClientRateLimitStore.SlidingWindowState state = 
            store.getOrCreateSlidingWindowState(clientId);

        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - config.getWindowSizeMillis();

        // BUG: We count old timestamps but never remove them!
        // This causes the list to grow unbounded, leading to memory leak
        // and slower performance over time
        
        // Count requests in current window
        int requestCount = 0;
        for (Long timestamp : state.requestTimestamps) {
            if (timestamp >= windowStart) {
                requestCount++;
            }
        }

        // Check if we're under the limit
        if (requestCount < config.getMaxRequests()) {
            state.requestTimestamps.add(currentTime);
            return true;
        }

        return false;
    }

    @Override
    public int getRemainingRequests(String clientId) {
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

        return Math.max(0, config.getMaxRequests() - requestCount);
    }

    @Override
    public void reset(String clientId) {
        store.reset(clientId);
    }

    @Override
    public long getResetTimeMillis(String clientId) {
        ClientRateLimitStore.SlidingWindowState state = 
            store.getOrCreateSlidingWindowState(clientId);

        if (state.requestTimestamps.isEmpty()) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - config.getWindowSizeMillis();

        // Find oldest request in current window
        Long oldestInWindow = null;
        for (Long timestamp : state.requestTimestamps) {
            if (timestamp >= windowStart) {
                if (oldestInWindow == null || timestamp < oldestInWindow) {
                    oldestInWindow = timestamp;
                }
            }
        }

        if (oldestInWindow == null) {
            return 0;
        }

        // Time until oldest request expires from window
        long resetTime = oldestInWindow + config.getWindowSizeMillis() - currentTime;
        return Math.max(0, resetTime);
    }
}
