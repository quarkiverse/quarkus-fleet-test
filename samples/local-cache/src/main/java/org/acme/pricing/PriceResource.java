package org.acme.pricing;

import java.sql.SQLException;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/**
 * Prices, read from the database once and then served from a local cache until a change evicts them. The cache
 * lives inside the process: with two instances there are two caches, and {@code @CacheInvalidate} knows only
 * the one in the process that handles the write.
 */
@Path("/prices")
public class PriceResource {

    private final PriceService prices;

    public PriceResource(PriceService prices) {
        this.prices = prices;
    }

    @GET
    @Path("/{sku}")
    public int price(@PathParam("sku") String sku) throws SQLException {
        return prices.price(sku);
    }

    @PUT
    @Path("/{sku}")
    public void change(@PathParam("sku") String sku, Map<String, Object> price) throws SQLException {
        prices.change(sku, price);
    }
}
