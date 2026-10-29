# Event Ordering

**The bug.** Causally ordered events for one subscription (activate at seq 1, then cancel at seq 2) land in a
shared `events` table, and a poller applies them to the `subscriptions` aggregate. The poller runs on every
instance against the same table, so two instances can apply events for the same subscription at once. Nothing
gives the events a per-subscription order: each instance reads the aggregate, does its work and writes back the
state its event implies, so the aggregate ends up in whatever state the last writer committed, in commit order
rather than event order. One instance is slow applying the earlier `activate` while the other applies the later
`cancel` and commits; then the slow one finishes and writes `active` on top. The subscription ends `active`
though the later event was `cancel`. A single poller is inherently serial per key, so a `@QuarkusTest` with one
process never sees it: it takes two pollers on the same key at once.

**The fix.** `events.ordered-apply=true`: each apply locks the aggregate row with `SELECT ... FOR UPDATE` and
holds the lock for its whole run, and it applies an event only when `event.seq` is greater than the aggregate's
`last_seq`. A competing apply of the same subscription blocks on the lock until the first commits, then reads the
updated `last_seq`; a stale lower-seq event is skipped rather than written on top. Same-key events serialise in
order, so the terminal state is the one the latest event implies.

Start with
[`OutOfOrderLeavesWrongStateIT`](src/test/java/org/acme/subscriptions/OutOfOrderLeavesWrongStateIT.java), which
has instance 0 apply the earlier event slowly while instance 1 applies the later event and commits, then
[`OrderedApplyKeepsStateIT`](src/test/java/org/acme/subscriptions/OrderedApplyKeepsStateIT.java), the same with
the fix switched on by a `@TestProfile`. The steps are in
[`Events`](src/test/java/org/acme/subscriptions/Events.java); the application is
[`SubscriptionsResource`](src/main/java/org/acme/subscriptions/SubscriptionsResource.java), where apply is
triggered by hand one event at a time so the test controls which instance applies which event and when.

```bash
./mvnw -pl samples/event-ordering verify
```
