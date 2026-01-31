package com.meta.ratelimiter;

/**
 * Rate limiter that never limits requests.
 */
public class UnlimitedRateLimiter implements RateLimiter {
    @Override
    public boolean allowRequest(String clientId) {
        return true;
    }

    @Override
    public int getRemainingRequests(String clientId) {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getLimit(String clientId) {
        return Integer.MAX_VALUE;
    }

    @Override
    public void reset(String clientId) {
        // no-op
    }

    @Override
    public long getResetTimeMillis(String clientId) {
        return 0;
    }
}