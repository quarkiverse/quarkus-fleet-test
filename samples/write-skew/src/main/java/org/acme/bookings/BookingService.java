package org.acme.bookings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class BookingService {

    // At most this many bookings may exist for a slot. One keeps the scenario small: a slot holds a single booking.
    static final int CAPACITY = 1;

    private final AgroalDataSource dataSource;

    public BookingService(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int count(String slot) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement select = c.prepareStatement("SELECT COUNT(*) FROM bookings WHERE slot = ?")) {
            select.setString(1, slot);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Books the slot if it is not already full: counts the existing bookings, and if there is room, inserts one.
    // A delay between the count and the insert stands in for work and widens the window.
    // One request at a time, which is enough on one instance.
    public synchronized int book(String slot, String who, long holdMillis)
            throws SQLException, InterruptedException {
        // The count invariant cannot be expressed as a row lock (the rows that would break it do not exist yet) or
        // as a unique constraint, so under serializable isolation Postgres aborts the losing transaction with
        // SQLSTATE 40001 and the booking is retried. Under read-committed 40001 never fires and this loop is a no-op.
        for (int attempt = 0; attempt < 5; attempt++) {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                int count;
                try (PreparedStatement select = c.prepareStatement("SELECT COUNT(*) FROM bookings WHERE slot = ?")) {
                    select.setString(1, slot);
                    try (ResultSet rs = select.executeQuery()) {
                        rs.next();
                        count = rs.getInt(1);
                    }
                }
                if (count >= CAPACITY) {
                    c.rollback();
                    throw new WebApplicationException("slot full", 409);
                }
                Thread.sleep(holdMillis);
                try (PreparedStatement insert = c
                        .prepareStatement("INSERT INTO bookings (slot, who) VALUES (?, ?)")) {
                    insert.setString(1, slot);
                    insert.setString(2, who);
                    insert.executeUpdate();
                }
                c.commit();
                return count + 1;
            } catch (SQLException e) {
                // "40001" is serialization_failure: the transaction lost a serialization race and must be retried.
                if (!"40001".equals(e.getSQLState())) {
                    throw e;
                }
            }
        }
        // Still conflicting after the retries: treat the slot as full.
        throw new WebApplicationException("slot full", 409);
    }
}
