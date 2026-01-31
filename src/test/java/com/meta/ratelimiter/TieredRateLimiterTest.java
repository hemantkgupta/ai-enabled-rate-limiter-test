package com.meta.ratelimiter;

import org.junit.Before;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for tiered rate limiting.
 */
public class TieredRateLimiterTest {
    private InMemoryClientTierResolver tierResolver;
    private TieredRateLimiter tieredLimiter;

    @Before
    public void setUp() {
        tierResolver = new InMemoryClientTierResolver(ClientTier.FREE);
        DistributedRateLimitStore store = new InMemoryDistributedRateLimitStore();

        Map<ClientTier, RateLimiter> limiters = new EnumMap<>(ClientTier.class);
        limiters.put(ClientTier.FREE, buildDistributedLimiter(10, store, ClientTier.FREE));
        limiters.put(ClientTier.PREMIUM, buildDistributedLimiter(100, store, ClientTier.PREMIUM));
        limiters.put(ClientTier.ENTERPRISE, buildDistributedLimiter(1000, store, ClientTier.ENTERPRISE));

        tieredLimiter = new TieredRateLimiter(tierResolver, limiters);
    }

    @Test
    public void testFreeTierLimit() {
        String clientId = "free-client";
        tierResolver.setTier(clientId, ClientTier.FREE);

        for (int i = 0; i < 10; i++) {
            assertTrue(tieredLimiter.allowRequest(clientId));
        }

        assertFalse(tieredLimiter.allowRequest(clientId));
    }

    @Test
    public void testPremiumTierLimit() {
        String clientId = "premium-client";
        tierResolver.setTier(clientId, ClientTier.PREMIUM);

        for (int i = 0; i < 100; i++) {
            assertTrue(tieredLimiter.allowRequest(clientId));
        }

        assertFalse(tieredLimiter.allowRequest(clientId));
    }

    @Test
    public void testEnterpriseTierLimit() {
        String clientId = "enterprise-client";
        tierResolver.setTier(clientId, ClientTier.ENTERPRISE);

        for (int i = 0; i < 1000; i++) {
            assertTrue(tieredLimiter.allowRequest(clientId));
        }

        assertFalse(tieredLimiter.allowRequest(clientId));
    }

    @Test
    public void testDifferentTiersIndependent() {
        String freeClient = "free-client-2";
        String premiumClient = "premium-client-2";
        tierResolver.setTier(freeClient, ClientTier.FREE);
        tierResolver.setTier(premiumClient, ClientTier.PREMIUM);

        for (int i = 0; i < 10; i++) {
            assertTrue(tieredLimiter.allowRequest(freeClient));
        }
        assertFalse(tieredLimiter.allowRequest(freeClient));

        for (int i = 0; i < 100; i++) {
            assertTrue(tieredLimiter.allowRequest(premiumClient));
        }
        assertFalse(tieredLimiter.allowRequest(premiumClient));
    }

    private RateLimiter buildDistributedLimiter(int maxRequests, DistributedRateLimitStore store, ClientTier tier) {
        RateLimitConfig config = new RateLimitConfig(maxRequests, 60_000, RateLimitStrategy.TOKEN_BUCKET);
        RateLimiter fallbackLimiter = new TokenBucketRateLimiter(config, new ClientRateLimitStore());
        String namespace = "token-bucket:" + tier.name().toLowerCase() + ":";
        return new DistributedTokenBucketRateLimiter(config, store, fallbackLimiter, namespace);
    }
}