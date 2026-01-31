package com.meta.ratelimiter;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency tests for rate limiter
 * 
 * These tests are ALL COMMENTED OUT
 * Uncomment them to reveal concurrency bugs in ClientRateLimitStore
 */
public class ConcurrencyTest {
    private TokenBucketRateLimiter rateLimiter;
    private ClientRateLimitStore store;
    private RateLimitConfig config;

    @Before
    public void setUp() {
        store = new ClientRateLimitStore();
        config = new RateLimitConfig(100, 1000, RateLimitStrategy.TOKEN_BUCKET);
        rateLimiter = new TokenBucketRateLimiter(config, store);
    }

    @Test
    public void testConcurrentRequests() throws InterruptedException {
        String clientId = "concurrent-client";
        int numThreads = 10;
        int requestsPerThread = 20;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger allowedRequests = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    if (rateLimiter.allowRequest(clientId)) {
                        allowedRequests.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Should allow exactly 100 requests (the rate limit)
        System.out.println("Allowed requests: " + allowedRequests.get());
        
        assertTrue("Should not exceed rate limit. Allowed: " + allowedRequests.get(),
                  allowedRequests.get() <= config.getMaxRequests());
    }

    @Test
    public void testConcurrentMultipleClients() throws InterruptedException {
        int numClients = 5;
        int requestsPerClient = 50;
        
        ExecutorService executor = Executors.newFixedThreadPool(numClients * 2);
        ConcurrentHashMap<String, AtomicInteger> allowedPerClient = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(numClients);

        for (int i = 0; i < numClients; i++) {
            String clientId = "client-" + i;
            allowedPerClient.put(clientId, new AtomicInteger(0));
            
            executor.submit(() -> {
                for (int j = 0; j < requestsPerClient; j++) {
                    if (rateLimiter.allowRequest(clientId)) {
                        allowedPerClient.get(clientId).incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Each client should be limited independently
        for (String clientId : allowedPerClient.keySet()) {
            int allowed = allowedPerClient.get(clientId).get();
            System.out.println(clientId + ": " + allowed + " requests allowed");
            assertTrue("Client should not exceed limit: " + allowed,
                      allowed <= config.getMaxRequests());
        }
    }


    @Test
    public void testHighConcurrencyStressTest() throws InterruptedException {
        String clientId = "stress-test-client";
        int numThreads = 50;
        int requestsPerThread = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger allowedRequests = new AtomicInteger(0);
        AtomicInteger deniedRequests = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    if (rateLimiter.allowRequest(clientId)) {
                        allowedRequests.incrementAndGet();
                    } else {
                        deniedRequests.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        int total = allowedRequests.get() + deniedRequests.get();
        
        System.out.println("Total requests: " + total);
        System.out.println("Allowed: " + allowedRequests.get());
        System.out.println("Denied: " + deniedRequests.get());
        
        assertEquals("All requests should be processed", 
                    numThreads * requestsPerThread, total);
        assertTrue("Should respect rate limit", 
                  allowedRequests.get() <= config.getMaxRequests());
    }
}
