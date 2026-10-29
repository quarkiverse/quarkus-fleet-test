# Scale out

**The bug.** No readiness probe in the deployment. A new replica gets traffic as soon as it listens, and for
the seconds it spends loading its catalog, every request routed to it fails.

**The fix.** It is in the deployment, not the application: a readiness probe on `/q/health/ready`. The new
replica joins the rotation when its catalog is loaded and not before.

Start with [`NewInstanceServesBeforeItIsReadyIT`](src/test/java/org/acme/catalog/NewInstanceServesBeforeItIsReadyIT.java),
which starts a fleet of two with one running (`running = 1`), sends requests without pause and starts the
second instance in the middle, then
[`NewInstanceWaitsUntilReadyIT`](src/test/java/org/acme/catalog/NewInstanceWaitsUntilReadyIT.java), the same
with readiness on.

```bash
./mvnw -pl samples/scale-out verify
```
