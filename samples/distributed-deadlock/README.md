# Distributed deadlock

**The bug.** A transfer moves money between two accounts in one transaction, locking both rows with
`SELECT ... FOR UPDATE`. It locks them in the order the request names them: a transfer from account 1 to account
2 locks row 1 then row 2, and a transfer from 2 to 1 locks row 2 then row 1. The method is `synchronized`, so on
one instance only one transfer runs at a time and the order never clashes. On two instances the lock protects
neither: the first transfer takes row 1 and waits for row 2 while the second takes row 2 and waits for row 1, and
they wait on each other until Postgres detects the cycle and aborts one with SQLSTATE `40P01`. A single-instance
test cannot produce this, because the in-JVM lock serialises every transfer it sees.

**The fix.** Acquire the two row locks in a consistent global order, the lower id first, whichever way the money
moves, switched on with `transfer.lock-order-by-id=true`. Now both transfers reach for row 1 first, so one simply
waits for the other on that lock instead of forming a cycle, and both commit. The cure is a single lock order
shared by every transfer, not more locking.

Start with [`OppositeTransfersDeadlockIT`](src/test/java/org/acme/accounts/OppositeTransfersDeadlockIT.java),
which runs two opposite transfers on the two instances and shows one of them aborted, then
[`LockOrderingAvoidsDeadlockIT`](src/test/java/org/acme/accounts/LockOrderingAvoidsDeadlockIT.java), the same with
the global lock order switched on by a `@TestProfile`. The application is in
[`AccountResource`](src/main/java/org/acme/accounts/AccountResource.java).

```bash
./mvnw -pl samples/distributed-deadlock verify
```
