package org.acme.leader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * Work that one instance at a time must do, coordinated with a lease in the shared database: whoever holds the
 * lease processes the next item. The lease expires if its holder stops renewing it, so a frozen leader loses it
 * and another instance takes over. The frozen leader does not know that: when it runs again, it finishes the
 * item it was on and records it, a second time, unless its write carries the token it got with the lease and
 * the database checks it.
 */
@ApplicationScoped
public class LeaderJob {

    private final AgroalDataSource dataSource;
    private final int instance;

    // The fix: a write is accepted only while the lease still carries the token it was acquired with.
    private final boolean fenced;

    public LeaderJob(AgroalDataSource dataSource,
            @ConfigProperty(name = "fleet-test.instance-index", defaultValue = "0") int instance,
            @ConfigProperty(name = "lease.fenced", defaultValue = "false") boolean fenced) {
        this.dataSource = dataSource;
        this.instance = instance;
        this.fenced = fenced;
    }

    // The token of the lease this instance holds, or -1.
    private volatile long token = -1;
    // The item being processed, or null between items.
    private volatile Integer working;

    // Takes the lease if it is free or expired, renews it if this instance holds it. A new holder gets a new token.
    @Scheduled(every = "PT0.2S")
    void renewLease() throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement renew = c.prepareStatement("UPDATE lease SET owner = ?, "
                        + "token = CASE WHEN owner = ? THEN token ELSE token + 1 END, "
                        + "expires_at = now() + interval '1 second' "
                        + "WHERE id = 1 AND (owner = ? OR expires_at < now()) RETURNING token")) {
            renew.setInt(1, instance);
            renew.setInt(2, instance);
            renew.setInt(3, instance);
            try (ResultSet rs = renew.executeQuery()) {
                token = rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    // The leader's work: the next item, which takes a while.
    @Scheduled(every = "PT0.2S", concurrentExecution = ConcurrentExecution.SKIP)
    void processNextItem() throws Exception {
        long myToken = token;
        if (myToken < 0) {
            return;
        }
        int item = nextItem();
        working = item;
        try {
            Thread.sleep(3000);
            record(item, myToken);
        } finally {
            working = null;
        }
    }

    private int nextItem() throws SQLException {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(item), 0) + 1 FROM done")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void record(int item, long myToken) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            if (fenced) {
                try (PreparedStatement fence = c
                        .prepareStatement("UPDATE lease SET expires_at = expires_at WHERE id = 1 AND token = ?")) {
                    fence.setLong(1, myToken);
                    if (fence.executeUpdate() == 0) {
                        c.rollback();
                        Log.warnf("Item %d not recorded: the lease moved on from token %d", item, myToken);
                        return;
                    }
                }
            }
            try (PreparedStatement insert = c.prepareStatement("INSERT INTO done (item, instance, token) VALUES (?, ?, ?)")) {
                insert.setInt(1, item);
                insert.setInt(2, instance);
                insert.setLong(3, myToken);
                insert.executeUpdate();
            }
            c.commit();
        }
    }

    boolean isLeader() {
        return token >= 0;
    }

    Integer working() {
        return working;
    }
}
