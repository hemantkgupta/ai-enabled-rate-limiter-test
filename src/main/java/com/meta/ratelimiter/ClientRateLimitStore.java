package com.meta.ratelimiter;

import java.util.HashMap;
import java.util.Map;

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

    // BUG: These maps are not thread-safe and cause race conditions
    private Map<String, TokenBucketState> tokenBucketStates = new HashMap<>();
    private Map<String, SlidingWindowState> slidingWindowStates = new HashMap<>();

    /**
     * Get or create token bucket state for a client
     */
    public TokenBucketState getOrCreateTokenBucketState(String clientId, double initialTokens) {
        TokenBucketState state = tokenBucketStates.get(clientId);
        if (state == null) {
            state = new TokenBucketState(initialTokens, System.currentTimeMillis());
            tokenBucketStates.put(clientId, state);
        }
        return state;
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
     */
    public SlidingWindowState getOrCreateSlidingWindowState(String clientId) {
        SlidingWindowState state = slidingWindowStates.get(clientId);
        if (state == null) {
            state = new SlidingWindowState();
            slidingWindowStates.put(clientId, state);
        }
        return state;
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
