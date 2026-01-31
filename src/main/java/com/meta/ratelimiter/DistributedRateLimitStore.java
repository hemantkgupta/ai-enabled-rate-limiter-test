package com.meta.ratelimiter;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Abstraction for a distributed rate limit store (e.g., Redis).
 * Provides atomic compute semantics to update state per key.
 */
public interface DistributedRateLimitStore {
    <T> T get(String key, Class<T> type);

    <T> void set(String key, T value, long ttlMillis);

    <T> T compute(
        String key,
        long ttlMillis,
        Class<T> type,
        Function<T, T> updateFunction,
        Supplier<T> initializer
    );

    void remove(String key);

    void clear();
}