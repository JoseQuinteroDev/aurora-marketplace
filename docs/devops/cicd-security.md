# DevSecOps Pipeline — Security in CI/CD

This document describes how security is **automated** in Aurora Marketplace: the
GitHub Actions workflows, the scanners they run, what each one defends against,
how findings surface, and the gating philosophy. It is the automation companion
to the [AppSec program](../appsec/README.md).

> **DevSecOps in one line:** shift security *left* (into every commit/PR) and make
> it *continuous* (re-scan on a schedule), so vulnerabilities are caught by the
> pipeline rather than in production.

## Pipeline overview

```
   push / pull_request ──┬──────────────► ci.yml         (Dev:  build + test)
                         │
                         └──────────────► security.yml   (Sec:  static scanners)
                                              │
              ┌───────────────┬───────────────┼───────────────┬───────────────┐
              ▼               ▼               ▼               ▼               ▼
            CodeQL          Trivy fs        Gitleaks      Trivy config      CycloneDX
            (SAST)          (SCA)        (secrets, GATE)  + Hadolint         (SBOM)
              │               │               │           (IaC/image)         │
              └──────► SARIF ─┴──► GitHub Security tab ◄──┘                artifact

   schedule (weekly) ──────────► security.yml   (catch newly-disclosed CVEs)
   schedule (weekly) / manual ─► dast.yml       (DAST: scan the RUNNING app)
   Dependabot         ──────────► automated dependency-update PRs
```

The split is deliberate: **static** analysis (code, manifests, Dockerfiles) runs
on every push/PR and is fast; **dynamic** analysis (DAST) needs a running stack
and minutes of scan time, so it runs on a schedule / on demand rather than on
every commit.

| Workflow | File | Trigger | Purpose |
|---|---|---|---|
| CI | [`ci.yml`](../../.github/workflows/ci.yml) | push / PR | Build + test the 3 Java modules (matrix) and build the Angular frontend. |
| Security | [`security.yml`](../../.github/workflows/security.yml) | push / PR / weekly | SAST, SCA, secret scanning, IaC/image scanning, SBOM. |
| DAST | [`dast.yml`](../../.github/workflows/dast.yml) | weekly / manual | NightVision dynamic scan of the running API (opt-in). |
| Dependabot | [`dependabot.yml`](../../.github/dependabot.yml) | weekly / on-advisory | Automated dependency-update PRs across all ecosystems. |

## The scanners

### 1. SAST — CodeQL
- **Defends against:** code-level vulnerabilities (injection, unsafe
  deserialization, path traversal, etc.) in Java and TypeScript.
- **How:** `github/codeql-action` with `build-mode: none` (buildless extraction)
  and the `security-extended` query suite, run per language in a matrix
  (`java-kotlin`, `javascript-typescript`).
- **Output:** alerts in the **Security → Code scanning** tab.

### 2. SCA — Trivy (filesystem)
- **Defends against:** known-CVE dependencies (A06) in Maven and npm manifests.
- **How:** `scan-type: fs`, `scanners: vuln`, `severity: CRITICAL,HIGH`,
  results as SARIF. The Trivy DB repository is pinned to the AWS ECR mirror so
  rate-limited pulls don't fail the job.
- **Output:** code-scanning alerts (category `trivy-dependencies`).
- **Gate:** report-only. Base images and transitive deps always carry *some*
  CVEs; these are triaged from the Security tab, not used to block every PR.

### 3. Secret scanning — Gitleaks  ⛔ workflow gate
- **Defends against:** credentials, tokens, or keys committed to the repo (A02 /
  A05). Scans the **full history** (`fetch-depth: 0`).
- **Gate:** **fails the workflow run.** A leaked secret is the one finding worth
  stopping for — once pushed, assume it is compromised and rotate it.
- **Config:** [`.gitleaks.toml`](../../.gitleaks.toml) extends the full default
  ruleset and adds a *narrow* allowlist for the known test-only HMAC keys in the
  JWT unit tests (not real credentials). This keeps the full-history scheduled
  scan green without weakening detection — only those exact strings are exempt.
- **Caveat (honest):** failing the *run* only blocks a *merge* once **branch
  protection** marks this job a required status check. Until that is configured on
  `main` (see *Hardening backlog*), a red `secret-scan` is loud but not yet
  merge-blocking.

### 4. IaC & image — Trivy config + Hadolint
- **Defends against:** Dockerfile and `docker-compose.yml` misconfiguration
  (running as root, missing `USER`, risky settings) — A05.
- **How:** Trivy `scan-type: config` (SARIF to the Security tab) plus Hadolint
  linting each of the three Dockerfiles at `failure-threshold: warning` (this
  step **does** fail the job on a warning-or-worse).
- **Note:** this scans Dockerfile *misconfiguration*, not the assembled image's
  OS-package CVEs — see the *Hardening backlog* (built-image scanning).

### 5. SBOM — CycloneDX
- **Defends against:** supply-chain blind spots (A08). A Software Bill of
  Materials makes "what are we actually shipping?" answerable and lets you react
  fast when a new CVE drops against a component you already use.
- **How:** `anchore/sbom-action` emits CycloneDX JSON, published as a build
  artifact (and downloadable from the run).

### 6. DAST — NightVision (dynamic application security testing)

This is the **one layer that exercises the running application** the way an
attacker would, rather than reading source. It lives in its own workflow
([`dast.yml`](../../.github/workflows/dast.yml)) because it needs the stack up
and takes 5–15 minutes.

**What NightVision is and how it acts.** NightVision is a *white-box-assisted*
DAST platform. A single scan combines three phases:

1. **API Discovery (static, white-box).** `nightvision swagger extract` performs
   static analysis of the Spring Boot core and produces an OpenAPI spec — *no
   running app or compilation needed*. Each endpoint is annotated with the source
   file and line where its route is declared.
2. **Dynamic scan (black-box).** The **ZAP** and **Nuclei** engines attack the
   live API (injection, auth, misconfig, missing headers, CVE templates, …). For
   private/localhost targets, NightVision's **Smart Proxy** tunnels scan traffic
   through the CLI automatically — no public exposure required.
3. **Code Traceback.** Because the spec carries source annotations, each finding
   links back to the exact controller/method — so the SARIF alert in the Security
   tab points at the *code to fix*, not just the URL that failed.

- **Defends against:** runtime-only issues the static scanners can't see —
  missing HTTP security headers (A05), CORS behavior (A05/A01), error-handling
  leakage (A09), and authentication edge cases at the edge (A07).
- **How the workflow runs:** install the CLI → extract the spec from `./backend`
  → `docker compose --profile apps up` and wait for the gateway to report healthy
  → create/update the target (gateway URL `:8088`) → scan via Smart Proxy →
  export SARIF (with Code Traceback) → upload to the Security tab → tear down.
- **Opt-in & self-gating:** it runs only on **manual dispatch** and a **weekly
  schedule**, and **no-ops cleanly unless the `NIGHTVISION_TOKEN` secret is set**,
  so forks/contributors are never blocked by a required-setup job.
- **Gate:** report-only (findings to the Security tab, category `nightvision-dast`).

**One-time setup (local + interactive — cannot be automated, needs a browser):**

```powershell
nightvision login                                   # browser-based login
nightvision token create                            # store output as repo secret NIGHTVISION_TOKEN
nightvision project create -n aurora-marketplace
# Optional, for authenticated coverage beyond the public surface:
nightvision auth playwright create aurora-auth http://localhost:8088
```

Add the token under **Settings → Secrets and variables → Actions →
`NIGHTVISION_TOKEN`**, then run the workflow from the Actions tab (or wait for the
weekly run). For deeper coverage, record auth and drop `--no-auth` in the scan
step. NightVision's agent skills (`/scan-configuration`, `/scan-triage`,
`/api-discovery`, `/ci-cd-integration`) can drive each of these steps.

> **Local DAST without an account.** You can still run a free baseline scan
> locally with OWASP ZAP — see [`../appsec/security-testing.md`](../appsec/security-testing.md) §3.

### 7. Dependabot
- **Defends against:** drift into outdated/vulnerable components (A06).
- **Covers:** Maven (×3 modules), npm (frontend), Docker base images (×3),
  GitHub Actions. Routine bumps are grouped weekly; **security updates are
  raised immediately** regardless of schedule.

## Gating philosophy

| Finding type | Action | Rationale |
|---|---|---|
| Committed secret | **Fail run** | Irreversible once pushed; must be caught pre-merge. |
| Dockerfile lint (Hadolint ≥ warning) | **Fail job** | Cheap, deterministic, fixed at source. |
| Dependency / image CVE (Trivy) | Report → triage | Noisy by nature; track and remediate via Dependabot. |
| Code scanning (CodeQL) | Report; review high-severity | Real bugs, but need human triage for exploitability. |
| IaC misconfig (Trivy config) | Report; fix in PR | Cheap to fix at source. |
| DAST (NightVision) | Report; triage | Needs a running app; confirm exploitability before gating. |

This mirrors how mature teams operate: **a small number of high-confidence hard
gates**, everything else visible and tracked. Over-gating trains people to
bypass the pipeline; under-gating lets real risk through.

## What the CI workflow actually gates (honest version)

`ci.yml` is **not** a uniform build-and-test gate:

- **Backend (gates):** `mvnw clean verify` across `backend`, `gateway`,
  `services/notification-service` (matrix) — a real failure fails CI.
- **Frontend build (gate):** `npm run build` must succeed.
- **Frontend unit tests (NOT a gate):** the Karma/headless test step is
  `continue-on-error: true`, so Angular test failures **do not** fail CI today.
  This is called out so the diagram isn't read as "the frontend is test-gated" —
  it isn't yet. See the *Hardening backlog*.

## How to read the results

1. **Security tab → Code scanning** — CodeQL, Trivy dependency, Trivy config, and
   NightVision DAST alerts, deduplicated and grouped by category.
2. **Actions tab → the workflow run** — per-job logs; `secret-scan` and the
   Hadolint step in `iac-scan` are the ones that can mark a run red.
3. **Run artifacts** — download `aurora-sbom.cyclonedx.json` (SBOM) and
   `nightvision-dast-logs` (DAST scan log / extracted spec).
4. **Pull requests** — Dependabot PRs arrive labeled by ecosystem.

## Local pre-commit checks (optional, recommended)

Catch issues before they reach CI:

```powershell
# Secret scan locally (Docker)
docker run --rm -v "${PWD}:/repo" zricethezav/gitleaks:latest detect --source=/repo -v

# Dependency + config scan locally (Docker)
docker run --rm -v "${PWD}:/repo" aquasec/trivy:latest fs --scanners vuln /repo
docker run --rm -v "${PWD}:/repo" aquasec/trivy:latest config /repo

# Lint a Dockerfile (matches the CI gate)
docker run --rm -i hadolint/hadolint hadolint --failure-threshold warning - < backend/Dockerfile
```

## What this demonstrates

**In place:** SAST, SCA, secret scanning, IaC/image scanning, SBOM, automated
dependency updates, **and DAST** — wired to run on every change, weekly, and on
demand. The static half runs on every commit; the dynamic half (NightVision)
runs against the live stack with source-linked findings.

## Hardening backlog (tracked, prioritized)

These are the concrete next steps, ordered by value. Several were surfaced by an
adversarial review of the pipeline itself.

| Priority | Item | Why |
|---|---|---|
| **P1** | **Pin every Action to a full commit SHA** (e.g. `actions/checkout@<sha> # v4`) and let the `github-actions` Dependabot ecosystem bump the SHAs. | Mutable tags are a real supply-chain risk — and the security pipeline is the *worst* place to run an attacker-re-tagged action. Today **nothing is SHA-pinned** (all `@v4`/`@v3`/`@v0`…). |
| **P1** | **Enable branch protection on `main`** requiring CI + `secret-scan` to pass before merge, plus PR review. | Converts the documented "hard gate" into an *enforced* one — today a red `secret-scan` can still be merged. |
| **P2** | **Add `actions/dependency-review-action`** on `pull_request`. | A focused, PR-scoped, *blocking* check on newly-introduced CRITICAL/HIGH CVEs (and disallowed licenses) — complements report-only Trivy and after-the-fact Dependabot. |
| **P2** | **Make the frontend test step a gate** (drop `continue-on-error`, fix any flakiness). | The frontend has no enforced test gate today. |
| **P2** | **Scan the built image** (`trivy image` on each built Dockerfile tag), not just the filesystem/Dockerfile. | Catches base-image / OS-package CVEs that manifest-only scanning misses. |
| **P2** | **Sign images + provenance** (cosign + SLSA build-provenance / attest the SBOM). | Lets consumers verify a deployed image is the genuine CI output (A08). |
| **P3** | **Pin Docker base images by digest** (`eclipse-temurin:21-jre@sha256:…`). | Reproducible builds; closes the floating-tag substitution risk. Dependabot can update the digest. |
| **P3** | **Narrow SCA gating** to fail on *net-new* CRITICAL application CVEs only. | Stops the worst cases without the over-gating that base-image noise would cause. |

See the [OWASP Top 10 map](../appsec/owasp-top-10.md) for how these tie back to
the application-level risk categories.
