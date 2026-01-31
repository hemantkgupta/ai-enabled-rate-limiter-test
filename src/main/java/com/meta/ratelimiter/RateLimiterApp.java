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

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("  Rate Limiter Service Started  ");
        System.out.println("=================================");

        // Initialize rate limiter with default config
        RateLimitConfig config = RateLimitConfig.getDefault();
        ClientRateLimitStore localStore = new ClientRateLimitStore();
        RateLimiter localFallback = new TokenBucketRateLimiter(config, localStore);
        DistributedRateLimitStore distributedStore = new InMemoryDistributedRateLimitStore();
        rateLimiter = buildDistributedLimiter(config, distributedStore, localFallback);

        System.out.println("Config: " + config.getMaxRequests() + 
                         " requests per " + config.getWindowSizeMillis() + "ms");
        System.out.println("Strategy: " + config.getStrategy() + " (distributed)");
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

            if (clientId == null || clientId.isEmpty()) {
                response.status(400);
                return gson.toJson(Map.of("error", "clientId is required"));
            }

            // Check rate limit
            boolean allowed = rateLimiter.allowRequest(clientId);
            int remaining = rateLimiter.getRemainingRequests(clientId);

            if (allowed) {
                response.status(200);
                response.header("X-RateLimit-Remaining", String.valueOf(remaining));
                
                return gson.toJson(Map.of(
                    "allowed", true,
                    "remaining", remaining,
                    "message", "Request allowed"
                ));
            } else {
                response.status(429); // Too Many Requests
                long resetTime = rateLimiter.getResetTimeMillis(clientId);
                response.header("X-RateLimit-Remaining", "0");
                response.header("Retry-After", String.valueOf(resetTime / 1000));
                
                return gson.toJson(Map.of(
                    "allowed", false,
                    "remaining", 0,
                    "resetAfterMs", resetTime,
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

    private static RateLimiter buildDistributedLimiter(
        RateLimitConfig config,
        DistributedRateLimitStore store,
        RateLimiter fallbackLimiter
    ) {
        return switch (config.getStrategy()) {
            case TOKEN_BUCKET -> new DistributedTokenBucketRateLimiter(config, store, fallbackLimiter);
            case SLIDING_WINDOW -> new DistributedSlidingWindowRateLimiter(config, store, fallbackLimiter);
            case FIXED_WINDOW -> new DistributedFixedWindowRateLimiter(config, store, fallbackLimiter);
        };
    }
}
