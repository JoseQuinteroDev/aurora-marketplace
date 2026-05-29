# Vulnerable Lab — Attack & Remediate

A deliberate **security learning lab** built on top of Aurora. The idea: on a
dedicated branch, *reintroduce* realistic vulnerabilities, prove they are
exploitable with a documented walkthrough, then show the fix that the `main`
branch already enforces. It demonstrates both sides of the craft — **offense and
defense** — which is what a DevSecOps profile is judged on.

> ⚠️ **Safety rules.** The lab lives **only** on a clearly-named branch
> (`vulnerable-lab`), never merges to `main`, never deploys to a public host, and
> runs against the local stack with throwaway data. Every weakening is a single,
> reviewable commit so the diff *is* the lesson.

## Why this exists

`main` is hardened. That's hard to *show* — a recruiter sees a control and has to
trust it works. The lab makes the control legible: here is the app **without** the
control (and the exploit), here is the app **with** it (and the exploit failing).
Each scenario is a self-contained story: vulnerability → exploit → impact → fix.

## How the lab is structured

```
vulnerable-lab branch
├── docs/appsec/labs/
│   ├── 01-broken-access-control-idor.md
│   ├── 02-jwt-trusting-the-role-claim.md
│   ├── 03-trusting-client-prices.md
│   └── ...
└── (one commit per scenario that weakens a control, tagged lab/<id>)
```

Each lab links its fix commit on `main` (or the control in
[`security-controls.md`](security-controls.md)), so the reader can diff the
"broken" and "fixed" states.

## Scenario backlog (mapped to this codebase)

These mirror the residual risks and controls already identified — they are not
hypothetical, they target Aurora's real surfaces.

| ID | OWASP | Scenario | Control on `main` it removes |
|---|---|---|---|
| 01 | A01 | **IDOR**: drop an ownership check so customer A reads B's order by id | Per-resource ownership assertion in services |
| 02 | A01/A07 | **Trust the JWT `role` claim** instead of the DB → forge an admin token | DB-sourced authorities in `SecurityConfig.userDetailsService` |
| 03 | A04 | **Trust client prices/totals** at checkout → buy for €0 | Server-side recomputation in `CheckoutService` |
| 04 | A03 | **String-concatenated query** → SQL injection in a search/filter | Parameterized JPA queries |
| 05 | A02 | **Weak/`alg=none` JWT acceptance** → unsigned token accepted | `verifyWith(key)` pins the algorithm |
| 06 | A05 | **Verbose error handler** leaking stack traces & SQL | `GlobalExceptionHandler` clean JSON |
| 07 | A05 | **Permissive CORS** (`*` + credentials) → cross-origin theft | Per-environment CORS allow-list |
| 08 | A07 | **No rate limit** → unthrottled credential brute force | Gateway Redis token-bucket limiter |

Start with **01, 02, 03** — they are the highest-signal because they show the
exact controls that make Aurora's commerce core trustworthy.

## Writeup template

Each `docs/appsec/labs/<id>-<slug>.md` follows this structure:

```markdown
# Lab <id> — <Title>

- **OWASP:** A0x – <category>
- **Severity (CVSS-ish):** <Critical/High/Medium> — <one-line justification>
- **Affected component:** <core / gateway / notification>
- **Control normally enforcing this:** <link to security-controls.md / main commit>

## 1. The weakness
What was changed to introduce it (link the lab commit/diff) and why the code is
now exploitable.

## 2. Exploit walkthrough
Exact, reproducible steps against the local stack — requests, payloads, tokens.
Include the observed result (the data leaked, the privilege gained, the €0 order).

    # e.g.
    curl -s http://localhost:8088/api/orders/<other-users-id> \
      -H "Authorization: Bearer <customer-A-token>"

## 3. Impact
What an attacker gains; blast radius; which asset (see threat-model.md) is hit.

## 4. Detection
What signal would reveal this in production — an audit-log entry, a metric/trace
anomaly, a rate-limit/DLT spike.

## 5. Remediation
The fix `main` already applies, with the code reference. Re-run the exploit to
show it now fails (paste the `403`/`400`/rejected result).

## 6. Regression test
Link the test that locks the fix in (e.g. the IDOR/authz test in
`security-testing.md`) so the vulnerability cannot silently return.
```

## Worked example (abbreviated) — Lab 02: trusting the `role` claim

**Weakness.** Change the authentication filter to read authorities from the JWT's
`role` claim instead of loading the user from the database.

**Exploit.** Log in as a normal customer, decode the JWT, change `"role":"CUSTOMER"`
to `"role":"ADMIN"`, re-sign it *if* the secret is known — or, in the worse
variant where signature isn't verified, just edit it — and call `/api/admin/**`.
Result: full admin access.

**Impact.** Complete authorization bypass (A01) — read all orders, change prices,
run batch jobs. Critical.

**Remediation (already on `main`).** `SecurityConfig.userDetailsService` reloads
the user from the database and derives authorities from the persisted `role`; the
token claim is non-authoritative, and `JwtService.verifyWith(key)` rejects
tampered/unsigned tokens.

**Regression test.** `JwtAuthenticationFilterTest` asserts the authenticated
principal's authorities come from the loaded `UserDetails`, not the token — so
this exploit cannot regress. (See [`security-testing.md`](security-testing.md).)

## Running the lab safely

```powershell
git checkout -b vulnerable-lab        # never push experiments to main
docker compose up -d                  # local infra only; throwaway data
# apply ONE scenario commit, reproduce the exploit, document it, then revert
```

When done, the branch is a teaching artifact: a gallery of "here's how it breaks,
here's why `main` doesn't." Keep it updated as new controls land on `main`.
