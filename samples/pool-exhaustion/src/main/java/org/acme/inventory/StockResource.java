package org.acme.inventory;

import java.sql.SQLException;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/stock")
public class StockResource {

    private final StockService stock;

    public StockResource(StockService stock) {
        this.stock = stock;
    }

    @GET
    public String count() throws SQLException {
        return stock.count();
    }
}
