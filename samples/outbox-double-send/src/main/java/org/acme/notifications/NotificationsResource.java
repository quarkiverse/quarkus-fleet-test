package org.acme.notifications;

import java.sql.SQLException;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Notifications go through an outbox table: a request inserts a PENDING row, and a poller picks the pending rows
 * up, hands them to the mail provider and marks them SENT. In production the poller is a {@code @Scheduled}
 * method, which runs on every instance, each against the same table. Here the drain is triggered by hand so the
 * test decides when the two instances overlap.
 */
@Path("/")
public class NotificationsResource {

    private final NotificationsService notifications;

    public NotificationsResource(NotificationsService notifications) {
        this.notifications = notifications;
    }

    @POST
    @Path("/notifications/{count}")
    public void enqueue(@PathParam("count") int count) throws SQLException {
        notifications.enqueue(count);
    }

    @POST
    @Path("/outbox/drain")
    @Produces(MediaType.TEXT_PLAIN)
    public int drain(@QueryParam("holdMillis") @DefaultValue("0") long holdMillis)
            throws SQLException, InterruptedException {
        return notifications.drain(holdMillis);
    }

    @GET
    @Path("/outbox/holding")
    @Produces(MediaType.TEXT_PLAIN)
    public int holding() {
        return notifications.holding();
    }

    @GET
    @Path("/outbox/summary")
    public NotificationsService.Summary summary() throws SQLException {
        return notifications.summary();
    }
}
