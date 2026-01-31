package com.meta.ratelimiter;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for burst allowance in token bucket.
 */
public class TokenBucketBurstTest {
    private TokenBucketRateLimiter rateLimiter;

    @Before
    public void setUp() {
        RateLimitConfig config = new RateLimitConfig(10, 1000, RateLimitStrategy.TOKEN_BUCKET, 20);
        rateLimiter = new TokenBucketRateLimiter(config, new ClientRateLimitStore());
    }

    @Test
    public void testBurstAllowance() {
        String clientId = "burst-client";

        for (int i = 0; i < 20; i++) {
            assertTrue("Request " + i + " should be allowed in burst", rateLimiter.allowRequest(clientId));
        }

        assertFalse("21st request should be denied", rateLimiter.allowRequest(clientId));
    }
}