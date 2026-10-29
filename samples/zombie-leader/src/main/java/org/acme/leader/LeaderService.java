package org.acme.leader;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class LeaderService {

    private final LeaderJob job;
    private final AgroalDataSource dataSource;
    private final int instance;

    public LeaderService(LeaderJob job, AgroalDataSource dataSource,
            @ConfigProperty(name = "fleet-test.instance-index", defaultValue = "0") int instance) {
        this.job = job;
        this.dataSource = dataSource;
        this.instance = instance;
    }

    // What this instance believes: whether it leads, and the item it is working on.
    public Map<String, Object> leader() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("instance", instance);
        state.put("leader", job.isLeader());
        state.put("working", job.working());
        return state;
    }

    // What the database holds: how many records, and the items recorded more than once.
    public Map<String, Object> done() throws SQLException {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Integer> items = new ArrayList<>();
        List<Integer> duplicates = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT item, COUNT(*) FROM done GROUP BY item ORDER BY item")) {
                while (rs.next()) {
                    items.add(rs.getInt(1));
                    if (rs.getLong(2) > 1) {
                        duplicates.add(rs.getInt(1));
                    }
                }
            }
        }
        summary.put("items", items);
        summary.put("duplicates", duplicates);
        return summary;
    }
}
