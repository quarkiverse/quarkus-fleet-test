package io.quarkiverse.fleettest.it;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/instance")
public class InstanceResource {

    @ConfigProperty(name = "fleet-test.instance-index")
    int instanceIndex;

    @ConfigProperty(name = "greeting", defaultValue = "none")
    String greeting;

    @GET
    public String index() {
        return Integer.toString(instanceIndex);
    }

    @GET
    @Path("/greeting")
    public String greeting() {
        return greeting;
    }
}
