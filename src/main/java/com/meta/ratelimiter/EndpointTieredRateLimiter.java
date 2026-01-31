package com.meta.ratelimiter;

import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint-aware, tiered rate limiter that dispatches per endpoint + tier.
 */
public class EndpointTieredRateLimiter implements EndpointRateLimiter {
    private final Map<String, TieredRateLimiter> endpointLimiters;
    private final TieredRateLimiter defaultLimiter;

    public EndpointTieredRateLimiter(
        Map<String, TieredRateLimiter> endpointLimiters,
        TieredRateLimiter defaultLimiter
    ) {
        this.endpointLimiters = new HashMap<>(endpointLimiters);
        this.defaultLimiter = defaultLimiter;
    }

    @Override
    public boolean allowRequest(String clientId, String endpoint) {
        return limiterFor(endpoint).allowRequest(clientId);
    }

    @Override
    public int getRemainingRequests(String clientId, String endpoint) {
        return limiterFor(endpoint).getRemainingRequests(clientId);
    }

    @Override
    public int getLimit(String clientId, String endpoint) {
        return limiterFor(endpoint).getLimit(clientId);
    }

    @Override
    public void reset(String clientId, String endpoint) {
        limiterFor(endpoint).reset(clientId);
    }

    @Override
    public long getResetTimeMillis(String clientId, String endpoint) {
        return limiterFor(endpoint).getResetTimeMillis(clientId);
    }

    private TieredRateLimiter limiterFor(String endpoint) {
        return endpointLimiters.getOrDefault(endpoint, defaultLimiter);
    }
}