package org.acme.reports;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class ReportsService {

    private final AgroalDataSource dataSource;

    public ReportsService(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    // How many reports were sent, and for how many distinct ticks: equal when the job runs once per tick.
    public Map<String, Long> summary() throws SQLException {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*), COUNT(DISTINCT tick) FROM reports_sent")) {
            rs.next();
            return Map.of("sent", rs.getLong(1), "ticks", rs.getLong(2));
        }
    }
}
