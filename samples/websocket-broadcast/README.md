# WebSocket broadcast

**The bug.** A chat endpoint on `quarkus-websockets-next` and a `POST /messages` that broadcasts the text to
every open connection it finds in `OpenConnections`. That bean knows the connections of its own process and
of no other, and the same goes for a Server-Sent Events broadcaster or an event-bus publish: a broadcast is
per process. A TCP load balancer pins each WebSocket to the instance that accepted it, so the clients connected
to the other instance never hear the message. With one instance every client is on it and the broadcast looks
complete, which is why a `@QuarkusTest` never sees this.

**The fix.** A channel between the instances. `chat.relay=redis` publishes each message on a Redis pub/sub
channel every instance subscribes to at startup, and each instance delivers it to its own connections.

Start with [`BroadcastReachesOneInstanceIT`](src/test/java/org/acme/chat/BroadcastReachesOneInstanceIT.java),
which connects one client to each instance and posts a message through the first, then
[`BroadcastReachesEveryInstanceIT`](src/test/java/org/acme/chat/BroadcastReachesEveryInstanceIT.java), the
same with the relay switched on by a `@TestProfile`. The client is
[`ChatClient`](src/test/java/org/acme/chat/ChatClient.java), the broadcast in
[`Broadcaster`](src/main/java/org/acme/chat/Broadcaster.java). Redis is started by Dev Services, once for the
fleet.

```bash
./mvnw -pl samples/websocket-broadcast verify
```
