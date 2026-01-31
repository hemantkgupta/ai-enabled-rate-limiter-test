package com.meta.ratelimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * In-memory simulation of a distributed store (e.g., Redis).
 * Provides atomic compute semantics per key with TTL support.
 */
public class InMemoryDistributedRateLimitStore implements DistributedRateLimitStore {
    private static class Entry {
        private Object value;
        private long expiresAtMillis;

        private Entry(Object value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public <T> T get(String key, Class<T> type) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }

        if (isExpired(entry)) {
            store.remove(key);
            return null;
        }

        return type.cast(entry.value);
    }

    @Override
    public <T> void set(String key, T value, long ttlMillis) {
        long expiresAt = computeExpiry(ttlMillis);
        store.put(key, new Entry(value, expiresAt));
    }

    @Override
    public <T> T compute(
        String key,
        long ttlMillis,
        Class<T> type,
        Function<T, T> updateFunction,
        Supplier<T> initializer
    ) {
        return type.cast(
            store.compute(key, (k, existing) -> {
                long now = System.currentTimeMillis();
                Entry entry = existing;
                T currentValue;
                if (entry == null || entry.expiresAtMillis <= now) {
                    currentValue = initializer.get();
                    entry = new Entry(currentValue, computeExpiry(ttlMillis));
                } else {
                    currentValue = type.cast(entry.value);
                }

                T updated = updateFunction.apply(currentValue);
                entry.value = updated;
                entry.expiresAtMillis = computeExpiry(ttlMillis);
                return entry;
            }).value
        );
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }

    @Override
    public void clear() {
        store.clear();
    }

    private boolean isExpired(Entry entry) {
        return entry.expiresAtMillis <= System.currentTimeMillis();
    }

    private long computeExpiry(long ttlMillis) {
        if (ttlMillis <= 0) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() + ttlMillis;
    }
}