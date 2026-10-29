---
name: quarkus-fleet-test
description: Write @QuarkusFleetTest integration tests that run a Quarkus application as several instances behind a load balancer. Use when adding tests for multi-instance behaviour (scheduled jobs, caches, sessions, rate limits, readiness, overload, rolling restarts) or when asked to test what breaks with more than one replica.
---

# Writing fleet tests

A guide for anyone, human or agent, adding `@QuarkusFleetTest` tests to a Quarkus application. It tells you
which tests to write from what the application uses, and gives the shape of each.

## Before writing

1. The test is an integration test: name it `*IT`, it runs under failsafe after `mvn package`, against the
   packaged artifact. `@Inject` does not work; talk to the application over HTTP.
2. Add `io.quarkiverse.fleettest:quarkus-fleet-test` in test scope. Annotate the class with
   `@QuarkusFleetTest(instances = 2)`; two instances find almost everything.
3. RestAssured and `@TestHTTPResource` point at the load balancer. `fleet.pin(i)` sends new connections to one
   instance; `fleet.instance(i).url()` bypasses the balancer. An instance can be `stop()`ped (SIGTERM),
   `kill()`ed (SIGKILL), `pause()`d and `resume()`d (SIGSTOP, it keeps its locks and leases), and `start()`ed
   again; `@QuarkusFleetTest(instances = 2, running = 1)` leaves instance 1 for the test to start. Routing
   and paused instances reset after each test.
4. One fleet per test class. A `@TestProfile` on the class reaches every instance as runtime configuration.
   Build-time properties cannot be changed by a profile: the artifact is the one the build produced.
5. When a test fails, the fleet's state and the tail of every instance's log are printed. Read them before
   changing the test.

## Which tests to write

Look at the dependencies and configuration of the application and pick the rows that apply.

| If the application has | Write a test that | Expect |
|---|---|---|
| `@Scheduled` or Quartz without `clustered=true` | lets the job tick a few times, then counts its effects through the balancer | one effect per tick, not one per instance |
| `quarkus-cache`, Hibernate second-level cache | writes through `pin(0)`, reads through `pin(1)` | the new value |
| `@RateLimit`, `@Bulkhead`, `@CircuitBreaker` | sends limit+1 requests split across two instances | a rejection; without it the limit is per instance |
| form authentication | logs in through `pin(0)`, uses the cookie through `pin(1)` | 200, not a redirect to the login page; set `quarkus.http.auth.session.encryption-key` |
| OIDC with PKCE or a nonce and a client secret shorter than 32 characters, a public client, or mTLS credentials | starts the code flow through `pin(0)`, logs in at the provider, finishes it through `pin(1)` | success, not 401; set `quarkus.oidc.authentication.state-secret` (and `quarkus.oidc.token-state-manager.encryption-secret` with mTLS) |
| WebSockets Next, SSE, event bus, in-memory channels | connects one client per instance and broadcasts from one | both clients receive it |
| an async job API, uploads on local disk, in-memory IDs | submits through `pin(0)`, polls or reads through `pin(1)` | found |
| a readiness check on a shared dependency | breaks the dependency, with `readiness` on | the behaviour the balancer policy implies |
| `quarkus-load-shedding` or any overload handling | holds instance 0 at its limit with blocking requests, `readiness` on | instance 0 stays ready and answers the excess with 503; instance 1 unaffected; `readinessTransitions()` is zero |
| anything deployed with rolling updates | runs a client loop, `stop()`s an instance, with `deregistrationLagMillis` on | zero errors; set `quarkus.shutdown.delay` |
| a client with keep-alive (REST Client, gRPC, HTTP/2) | sends N requests from one pooled client | every instance served some, or document that it is pinned |
| leader election, a job lock, any lease | `pause()`s the leader while it works, waits for the other instance to take over and do the work, `resume()`s | the old leader's write is rejected (fencing token), the work is done once |
| a Kafka consumer, any at-least-once consumer | lets one instance process the records, `kill()`s it, waits for the rebalance | the records are received again and their effect happens once |
| a read-modify-write on a shared row, however it is locked in the JVM | holds one write past its read through `pin(0)`, writes through `pin(1)` | the balance reflects both writes; lock the row (`FOR UPDATE`) or use a version column |
| two transactions that lock the same rows in opposite order | holds a transfer past its first row lock on one instance, runs the reverse transfer on the other | no deadlock abort; lock rows in a consistent global order |
| an invariant each request checks then breaks by inserting (a limit, no overlap) | holds the check on one instance, inserts on the other | the invariant holds; `SERIALIZABLE` isolation (a row lock cannot cover rows that do not exist yet) |
| causally ordered events over a shared queue | applies the earlier event slowly on one instance while the later commits on the other | the correct terminal state; a per-key lock held across apply plus a sequence check |
| a `@Scheduled` poller over a shared table (outbox, queue) | holds one drain past its select through `pin(0)`, drains through `pin(1)` | every row handled once; `FOR UPDATE SKIP LOCKED` |
| a long-running job started by a request | `kill()`s the instance running it midway | the other instance finishes it from a checkpoint |
| Agroal against one database | sizes the pool so that pool x instances exceeds `max_connections`, bursts through `pin(1)` | every request served; size the pool per replica |
| `quarkus-http-idempotency`, or any idempotency key, deduplication or rate-limit state | sends a `POST` with an `Idempotency-Key` through `pin(0)`, retries it through `pin(1)` | `Idempotent-Replayed: true` and one effect; set `quarkus.idempotency.store` to `jdbc` or `redis` |
| anything that scales out, any startup loading | `running = 1`, a client loop, `start()`s the second instance | zero errors with `readiness` on; some without, which is the missing probe |

## Shapes

### Cross-instance visibility

```java
@QuarkusFleetTest(instances = 2)
class InventoryFleetIT {

    @InjectFleet
    Fleet fleet;

    @ParameterizedTest
    @CsvSource({ "0, 1", "1, 0" })   // both directions: a test that only ever uses (0, 0) hides the bug
    void aWriteIsVisibleFromTheOtherInstance(int writer, int reader) {
        fleet.pin(writer);
        given().body("{\"sku\":\"A1\",\"count\":3}").post("/inventory").then().statusCode(201);
        fleet.pin(reader);
        given().get("/inventory/A1").then().statusCode(200).body("count", is(3));
    }
}
```

### Once per fleet

```java
@QuarkusFleetTest(instances = 2)
class ReportFleetIT {

    @Test
    void theReportGoesOutOncePerTick() throws Exception {
        Thread.sleep(3500);   // a few ticks on every instance
        Map<String, Long> summary = given().get("/reports").as(new TypeRef<>() {});
        assertThat(summary.get("sent")).isEqualTo(summary.get("ticks"));
    }
}
```

### Overload stays local

```java
@QuarkusFleetTest(instances = 2, readiness = @Readiness(path = "/q/health/ready", intervalMillis = 100, failureThreshold = 1))
class OverloadFleetIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void anOverloadedInstanceIsNotEjected() throws Exception {
        holdAtItsLimit(fleet.instance(0).url());          // N blocking requests, N = the configured limit
        Thread.sleep(500);                                 // a few readiness polls
        assertThat(fleet.readyInstances()).hasSize(2);
        assertThat(fleet.instance(0).readinessTransitions()).isZero();
        assertThat(RawHttp.get(fleet.instance(0).url(), "/work")).isIn(200, 503);
    }
}
```

### Rolling restart

```java
@QuarkusFleetTest(instances = 2, readiness = @Readiness(path = "/q/health/ready", intervalMillis = 100, failureThreshold = 1, deregistrationLagMillis = 1000))
@TestProfile(ShutdownDelayProfile.class)   // quarkus.shutdown.delay=PT2S; quarkus.shutdown.delay-enabled=true at build time
class RestartFleetIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void nothingIsDroppedWhileAnInstanceStops() throws Exception {
        var client = startRequestLoop(fleet.url(), Duration.ofSeconds(3));   // a client with no retries
        Thread.sleep(300);
        fleet.instance(0).stop();
        assertThat(client.join().failed()).isZero();
    }
}
```

### Zombie leader

```java
@QuarkusFleetTest(instances = 2)
@TestProfile(FencedProfile.class)   // every write carries the lease token and the database checks it
class LeaderFleetIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void aFrozenLeaderCannotWriteWithItsOldLease() throws Exception {
        FleetInstance leader = leaderCaughtMidWork(fleet);           // poll each instance's state endpoint
        leader.pause();                                              // its lease expires; it does not know
        FleetInstance successor = fleet.instance(1 - leader.index());
        awaitUntil(() -> recordedBy(successor).contains(theItem));  // the successor took over and did the item
        leader.resume();                                             // the zombie finishes the item it was on
        awaitUntil(() -> isIdle(leader));
        assertThat(duplicates()).isEmpty();
    }
}
```

### Crash before commit

```java
@QuarkusFleetTest(instances = 2)
@TestProfile(IdempotentProfile.class)
class ConsumerFleetIT {

    @Test
    void redeliveredRecordsHaveNoSecondEffect() throws Exception {
        given().post("/orders/20").then().statusCode(204);
        awaitUntil(() -> received() == 20);
        FleetInstance consumer = fleet.instance(whoReceived());      // one partition, one consumer
        consumer.kill();                                             // before the offsets are committed
        FleetInstance survivor = fleet.instance(1 - consumer.index());
        awaitUntil(() -> receivedBy(survivor) >= 20);                // session timeout, rebalance, redelivery
        assertThat(received()).isGreaterThan(20);
        assertThat(shipped()).isEqualTo(20);
    }
}
```

Set `session.timeout.ms` low on the channel (Kafka's default is 45 seconds) or the test waits that long for the
rebalance.

### Scale-out

```java
@QuarkusFleetTest(instances = 2, running = 1, readiness = @Readiness(path = "/q/health/ready", intervalMillis = 100))
class ScaleOutFleetIT {

    @Test
    void theNewInstanceGetsNoTrafficBeforeItIsReady() throws Exception {
        var client = startRequestLoop(fleet.url(), Duration.ofSeconds(4));
        Thread.sleep(300);
        fleet.instance(1).start();                                   // returns once the probe says ready
        assertThat(client.join().failed()).isZero();                 // without readiness, this is where it fails
    }
}
```

## Mistakes to avoid

- Asserting through a client that retries. RestAssured's HTTP client retries a request whose connection was
  closed, three times; a dropped connection then looks like success. Use a client without retries when the
  connection is the thing under test.
- Sending a burst from the common `ForkJoinPool`: its size is the core count, and held requests occupy it. Use
  virtual threads for anything that must be in flight at the same time.
- Writing to a CDI bean's field through a client proxy from a test endpoint: it writes the proxy's field.
  Expose a method.
- Letting the scheduler run before a startup observer created a table: create it where the job can find it.
- Expecting a build-time property to change through a `@TestProfile`. It will not; the instance warns that the
  property is ignored.
- Pausing an instance and expecting the balancer to notice at once. It does not, any more than kube-proxy
  would: only the readiness probe, with its interval and threshold, takes a frozen instance out of rotation.
- Using the short `every = "200ms"` form on `@Scheduled`: it is invalid; write `PT0.2S`.
