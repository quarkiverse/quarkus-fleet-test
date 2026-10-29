package org.acme.accounts;

import java.sql.SQLException;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/accounts")
public class AccountResource {

    private final AccountService accounts;

    public AccountResource(AccountService accounts) {
        this.accounts = accounts;
    }

    @GET
    @Path("/{id}")
    public int balance(@PathParam("id") int id) throws SQLException {
        return accounts.balance(id);
    }

    @POST
    @Path("/transfer")
    public Response transfer(@QueryParam("from") int from, @QueryParam("to") int to,
            @QueryParam("amount") int amount, @QueryParam("holdMillis") @DefaultValue("0") long holdMillis)
            throws SQLException, InterruptedException {
        AccountService.Outcome outcome = accounts.transfer(from, to, amount, holdMillis);
        return switch (outcome) {
            case INSUFFICIENT_FUNDS -> Response.status(422).entity("insufficient funds").build();
            case DEADLOCK -> Response.status(409).entity("deadlock").build();
            default -> Response.ok().build();
        };
    }
}
