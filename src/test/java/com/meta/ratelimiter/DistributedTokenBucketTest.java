package com.meta.ratelimiter;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for DistributedTokenBucketRateLimiter
 */
public class DistributedTokenBucketTest {
    private DistributedTokenBucketRateLimiter rateLimiter;
    private InMemoryDistributedRateLimitStore store;
    private RateLimitConfig config;

    @Before
    public void setUp() {
        store = new InMemoryDistributedRateLimitStore();
        config = new RateLimitConfig(10, 1000, RateLimitStrategy.TOKEN_BUCKET);
        RateLimiter fallback = new TokenBucketRateLimiter(config, new ClientRateLimitStore());
        rateLimiter = new DistributedTokenBucketRateLimiter(config, store, fallback);
    }

    @Test
    public void testBasicRateLimit() {
        String clientId = "dist-client-1";

        for (int i = 0; i < 10; i++) {
            assertTrue("Request " + i + " should be allowed",
                rateLimiter.allowRequest(clientId));
        }

        assertFalse("11th request should be denied",
            rateLimiter.allowRequest(clientId));
    }

    @Test
    public void testTokenRefill() throws InterruptedException {
        String clientId = "dist-client-2";

        for (int i = 0; i < 10; i++) {
            rateLimiter.allowRequest(clientId);
        }

        assertFalse("Should be rate limited", rateLimiter.allowRequest(clientId));

        Thread.sleep(100);

        assertTrue("Should allow request after refill",
            rateLimiter.allowRequest(clientId));

        assertFalse("Should be rate limited again",
            rateLimiter.allowRequest(clientId));
    }

    @Test
    public void testRemainingRequests() {
        String clientId = "dist-client-3";

        assertEquals(10, rateLimiter.getRemainingRequests(clientId));

        for (int i = 0; i < 3; i++) {
            rateLimiter.allowRequest(clientId);
        }

        assertEquals(7, rateLimiter.getRemainingRequests(clientId));
    }
}