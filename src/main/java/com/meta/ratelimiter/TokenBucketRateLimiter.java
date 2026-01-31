package com.meta.ratelimiter;

/**
 * Token Bucket Rate Limiter Implementation
 * 
 * Algorithm:
 * - Bucket holds tokens (max = rate limit)
 * - Each request consumes 1 token
 * - Tokens refill at a constant rate over time
 * - If no tokens available, request is denied
 * 
 * WARNING: This implementation has a bug in the refill logic!
 */
public class TokenBucketRateLimiter implements RateLimiter {
    private final RateLimitConfig config;
    private final ClientRateLimitStore store;

    public TokenBucketRateLimiter(RateLimitConfig config, ClientRateLimitStore store) {
        this.config = config;
        this.store = store;
    }

    @Override
    public boolean allowRequest(String clientId) {
        ClientRateLimitStore.TokenBucketState state = 
            store.getOrCreateTokenBucketState(clientId, config.getMaxRequests());

        long currentTime = System.currentTimeMillis();
        
        // Refill tokens based on time elapsed
        refillTokens(state, currentTime);

        // Check if we have tokens available
        if (state.tokens >= 1.0) {
            state.tokens -= 1.0;
            return true;
        }

        return false;
    }

    /**
     * Refill tokens based on elapsed time
     * 
     * BUG: The refill calculation has a subtle timing precision issue
     * that causes tokens to refill incorrectly in some edge cases
     */
    private void refillTokens(ClientRateLimitStore.TokenBucketState state, long currentTime) {
        long elapsedTime = currentTime - state.lastRefillTimestamp;
        
        // Calculate refill rate: tokens per millisecond
        // BUG: Integer division loses precision!
        // This should be: (double) config.getMaxRequests() / config.getWindowSizeMillis()
        double refillRate = config.getMaxRequests() / config.getWindowSizeMillis();
        
        double tokensToAdd = elapsedTime * refillRate;
        
        state.tokens = Math.min(config.getMaxRequests(), state.tokens + tokensToAdd);
        state.lastRefillTimestamp = currentTime;
    }

    @Override
    public int getRemainingRequests(String clientId) {
        ClientRateLimitStore.TokenBucketState state = 
            store.getOrCreateTokenBucketState(clientId, config.getMaxRequests());
        
        long currentTime = System.currentTimeMillis();
        refillTokens(state, currentTime);
        
        return (int) Math.floor(state.tokens);
    }

    @Override
    public void reset(String clientId) {
        store.reset(clientId);
    }

    @Override
    public long getResetTimeMillis(String clientId) {
        ClientRateLimitStore.TokenBucketState state = 
            store.getOrCreateTokenBucketState(clientId, config.getMaxRequests());
        
        if (state.tokens >= 1.0) {
            return 0; // Not rate limited
        }

        // Calculate time until 1 token is available
        double refillRate = (double) config.getMaxRequests() / config.getWindowSizeMillis();
        double tokensNeeded = 1.0 - state.tokens;
        long timeNeeded = (long) (tokensNeeded / refillRate);
        
        return timeNeeded;
    }
}
