package org.acme.chat;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;

/**
 * The connection a client listens on. Clients only receive; the messages come in through {@link MessageResource}.
 * The greeting tells the client which instance holds its connection, and that the instance has registered it.
 */
@WebSocket(path = "/chat")
public class ChatSocket {

    private final int instance;

    public ChatSocket(@ConfigProperty(name = "fleet-test.instance-index", defaultValue = "0") int instance) {
        this.instance = instance;
    }

    @OnOpen
    public String opened() {
        return "connected to instance " + instance;
    }
}
