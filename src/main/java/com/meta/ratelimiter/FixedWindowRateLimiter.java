package com.meta.ratelimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed Window Rate Limiter Implementation
 */
public class FixedWindowRateLimiter implements RateLimiter {
    private static class FixedWindowState {
        private int count;
        private long windowStartMillis;

        private FixedWindowState(long windowStartMillis) {
            this.count = 0;
            this.windowStartMillis = windowStartMillis;
        }
    }

    private final RateLimitConfig config;
    private final Map<String, FixedWindowState> states = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(RateLimitConfig config) {
        this.config = config;
    }

    @Override
    public boolean allowRequest(String clientId) {
        FixedWindowState state = states.computeIfAbsent(
            clientId, key -> new FixedWindowState(System.currentTimeMillis()));

        synchronized (state) {
            refreshWindowIfNeeded(state);
            if (state.count < config.getMaxRequests()) {
                state.count++;
                return true;
            }
            return false;
        }
    }

    @Override
    public int getRemainingRequests(String clientId) {
        FixedWindowState state = states.computeIfAbsent(
            clientId, key -> new FixedWindowState(System.currentTimeMillis()));

        synchronized (state) {
            refreshWindowIfNeeded(state);
            return Math.max(0, config.getMaxRequests() - state.count);
        }
    }

    @Override
    public void reset(String clientId) {
        states.remove(clientId);
    }

    @Override
    public long getResetTimeMillis(String clientId) {
        FixedWindowState state = states.computeIfAbsent(
            clientId, key -> new FixedWindowState(System.currentTimeMillis()));

        synchronized (state) {
            refreshWindowIfNeeded(state);
            if (state.count < config.getMaxRequests()) {
                return 0;
            }
            long currentTime = System.currentTimeMillis();
            return Math.max(0, state.windowStartMillis + config.getWindowSizeMillis() - currentTime);
        }
    }

    private void refreshWindowIfNeeded(FixedWindowState state) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - state.windowStartMillis >= config.getWindowSizeMillis()) {
            state.count = 0;
            state.windowStartMillis = currentTime;
        }
    }
}