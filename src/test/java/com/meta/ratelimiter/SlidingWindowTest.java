package com.meta.ratelimiter;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for Sliding Window Rate Limiter
 */
public class SlidingWindowTest {
    private SlidingWindowRateLimiter rateLimiter;
    private ClientRateLimitStore store;
    private RateLimitConfig config;

    @Before
    public void setUp() {
        store = new ClientRateLimitStore();
        // 5 requests per second
        config = new RateLimitConfig(5, 1000, RateLimitStrategy.SLIDING_WINDOW);
        rateLimiter = new SlidingWindowRateLimiter(config, store);
    }

    @Test
    public void testBasicRateLimit() {
        String clientId = "test-client-1";

        // Should allow 5 requests
        for (int i = 0; i < 5; i++) {
            assertTrue("Request " + i + " should be allowed",
                      rateLimiter.allowRequest(clientId));
        }

        // 6th request should be denied
        assertFalse("6th request should be denied",
                   rateLimiter.allowRequest(clientId));
    }

    @Test
    public void testRemainingRequests() {
        String clientId = "test-client-2";

        assertEquals("Should have 5 requests available",
                    5, rateLimiter.getRemainingRequests(clientId));

        rateLimiter.allowRequest(clientId);
        rateLimiter.allowRequest(clientId);

        assertEquals("Should have 3 requests remaining",
                    3, rateLimiter.getRemainingRequests(clientId));
    }

    // UNCOMMENT THIS TEST - it will reveal the memory leak!
    /*
    @Test
    public void testMemoryLeak() throws InterruptedException {
        String clientId = "test-client-3";

        // Make 1000 requests over time
        for (int i = 0; i < 1000; i++) {
            rateLimiter.allowRequest(clientId);
            Thread.sleep(10); // 10ms between requests
        }

        // Get the state and check timestamp list size
        ClientRateLimitStore.SlidingWindowState state = 
            store.getOrCreateSlidingWindowState(clientId);

        // BUG: The list should only contain timestamps from last 1 second
        // But it contains ALL 1000 timestamps!
        System.out.println("Timestamp list size: " + state.requestTimestamps.size());
        
        // This assertion will fail, revealing the memory leak
        assertTrue("Timestamp list should not grow unbounded. Size: " + 
                  state.requestTimestamps.size(),
                  state.requestTimestamps.size() < 100);
    }
    */

    // UNCOMMENT THIS TEST - tests window sliding behavior
    /*
    @Test
    public void testWindowSliding() throws InterruptedException {
        String clientId = "test-client-4";

        // Use all 5 requests
        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(clientId);
        }

        // Should be blocked
        assertFalse("Should be rate limited", rateLimiter.allowRequest(clientId));

        // Wait for window to expire (1 second)
        Thread.sleep(1100);

        // Should allow requests again
        assertTrue("Should allow request after window slides",
                  rateLimiter.allowRequest(clientId));
    }
    */

    @Test
    public void testMultipleClients() {
        String client1 = "client-1";
        String client2 = "client-2";

        // Client 1 uses all requests
        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(client1);
        }

        assertFalse("Client 1 should be rate limited",
                   rateLimiter.allowRequest(client1));

        // Client 2 should still work
        assertTrue("Client 2 should be allowed",
                  rateLimiter.allowRequest(client2));
    }

    @Test
    public void testReset() {
        String clientId = "test-client-5";

        // Exhaust all requests
        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(clientId);
        }

        assertFalse("Should be rate limited", rateLimiter.allowRequest(clientId));

        // Reset
        rateLimiter.reset(clientId);

        // Should work again
        assertTrue("Should allow request after reset",
                  rateLimiter.allowRequest(clientId));
    }
}
