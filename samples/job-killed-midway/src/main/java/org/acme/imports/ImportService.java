package org.acme.imports;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class ImportService {

    private final AgroalDataSource dataSource;
    private final ImportJob job;
    private final int instance;

    public ImportService(AgroalDataSource dataSource, ImportJob job,
            @ConfigProperty(name = "fleet-test.instance-index", defaultValue = "0") int instance) {
        this.dataSource = dataSource;
        this.job = job;
        this.instance = instance;
    }

    // Creates an import of the given number of rows and starts processing it on this instance.
    public int start(int rows) throws SQLException {
        int id;
        try (Connection c = dataSource.getConnection();
                PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO imports (total, owner, heartbeat) VALUES (?, ?, now()) RETURNING id")) {
            insert.setInt(1, rows);
            insert.setInt(2, instance);
            try (ResultSet rs = insert.executeQuery()) {
                rs.next();
                id = rs.getInt(1);
            }
        }
        job.start(id, 1, rows);
        return id;
    }

    // What the database holds about the import: its size, the checkpoint, the rows actually imported and the
    // instances that imported them. Any instance can answer, the import itself runs elsewhere.
    public Map<String, Object> status(int id) throws SQLException {
        Map<String, Object> status = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement select = c.prepareStatement("SELECT total, processed FROM imports WHERE id = ?")) {
                select.setInt(1, id);
                try (ResultSet rs = select.executeQuery()) {
                    rs.next();
                    status.put("total", rs.getInt(1));
                    status.put("processed", rs.getInt(2));
                }
            }
            try (PreparedStatement count = c.prepareStatement("SELECT COUNT(*) FROM import_rows WHERE import_id = ?")) {
                count.setInt(1, id);
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    status.put("rows", rs.getLong(1));
                }
            }
            List<Integer> instances = new ArrayList<>();
            try (PreparedStatement distinct = c.prepareStatement(
                    "SELECT DISTINCT instance FROM import_rows WHERE import_id = ? ORDER BY instance")) {
                distinct.setInt(1, id);
                try (ResultSet rs = distinct.executeQuery()) {
                    while (rs.next()) {
                        instances.add(rs.getInt(1));
                    }
                }
            }
            status.put("instances", instances);
        }
        return status;
    }
}
