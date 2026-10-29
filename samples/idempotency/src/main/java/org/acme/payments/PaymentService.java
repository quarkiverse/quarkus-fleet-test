package org.acme.payments;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class PaymentService {

    private final AgroalDataSource dataSource;
    private final int instance;

    public PaymentService(AgroalDataSource dataSource,
            @ConfigProperty(name = "fleet-test.instance-index", defaultValue = "0") int instance) {
        this.dataSource = dataSource;
        this.instance = instance;
    }

    // Charges the account. A delay stands in for the time a real payment provider takes.
    public Map<String, Object> pay(Map<String, Object> payment, long delayMillis)
            throws SQLException, InterruptedException {
        Thread.sleep(delayMillis);
        try (Connection c = dataSource.getConnection();
                PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO payments (account, amount, instance) VALUES (?, ?, ?) RETURNING id")) {
            insert.setString(1, (String) payment.get("account"));
            insert.setInt(2, ((Number) payment.get("amount")).intValue());
            insert.setInt(3, instance);
            try (ResultSet rs = insert.executeQuery()) {
                rs.next();
                Map<String, Object> receipt = new LinkedHashMap<>();
                receipt.put("id", rs.getInt(1));
                receipt.put("instance", instance);
                return receipt;
            }
        }
    }

    public Map<String, Object> summary() throws SQLException {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM payments")) {
            rs.next();
            return Map.of("payments", rs.getLong(1));
        }
    }
}
