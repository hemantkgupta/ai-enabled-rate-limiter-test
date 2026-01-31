package com.meta.ratelimiter;

import java.util.function.Supplier;

/**
 * Fixed Window Rate Limiter backed by a distributed store.
 */
public class DistributedFixedWindowRateLimiter implements RateLimiter {
    private static final String KEY_PREFIX = "fixed-window:";

    private static class FixedWindowSnapshot {
        private int count;
        private long windowStartMillis;
        private boolean lastRequestAllowed;

        private FixedWindowSnapshot(int count, long windowStartMillis, boolean lastRequestAllowed) {
            this.count = count;
            this.windowStartMillis = windowStartMillis;
            this.lastRequestAllowed = lastRequestAllowed;
        }
    }

    private final RateLimitConfig config;
    private final DistributedRateLimitStore store;
    private final RateLimiter fallbackLimiter;

    public DistributedFixedWindowRateLimiter(
        RateLimitConfig config,
        DistributedRateLimitStore store,
        RateLimiter fallbackLimiter
    ) {
        this.config = config;
        this.store = store;
        this.fallbackLimiter = fallbackLimiter;
    }

    @Override
    public boolean allowRequest(String clientId) {
        try {
            FixedWindowSnapshot snapshot = updateState(clientId, state -> {
                refreshWindowIfNeeded(state);
                if (state.count < config.getMaxRequests()) {
                    state.count++;
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
            FixedWindowSnapshot snapshot = updateState(clientId, state -> {
                refreshWindowIfNeeded(state);
                state.lastRequestAllowed = false;
                return state;
            });

            return Math.max(0, config.getMaxRequests() - snapshot.count);
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
            FixedWindowSnapshot snapshot = updateState(clientId, state -> {
                refreshWindowIfNeeded(state);
                state.lastRequestAllowed = false;
                return state;
            });

            if (snapshot.count < config.getMaxRequests()) {
                return 0;
            }

            long currentTime = System.currentTimeMillis();
            return Math.max(0, snapshot.windowStartMillis + config.getWindowSizeMillis() - currentTime);
        } catch (Exception ex) {
            return fallbackLimiter.getResetTimeMillis(clientId);
        }
    }

    private FixedWindowSnapshot updateState(
        String clientId,
        java.util.function.Function<FixedWindowSnapshot, FixedWindowSnapshot> updater
    ) {
        String key = keyFor(clientId);
        long ttlMillis = config.getWindowSizeMillis();
        Supplier<FixedWindowSnapshot> initializer = () ->
            new FixedWindowSnapshot(0, System.currentTimeMillis(), false);

        return store.compute(key, ttlMillis, FixedWindowSnapshot.class, updater, initializer);
    }

    private void refreshWindowIfNeeded(FixedWindowSnapshot state) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - state.windowStartMillis >= config.getWindowSizeMillis()) {
            state.count = 0;
            state.windowStartMillis = currentTime;
        }
    }

    private String keyFor(String clientId) {
        return KEY_PREFIX + clientId;
    }
}