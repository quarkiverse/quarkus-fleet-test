# Quarkus Fleet Test

[![Quarkus](https://img.shields.io/badge/Quarkus-4.0.0.Beta3%2B-4695EB?logo=quarkus&style=flat-square)](https://quarkus.io/)

**Run several instances of your application behind a load balancer, inside one JUnit test.**

Some bugs do not exist until there is more than one copy of the application running: a login that
works on the instance that issued the cookie and fails on the next, a scheduled report sent once
per replica, a cache that is fresh where it was written and stale where it is read, an overloaded
replica that takes its neighbours down with it, requests dropped at every rolling restart. A
`@QuarkusTest` runs one instance and cannot see any of them. They are found in production.

A `@QuarkusFleetTest` starts N processes of the packaged application, puts a TCP load balancer in
front of them, and lets the test talk to the fleet or to one instance.

```java
@QuarkusFleetTest(instances = 2)
class InventoryFleetIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void aWriteOnOneInstanceIsVisibleOnTheOther() {
        fleet.pin(0);
        given().body("{\"sku\":\"A1\",\"count\":3}").post("/inventory").then().statusCode(201);

        fleet.pin(1);
        given().get("/inventory/A1").then().statusCode(200).body("count", is(3));
    }
}
```

RestAssured and `@TestHTTPResource` point at the load balancer. `fleet.pin(i)` routes every new
connection to one instance, `fleet.instance(i).url()` bypasses the balancer, `stop()` and `start()`
take an instance out of and back into the fleet, `kill()` crashes it, `pause()` and `resume()`
freeze it with every lock and lease it holds, and `running = 1` starts a fleet smaller than its
size.

Like `@QuarkusIntegrationTest`, the test runs against the artifact the build produced, as a jar, a
container or a native binary, under failsafe after `mvn package`. Dev Services start once and every
instance receives the same connection properties: N instances, one database, one broker.
`@TestProfile` overrides reach every instance, and each test class gets a fleet of its own.

## The load balancer is a Kubernetes `Service`

It balances connections, not requests, and the bytes go through untouched, whatever the protocol. A
client that opens a connection per request is balanced per request; a keep-alive client sticks to
one instance, exactly as it does in production. With readiness on, it polls each instance like a
kubelet, with thresholds, keeps routing to an unready or stopped instance for a configurable
propagation delay like kube-proxy, and either refuses or fails open when nothing is ready, because
kube-proxy and an AWS ALB disagree on that:

```java
@QuarkusFleetTest(instances = 2, readiness = @Readiness(
        path = "/q/health/ready", intervalMillis = 100, failureThreshold = 1,
        deregistrationLagMillis = 1000, failOpen = false))
```

## Nineteen bugs, caught

Every module under `samples/` is one application with a bug, a fleet test that fails on it, and a
`@TestProfile` that fixes it so that the same test passes. The modules under `integration-tests/`
cover the extension itself.

| Module | The bug | The fix |
|---|---|---|
| [`scheduled-job`](samples/scheduled-job/) | A `@Scheduled` report runs on every instance: more reports than ticks | Claim the tick in the shared database first |
| [`form-auth`](samples/form-auth/) | No session encryption key: the cookie instance 0 issues is a redirect to the login page on instance 1 | `quarkus.http.auth.session.encryption-key` |
| [`oidc-login`](samples/oidc-login/) | OIDC with PKCE and a short client secret: the state cookie instance 0 set before sending the browser to Keycloak cannot be decrypted by instance 1 when the browser comes back | `quarkus.oidc.authentication.state-secret` |
| [`rolling-restart`](samples/rolling-restart/) | On SIGTERM the instance stops listening while the balancer keeps routing to it for a second: a client without retries sees connection errors | `quarkus.shutdown.delay` |
| [`zombie-leader`](samples/zombie-leader/) | The leader of a leased job is frozen past its lease (`pause()`), the other instance takes over, and the old leader wakes up and records the same item again | A fencing token on every write |
| [`crash-redelivery`](samples/crash-redelivery/) | The instance consuming a Kafka partition is killed (`kill()`) after shipping and before committing offsets: the survivor ships every order again | An idempotent consumer, claiming the order id in the shared database |
| [`scale-out`](samples/scale-out/) | A second replica (`running = 1`, then `start()`) receives traffic while it is still loading; every request routed to it fails | A readiness probe in the deployment |
| [`idempotency`](samples/idempotency/) | [Quarkus HTTP Idempotency](https://github.com/quarkiverse/quarkus-http-idempotency) with its default in-memory store: a payment retried with the same `Idempotency-Key` on the other instance is charged twice | `quarkus.idempotency.store=jdbc`, the store in the shared database |
| [`local-cache`](samples/local-cache/) | `@CacheResult` with the Caffeine backend: `@CacheInvalidate` on the instance that took the write evicts that JVM only, and the other keeps serving the old price | Tell the other instances, here over Redis pub/sub, or use a shared cache backend |
| [`rate-limit`](samples/rate-limit/) | `@RateLimit(5)` counts per JVM: a client balanced round robin gets 10 requests through | A fleet-wide counter in Redis in front of the resource |
| [`websocket-broadcast`](samples/websocket-broadcast/) | A WebSockets Next broadcast reaches the sockets of the instance that sends it; clients on the other instance hear nothing | Relay the message to every instance over Redis pub/sub |
| [`outbox-double-send`](samples/outbox-double-send/) | Two instances poll the same outbox table and both send every pending notification | `SELECT ... FOR UPDATE SKIP LOCKED`, so a poller skips the rows another holds |
| [`pool-exhaustion`](samples/pool-exhaustion/) | Each instance opens its full pool against one `max_connections`: the second instance starts, reports ready, and its requests fail for lack of a connection | Size the pool per replica |
| [`lost-update`](samples/lost-update/) | Read, compute, write on two instances: a `synchronized` method protects one JVM, and fifty units vanish from a wallet | `SELECT ... FOR UPDATE`, or optimistic locking with a version column |
| [`distributed-deadlock`](samples/distributed-deadlock/) | Two transfers lock the same two rows in opposite order on the two instances and Postgres aborts one; a `synchronized` method hides it on one JVM | Lock the rows in a consistent order (lowest id first) |
| [`write-skew`](samples/write-skew/) | Both instances check "slot not full", both pass, both insert: the limit is broken though no single-row lock could stop it | `SERIALIZABLE` isolation, so Postgres aborts the conflicting transaction to retry |
| [`event-ordering`](samples/event-ordering/) | Two instances apply causally ordered events for one aggregate in commit order, not event order, leaving the wrong state | A per-aggregate row lock held across apply, plus a last-applied-sequence check |
| [`job-killed-midway`](samples/job-killed-midway/) | A long import runs in a thread of one instance; `kill()` it and the remaining rows are never processed | A checkpoint in the database and a reaper on every instance that resumes abandoned jobs |
| [`upload-local-disk`](samples/upload-local-disk/) | An upload stored on the instance's disk is a 404 through the other instance | Shared storage: the database, object storage, or a shared volume |

The [scenarios page](docs/modules/ROOT/pages/scenarios.adoc) lists the rest of what a two-instance
fleet catches, grouped by how deterministic the test is, and the
[skill for coding agents](skills/quarkus-fleet-test/SKILL.md) maps an application's
dependencies to the tests worth writing.

## Why not a `@QuarkusTest`?

Because a `@QuarkusTest` runs one instance, and every bug here is invisible to one instance — which
is why it reaches production. There is a single copy of each cache, counter, scheduler, session key
and in-process lock, so two threads share it and the fault never shows. Nine of the samples cannot
fail with one process at all; six need a process the test can stop, kill or freeze behind a load
balancer with readiness; and the database ones are hidden by in-process coordination — a
`synchronized` method, a single `@Scheduled` poller — that is correct on one instance and wrong on
many. `outbox-double-send` and `event-ordering` ship a plain `@QuarkusTest` that races two threads
against one instance and still cannot reproduce the bug, next to the fleet test that does.

## When a test fails

A multi-instance failure is hard to read from an assertion alone. On failure the extension prints what the
fleet looked like, what the load balancer did during the test with timestamps that line up with the instance
logs, and the tail of each instance's log. For the crash-redelivery test, a failure looks like this:

```
Fleet state when ordersReceivedAgainAreNotShippedAgain() failed:
Fleet of 2 at http://localhost:46435, round-robin
  instance 0: stopped, readiness transitions 0, served 6 connection(s), log: target/test-GXXHD/quarkus.log
  instance 1: running on port 44527, ready, readiness transitions 0, served 5 connection(s), log: target/test-dMzXk/quarkus.log
  what the load balancer did during the test:
    11:35:02.003 (+ 0.000s) instance 0 started on port 44119
    11:35:02.857 (+ 0.854s) instance 1 started on port 44527
    11:35:04.226 (+ 2.223s) instance 0 killed
  last 17 line(s) of instance 1 (target/test-dMzXk/quarkus.log):
    ... the survivor's log, where the Kafka rebalance should appear ...
```

The timeline says the balancer killed instance 0 at 2.2 s in and did nothing after, so a wait that never
completes is downstream of the balancer, here Kafka moving the partition to the survivor. Other scenarios add
their own lines: `instance 1 became unready (probe /q/health/ready failed 1 times)`, `routing pinned to
instance 0`, and, above the tail, any warnings and errors from earlier in a log that the tail would otherwise
bury. The whole report is also written to
`target/fleet-test/<test>-fleet.txt`, so it survives in CI where the console does not.

## Which project answers what

A `@QuarkusFleetTest` asks what goes wrong only with more than one instance. What one instance does when a
dependency breaks or a pool fills up is a question for
[Quarkus Network Faults](https://github.com/quarkiverse/quarkus-network-faults), in a `@QuarkusTest`. Its
`pool-exhausted-500` sample is about the answer a full pool gives; `pool-exhaustion` here is about pools that add up to
more than the database allows.

## Status

Working: jar, container and native fleets, round-robin and pinned routing, readiness with
thresholds, deregistration lag and fail-open, stop and start of an instance,
kill, pause and resume of an instance, fleets that start smaller than their size, shared Dev
Services (PostgreSQL and Kafka demonstrated), test resources, `@TestProfile`, `@TestHTTPEndpoint`,
`@TestHTTPResource` and RestAssured, and on failure the fleet's state, a timeline of what the balancer did, and
each instance's log tail, printed and written under `target/`. Fault injection inside an
instance (a database that hangs for one instance only) is out of scope; the research behind the
project, including the design for that integration, is under [`research/`](research/README.md).

## Building

```bash
./mvnw install
```

To run a module's fleet tests against a native executable, built in a container:

```bash
./mvnw -pl integration-tests/basic verify -Dquarkus.native.enabled=true \
    -Dquarkus.package.jar.enabled=false -Dquarkus.native.container-build=true
```

Requires Java 21, Docker or Podman for the Dev Services and container modules, and Quarkus
4.0.0.Beta3 or later.
