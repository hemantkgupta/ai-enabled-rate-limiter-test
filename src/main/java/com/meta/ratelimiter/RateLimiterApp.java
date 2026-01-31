package com.meta.ratelimiter;

import com.google.gson.Gson;
import spark.Request;
import spark.Response;

import static spark.Spark.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Main application with REST API for rate limiting
 */
public class RateLimiterApp {
    private static final Gson gson = new Gson();
    private static RateLimiter rateLimiter;
    private static EndpointRateLimiter endpointRateLimiter;

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("  Rate Limiter Service Started  ");
        System.out.println("=================================");

        // Initialize tiered, distributed rate limiter
        DistributedRateLimitStore distributedStore = new InMemoryDistributedRateLimitStore();
        InMemoryClientTierResolver tierResolver = new InMemoryClientTierResolver(ClientTier.FREE);
        TieredRateLimitConfig tieredConfig = TieredRateLimitConfig.defaultPerSecond();
        rateLimiter = buildTieredLimiter(tieredConfig, distributedStore, tierResolver);
        endpointRateLimiter = buildEndpointTieredLimiter(tierResolver, distributedStore);

        System.out.println("Tiered Config: FREE/PREMIUM/ENTERPRISE per second");
        System.out.println("Strategy: TOKEN_BUCKET (distributed)");
        System.out.println("Endpoint limits: /api/search=5/s, /api/users=20/s, /api/health=unlimited");
        System.out.println("\nAPI running on http://localhost:4567");
        System.out.println("=================================\n");

        // Configure Spark
        port(4567);

        // Health check endpoint
        get("/health", (req, res) -> {
            res.type("application/json");
            Map<String, String> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("service", "rate-limiter");
            return gson.toJson(health);
        });

        // Check rate limit endpoint
        post("/api/check-limit", RateLimiterApp::checkRateLimit);

        // Get remaining requests endpoint
        get("/api/remaining/:clientId", RateLimiterApp::getRemainingRequests);

        // Reset rate limit for a client (useful for testing)
        delete("/api/reset/:clientId", RateLimiterApp::resetClient);

        System.out.println("Try:");
        System.out.println("  POST http://localhost:4567/api/check-limit");
        System.out.println("  Body: {\"clientId\": \"test-client-1\"}");
    }

    /**
     * Check if a request should be rate limited
     */
    private static Object checkRateLimit(Request request, Response response) {
        response.type("application/json");

        try {
            // Parse request body
            Map<String, String> body = gson.fromJson(request.body(), Map.class);
            String clientId = body.get("clientId");
            String endpoint = body.getOrDefault("endpoint", "/api/check-limit");

            if (clientId == null || clientId.isEmpty()) {
                response.status(400);
                return gson.toJson(Map.of("error", "clientId is required"));
            }

            // Check rate limit
            boolean allowed = endpointRateLimiter.allowRequest(clientId, endpoint);
            int remaining = endpointRateLimiter.getRemainingRequests(clientId, endpoint);
            int limit = endpointRateLimiter.getLimit(clientId, endpoint);
            long resetAfterMs = endpointRateLimiter.getResetTimeMillis(clientId, endpoint);
            long resetUnixSeconds = toResetUnixSeconds(resetAfterMs);

            response.header("X-RateLimit-Limit", String.valueOf(limit));
            response.header("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));
            response.header("X-RateLimit-Reset", String.valueOf(resetUnixSeconds));

            if (allowed) {
                response.status(200);
                
                return gson.toJson(Map.of(
                    "allowed", true,
                    "remaining", remaining,
                    "message", "Request allowed"
                ));
            } else {
                response.status(429); // Too Many Requests
                response.header("Retry-After", String.valueOf(resetAfterMs / 1000));
                
                return gson.toJson(Map.of(
                    "allowed", false,
                    "remaining", 0,
                    "resetAfterMs", resetAfterMs,
                    "message", "Rate limit exceeded"
                ));
            }
        } catch (Exception e) {
            response.status(500);
            return gson.toJson(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Get remaining requests for a client
     */
    private static Object getRemainingRequests(Request request, Response response) {
        response.type("application/json");
        String clientId = request.params(":clientId");

        int remaining = rateLimiter.getRemainingRequests(clientId);
        
        return gson.toJson(Map.of(
            "clientId", clientId,
            "remaining", remaining
        ));
    }

    /**
     * Reset rate limit for a client
     */
    private static Object resetClient(Request request, Response response) {
        response.type("application/json");
        String clientId = request.params(":clientId");

        rateLimiter.reset(clientId);
        
        return gson.toJson(Map.of(
            "clientId", clientId,
            "message", "Rate limit reset"
        ));
    }

    // Expose rate limiter for testing
    public static RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public static void setRateLimiter(RateLimiter limiter) {
        rateLimiter = limiter;
    }

    private static long toResetUnixSeconds(long resetAfterMillis) {
        long currentSeconds = System.currentTimeMillis() / 1000;
        long resetAfterSeconds = (long) Math.ceil(resetAfterMillis / 1000.0);
        return currentSeconds + Math.max(0, resetAfterSeconds);
    }

    private static TieredRateLimiter buildTieredLimiter(
        TieredRateLimitConfig tieredConfig,
        DistributedRateLimitStore store,
        ClientTierResolver tierResolver
    ) {
        java.util.Map<ClientTier, RateLimiter> limiters = new java.util.EnumMap<>(ClientTier.class);

        for (ClientTier tier : ClientTier.values()) {
            RateLimitConfig config = tieredConfig.getConfigFor(tier);
            ClientRateLimitStore localStore = new ClientRateLimitStore();
            RateLimiter fallbackLimiter = new TokenBucketRateLimiter(config, localStore);
            String namespace = "token-bucket:" + tier.name().toLowerCase() + ":";

            RateLimiter limiter = new DistributedTokenBucketRateLimiter(
                config,
                store,
                fallbackLimiter,
                namespace
            );
            limiters.put(tier, limiter);
        }

        return new TieredRateLimiter(tierResolver, limiters);
    }

    private static EndpointRateLimiter buildEndpointTieredLimiter(
        ClientTierResolver tierResolver,
        DistributedRateLimitStore store
    ) {
        TieredRateLimiter defaultLimiter = buildTieredLimiter(
            TieredRateLimitConfig.defaultPerSecond(),
            store,
            tierResolver
        );

        TieredRateLimiter searchLimiter = buildEndpointLimiter(
            tierResolver,
            store,
            5
        );
        TieredRateLimiter usersLimiter = buildEndpointLimiter(
            tierResolver,
            store,
            20
        );

        java.util.Map<String, TieredRateLimiter> endpointLimiters = new java.util.HashMap<>();
        endpointLimiters.put("/api/search", searchLimiter);
        endpointLimiters.put("/api/users", usersLimiter);
        endpointLimiters.put("/api/health", new TieredRateLimiter(
            tierResolver,
            java.util.Map.of(
                ClientTier.FREE, new UnlimitedRateLimiter(),
                ClientTier.PREMIUM, new UnlimitedRateLimiter(),
                ClientTier.ENTERPRISE, new UnlimitedRateLimiter()
            )
        ));

        return new EndpointTieredRateLimiter(endpointLimiters, defaultLimiter);
    }

    private static TieredRateLimiter buildEndpointLimiter(
        ClientTierResolver tierResolver,
        DistributedRateLimitStore store,
        int basePerSecond
    ) {
        java.util.Map<ClientTier, RateLimiter> limiters = new java.util.EnumMap<>(ClientTier.class);
        limiters.put(ClientTier.FREE, buildDistributedLimiter(store, basePerSecond, "endpoint:free:"));
        limiters.put(ClientTier.PREMIUM, buildDistributedLimiter(store, basePerSecond * 10, "endpoint:premium:"));
        limiters.put(ClientTier.ENTERPRISE, buildDistributedLimiter(store, basePerSecond * 100, "endpoint:enterprise:"));
        return new TieredRateLimiter(tierResolver, limiters);
    }

    private static RateLimiter buildDistributedLimiter(
        DistributedRateLimitStore store,
        int maxRequestsPerSecond,
        String namespacePrefix
    ) {
        RateLimitConfig config = new RateLimitConfig(maxRequestsPerSecond, 1000, RateLimitStrategy.TOKEN_BUCKET);
        RateLimiter fallbackLimiter = new TokenBucketRateLimiter(config, new ClientRateLimitStore());
        return new DistributedTokenBucketRateLimiter(
            config,
            store,
            fallbackLimiter,
            "token-bucket:" + namespacePrefix
        );
    }
}
