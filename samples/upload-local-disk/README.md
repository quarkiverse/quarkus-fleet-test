# Upload on local disk

**The bug.** A file upload saved to the local disk of the instance that received it. A pod's disk is its own:
nothing written there is visible to the other pods, and it is gone when the pod is replaced. The next request
for the document goes through the load balancer to whichever instance is next, which has no such file and
answers 404. The signature is a document that is there on one refresh and gone on the next, with nothing in the
logs. On one instance uploads and downloads always agree, so a `@QuarkusTest` never sees this.

**The fix.** Shared storage: the database, as here, or object storage, or a volume mounted into every pod.

Start with [`DocumentMissingOnOtherInstanceIT`](src/test/java/org/acme/documents/DocumentMissingOnOtherInstanceIT.java),
which uploads through one instance and downloads through the other, then
[`DocumentFoundOnEveryInstanceIT`](src/test/java/org/acme/documents/DocumentFoundOnEveryInstanceIT.java), the
same with the database store set by a `@TestProfile`. The application is
[`DocumentResource`](src/main/java/org/acme/documents/DocumentResource.java); the fleet gives each instance a
temp directory of its own, as each pod has a filesystem of its own.

```bash
./mvnw -pl samples/upload-local-disk verify
```
