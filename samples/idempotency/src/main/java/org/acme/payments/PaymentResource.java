package org.acme.payments;

import java.sql.SQLException;
import java.util.Map;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@Path("/payments")
public class PaymentResource {

    private final PaymentService payments;

    public PaymentResource(PaymentService payments) {
        this.payments = payments;
    }

    @POST
    public Map<String, Object> pay(Map<String, Object> payment,
            @QueryParam("delayMillis") @DefaultValue("0") long delayMillis)
            throws SQLException, InterruptedException {
        return payments.pay(payment, delayMillis);
    }

    @GET
    public Map<String, Object> summary() throws SQLException {
        return payments.summary();
    }
}
