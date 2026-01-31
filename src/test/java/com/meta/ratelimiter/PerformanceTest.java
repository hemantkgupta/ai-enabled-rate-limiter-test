package com.meta.ratelimiter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Simple performance test for throughput/latency under concurrency.
 */
public class PerformanceTest {

    @Test
    public void testThroughputAndLatency() throws InterruptedException {
        int concurrentClients = 1000;
        int totalRequests = 10_000;
        int requestsPerClient = totalRequests / concurrentClients;

        RateLimitConfig config = new RateLimitConfig(10_000, 1000, RateLimitStrategy.TOKEN_BUCKET, 10_000);
        RateLimiter rateLimiter = new TokenBucketRateLimiter(config, new ClientRateLimitStore());

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(200, concurrentClients));
        CountDownLatch latch = new CountDownLatch(concurrentClients);
        AtomicInteger completed = new AtomicInteger(0);
        List<Long> latencies = new CopyOnWriteArrayList<>();

        long startWall = System.nanoTime();
        for (int i = 0; i < concurrentClients; i++) {
            String clientId = UUID.randomUUID().toString();
            executor.submit(() -> {
                for (int j = 0; j < requestsPerClient; j++) {
                    long start = System.nanoTime();
                    rateLimiter.allowRequest(clientId);
                    long duration = System.nanoTime() - start;
                    latencies.add(duration);
                    completed.incrementAndGet();
                }
                latch.countDown();
            });
        }

        boolean finished = latch.await(20, TimeUnit.SECONDS);
        long totalElapsedNs = System.nanoTime() - startWall;
        executor.shutdownNow();

        assertTrue("Performance test should finish", finished);
        assertEquals(totalRequests, completed.get());

        double elapsedSeconds = totalElapsedNs / 1_000_000_000.0;
        double throughput = totalRequests / elapsedSeconds;

        long p95LatencyNs = percentile(latencies, 95);
        long p99LatencyNs = percentile(latencies, 99);

        System.out.println("Throughput req/s: " + throughput);
        System.out.println("P95 latency ms: " + (p95LatencyNs / 1_000_000.0));
        System.out.println("P99 latency ms: " + (p99LatencyNs / 1_000_000.0));

        assertTrue("Throughput should be >= 10k req/s", throughput >= 10_000);
        assertTrue("P95 latency should be < 1ms", (p95LatencyNs / 1_000_000.0) < 1.0);
    }

    private long percentile(List<Long> values, int percentile) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}