package com.meta.ratelimiter;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests endpoint-specific rate limiting.
 */
public class EndpointRateLimiterTest {
    private EndpointRateLimiter endpointLimiter;

    @Before
    public void setUp() {
        DistributedRateLimitStore store = new InMemoryDistributedRateLimitStore();
        InMemoryClientTierResolver tierResolver = new InMemoryClientTierResolver(ClientTier.FREE);

        TieredRateLimiter searchLimiter = buildEndpointLimiter(tierResolver, store, 5);
        TieredRateLimiter usersLimiter = buildEndpointLimiter(tierResolver, store, 20);
        TieredRateLimiter defaultLimiter = buildEndpointLimiter(tierResolver, store, 10);

        Map<String, TieredRateLimiter> endpointLimiters = new HashMap<>();
        endpointLimiters.put("/api/search", searchLimiter);
        endpointLimiters.put("/api/users", usersLimiter);
        endpointLimiters.put("/api/health", new TieredRateLimiter(
            tierResolver,
            Map.of(
                ClientTier.FREE, new UnlimitedRateLimiter(),
                ClientTier.PREMIUM, new UnlimitedRateLimiter(),
                ClientTier.ENTERPRISE, new UnlimitedRateLimiter()
            )
        ));

        endpointLimiter = new EndpointTieredRateLimiter(endpointLimiters, defaultLimiter);
    }

    @Test
    public void testSearchEndpointLimit() {
        String clientId = "search-client";
        for (int i = 0; i < 5; i++) {
            assertTrue(endpointLimiter.allowRequest(clientId, "/api/search"));
        }
        assertFalse(endpointLimiter.allowRequest(clientId, "/api/search"));
    }

    @Test
    public void testUsersEndpointLimit() {
        String clientId = "users-client";
        for (int i = 0; i < 20; i++) {
            assertTrue(endpointLimiter.allowRequest(clientId, "/api/users"));
        }
        assertFalse(endpointLimiter.allowRequest(clientId, "/api/users"));
    }

    @Test
    public void testHealthEndpointUnlimited() {
        String clientId = "health-client";
        for (int i = 0; i < 100; i++) {
            assertTrue(endpointLimiter.allowRequest(clientId, "/api/health"));
        }
    }

    private TieredRateLimiter buildEndpointLimiter(
        ClientTierResolver tierResolver,
        DistributedRateLimitStore store,
        int basePerSecond
    ) {
        Map<ClientTier, RateLimiter> limiters = new java.util.EnumMap<>(ClientTier.class);
        limiters.put(ClientTier.FREE, buildDistributedLimiter(store, basePerSecond, "endpoint:free:"));
        limiters.put(ClientTier.PREMIUM, buildDistributedLimiter(store, basePerSecond * 10, "endpoint:premium:"));
        limiters.put(ClientTier.ENTERPRISE, buildDistributedLimiter(store, basePerSecond * 100, "endpoint:enterprise:"));
        return new TieredRateLimiter(tierResolver, limiters);
    }

    private RateLimiter buildDistributedLimiter(
        DistributedRateLimitStore store,
        int maxRequestsPerSecond,
        String namespacePrefix
    ) {
        RateLimitConfig config = new RateLimitConfig(maxRequestsPerSecond, 60_000, RateLimitStrategy.TOKEN_BUCKET);
        RateLimiter fallbackLimiter = new TokenBucketRateLimiter(config, new ClientRateLimitStore());
        return new DistributedTokenBucketRateLimiter(
            config,
            store,
            fallbackLimiter,
            "token-bucket:" + namespacePrefix
        );
    }
}