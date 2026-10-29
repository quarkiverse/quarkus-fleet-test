# Write skew

**The bug.** Booking a slot counts the bookings it already has, and if the count is below the capacity it inserts
one: a read then a write over an invariant ("at most one booking per slot"). The method is `synchronized`, so two
bookings on one instance queue up and the second counts what the first inserted. A lock in the JVM protects one
process and no other, though: two bookings on two instances both count zero, both pass the capacity check, and
both insert. The slot now holds two bookings, no error was raised, and a single-instance test cannot produce this
because the lock serialises everything it sees. This is write skew: neither transaction touched a row the other
read, so nothing conflicted, yet together they broke the invariant.

**The fix.** A row lock cannot help, because the rows that would break the invariant do not exist yet, so there is
nothing to lock; and no unique constraint can express "at most N rows". What is left is SERIALIZABLE isolation,
which makes the database track what each transaction read. When the second booking inserts into a range the first
one counted, Postgres sees that the two cannot be ordered and aborts the later committer with SQLSTATE 40001
(serialization_failure). The booking method catches it, retries, re-counts, finds the slot full, and returns 409.
The fix is the isolation level alone, set by `quarkus.datasource.jdbc.transaction-isolation-level=serializable`;
the application code does not change.

Start with [`SlotOversoldIT`](src/test/java/org/acme/bookings/SlotOversoldIT.java), which books the same slot on
two instances at once and ends with the slot oversold, then
[`SerializableHoldsTheLimitIT`](src/test/java/org/acme/bookings/SerializableHoldsTheLimitIT.java), the same with
serializable isolation switched on by a `@TestProfile`, where one booking wins and the other gets a 409. The
application is in [`BookingResource`](src/main/java/org/acme/bookings/BookingResource.java).

```bash
./mvnw -pl samples/write-skew verify
```
