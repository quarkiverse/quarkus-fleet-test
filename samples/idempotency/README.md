# HTTP idempotency

**The bug.** [Quarkus HTTP Idempotency](https://github.com/quarkiverse/quarkus-http-idempotency) with its
default in-memory store. The `Idempotency-Key` is known to the instance that served the first attempt and to
no other, so a retry that lands on the next instance is a new payment. On one instance the extension works
exactly as documented, which is why a `@QuarkusTest` never sees this.

**The fix.** `quarkus.idempotency.store=jdbc`, the store in the database every instance shares.

Start with [`PaymentChargedTwiceIT`](src/test/java/org/acme/payments/PaymentChargedTwiceIT.java), which pays
through one instance and retries through the other, then
[`PaymentChargedOnceIT`](src/test/java/org/acme/payments/PaymentChargedOnceIT.java), the same and two more
cross-instance cases with the jdbc store set by a `@TestProfile`.

```bash
./mvnw -pl samples/idempotency verify
```
