# Lost update

**The bug.** A withdrawal reads the balance, calls a fraud-check service, and writes the balance minus the
amount: a read-modify-write. The method is `synchronized`, so two withdrawals on one instance queue up and the
second reads what the first wrote. A lock in the JVM protects one process and no other, though: two
withdrawals on two instances both read 100, the second writes 50, and the first writes 70 over it. Fifty units
vanished, no error was raised, and a single-instance test cannot produce this because the lock serialises
everything it sees.

**The fix.** Make the database do the locking, since the database is what the instances share. Here it is a
pessimistic lock: `SELECT ... FOR UPDATE` in the transaction that does the write, switched on with
`wallet.lock-rows=true`, so the second transaction waits until the first commits and then reads 70. The
alternative is optimistic locking: a `version` column, an `UPDATE ... WHERE id = ? AND version = ?`, and a
retry of the whole read-modify-write when the update touched no row.

Start with [`WithdrawalLostOnTwoInstancesIT`](src/test/java/org/acme/wallet/WithdrawalLostOnTwoInstancesIT.java),
which starts a slow withdrawal on one instance and a quick one on the other, and also shows the two withdrawals
on one instance that come out right, then
[`WithdrawalsSerialisedAcrossInstancesIT`](src/test/java/org/acme/wallet/WithdrawalsSerialisedAcrossInstancesIT.java),
the same with row locks switched on by a `@TestProfile`. The application is in
[`WalletResource`](src/main/java/org/acme/wallet/WalletResource.java).

```bash
./mvnw -pl samples/lost-update verify
```
