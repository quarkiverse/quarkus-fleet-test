package org.acme.profile;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkus.security.identity.SecurityIdentity;

@Path("/profile")
public class ProfileResource {

    private final SecurityIdentity identity;

    public ProfileResource(SecurityIdentity identity) {
        this.identity = identity;
    }

    @GET
    public String whoAmI() {
        return identity.getPrincipal().getName();
    }
}
