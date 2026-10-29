# Zombie leader

**The bug.** Work that one instance at a time must do, coordinated with a lease in the shared database. A
leader frozen for longer than its lease (a long GC pause, a VM migration, a network partition) does not know
it lost the lease. When it runs again it records the item it was on, which its successor already processed:
the item is done twice.

**The fix.** A fencing token. Every write carries the token the lease was acquired with, and the database
accepts it only while the lease still carries that token.

Start with [`ZombieLeaderRecordsTwiceIT`](src/test/java/org/acme/leader/ZombieLeaderRecordsTwiceIT.java),
which freezes the leader with `pause()` in the middle of an item, then
[`FencingTokenRejectsZombieIT`](src/test/java/org/acme/leader/FencingTokenRejectsZombieIT.java), the same
with fencing switched on by a `@TestProfile`. The steps of the scenario are in
[`Zombie`](src/test/java/org/acme/leader/Zombie.java), the application in
[`LeaderJob`](src/main/java/org/acme/leader/LeaderJob.java).

```bash
./mvnw -pl samples/zombie-leader verify
```
