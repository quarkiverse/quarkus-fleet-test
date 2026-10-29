package org.acme.orders;

import java.sql.SQLException;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/orders")
public class OrdersResource {

    private final OrdersService orders;

    public OrdersResource(OrdersService orders) {
        this.orders = orders;
    }

    @POST
    @Path("/{count}")
    public void place(@PathParam("count") int count) {
        orders.place(count);
    }

    // Deliveries and shipments so far, and which instance did them.
    @GET
    public Map<String, Object> summary() throws SQLException {
        return orders.summary();
    }
}
