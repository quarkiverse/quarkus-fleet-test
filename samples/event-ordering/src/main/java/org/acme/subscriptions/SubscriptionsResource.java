package org.acme.subscriptions;

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
 * Causally ordered events for one subscription land in a shared {@code events} table, and a poller applies them
 * to the {@code subscriptions} aggregate. In production the poller runs on every instance against the same
 * table, so two instances can apply events for the same subscription at once. Here apply is triggered by hand,
 * one event at a time, so the test decides which instance applies which event and when.
 */
@Path("/")
public class SubscriptionsResource {

    private final SubscriptionsService subscriptions;

    public SubscriptionsResource(SubscriptionsService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @POST
    @Path("/events/{subscriptionId}/{seq}/{type}")
    @Produces(MediaType.TEXT_PLAIN)
    public int enqueue(@PathParam("subscriptionId") int subscriptionId, @PathParam("seq") int seq,
            @PathParam("type") String type) throws SQLException {
        return subscriptions.enqueue(subscriptionId, seq, type);
    }

    @POST
    @Path("/events/apply")
    @Produces(MediaType.TEXT_PLAIN)
    public int apply(@QueryParam("eventId") int eventId,
            @QueryParam("holdMillis") @DefaultValue("0") long holdMillis)
            throws SQLException, InterruptedException {
        return subscriptions.apply(eventId, holdMillis);
    }

    @GET
    @Path("/subscriptions/{id}")
    public SubscriptionsService.Subscription subscription(@PathParam("id") int id) throws SQLException {
        return subscriptions.subscription(id);
    }
}
