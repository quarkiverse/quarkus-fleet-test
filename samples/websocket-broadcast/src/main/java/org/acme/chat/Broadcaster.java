package org.acme.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;

/**
 * Sends a message to every connected client. {@link OpenConnections} holds the connections of this process and
 * of no other, so on its own a broadcast reaches the clients of one instance. With the Redis relay the message is
 * published on a channel every instance subscribes to at startup, and each instance delivers it to its own
 * connections.
 */
@ApplicationScoped
public class Broadcaster {

    private static final String CHANNEL = "chat";

    private final OpenConnections connections;
    private final RedisDataSource redis;

    // The fix: "redis" relays each message through Redis pub/sub so that every instance delivers it.
    private final String relay;

    public Broadcaster(OpenConnections connections, RedisDataSource redis,
            @ConfigProperty(name = "chat.relay", defaultValue = "local") String relay) {
        this.connections = connections;
        this.redis = redis;
        this.relay = relay;
    }

    void subscribe(@Observes StartupEvent event) {
        if (relay.equals("redis")) {
            redis.pubsub(String.class).subscribe(CHANNEL, this::broadcastLocally);
        }
    }

    public void broadcast(String text) {
        if (relay.equals("redis")) {
            redis.pubsub(String.class).publish(CHANNEL, text);
        } else {
            broadcastLocally(text);
        }
    }

    private void broadcastLocally(String text) {
        for (WebSocketConnection connection : connections) {
            // not awaited: a relayed message arrives on the Redis connection's event loop, which must not block
            connection.sendText(text).subscribe().with(sent -> {
            }, failure -> Log.warnf(failure, "Could not send to connection %s", connection.id()));
        }
    }
}
