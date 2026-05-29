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
                         └──────────────► security.yml   (Sec:  layered scanners)
                                              │
              ┌───────────────┬───────────────┼───────────────┬───────────────┐
              ▼               ▼               ▼               ▼               ▼
            CodeQL          Trivy fs        Gitleaks      Trivy config      CycloneDX
            (SAST)          (SCA)        (secrets, GATE)  + Hadolint         (SBOM)
              │               │               │           (IaC/image)         │
              └──────► SARIF ─┴──► GitHub Security tab ◄──┘                artifact

   schedule (weekly) ──────────► security.yml   (catch newly-disclosed CVEs)
   Dependabot         ──────────► automated dependency-update PRs
```

| Workflow | File | Purpose |
|---|---|---|
| CI | [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) | Build + test the 3 Java modules (matrix) and the Angular frontend. |
| Security | [`.github/workflows/security.yml`](../../.github/workflows/security.yml) | SAST, SCA, secret scanning, IaC/image scanning, SBOM. |
| Dependabot | [`.github/dependabot.yml`](../../.github/dependabot.yml) | Automated dependency-update PRs across all ecosystems. |

## The scanners

### 1. SAST — CodeQL
- **Defends against:** code-level vulnerabilities (injection, unsafe
  deserialization, path traversal, etc.) in Java and TypeScript.
- **How:** `github/codeql-action` with `build-mode: none` (buildless extraction)
  and the `security-extended` query suite, run per language in a matrix.
- **Output:** alerts in the **Security → Code scanning** tab.

### 2. SCA — Trivy (filesystem)
- **Defends against:** known-CVE dependencies (A06) in Maven and npm manifests.
- **How:** `scan-type: fs`, `scanners: vuln`, `severity: CRITICAL,HIGH`,
  results as SARIF.
- **Output:** code-scanning alerts (category `trivy-dependencies`).
- **Gate:** report-only. Base images and transitive deps always carry *some*
  CVEs; these are triaged from the Security tab, not used to block every PR.

### 3. Secret scanning — Gitleaks  ⛔ hard gate
- **Defends against:** credentials, tokens, or keys committed to the repo (A02 /
  A05). Scans the **full history** (`fetch-depth: 0`).
- **Gate:** **fails the build.** A leaked secret is the one finding worth
  stopping a merge for — once pushed, assume it is compromised and rotate it.

### 4. IaC & image — Trivy config + Hadolint
- **Defends against:** Dockerfile and `docker-compose.yml` misconfiguration
  (running as root, missing `USER`, risky settings) — A05.
- **How:** Trivy `scan-type: config` (SARIF to the Security tab) plus Hadolint
  linting each of the three Dockerfiles at `failure-threshold: warning`.

### 5. SBOM — CycloneDX
- **Defends against:** supply-chain blind spots (A08). A Software Bill of
  Materials makes "what are we actually shipping?" answerable and lets you react
  fast when a new CVE drops against a component you already use.
- **How:** `anchore/sbom-action` emits CycloneDX JSON, published as a build
  artifact (and downloadable from the run).

### 6. Dependabot
- **Defends against:** drift into outdated/vulnerable components (A06).
- **Covers:** Maven (×3 modules), npm (frontend), Docker base images (×3),
  GitHub Actions. Routine bumps are grouped weekly; **security updates are
  raised immediately** regardless of schedule.

## Gating philosophy

| Finding type | Action | Rationale |
|---|---|---|
| Committed secret | **Block** | Irreversible once pushed; must be caught pre-merge. |
| Dependency / image CVE | Report → triage | Noisy by nature; track and remediate via Dependabot, don't wall off every PR. |
| Code scanning (CodeQL) | Report; review high-severity | Real bugs, but need human triage for exploitability. |
| IaC misconfig | Report; fix in PR | Cheap to fix at source. |

This mirrors how mature teams operate: **a small number of high-confidence hard
gates**, everything else visible and tracked. Over-gating trains people to
bypass the pipeline; under-gating lets real risk through. The line above is
where Aurora draws it.

## How to read the results

1. **Security tab → Code scanning** — CodeQL, Trivy dependency, and Trivy config
   alerts, deduplicated and grouped by category.
2. **Actions tab → the workflow run** — per-job logs; the `secret-scan` job is
   the one that can mark a run red.
3. **Run artifacts** — download `aurora-sbom.cyclonedx.json` for the SBOM.
4. **Pull requests** — Dependabot PRs arrive labeled by ecosystem.

## Local pre-commit checks (optional, recommended)

Catch issues before they reach CI:

```powershell
# Secret scan locally (Docker)
docker run --rm -v "${PWD}:/repo" zricethezav/gitleaks:latest detect --source=/repo -v

# Dependency + config scan locally (Docker)
docker run --rm -v "${PWD}:/repo" aquasec/trivy:latest fs --scanners vuln /repo
docker run --rm -v "${PWD}:/repo" aquasec/trivy:latest config /repo

# Lint a Dockerfile
docker run --rm -i hadolint/hadolint < backend/Dockerfile
```

## What this demonstrates (and what is next)

**In place:** SAST, SCA, secret scanning, IaC/image scanning, SBOM, and automated
dependency updates — wired to run on every change and weekly.

**Natural next steps** (deployment-dependent, out of scope for the local repo):
- **DAST** — run OWASP ZAP against a running stack (see
  [`../appsec/security-testing.md`](../appsec/security-testing.md)).
- **Image signing & provenance** — cosign + SLSA attestation on pushed images.
- **Branch protection** — require the security workflow to pass before merge.
- **Pinned actions by SHA** — Dependabot's `github-actions` updates keep them current.
