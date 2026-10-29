package org.acme.accounts;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class AccountService {

    public enum Outcome {
        OK,
        INSUFFICIENT_FUNDS,
        DEADLOCK
    }

    private final AgroalDataSource dataSource;

    // The fix: acquire the two row locks in a consistent global order (lower id first), the same whichever way
    // the money moves, so two transfers can never grab them in opposite order and deadlock.
    private final boolean lockOrderById;

    public AccountService(AgroalDataSource dataSource,
            @ConfigProperty(name = "transfer.lock-order-by-id", defaultValue = "false") boolean lockOrderById) {
        this.dataSource = dataSource;
        this.lockOrderById = lockOrderById;
    }

    public int balance(int id) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement select = c.prepareStatement("SELECT balance FROM accounts WHERE id = ?")) {
            select.setInt(1, id);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Moves amount from one account to the other inside one transaction, locking both rows with SELECT ... FOR
    // UPDATE. One transfer at a time, which is enough on one instance: the lock lives only in this JVM.
    public synchronized Outcome transfer(int from, int to, int amount, long holdMillis)
            throws SQLException, InterruptedException {
        // when the fix is off, lock in request order (from then to); when on, always lock the lower id first
        int firstId = lockOrderById ? Math.min(from, to) : from;
        int secondId = lockOrderById ? Math.max(from, to) : to;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int firstBalance = lockRow(c, firstId);
                // stands in for work between the two locks, and guarantees both transfers hold their first lock
                // before either asks for the second
                Thread.sleep(holdMillis);
                int secondBalance = lockRow(c, secondId);

                int fromBalance = firstId == from ? firstBalance : secondBalance;
                int toBalance = firstId == to ? firstBalance : secondBalance;

                if (amount > fromBalance) {
                    c.rollback();
                    return Outcome.INSUFFICIENT_FUNDS;
                }
                updateBalance(c, from, fromBalance - amount);
                updateBalance(c, to, toBalance + amount);
                c.commit();
                return Outcome.OK;
            } catch (SQLException e) {
                c.rollback();
                // 40P01 is Postgres deadlock_detected: the database aborted this transaction to break the cycle
                if ("40P01".equals(e.getSQLState())) {
                    return Outcome.DEADLOCK;
                }
                throw e;
            }
        }
    }

    private static int lockRow(Connection c, int id) throws SQLException {
        try (PreparedStatement select = c.prepareStatement("SELECT balance FROM accounts WHERE id = ? FOR UPDATE")) {
            select.setInt(1, id);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void updateBalance(Connection c, int id, int balance) throws SQLException {
        try (PreparedStatement update = c.prepareStatement("UPDATE accounts SET balance = ? WHERE id = ?")) {
            update.setInt(1, balance);
            update.setInt(2, id);
            update.executeUpdate();
        }
    }
}
