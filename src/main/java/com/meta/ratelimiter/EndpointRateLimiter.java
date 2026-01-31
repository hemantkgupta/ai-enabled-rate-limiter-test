package com.meta.ratelimiter;

/**
 * Endpoint-aware rate limiter interface.
 */
public interface EndpointRateLimiter {
    boolean allowRequest(String clientId, String endpoint);

    int getRemainingRequests(String clientId, String endpoint);

    void reset(String clientId, String endpoint);

    long getResetTimeMillis(String clientId, String endpoint);
}