# Pool exhaustion

**The bug.** A connection pool sized as if the instance were alone. The pool holds 8 connections, opened at
startup; the database accepts 12 (`max_connections`, minus the slots PostgreSQL reserves for superusers when
the application is not one). Two instances want 16. The first to start takes its 8, the second gets the 4 that
are left, logs the refusals, and reports ready like any other: readiness says the process is up, not that the
database gave it what it asked for. The balancer sends it half the traffic, and every request that finds its
four connections busy waits out the acquisition timeout and fails with 500. On one instance the pool is always
full, which is why a `@QuarkusTest` never sees this.

**The fix.** Size the pool per replica: `quarkus.datasource.jdbc.max-size` (and `min-size`, `initial-size`) set
to the database's connections divided by the number of replicas, 12 / 2 = 6 here. Or put a pooler such as
PgBouncer in front of the database, so that the instances share one pool instead of competing for the slots.

Start with [`SecondInstanceStarvedIT`](src/test/java/org/acme/inventory/SecondInstanceStarvedIT.java), which
sends a burst of six slow queries to each instance and sees the second one fail some of them while the fleet
reports it ready, then
[`EveryInstanceHasConnectionsIT`](src/test/java/org/acme/inventory/EveryInstanceHasConnectionsIT.java), the
same bursts with the pool sized per replica by a `@TestProfile`. The burst is in
[`Burst`](src/test/java/org/acme/inventory/Burst.java). PostgreSQL is started by Dev Services, once for the
fleet, with `max_connections=12`.

```bash
./mvnw -pl samples/pool-exhaustion verify
```
