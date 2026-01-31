package com.meta.ratelimiter;

/**
 * Resolves a client's subscription tier.
 */
public interface ClientTierResolver {
    ClientTier resolveTier(String clientId);
}