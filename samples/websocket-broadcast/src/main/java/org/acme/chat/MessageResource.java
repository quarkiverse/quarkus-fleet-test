package org.acme.chat;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

@Path("/messages")
public class MessageResource {

    private final Broadcaster broadcaster;

    public MessageResource(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    // Sends the text to every connected client, as far as this instance can see them.
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public void send(String text) {
        broadcaster.broadcast(text);
    }
}
