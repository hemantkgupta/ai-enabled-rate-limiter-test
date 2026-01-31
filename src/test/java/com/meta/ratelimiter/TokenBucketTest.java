package com.meta.ratelimiter;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for Token Bucket Rate Limiter
 * 
 * Some tests are commented out - uncomment them and make them pass!
 */
public class TokenBucketTest {
    private TokenBucketRateLimiter rateLimiter;
    private ClientRateLimitStore store;
    private RateLimitConfig config;

    @Before
    public void setUp() {
        store = new ClientRateLimitStore();
        // 10 requests per second
        config = new RateLimitConfig(10, 1000, RateLimitStrategy.TOKEN_BUCKET);
        rateLimiter = new TokenBucketRateLimiter(config, store);
    }

    @Test
    public void testBasicRateLimit() {
        String clientId = "test-client-1";

        // Should allow first 10 requests
        for (int i = 0; i < 10; i++) {
            assertTrue("Request " + i + " should be allowed", 
                      rateLimiter.allowRequest(clientId));
        }

        // 11th request should be denied
        assertFalse("11th request should be denied", 
                   rateLimiter.allowRequest(clientId));
    }

    @Test
    public void testRemainingRequests() {
        String clientId = "test-client-2";

        assertEquals("Should have 10 requests available", 
                    10, rateLimiter.getRemainingRequests(clientId));

        // Make 3 requests
        for (int i = 0; i < 3; i++) {
            rateLimiter.allowRequest(clientId);
        }

        assertEquals("Should have 7 requests remaining", 
                    7, rateLimiter.getRemainingRequests(clientId));
    }

    @Test
    public void testReset() {
        String clientId = "test-client-3";

        // Exhaust all requests
        for (int i = 0; i < 10; i++) {
            rateLimiter.allowRequest(clientId);
        }

        assertFalse("Should be rate limited", rateLimiter.allowRequest(clientId));

        // Reset
        rateLimiter.reset(clientId);

        // Should work again
        assertTrue("Should allow request after reset", 
                  rateLimiter.allowRequest(clientId));
    }

    // UNCOMMENT THIS TEST - it will fail due to the refill bug!
    /*
    @Test
    public void testTokenRefill() throws InterruptedException {
        String clientId = "test-client-4";

        // Use all 10 tokens
        for (int i = 0; i < 10; i++) {
            rateLimiter.allowRequest(clientId);
        }

        // Should be rate limited now
        assertFalse("Should be rate limited", rateLimiter.allowRequest(clientId));

        // Wait 100ms (should refill 1 token since rate is 10/second = 1 per 100ms)
        Thread.sleep(100);

        // Should allow 1 request now
        assertTrue("Should allow request after refill", 
                  rateLimiter.allowRequest(clientId));
        
        // But not a second one
        assertFalse("Should be rate limited again", 
                   rateLimiter.allowRequest(clientId));
    }
    */

    // UNCOMMENT THIS TEST - tests partial refill
    /*
    @Test
    public void testPartialRefill() throws InterruptedException {
        String clientId = "test-client-5";

        // Use all tokens
        for (int i = 0; i < 10; i++) {
            rateLimiter.allowRequest(clientId);
        }

        // Wait 500ms (should refill 5 tokens)
        Thread.sleep(500);

        // Should allow exactly 5 requests
        for (int i = 0; i < 5; i++) {
            assertTrue("Request " + i + " should be allowed after partial refill",
                      rateLimiter.allowRequest(clientId));
        }

        // 6th should be denied
        assertFalse("Should be rate limited after using refilled tokens",
                   rateLimiter.allowRequest(clientId));
    }
    */

    @Test
    public void testMultipleClients() {
        String client1 = "client-1";
        String client2 = "client-2";

        // Client 1 uses all requests
        for (int i = 0; i < 10; i++) {
            rateLimiter.allowRequest(client1);
        }

        // Client 1 should be blocked
        assertFalse("Client 1 should be rate limited", 
                   rateLimiter.allowRequest(client1));

        // Client 2 should still work
        assertTrue("Client 2 should be allowed", 
                  rateLimiter.allowRequest(client2));
    }
}
