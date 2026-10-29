package org.acme.orders;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.agroal.api.AgroalDataSource;
import io.smallrye.common.annotation.Blocking;

/**
 * Orders arrive on a Kafka topic and each one is shipped, with a row in the shared database. Kafka delivers at
 * least once: offsets are committed some time after a record was processed, so an instance that crashes in
 * between leaves records that the next holder of the partition receives again. Shipping them again is the bug;
 * remembering what was shipped, in the same database, is the fix.
 */
@ApplicationScoped
public class OrderProcessor {

    private final AgroalDataSource dataSource;
    private final Emitter<String> orders;
    private final int instance;

    // The fix: claim the order id with a unique insert, and ship only when this is its first delivery.
    private final boolean idempotent;

    public OrderProcessor(AgroalDataSource dataSource, @Channel("orders-out") Emitter<String> orders,
            @ConfigProperty(name = "fleet-test.instance-index", defaultValue = "0") int instance,
            @ConfigProperty(name = "orders.idempotent", defaultValue = "false") boolean idempotent) {
        this.dataSource = dataSource;
        this.orders = orders;
        this.instance = instance;
        this.idempotent = idempotent;
    }

    void place(int count) {
        for (int i = 1; i <= count; i++) {
            orders.send("order-" + i);
        }
    }

    @Incoming("orders")
    @Blocking
    void ship(String orderId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            insert(c, "INSERT INTO received (order_id, instance) VALUES (?, ?)", orderId);
            if (idempotent) {
                try (PreparedStatement claim = c
                        .prepareStatement("INSERT INTO shipped_orders (order_id) VALUES (?) ON CONFLICT DO NOTHING")) {
                    claim.setString(1, orderId);
                    if (claim.executeUpdate() == 0) {
                        return; // shipped already, by this instance or another, before the crash
                    }
                }
            }
            insert(c, "INSERT INTO shipments (order_id, instance) VALUES (?, ?)", orderId);
        }
    }

    private void insert(Connection c, String sql, String orderId) throws SQLException {
        try (PreparedStatement insert = c.prepareStatement(sql)) {
            insert.setString(1, orderId);
            insert.setInt(2, instance);
            insert.executeUpdate();
        }
    }
}
