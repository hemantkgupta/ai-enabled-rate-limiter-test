package com.meta.ratelimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sliding Window Rate Limiter backed by a distributed store.
 */
public class DistributedSlidingWindowRateLimiter implements RateLimiter {
    private static final String KEY_PREFIX = "sliding-window:";
    private final String keyNamespace;

    private static class SlidingWindowSnapshot {
        private List<Long> requestTimestamps;
        private boolean lastRequestAllowed;

        private SlidingWindowSnapshot(List<Long> requestTimestamps, boolean lastRequestAllowed) {
            this.requestTimestamps = requestTimestamps;
            this.lastRequestAllowed = lastRequestAllowed;
        }
    }

    private final RateLimitConfig config;
    private final DistributedRateLimitStore store;
    private final RateLimiter fallbackLimiter;

    public DistributedSlidingWindowRateLimiter(
        RateLimitConfig config,
        DistributedRateLimitStore store,
        RateLimiter fallbackLimiter
    ) {
        this.config = config;
        this.store = store;
        this.fallbackLimiter = fallbackLimiter;
        this.keyNamespace = KEY_PREFIX;
    }

    public DistributedSlidingWindowRateLimiter(
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
            SlidingWindowSnapshot snapshot = updateState(clientId, state -> {
                purgeOldEntries(state.requestTimestamps);
                if (state.requestTimestamps.size() < config.getMaxRequests()) {
                    state.requestTimestamps.add(System.currentTimeMillis());
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
            SlidingWindowSnapshot snapshot = updateState(clientId, state -> {
                purgeOldEntries(state.requestTimestamps);
                state.lastRequestAllowed = false;
                return state;
            });

            return Math.max(0, config.getMaxRequests() - snapshot.requestTimestamps.size());
        } catch (Exception ex) {
            return fallbackLimiter.getRemainingRequests(clientId);
        }
    }

    @Override
    public int getLimit(String clientId) {
        return config.getMaxRequests();
    }

    @Override
    public void reset(String clientId) {
        store.remove(keyFor(clientId));
        fallbackLimiter.reset(clientId);
    }

    @Override
    public long getResetTimeMillis(String clientId) {
        try {
            SlidingWindowSnapshot snapshot = updateState(clientId, state -> {
                purgeOldEntries(state.requestTimestamps);
                state.lastRequestAllowed = false;
                return state;
            });

            if (snapshot.requestTimestamps.isEmpty()) {
                return 0;
            }

            long oldest = snapshot.requestTimestamps.get(0);
            for (Long timestamp : snapshot.requestTimestamps) {
                if (timestamp < oldest) {
                    oldest = timestamp;
                }
            }

            long currentTime = System.currentTimeMillis();
            return Math.max(0, oldest + config.getWindowSizeMillis() - currentTime);
        } catch (Exception ex) {
            return fallbackLimiter.getResetTimeMillis(clientId);
        }
    }

    private SlidingWindowSnapshot updateState(
        String clientId,
        java.util.function.Function<SlidingWindowSnapshot, SlidingWindowSnapshot> updater
    ) {
        String key = keyFor(clientId);
        long ttlMillis = config.getWindowSizeMillis();
        Supplier<SlidingWindowSnapshot> initializer = () ->
            new SlidingWindowSnapshot(new ArrayList<>(), false);

        return store.compute(key, ttlMillis, SlidingWindowSnapshot.class, updater, initializer);
    }

    private void purgeOldEntries(List<Long> timestamps) {
        long windowStart = System.currentTimeMillis() - config.getWindowSizeMillis();
        timestamps.removeIf(timestamp -> timestamp < windowStart);
    }

    private String keyFor(String clientId) {
        return keyNamespace + clientId;
    }
}