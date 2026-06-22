# Vulnerable Lab — exploit → remediate writeups

This directory holds the worked **attack-and-defend** scenarios designed in
[`../vulnerable-lab.md`](../vulnerable-lab.md). It exists **only on the
`vulnerable-lab` branch** and is never merged to `main`.

> ⚠️ **Safety.** Every lab *reintroduces* a real vulnerability that `main`
> already fixes. The weakened code lives **only here**, runs **only** against the
> local stack with throwaway data, and is **never** deployed or pushed to `main`.
> Each scenario is a single, reviewable commit tagged `lab/<id>` — so the diff
> *is* the lesson.

## Why this exists

`main` is hardened, and that is hard to *show*: a reader sees a control and has
to trust it works. These labs make each control legible — here is Aurora
**without** the control (and the working exploit), and here is the control on
`main` that makes the exploit fail. That is the whole craft of an AppSec /
DevSecOps profile in one artifact: **offense and defense, side by side.**

## How to read a lab

Each writeup follows the same six-part structure:

1. **The weakness** — what this branch changed to break the control (with the
   commit/diff) and why the code is now exploitable.
2. **Exploit walkthrough** — exact, reproducible requests against the local
   stack, with the observed result.
3. **Impact** — what the attacker gains and which asset (see
   [`../threat-model.md`](../threat-model.md)) is hit.
4. **Detection** — the signal that would reveal this in production.
5. **Remediation** — the fix `main` already applies, with the code reference;
   re-run the exploit to show it now fails.
6. **Regression test** — the test that locks the fix in (or, honestly, the gap
   where one is still missing).

## Scenarios

| ID | OWASP | Scenario | Control on `main` | Tag |
|---|---|---|---|---|
| [01](01-broken-access-control-idor.md) | A01 | **IDOR** — read another customer's order by id | Per-resource ownership check in `OrderService` | `lab/01` |
| [02](02-jwt-trusting-the-role-claim.md) | A01 / A07 | **Trust the JWT `role` claim** — forge an admin token | DB-sourced authorities in the auth filter | `lab/02` |
| [03](03-trusting-client-prices.md) | A04 | **Trust client prices** at checkout — buy for $0 | Server-side recomputation in `CheckoutService` | `lab/03` |

These are the three highest-signal scenarios because they map directly to the
controls that make Aurora's commerce core trustworthy. The remaining backlog
(SQLi, `alg=none`, verbose errors, permissive CORS, missing rate limit) is
listed in [`../vulnerable-lab.md`](../vulnerable-lab.md).

## Running a lab safely

```powershell
git checkout vulnerable-lab            # the teaching branch — never push to main
docker compose up -d                   # local infra only; throwaway data
cd backend; .\mvnw.cmd spring-boot:run # the weakened core

# reproduce the exploit from the lab writeup, observe the result,
# then diff against main to see the control that stops it:
git diff main -- <weakened file>
```

To inspect one scenario in isolation, check out its tag: `git show lab/01`.
