package org.acme.work;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/work")
public class WorkResource {

    @GET
    public String work() throws InterruptedException {
        Thread.sleep(20);
        return "done";
    }
}
