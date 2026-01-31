package com.meta.ratelimiter;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tier-aware rate limiter that dispatches to per-tier distributed limiters.
 */
public class TieredRateLimiter implements RateLimiter {
    private final ClientTierResolver tierResolver;
    private final Map<ClientTier, RateLimiter> limiters;

    public TieredRateLimiter(ClientTierResolver tierResolver, Map<ClientTier, RateLimiter> limiters) {
        this.tierResolver = tierResolver;
        this.limiters = new EnumMap<>(limiters);
    }

    @Override
    public boolean allowRequest(String clientId) {
        return limiterFor(clientId).allowRequest(clientId);
    }

    @Override
    public int getRemainingRequests(String clientId) {
        return limiterFor(clientId).getRemainingRequests(clientId);
    }

    @Override
    public void reset(String clientId) {
        limiterFor(clientId).reset(clientId);
    }

    @Override
    public long getResetTimeMillis(String clientId) {
        return limiterFor(clientId).getResetTimeMillis(clientId);
    }

    private RateLimiter limiterFor(String clientId) {
        ClientTier tier = tierResolver.resolveTier(clientId);
        RateLimiter limiter = limiters.get(tier);
        if (limiter == null) {
            throw new IllegalStateException("No rate limiter configured for tier " + tier);
        }
        return limiter;
    }
}