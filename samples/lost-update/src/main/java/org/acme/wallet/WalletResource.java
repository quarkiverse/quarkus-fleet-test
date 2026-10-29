package org.acme.wallet;

import java.sql.SQLException;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/wallets")
public class WalletResource {

    private final WalletService wallets;

    public WalletResource(WalletService wallets) {
        this.wallets = wallets;
    }

    @GET
    @Path("/{id}")
    public int balance(@PathParam("id") int id) throws SQLException {
        return wallets.balance(id);
    }

    @POST
    @Path("/{id}/withdraw")
    public int withdraw(@PathParam("id") int id, @QueryParam("amount") int amount,
            @QueryParam("holdMillis") @DefaultValue("0") long holdMillis) throws SQLException, InterruptedException {
        return wallets.withdraw(id, amount, holdMillis);
    }
}
