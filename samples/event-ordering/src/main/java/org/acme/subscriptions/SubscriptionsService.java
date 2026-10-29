package org.acme.subscriptions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class SubscriptionsService {

    private final AgroalDataSource dataSource;
    private final int instance;

    // The fix: lock the aggregate row for the whole apply and apply only events newer than the last one applied.
    private final boolean orderedApply;

    public SubscriptionsService(AgroalDataSource dataSource,
            @ConfigProperty(name = "fleet-test.instance-index", defaultValue = "0") int instance,
            @ConfigProperty(name = "events.ordered-apply", defaultValue = "false") boolean orderedApply) {
        this.dataSource = dataSource;
        this.instance = instance;
        this.orderedApply = orderedApply;
    }

    // Enqueue one event for a subscription. Returns the new event id so the test can apply it by id.
    public int enqueue(int subscriptionId, int seq, String type) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO events (subscription_id, seq, type) VALUES (?, ?, ?) RETURNING id")) {
            insert.setInt(1, subscriptionId);
            insert.setInt(2, seq);
            insert.setString(3, type);
            try (ResultSet rs = insert.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Apply one specific event, the way a poller would: read it, apply it to its subscription, commit. The whole
    // run is one transaction on one connection, so the lock the fix takes lasts until the commit. holdMillis
    // stands in for the work of applying and widens the window in which the two instances interleave.
    // Synchronized because in production the poller is a single @Scheduled method per instance: it applies one
    // event at a time in one JVM, so one instance never applies a subscription out of order. Only a second
    // instance can, which is the point.
    public synchronized int apply(int eventId, long holdMillis)
            throws SQLException, InterruptedException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            int subscriptionId;
            int seq;
            String type;
            try (PreparedStatement s = c.prepareStatement("SELECT subscription_id, seq, type FROM events WHERE id = ?")) {
                s.setInt(1, eventId);
                try (ResultSet rs = s.executeQuery()) {
                    if (!rs.next()) {
                        throw new NotFoundException("no event " + eventId);
                    }
                    subscriptionId = rs.getInt(1);
                    seq = rs.getInt(2);
                    type = rs.getString(3);
                }
            }

            // The fix locks the aggregate row first, so a competing apply of the same subscription blocks here
            // until this one commits. Without it the two applies read and write the row concurrently.
            if (orderedApply) {
                try (PreparedStatement lock = c.prepareStatement(
                        "SELECT last_seq FROM subscriptions WHERE id = ? FOR UPDATE")) {
                    lock.setInt(1, subscriptionId);
                    try (ResultSet rs = lock.executeQuery()) {
                        rs.next();
                    }
                }
            }

            int lastSeq;
            try (PreparedStatement s = c.prepareStatement("SELECT last_seq FROM subscriptions WHERE id = ?")) {
                s.setInt(1, subscriptionId);
                try (ResultSet rs = s.executeQuery()) {
                    rs.next();
                    lastSeq = rs.getInt(1);
                }
            }

            Thread.sleep(holdMillis);

            if (orderedApply) {
                // Only apply events newer than the last one applied, so a stale lower-seq event cannot overwrite
                // a later one. A competing apply that blocked on the lock sees the updated last_seq once unblocked.
                if (seq > lastSeq) {
                    update(c, subscriptionId, stateFor(type), seq);
                }
            } else {
                // Blindly apply: no lock, no seq check, so whichever apply commits last wins, in commit order
                // rather than event order.
                update(c, subscriptionId, stateFor(type), seq);
            }

            c.commit();
            return instance;
        }
    }

    private static void update(Connection c, int subscriptionId, String state, int seq) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE subscriptions SET state = ?, last_seq = ? WHERE id = ?")) {
            s.setString(1, state);
            s.setInt(2, seq);
            s.setInt(3, subscriptionId);
            s.executeUpdate();
        }
    }

    private static String stateFor(String type) {
        return switch (type) {
            case "activate" -> "active";
            case "cancel" -> "cancelled";
            default -> throw new IllegalArgumentException("unknown event type " + type);
        };
    }

    public record Subscription(String state, int lastSeq) {
    }

    public Subscription subscription(int id) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement s = c.prepareStatement("SELECT state, last_seq FROM subscriptions WHERE id = ?")) {
            s.setInt(1, id);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("no subscription " + id);
                }
                return new Subscription(rs.getString(1), rs.getInt(2));
            }
        }
    }
}
