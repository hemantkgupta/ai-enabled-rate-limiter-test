package com.meta.ratelimiter;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Token Bucket Rate Limiter backed by a distributed store.
 * Uses atomic compute operations on the store to update token state.
 */
public class DistributedTokenBucketRateLimiter implements RateLimiter {
    private static final String KEY_PREFIX = "token-bucket:";
    private final String keyNamespace;

    private static class TokenBucketSnapshot {
        private double tokens;
        private long lastRefillTimestamp;
        private boolean lastRequestAllowed;

        private TokenBucketSnapshot(double tokens, long lastRefillTimestamp, boolean lastRequestAllowed) {
            this.tokens = tokens;
            this.lastRefillTimestamp = lastRefillTimestamp;
            this.lastRequestAllowed = lastRequestAllowed;
        }
    }

    private final RateLimitConfig config;
    private final DistributedRateLimitStore store;
    private final RateLimiter fallbackLimiter;

    public DistributedTokenBucketRateLimiter(
        RateLimitConfig config,
        DistributedRateLimitStore store,
        RateLimiter fallbackLimiter
    ) {
        this.config = config;
        this.store = store;
        this.fallbackLimiter = fallbackLimiter;
        this.keyNamespace = KEY_PREFIX;
    }

    public DistributedTokenBucketRateLimiter(
        RateLimitConfig config,
        DistributedRateLimitStore store,
        RateLimiter fallbackLimiter,
        String keyNamespace
    ) {
        this.config = config;
        this.store = store;
        this.fallbackLimiter = fallbackLimiter;
        this.keyNamespace = keyNamespace;
    }

    @Override
    public boolean allowRequest(String clientId) {
        try {
            TokenBucketSnapshot snapshot = updateState(clientId, state -> {
                refillTokens(state, System.currentTimeMillis());
                if (state.tokens >= 1.0) {
                    state.tokens -= 1.0;
                    state.lastRequestAllowed = true;
                } else {
                    state.lastRequestAllowed = false;
                }
                return state;
            });

            return snapshot.lastRequestAllowed;
        } catch (Exception ex) {
            return fallbackLimiter.allowRequest(clientId);
        }
    }

    @Override
    public int getRemainingRequests(String clientId) {
        try {
            TokenBucketSnapshot snapshot = updateState(clientId, state -> {
                refillTokens(state, System.currentTimeMillis());
                state.lastRequestAllowed = false;
                return state;
            });
            return (int) Math.floor(snapshot.tokens);
        } catch (Exception ex) {
            return fallbackLimiter.getRemainingRequests(clientId);
        }
    }

    @Override
    public void reset(String clientId) {
        store.remove(keyFor(clientId));
        fallbackLimiter.reset(clientId);
    }

    @Override
    public long getResetTimeMillis(String clientId) {
        try {
            TokenBucketSnapshot snapshot = updateState(clientId, state -> {
                refillTokens(state, System.currentTimeMillis());
                state.lastRequestAllowed = false;
                return state;
            });

            if (snapshot.tokens >= 1.0) {
                return 0;
            }

            double refillRate = (double) config.getMaxRequests() / config.getWindowSizeMillis();
            double tokensNeeded = 1.0 - snapshot.tokens;
            return (long) Math.ceil(tokensNeeded / refillRate);
        } catch (Exception ex) {
            return fallbackLimiter.getResetTimeMillis(clientId);
        }
    }

    private TokenBucketSnapshot updateState(String clientId, Function<TokenBucketSnapshot, TokenBucketSnapshot> updater) {
        String key = keyFor(clientId);
        long ttlMillis = config.getWindowSizeMillis();
        Supplier<TokenBucketSnapshot> initializer = () ->
            new TokenBucketSnapshot(config.getMaxRequests(), System.currentTimeMillis(), false);

        return store.compute(key, ttlMillis, TokenBucketSnapshot.class, updater, initializer);
    }

    private void refillTokens(TokenBucketSnapshot state, long currentTime) {
        long elapsedTime = currentTime - state.lastRefillTimestamp;
        if (elapsedTime <= 0) {
            return;
        }

        double refillRate = (double) config.getMaxRequests() / config.getWindowSizeMillis();
        double tokensToAdd = elapsedTime * refillRate;
        state.tokens = Math.min(config.getMaxRequests(), state.tokens + tokensToAdd);
        state.lastRefillTimestamp = currentTime;
    }

    private String keyFor(String clientId) {
        return keyNamespace + clientId;
    }
}