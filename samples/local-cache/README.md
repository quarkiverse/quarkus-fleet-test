# Local cache

**The bug.** A price looked up in the database and kept in a local cache (`quarkus-cache`, Caffeine), evicted
with `@CacheInvalidate` when the price changes. A cache inside the process is one cache per instance, and the
invalidation reaches only the instance that performed the write. The other instance has the old price cached
and keeps serving it, here forever, since the entry has no expiry. On one instance a write is always followed by
fresh reads, which is why a `@QuarkusTest` never sees this.

**The fix.** Tell the other instances. Here the instance that handles the write publishes the key on a Redis
pub/sub channel and every instance, subscribed at startup, evicts its own entry. The alternative is a cache the
instances share, such as the Redis backend of `quarkus-cache`, at the price of a network round trip per read.

Start with [`PriceStaleOnOtherInstanceIT`](src/test/java/org/acme/pricing/PriceStaleOnOtherInstanceIT.java),
which reads the price through one instance, changes it through the other, and reads it through the first again,
then [`PriceFreshOnOtherInstanceIT`](src/test/java/org/acme/pricing/PriceFreshOnOtherInstanceIT.java), the same
with the pub/sub invalidation switched on by a `@TestProfile`. The application is in
[`PriceResource`](src/main/java/org/acme/pricing/PriceResource.java) and
[`FleetWideInvalidation`](src/main/java/org/acme/pricing/FleetWideInvalidation.java). PostgreSQL and Redis are
started by Dev Services, once for the fleet.

```bash
./mvnw -pl samples/local-cache verify
```
