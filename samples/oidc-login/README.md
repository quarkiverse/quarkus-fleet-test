# OIDC login

**The bug.** An OpenID Connect login with PKCE. The instance that sends the browser to Keycloak sets an encrypted
state cookie, and the instance that receives the browser back has to decrypt it. The key is derived from the
client secret when that is at least 32 characters long; otherwise each instance generates its own at startup.
Keycloak Dev Services registers the client with the secret `secret`, so a login that starts on one instance and
comes back, through the load balancer, to the other fails with 401, although the user just authenticated. On one
instance the flow always completes.

**The fix.** `quarkus.oidc.authentication.state-secret`, the same on every instance.

Start with [`LoginFailsOnOtherInstanceIT`](src/test/java/org/acme/profile/LoginFailsOnOtherInstanceIT.java),
which starts the flow through one instance, logs in at Keycloak, and follows the redirect back through the
other, then [`LoginCompletesOnOtherInstanceIT`](src/test/java/org/acme/profile/LoginCompletesOnOtherInstanceIT.java),
the same with the secret set by a `@TestProfile`. The steps of the flow are in
[`CodeFlow`](src/test/java/org/acme/profile/CodeFlow.java). Keycloak is started by Dev Services, once for the
fleet.

```bash
./mvnw -pl samples/oidc-login verify
```
