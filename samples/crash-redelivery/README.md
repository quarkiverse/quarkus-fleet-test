# Crash redelivery

**The bug.** Orders arrive on a Kafka topic and each one is shipped. Kafka delivers at least once: offsets are
committed some time after a record was processed, so the instance that owns the partition, killed after
shipping and before committing, leaves records that the next owner receives again and ships again.

**The fix.** An idempotent consumer: the shipment is recorded under the order id in the shared database before
it is made, and a redelivered order is recognised whichever instance it lands on.

Start with [`OrdersShippedTwiceAfterCrashIT`](src/test/java/org/acme/orders/OrdersShippedTwiceAfterCrashIT.java),
which kills the consuming instance with `kill()` and waits for the partition to move, then
[`OrdersShippedOnceAfterCrashIT`](src/test/java/org/acme/orders/OrdersShippedOnceAfterCrashIT.java), the same
with the fix switched on by a `@TestProfile`. The steps of the scenario are in
[`Crash`](src/test/java/org/acme/orders/Crash.java), the application in
[`OrderProcessor`](src/main/java/org/acme/orders/OrderProcessor.java).

```bash
./mvnw -pl samples/crash-redelivery verify
```
