# Outbox double send

**The bug.** The transactional outbox: a request writes the notification it wants sent into an `outbox` table in
the same transaction as its own data, and a `@Scheduled` poller selects the `PENDING` rows, hands them to the
mail provider and marks them `SENT`. The scheduler runs on every instance, each against the same table. Between
one instance's `SELECT` and its `UPDATE` the rows still say `PENDING`, so the other instance's poller, firing a
moment later, selects the same rows and sends them again. On one instance the poller never overlaps with itself,
which is why a `@QuarkusTest` never sees the duplicate: it takes two pollers on one table.

**The fix.** `outbox.skip-locked=true`: the poller selects with `FOR UPDATE SKIP LOCKED` and does all of its work on
that one connection, so the rows it picked stay locked until it commits and the other poller skips them instead of
sending them. The alternative is to claim the rows in one atomic statement, `UPDATE outbox SET status = 'SENDING'
WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING id`, and send only what came back. Either way, a row is
handed to one poller at a time.

Start with [`NotificationsSentTwiceIT`](src/test/java/org/acme/notifications/NotificationsSentTwiceIT.java),
which starts a drain on one instance that is slow at the mail provider and runs a second drain on the other
instance while the first is still at it, then
[`NotificationsSentOnceIT`](src/test/java/org/acme/notifications/NotificationsSentOnceIT.java), the same with the
fix switched on by a `@TestProfile`. The steps are in
[`Outbox`](src/test/java/org/acme/notifications/Outbox.java); the application is
[`NotificationsResource`](src/main/java/org/acme/notifications/NotificationsResource.java), where the drain is
triggered by hand so the test controls the overlap.

```bash
./mvnw -pl samples/outbox-double-send verify
```
