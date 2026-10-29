package org.acme.leader;

import java.sql.SQLException;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/")
public class LeaderResource {

    private final LeaderService leader;

    public LeaderResource(LeaderService leader) {
        this.leader = leader;
    }

    // What this instance believes: whether it leads, and the item it is working on.
    @GET
    @Path("/leader")
    public Map<String, Object> leader() {
        return leader.leader();
    }

    // What the database holds: how many records, and the items recorded more than once.
    @GET
    @Path("/done")
    public Map<String, Object> done() throws SQLException {
        return leader.done();
    }
}
