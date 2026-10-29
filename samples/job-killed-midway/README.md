# Job killed midway

**The bug.** A long-running import, accepted by a REST endpoint and processed row by row in a background thread
of the instance that accepted it. A thread dies with its process: when that instance is killed (OOM, node
failure, scale-in) the import stops where it was. The other instance never knew about it, the database says
only how far it got, and nothing picks it up again. The client who was told the import had started waits for
ever.

**The fix.** A checkpoint plus a reaper that every instance runs. The job writes its progress and a heartbeat to
the database after every row, in the same transaction as the row itself. A scheduled reaper on every instance
claims imports whose heartbeat is older than a few seconds and resumes them from the checkpoint. The claim is one
`UPDATE ... WHERE heartbeat < now() - interval '3 seconds' RETURNING id, processed` that refreshes the heartbeat
as it takes the import, so two reapers cannot take the same one (`FOR UPDATE SKIP LOCKED` would do as well). The
primary key on `(import_id, row_no)` guarantees that no row is imported twice, whoever resumes.

Start with [`ImportLostWhenInstanceDiesIT`](src/test/java/org/acme/imports/ImportLostWhenInstanceDiesIT.java),
which starts the import on one instance, kills it a hundred rows in and watches the row count stand still, then
[`ImportResumedByOtherInstanceIT`](src/test/java/org/acme/imports/ImportResumedByOtherInstanceIT.java), the same
with the reaper switched on by a `@TestProfile`: the survivor finishes the import, and the rows come from both
instances. The steps of the scenario are in [`Imports`](src/test/java/org/acme/imports/Imports.java), the
application in [`ImportJob`](src/main/java/org/acme/imports/ImportJob.java).

```bash
./mvnw -pl samples/job-killed-midway verify
```
