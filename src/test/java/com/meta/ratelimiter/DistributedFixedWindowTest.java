package com.meta.ratelimiter;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for DistributedFixedWindowRateLimiter
 */
public class DistributedFixedWindowTest {
    private DistributedFixedWindowRateLimiter rateLimiter;
    private RateLimitConfig config;

    @Before
    public void setUp() {
        config = new RateLimitConfig(5, 1000, RateLimitStrategy.FIXED_WINDOW);
        DistributedRateLimitStore store = new InMemoryDistributedRateLimitStore();
        RateLimiter fallback = new FixedWindowRateLimiter(config);
        rateLimiter = new DistributedFixedWindowRateLimiter(config, store, fallback);
    }

    @Test
    public void testBasicRateLimit() {
        String clientId = "dist-fw-client-1";

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allowRequest(clientId));
        }

        assertFalse(rateLimiter.allowRequest(clientId));
    }

    @Test
    public void testRemainingRequests() {
        String clientId = "dist-fw-client-2";

        assertEquals(5, rateLimiter.getRemainingRequests(clientId));

        rateLimiter.allowRequest(clientId);
        rateLimiter.allowRequest(clientId);

        assertEquals(3, rateLimiter.getRemainingRequests(clientId));
    }

    @Test
    public void testWindowResets() throws InterruptedException {
        String clientId = "dist-fw-client-3";

        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(clientId);
        }

        assertFalse(rateLimiter.allowRequest(clientId));

        Thread.sleep(1100);

        assertTrue(rateLimiter.allowRequest(clientId));
    }
}