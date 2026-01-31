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

        synchronized (state) {
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - config.getWindowSizeMillis();

            // Remove old timestamps to prevent memory leak
            state.requestTimestamps.removeIf(timestamp -> timestamp < windowStart);
            
            // Count requests in current window
            int requestCount = state.requestTimestamps.size();

            // Check if we're under the limit
            if (requestCount < config.getMaxRequests()) {
                state.requestTimestamps.add(currentTime);
                return true;
            }

            return false;
        }
    }

    @Override
    public int getRemainingRequests(String clientId) {
        ClientRateLimitStore.SlidingWindowState state = 
            store.getOrCreateSlidingWindowState(clientId);

        synchronized (state) {
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - config.getWindowSizeMillis();

            // Remove old timestamps
            state.requestTimestamps.removeIf(timestamp -> timestamp < windowStart);
            
            // Count requests in current window
            int requestCount = state.requestTimestamps.size();

            return Math.max(0, config.getMaxRequests() - requestCount);
        }
    }

    @Override
    public void reset(String clientId) {
        store.reset(clientId);
    }

    @Override
    public long getResetTimeMillis(String clientId) {
        ClientRateLimitStore.SlidingWindowState state = 
            store.getOrCreateSlidingWindowState(clientId);

        synchronized (state) {
            if (state.requestTimestamps.isEmpty()) {
                return 0;
            }

            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - config.getWindowSizeMillis();

            // Remove old timestamps
            state.requestTimestamps.removeIf(timestamp -> timestamp < windowStart);
            
            if (state.requestTimestamps.isEmpty()) {
                return 0;
            }

            // Find oldest request in current window (list is already filtered)
            Long oldestInWindow = state.requestTimestamps.get(0);
            for (Long timestamp : state.requestTimestamps) {
                if (timestamp < oldestInWindow) {
                    oldestInWindow = timestamp;
                }
            }

            // Time until oldest request expires from window
            long resetTime = oldestInWindow + config.getWindowSizeMillis() - currentTime;
            return Math.max(0, resetTime);
        }
    }
}
