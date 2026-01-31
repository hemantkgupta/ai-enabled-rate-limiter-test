package com.meta.ratelimiter;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for header calculation helpers in RateLimiterApp.
 */
public class RateLimiterHeaderTest {

    @Test
    public void testResetUnixSecondsConversion() {
        long resetAfterMs = 1500;
        long before = System.currentTimeMillis() / 1000;
        long resetSeconds = invokeToResetUnixSeconds(resetAfterMs);
        long after = System.currentTimeMillis() / 1000;

        assertTrue(resetSeconds >= before + 2);
        assertTrue(resetSeconds <= after + 2);
    }

    private long invokeToResetUnixSeconds(long resetAfterMs) {
        try {
            java.lang.reflect.Method method = RateLimiterApp.class
                .getDeclaredMethod("toResetUnixSeconds", long.class);
            method.setAccessible(true);
            return (long) method.invoke(null, resetAfterMs);
        } catch (Exception e) {
            throw new AssertionError("Reflection failed", e);
        }
    }
}