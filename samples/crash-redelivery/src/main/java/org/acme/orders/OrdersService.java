package org.acme.orders;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class OrdersService {

    private final OrderProcessor processor;
    private final AgroalDataSource dataSource;

    public OrdersService(OrderProcessor processor, AgroalDataSource dataSource) {
        this.processor = processor;
        this.dataSource = dataSource;
    }

    public void place(int count) {
        processor.place(count);
    }

    // Deliveries and shipments so far, and which instance did them.
    public Map<String, Object> summary() throws SQLException {
        Map<String, Object> summary = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            summary.put("received", count(s, "received"));
            summary.put("receivedBy", byInstance(s, "received"));
            summary.put("shipped", count(s, "shipments"));
            summary.put("shippedBy", byInstance(s, "shipments"));
        }
        return summary;
    }

    private static long count(Statement s, String table) throws SQLException {
        try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Map<Integer, Long> byInstance(Statement s, String table) throws SQLException {
        Map<Integer, Long> counts = new TreeMap<>();
        try (ResultSet rs = s.executeQuery("SELECT instance, COUNT(*) FROM " + table + " GROUP BY instance")) {
            while (rs.next()) {
                counts.put(rs.getInt(1), rs.getLong(2));
            }
        }
        return counts;
    }
}
