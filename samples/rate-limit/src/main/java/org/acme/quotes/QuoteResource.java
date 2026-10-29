package org.acme.quotes;

import java.time.temporal.ChronoUnit;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.smallrye.faulttolerance.api.RateLimit;

@Path("/quotes")
public class QuoteResource {

    // Five quotes a minute. The count lives in this JVM: another instance has its own five.
    @GET
    @RateLimit(value = 5, window = 1, windowUnit = ChronoUnit.MINUTES)
    public String quote() {
        return "Talk is cheap. Show me the code.";
    }
}
