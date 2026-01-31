package com.meta.ratelimiter;

/**
 * Configuration for rate limiting
 */
public class RateLimitConfig {
    private final int maxRequests;
    private final long windowSizeMillis;
    private final RateLimitStrategy strategy;
    private final int burstCapacity;

    public RateLimitConfig(int maxRequests, long windowSizeMillis, RateLimitStrategy strategy) {
        this(maxRequests, windowSizeMillis, strategy, maxRequests);
    }

    public RateLimitConfig(
        int maxRequests,
        long windowSizeMillis,
        RateLimitStrategy strategy,
        int burstCapacity
    ) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = windowSizeMillis;
        this.strategy = strategy;
        this.burstCapacity = burstCapacity;
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

    public int getBurstCapacity() {
        return burstCapacity;
    }

    /**
     * Default configuration: 10 requests per second using token bucket
     */
    public static RateLimitConfig getDefault() {
        return new RateLimitConfig(10, 1000, RateLimitStrategy.TOKEN_BUCKET, 20);
    }

    /**
     * Lenient configuration for testing: 100 requests per second
     */
    public static RateLimitConfig getLenient() {
        return new RateLimitConfig(100, 1000, RateLimitStrategy.TOKEN_BUCKET, 200);
    }

    /**
     * Strict configuration: 5 requests per second
     */
    public static RateLimitConfig getStrict() {
        return new RateLimitConfig(5, 1000, RateLimitStrategy.TOKEN_BUCKET, 10);
    }
}
