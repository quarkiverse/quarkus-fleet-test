package org.acme.imports;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;

/**
 * A long-running import, processed row by row in a background thread of the instance that accepted it. After
 * each row the import's progress and a heartbeat are written to the database, so the database always knows how
 * far the import got and when its owner was last alive. A thread dies with its process, and nothing but this
 * checkpoint survives the kill: without a reaper, an import whose owner died stays where it was for ever.
 */
@ApplicationScoped
public class ImportJob {

    private final AgroalDataSource dataSource;
    private final int instance;

    // The fix: every instance looks for imports whose owner stopped heartbeating and resumes them.
    private final boolean resumable;

    public ImportJob(AgroalDataSource dataSource,
            @ConfigProperty(name = "fleet-test.instance-index", defaultValue = "0") int instance,
            @ConfigProperty(name = "imports.resumable", defaultValue = "false") boolean resumable) {
        this.dataSource = dataSource;
        this.instance = instance;
        this.resumable = resumable;
    }

    // Processes rows from..total of the import on this instance, in the background.
    void start(int id, int from, int total) {
        Thread worker = new Thread(() -> process(id, from, total), "import-" + id);
        worker.start();
    }

    private void process(int id, int from, int total) {
        try {
            for (int row = from; row <= total; row++) {
                // the work itself, long enough for an import of a few hundred rows to outlive a kill
                Thread.sleep(10);
                record(id, row);
            }
        } catch (Exception e) {
            Log.errorf(e, "Import %d failed", id);
        }
    }

    // The row and the checkpoint in one transaction: a kill between them would leave a row the resumed import
    // does again, and the primary key would reject it.
    private void record(int id, int row) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement insert = c
                    .prepareStatement("INSERT INTO import_rows (import_id, row_no, instance) VALUES (?, ?, ?)")) {
                insert.setInt(1, id);
                insert.setInt(2, row);
                insert.setInt(3, instance);
                insert.executeUpdate();
            }
            try (PreparedStatement checkpoint = c
                    .prepareStatement("UPDATE imports SET processed = ?, heartbeat = now() WHERE id = ?")) {
                checkpoint.setInt(1, row);
                checkpoint.setInt(2, id);
                checkpoint.executeUpdate();
            }
            c.commit();
        }
    }

    // The reaper. An owner heartbeats after every row, so one that has been silent for seconds is dead. The claim
    // sets the heartbeat as it takes the import, so two reapers cannot take the same one: the second evaluates
    // the predicate after the first committed and finds the heartbeat fresh.
    @Scheduled(every = "1s")
    void resumeAbandonedImports() throws SQLException {
        if (!resumable) {
            return;
        }
        List<int[]> claimed = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
                PreparedStatement claim = c.prepareStatement("UPDATE imports SET owner = ?, heartbeat = now() "
                        + "WHERE processed < total AND heartbeat < now() - interval '3 seconds' "
                        + "RETURNING id, processed, total")) {
            claim.setInt(1, instance);
            try (ResultSet rs = claim.executeQuery()) {
                while (rs.next()) {
                    claimed.add(new int[] { rs.getInt(1), rs.getInt(2), rs.getInt(3) });
                }
            }
        }
        for (int[] job : claimed) {
            Log.infof("Resuming import %d from row %d", job[0], job[1] + 1);
            start(job[0], job[1] + 1, job[2]);
        }
    }
}
