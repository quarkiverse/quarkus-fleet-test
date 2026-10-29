package org.acme.bookings;

import java.sql.SQLException;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/bookings")
public class BookingResource {

    private final BookingService bookings;

    public BookingResource(BookingService bookings) {
        this.bookings = bookings;
    }

    @GET
    @Path("/{slot}")
    public int count(@PathParam("slot") String slot) throws SQLException {
        return bookings.count(slot);
    }

    @POST
    public int book(@QueryParam("slot") String slot, @QueryParam("who") String who,
            @QueryParam("holdMillis") @DefaultValue("0") long holdMillis) throws SQLException, InterruptedException {
        return bookings.book(slot, who, holdMillis);
    }
}
