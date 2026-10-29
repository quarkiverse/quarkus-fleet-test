package org.acme.quotes;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.smallrye.faulttolerance.api.RateLimitException;

// Quarkus does not map a RateLimitException to a status on its own; without this it would be a 500.
@Provider
public class RateLimitExceptionMapper implements ExceptionMapper<RateLimitException> {

    @Override
    public Response toResponse(RateLimitException e) {
        return Response.status(429).build();
    }
}
