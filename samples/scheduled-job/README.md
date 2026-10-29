# Scheduled job

**The bug.** A `@Scheduled` report runs on every instance. One instance sends one report per tick, two
instances send two. A `@QuarkusTest` runs one instance and cannot tell the difference.

**The fix.** Each run claims its tick in the shared database with a unique insert, and only the instance that
wins sends the report.

Start with [`ReportSentTwiceIT`](src/test/java/org/acme/reports/ReportSentTwiceIT.java), which fails on the
bug, then [`ReportSentOnceIT`](src/test/java/org/acme/reports/ReportSentOnceIT.java), the same scenario with
the fix switched on by a `@TestProfile`. The application is
[`NightlyReport`](src/main/java/org/acme/reports/NightlyReport.java).

```bash
./mvnw -pl samples/scheduled-job verify
```
