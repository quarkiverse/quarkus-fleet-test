package org.acme.notifications;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class NotificationsService {

    private final AgroalDataSource dataSource;
    private final int instance;

    // The fix: the poller locks the rows it selects and skips the rows another poller has locked.
    private final boolean skipLocked;

    // Drains of this instance that have selected their rows and are at the mail provider; for the test to see.
    private final AtomicInteger holding = new AtomicInteger();

    public NotificationsService(AgroalDataSource dataSource,
            @ConfigProperty(name = "fleet-test.instance-index", defaultValue = "0") int instance,
            @ConfigProperty(name = "outbox.skip-locked", defaultValue = "false") boolean skipLocked) {
        this.dataSource = dataSource;
        this.instance = instance;
        this.skipLocked = skipLocked;
    }

    public void enqueue(int count) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement insert = c.prepareStatement("INSERT INTO outbox (status) VALUES ('PENDING')")) {
            for (int i = 0; i < count; i++) {
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    // One run of the poller: the pending rows, a delay standing in for the mail provider, then the bookkeeping.
    // The whole run is one transaction on one connection, so that the locks the fix takes last until the commit.
    // Synchronized because in production the poller is a single @Scheduled method per instance: it never runs
    // twice at once in one JVM, so one instance never double-sends. Only a second instance can, which is the point.
    public synchronized int drain(long holdMillis)
            throws SQLException, InterruptedException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            List<Integer> ids = new ArrayList<>();
            try (Statement s = c.createStatement();
                    ResultSet rs = s.executeQuery("SELECT id FROM outbox WHERE status = 'PENDING' ORDER BY id LIMIT 100"
                            + (skipLocked ? " FOR UPDATE SKIP LOCKED" : ""))) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
            holding.incrementAndGet();
            try {
                Thread.sleep(holdMillis);
            } finally {
                holding.decrementAndGet();
            }
            try (PreparedStatement log = c.prepareStatement("INSERT INTO sent_log (notification_id, instance) VALUES (?, ?)");
                    PreparedStatement sent = c.prepareStatement("UPDATE outbox SET status = 'SENT' WHERE id = ?")) {
                for (int id : ids) {
                    log.setInt(1, id);
                    log.setInt(2, instance);
                    log.executeUpdate();
                    sent.setInt(1, id);
                    sent.executeUpdate();
                }
            }
            c.commit();
            return ids.size();
        }
    }

    public int holding() {
        return holding.get();
    }

    public record Summary(long pending, long sent, List<Integer> duplicates) {
    }

    // What the database holds: rows still pending, rows in the sent log, and the notifications sent more than once.
    public Summary summary() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            long pending;
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")) {
                rs.next();
                pending = rs.getLong(1);
            }
            long sent;
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM sent_log")) {
                rs.next();
                sent = rs.getLong(1);
            }
            List<Integer> duplicates = new ArrayList<>();
            try (ResultSet rs = s.executeQuery(
                    "SELECT notification_id FROM sent_log GROUP BY notification_id HAVING COUNT(*) > 1 ORDER BY notification_id")) {
                while (rs.next()) {
                    duplicates.add(rs.getInt(1));
                }
            }
            return new Summary(pending, sent, duplicates);
        }
    }
}
