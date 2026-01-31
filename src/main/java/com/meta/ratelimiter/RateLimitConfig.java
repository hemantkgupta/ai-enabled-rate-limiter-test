package com.meta.ratelimiter;

/**
 * Configuration for rate limiting
 */
public class RateLimitConfig {
    private final int maxRequests;
    private final long windowSizeMillis;
    private final RateLimitStrategy strategy;

    public RateLimitConfig(int maxRequests, long windowSizeMillis, RateLimitStrategy strategy) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = windowSizeMillis;
        this.strategy = strategy;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public long getWindowSizeMillis() {
        return windowSizeMillis;
    }

    public RateLimitStrategy getStrategy() {
        return strategy;
    }

    /**
     * Default configuration: 10 requests per second using token bucket
     */
    public static RateLimitConfig getDefault() {
        return new RateLimitConfig(10, 1000, RateLimitStrategy.TOKEN_BUCKET);
    }

    /**
     * Lenient configuration for testing: 100 requests per second
     */
    public static RateLimitConfig getLenient() {
        return new RateLimitConfig(100, 1000, RateLimitStrategy.TOKEN_BUCKET);
    }

    /**
     * Strict configuration: 5 requests per second
     */
    public static RateLimitConfig getStrict() {
        return new RateLimitConfig(5, 1000, RateLimitStrategy.TOKEN_BUCKET);
    }
}
