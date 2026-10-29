package org.acme.pricing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import io.agroal.api.AgroalDataSource;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;

@ApplicationScoped
public class PriceService {

    private final AgroalDataSource dataSource;
    private final FleetWideInvalidation fleetWide;

    public PriceService(AgroalDataSource dataSource, FleetWideInvalidation fleetWide) {
        this.dataSource = dataSource;
        this.fleetWide = fleetWide;
    }

    @CacheResult(cacheName = "prices")
    public int price(String sku) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement select = c.prepareStatement("SELECT amount FROM prices WHERE sku = ?")) {
            select.setString(1, sku);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException();
                }
                return rs.getInt(1);
            }
        }
    }

    // Evicts the entry for the sku, in this process only, before the update runs.
    @CacheInvalidate(cacheName = "prices")
    public void change(@CacheKey String sku, Map<String, Object> price) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement update = c.prepareStatement("UPDATE prices SET amount = ? WHERE sku = ?")) {
            update.setInt(1, ((Number) price.get("amount")).intValue());
            update.setString(2, sku);
            update.executeUpdate();
        }
        fleetWide.priceChanged(sku);
    }
}
