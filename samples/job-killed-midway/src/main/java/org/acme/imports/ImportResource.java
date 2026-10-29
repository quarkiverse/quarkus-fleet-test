package org.acme.imports;

import java.sql.SQLException;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/imports")
public class ImportResource {

    private final ImportService imports;

    public ImportResource(ImportService imports) {
        this.imports = imports;
    }

    // Creates an import of the given number of rows and starts processing it on this instance.
    @POST
    public int start(@QueryParam("rows") int rows) throws SQLException {
        return imports.start(rows);
    }

    // What the database holds about the import: its size, the checkpoint, the rows actually imported and the
    // instances that imported them. Any instance can answer, the import itself runs elsewhere.
    @GET
    @Path("/{id}")
    public Map<String, Object> status(@PathParam("id") int id) throws SQLException {
        return imports.status(id);
    }
}
