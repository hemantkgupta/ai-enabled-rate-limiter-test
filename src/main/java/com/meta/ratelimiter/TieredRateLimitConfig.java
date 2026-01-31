package com.meta.ratelimiter;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration providing rate limits per client tier.
 */
public class TieredRateLimitConfig {
    private final Map<ClientTier, RateLimitConfig> tierConfigs;

    public TieredRateLimitConfig(Map<ClientTier, RateLimitConfig> tierConfigs) {
        this.tierConfigs = new EnumMap<>(tierConfigs);
    }

    public RateLimitConfig getConfigFor(ClientTier tier) {
        return tierConfigs.get(tier);
    }

    public static TieredRateLimitConfig defaultPerSecond() {
        Map<ClientTier, RateLimitConfig> configs = new EnumMap<>(ClientTier.class);
        configs.put(ClientTier.FREE, new RateLimitConfig(10, 1000, RateLimitStrategy.TOKEN_BUCKET));
        configs.put(ClientTier.PREMIUM, new RateLimitConfig(100, 1000, RateLimitStrategy.TOKEN_BUCKET));
        configs.put(ClientTier.ENTERPRISE, new RateLimitConfig(1000, 1000, RateLimitStrategy.TOKEN_BUCKET));
        return new TieredRateLimitConfig(configs);
    }
}