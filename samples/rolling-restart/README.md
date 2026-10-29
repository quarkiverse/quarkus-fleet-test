# Rolling restart

**The bug.** Kubernetes sends SIGTERM and removes the endpoint at the same time, and proxies act on the
removal a little later. An instance that stops listening at once leaves every request routed to it in that
window with a connection error. The balancer here keeps routing to a stopped instance for one second, as
kube-proxy would.

**The fix.** `quarkus.shutdown.delay`: the instance fails its readiness probe but keeps serving for longer
than the propagation delay, so every request routed to it in that window still succeeds.

Start with [`RequestsDroppedOnRestartIT`](src/test/java/org/acme/work/RequestsDroppedOnRestartIT.java),
which sends requests without pause while one instance is stopped and counts the failures, then
[`NoRequestDroppedOnRestartIT`](src/test/java/org/acme/work/NoRequestDroppedOnRestartIT.java), the same with
the delay set by a `@TestProfile`.

```bash
./mvnw -pl samples/rolling-restart verify
```
