package org.acme.pricing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.pubsub.PubSubCommands;
import io.quarkus.runtime.StartupEvent;

/**
 * The fix: the instance that changes a price publishes the sku on a Redis channel, and every instance, subscribed
 * at startup, evicts the entry from its own cache. Switched on with {@code pricing.invalidate-fleet-wide}; off,
 * the write evicts only locally.
 */
@ApplicationScoped
public class FleetWideInvalidation {

    private static final String CHANNEL = "price-changed";

    private final RedisDataSource redis;
    private final Cache prices;
    private final boolean enabled;

    private PubSubCommands<String> pubsub;

    public FleetWideInvalidation(RedisDataSource redis, @CacheName("prices") Cache prices,
            @ConfigProperty(name = "pricing.invalidate-fleet-wide", defaultValue = "false") boolean enabled) {
        this.redis = redis;
        this.prices = prices;
        this.enabled = enabled;
    }

    void subscribe(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        pubsub = redis.pubsub(String.class);
        // messages arrive on an event loop thread, so the eviction must not block
        pubsub.subscribe(CHANNEL, sku -> prices.invalidate(sku).subscribe().with(ignored -> {
        }));
    }

    void priceChanged(String sku) {
        if (enabled) {
            pubsub.publish(CHANNEL, sku);
        }
    }
}
