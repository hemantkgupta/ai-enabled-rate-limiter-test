package com.meta.ratelimiter;

/**
 * Defines different rate limiting strategies
 */
public enum RateLimitStrategy {
    TOKEN_BUCKET,
    SLIDING_WINDOW,
    FIXED_WINDOW
}
