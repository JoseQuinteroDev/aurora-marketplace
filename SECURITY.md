# Security Policy

Aurora Marketplace is a security-focused portfolio project. Security is a
first-class concern here — see the full [AppSec program](docs/appsec/README.md),
[threat model](docs/appsec/threat-model.md), and [OWASP Top 10 coverage](docs/appsec/owasp-top-10.md).

## Supported versions

This is an actively-developed portfolio repository; the `main` branch is the only
supported version. Security fixes land on `main`.

## Reporting a vulnerability

If you discover a security issue, please report it **privately** — do not open a
public issue, and do not disclose it publicly until it has been addressed.

- **Preferred:** open a [GitHub private security advisory](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)
  on this repository (Security → Advisories → Report a vulnerability).
- Include: affected component (core / gateway / notification-service / frontend),
  a description, reproduction steps or a proof of concept, and the impact you
  observed.

Please act in good faith: only test against your **own local instance**, never
against data or systems you do not own, and avoid privacy violations or service
disruption.

### What to expect

- Acknowledgement of your report within a reasonable time.
- An assessment of severity (impact × likelihood) and a remediation plan.
- Credit for the finding if you'd like it, once a fix is in place.

## Scope

**In scope:** the application code in this repository — the commerce core, API
gateway, notification-service, and the Angular frontend.

**Out of scope:**
- The local development stack's deliberately-simple defaults (plaintext
  Postgres/Redis/Kafka/SMTP, dev credentials in `docker-compose.yml`). These are
  documented dev conveniences, hardened for real deployments — see
  [`docs/devops/cicd-security.md`](docs/devops/cicd-security.md) and `.env.example`.
- The intentionally-vulnerable [`vulnerable-lab`](docs/appsec/vulnerable-lab.md)
  branch, which exists *to* contain vulnerabilities for educational purposes.
- Findings that require physical access or a compromised developer machine.

## Hardening for deployment

Before running outside local development, review the production checklist in the
[AppSec program](docs/appsec/README.md#security-posture-at-a-glance) and at
minimum:

- Set a strong `APP_SECURITY_JWT_SECRET` (≥32 chars) from a secret manager.
- Replace all development credentials (see `.env.example`).
- Enforce TLS in transit and restrict CORS to known origins.
- Run containers as non-root (already configured in the Dockerfiles) and behind
  a network that does not expose the core/data/event tiers publicly.
