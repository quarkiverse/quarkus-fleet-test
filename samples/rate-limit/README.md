# Rate limit

**The bug.** `GET /quotes` is annotated with SmallRye Fault Tolerance `@RateLimit(value = 5, window = 1,
windowUnit = ChronoUnit.MINUTES)`. The count behind that annotation lives in the JVM, as it does for `@Bulkhead`
and `@CircuitBreaker`: each instance refuses a client's sixth request in a minute, and knows nothing about the
requests the other instances served. Behind a load balancer the limit meant for the service becomes five times
the number of instances. On one instance the sixth request is refused exactly as documented, which is why a
`@QuarkusTest` never sees this.

**The fix.** `quotes.limit.shared=true`: a request filter counts each client in Redis, `INCR` on a key per client
that expires with the window, and refuses the request once the count passes five. A limit meant for the service
has to live somewhere every instance can see; the per-JVM annotation stays, as a last line of defence for the
single instance.

Start with [`LimitMultipliedByInstancesIT`](src/test/java/org/acme/quotes/LimitMultipliedByInstancesIT.java),
which uses up the limit on one instance, is refused there, and is served five more times by the other, then
[`LimitSharedAcrossInstancesIT`](src/test/java/org/acme/quotes/LimitSharedAcrossInstancesIT.java), the same
with the shared count switched on by a `@TestProfile`: the other instance refuses the sixth request. Redis is
started by Dev Services, once for the fleet.

```bash
./mvnw -pl samples/rate-limit verify
```
