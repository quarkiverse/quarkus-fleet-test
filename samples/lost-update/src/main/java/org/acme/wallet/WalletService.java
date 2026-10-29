package org.acme.wallet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;

/**
 * The wallet business logic: reading a balance and withdrawing from it. The withdrawal is synchronized, which is
 * enough to make it safe on one instance and not enough across the fleet, where each instance has its own monitor.
 */
@ApplicationScoped
public class WalletService {

    private final AgroalDataSource dataSource;

    // The fix: read the balance with a row lock, so that a concurrent withdrawal waits until this one has committed.
    private final boolean lockRows;

    public WalletService(AgroalDataSource dataSource,
            @ConfigProperty(name = "wallet.lock-rows", defaultValue = "false") boolean lockRows) {
        this.dataSource = dataSource;
        this.lockRows = lockRows;
    }

    public int balance(int id) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement select = c.prepareStatement("SELECT balance FROM wallets WHERE id = ?")) {
            select.setInt(1, id);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Withdraws from the wallet: reads the balance, checks it covers the amount, and writes the new balance. A
    // delay between the read and the write stands in for the call to a fraud-check service.
    // One withdrawal at a time, which is enough on one instance.
    public synchronized int withdraw(int id, int amount, long holdMillis) throws SQLException, InterruptedException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            int balance;
            try (PreparedStatement select = c.prepareStatement(
                    "SELECT balance FROM wallets WHERE id = ?" + (lockRows ? " FOR UPDATE" : ""))) {
                select.setInt(1, id);
                try (ResultSet rs = select.executeQuery()) {
                    rs.next();
                    balance = rs.getInt(1);
                }
            }
            if (amount > balance) {
                c.rollback();
                throw new WebApplicationException("insufficient funds", 409);
            }
            Thread.sleep(holdMillis);
            try (PreparedStatement update = c.prepareStatement("UPDATE wallets SET balance = ? WHERE id = ?")) {
                update.setInt(1, balance - amount);
                update.setInt(2, id);
                update.executeUpdate();
            }
            c.commit();
            return balance - amount;
        }
    }
}
