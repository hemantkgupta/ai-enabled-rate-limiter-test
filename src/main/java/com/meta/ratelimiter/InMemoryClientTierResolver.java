package com.meta.ratelimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory tier resolver for demo/testing.
 */
public class InMemoryClientTierResolver implements ClientTierResolver {
    private final Map<String, ClientTier> tiers = new ConcurrentHashMap<>();
    private final ClientTier defaultTier;

    public InMemoryClientTierResolver(ClientTier defaultTier) {
        this.defaultTier = defaultTier;
    }

    public void setTier(String clientId, ClientTier tier) {
        tiers.put(clientId, tier);
    }

    @Override
    public ClientTier resolveTier(String clientId) {
        return tiers.getOrDefault(clientId, defaultTier);
    }
}