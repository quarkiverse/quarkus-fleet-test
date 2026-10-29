package org.acme.account;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkus.security.identity.SecurityIdentity;

@Path("/account")
public class AccountResource {

    private final SecurityIdentity identity;

    public AccountResource(SecurityIdentity identity) {
        this.identity = identity;
    }

    @GET
    public String whoAmI() {
        return identity.getPrincipal().getName();
    }
}
