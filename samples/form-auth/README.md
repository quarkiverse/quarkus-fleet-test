# Form authentication

**The bug.** Form authentication without `quarkus.http.auth.session.encryption-key`. Each instance encrypts
the login cookie with a key it generated at startup, so a user logged in on one instance is anonymous on the
next. In production that is a login loop whenever the load balancer is not sticky; on one instance it never
shows.

**The fix.** One configured encryption key, the same on every instance.

Start with [`LoginLostOnOtherInstanceIT`](src/test/java/org/acme/account/LoginLostOnOtherInstanceIT.java),
which pins the balancer to one instance to log in and to the other to use the cookie, then
[`LoginSharedAcrossInstancesIT`](src/test/java/org/acme/account/LoginSharedAcrossInstancesIT.java), the same
with the key set by a `@TestProfile`.

```bash
./mvnw -pl samples/form-auth verify
```
