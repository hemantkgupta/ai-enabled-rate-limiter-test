package com.meta.ratelimiter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage for client rate limit state
 * 
 * WARNING: This implementation has concurrency issues!
 * Find and fix them.
 */
public class ClientRateLimitStore {
    
    // Storage for token bucket state
    public static class TokenBucketState {
        public double tokens;
        public long lastRefillTimestamp;

        public TokenBucketState(double tokens, long lastRefillTimestamp) {
            this.tokens = tokens;
            this.lastRefillTimestamp = lastRefillTimestamp;
        }
    }

    // Storage for sliding window state
    public static class SlidingWindowState {
        public java.util.List<Long> requestTimestamps;

        public SlidingWindowState() {
            this.requestTimestamps = new java.util.ArrayList<>();
        }
    }

    private Map<String, TokenBucketState> tokenBucketStates = new ConcurrentHashMap<>();
    private Map<String, SlidingWindowState> slidingWindowStates = new ConcurrentHashMap<>();

    /**
     * Get or create token bucket state for a client
     * Uses putIfAbsent to avoid race condition
     */
    public TokenBucketState getOrCreateTokenBucketState(String clientId, double initialTokens) {
        return tokenBucketStates.computeIfAbsent(clientId, 
            k -> new TokenBucketState(initialTokens, System.currentTimeMillis()));
    }

    /**
     * Update token bucket state for a client
     */
    public void updateTokenBucketState(String clientId, double tokens, long timestamp) {
        TokenBucketState state = tokenBucketStates.get(clientId);
        if (state != null) {
            state.tokens = tokens;
            state.lastRefillTimestamp = timestamp;
        }
    }

    /**
     * Get or create sliding window state for a client
     * Uses computeIfAbsent to avoid race condition
     */
    public SlidingWindowState getOrCreateSlidingWindowState(String clientId) {
        return slidingWindowStates.computeIfAbsent(clientId, k -> new SlidingWindowState());
    }

    /**
     * Add a request timestamp to sliding window
     */
    public void addRequestTimestamp(String clientId, long timestamp) {
        SlidingWindowState state = getOrCreateSlidingWindowState(clientId);
        state.requestTimestamps.add(timestamp);
    }

    /**
     * Reset state for a specific client
     */
    public void reset(String clientId) {
        tokenBucketStates.remove(clientId);
        slidingWindowStates.remove(clientId);
    }

    /**
     * Clear all state (useful for testing)
     */
    public void clearAll() {
        tokenBucketStates.clear();
        slidingWindowStates.clear();
    }
}
