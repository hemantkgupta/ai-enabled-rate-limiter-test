package com.meta.ratelimiter;

/**
 * Core interface for rate limiting implementations
 */
public interface RateLimiter {
    /**
     * Check if a request should be allowed based on rate limits
     * 
     * @param clientId Unique identifier for the client making the request
     * @return true if request is allowed, false if rate limited
     */
    boolean allowRequest(String clientId);

    /**
     * Get the number of remaining requests allowed for a client
     * 
     * @param clientId Unique identifier for the client
     * @return Number of requests remaining in current window
     */
    int getRemainingRequests(String clientId);

    /**
     * Reset rate limit state for a specific client
     * Useful for testing or manual overrides
     * 
     * @param clientId Unique identifier for the client
     */
    void reset(String clientId);

    /**
     * Get time in milliseconds until the rate limit resets
     * 
     * @param clientId Unique identifier for the client
     * @return Milliseconds until reset, or 0 if not rate limited
     */
    long getResetTimeMillis(String clientId);
}
