package org.acme.catalog;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/catalog")
public class CatalogResource {

    private final Catalog catalog;

    public CatalogResource(Catalog catalog) {
        this.catalog = catalog;
    }

    @GET
    public Response catalog() {
        if (!catalog.isLoaded()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("catalog not loaded yet").build();
        }
        return Response.ok("42 products").build();
    }
}
