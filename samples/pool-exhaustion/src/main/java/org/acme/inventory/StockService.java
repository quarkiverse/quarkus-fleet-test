package org.acme.inventory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.enterprise.context.ApplicationScoped;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class StockService {

    private final AgroalDataSource dataSource;

    public StockService(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    // How many items are in stock. The sleep stands in for a slow query: it keeps the connection busy for longer
    // than the acquisition timeout, so a request that finds the pool empty fails instead of waiting its turn.
    public String count() throws SQLException {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT pg_sleep(2), COUNT(*) FROM stock")) {
            rs.next();
            return String.valueOf(rs.getLong(2));
        }
    }
}
