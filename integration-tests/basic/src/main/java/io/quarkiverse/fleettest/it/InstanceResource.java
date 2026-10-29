package io.quarkiverse.fleettest.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/instance")
public class InstanceResource {

    @ConfigProperty(name = "fleet-test.instance-index")
    int instanceIndex;

    @ConfigProperty(name = "greeting", defaultValue = "none")
    String greeting;

    @Inject
    @org.eclipse.microprofile.health.Readiness
    ToggleableReadiness readiness;

    @GET
    public String index() {
        return Integer.toString(instanceIndex);
    }

    @GET
    @Path("/greeting")
    public String greeting() {
        return greeting;
    }

    @POST
    @Path("/ready/{ready}")
    public void ready(@PathParam("ready") boolean ready) {
        this.readiness.ready(ready);
    }
}
