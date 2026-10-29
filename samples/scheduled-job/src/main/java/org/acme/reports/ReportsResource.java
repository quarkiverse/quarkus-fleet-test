package org.acme.reports;

import java.sql.SQLException;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/reports")
public class ReportsResource {

    private final ReportsService reports;

    public ReportsResource(ReportsService reports) {
        this.reports = reports;
    }

    @GET
    public Map<String, Long> summary() throws SQLException {
        return reports.summary();
    }
}
