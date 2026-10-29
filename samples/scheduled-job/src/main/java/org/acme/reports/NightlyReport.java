package org.acme.reports;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;
import io.quarkus.scheduler.Scheduled;

/**
 * A job that must run once per tick for the whole application, say a report that is e-mailed. The scheduler is
 * in-memory and per instance, so with two instances it runs twice, unless each run first claims its tick in the
 * shared database.
 */
@ApplicationScoped
public class NightlyReport {

    private final AgroalDataSource dataSource;
    private final int instance;

    // The fix: claim the tick with a unique insert, and only the instance that wins does the work.
    private final boolean claimTick;

    public NightlyReport(AgroalDataSource dataSource,
            @ConfigProperty(name = "fleet-test.instance-index", defaultValue = "0") int instance,
            @ConfigProperty(name = "report.claim-tick", defaultValue = "false") boolean claimTick) {
        this.dataSource = dataSource;
        this.instance = instance;
        this.claimTick = claimTick;
    }

    @Scheduled(every = "1s")
    void sendReport() throws SQLException {
        long tick = System.currentTimeMillis() / 1000;
        try (Connection c = dataSource.getConnection()) {
            if (claimTick) {
                try (PreparedStatement claim = c
                        .prepareStatement("INSERT INTO report_claims (tick) VALUES (?) ON CONFLICT DO NOTHING")) {
                    claim.setLong(1, tick);
                    if (claim.executeUpdate() == 0) {
                        return; // another instance sent this tick's report
                    }
                }
            }
            try (PreparedStatement send = c.prepareStatement("INSERT INTO reports_sent (tick, instance) VALUES (?, ?)")) {
                send.setLong(1, tick);
                send.setInt(2, instance);
                send.executeUpdate();
            }
        }
    }
}
