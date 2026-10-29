package org.acme.quotes;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.redis.datasource.RedisDataSource;

/**
 * The fix: a count per client in Redis, which every instance sees, instead of one per JVM. INCR is atomic, so two
 * instances serving the same client at the same time cannot both let a sixth request through. The key expires with
 * the window, set when the first request of the window creates it. The filter runs before the resource method, so
 * when it is on the fleet-wide count decides and the per-JVM {@code @RateLimit} never gets to.
 */
@Provider
public class SharedLimitFilter implements ContainerRequestFilter {

    static final int LIMIT = 5;
    static final long WINDOW_SECONDS = 60;

    private final boolean shared;
    private final RedisDataSource redis;

    public SharedLimitFilter(@ConfigProperty(name = "quotes.limit.shared", defaultValue = "false") boolean shared,
            RedisDataSource redis) {
        this.shared = shared;
        this.redis = redis;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (!shared || !request.getUriInfo().getPath().startsWith("/quotes")) {
            return;
        }
        String client = request.getHeaderString("X-Client");
        String key = "quotes:" + (client == null ? "anonymous" : client);
        long count = redis.value(Long.class).incr(key);
        if (count == 1) {
            redis.key().expire(key, WINDOW_SECONDS);
        }
        if (count > LIMIT) {
            request.abortWith(Response.status(429).build());
        }
    }
}
