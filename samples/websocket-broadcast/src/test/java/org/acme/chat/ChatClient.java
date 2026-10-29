package org.acme.chat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.fleettest.Fleet;

/**
 * A WebSocket client connected through the load balancer. The balancer picks an instance when the connection is
 * opened and the connection stays there, so a client opened while an instance is pinned listens to that instance
 * for as long as it lives.
 */
final class ChatClient implements AutoCloseable {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private WebSocket socket;
    private int instance;

    private ChatClient() {
    }

    // Opens a connection to the given instance and waits for the greeting, which says the instance registered it.
    static ChatClient connectThrough(Fleet fleet, int instance) throws Exception {
        fleet.pin(instance);
        ChatClient client = new ChatClient();
        URI chat = URI.create("ws://" + fleet.url().getAuthority() + "/chat");
        client.socket = HTTP.newWebSocketBuilder().buildAsync(chat, client.new Listener()).join();
        String greeting = client.nextMessage(Duration.ofSeconds(10));
        if (greeting == null || !greeting.startsWith("connected to instance ")) {
            throw new AssertionError("no greeting from the server, got: " + greeting);
        }
        client.instance = Integer.parseInt(greeting.substring("connected to instance ".length()));
        return client;
    }

    // The instance that holds this connection, as the greeting said.
    int instance() {
        return instance;
    }

    // The next message, or null if none arrives in time.
    String nextMessage(Duration timeout) throws InterruptedException {
        return messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        socket.abort();
    }

    private final class Listener implements WebSocket.Listener {

        // a text message may arrive in several frames
        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.add(partial.toString());
                partial.setLength(0);
            }
            socket.request(1);
            return null;
        }
    }
}
