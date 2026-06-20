# Aurora Marketplace — Frontend Cleanup (progress log)

> Last updated: 2026-06-20.
> ✅ **Everything merged to `main`** (fast-forward) and pushed to GitHub. Each commit is atomic and builds green.
> **4 rounds of review** were performed (the 4th over the session's new code: full-stack ratings + test harness → clean backend and harness, only 2 trivial pluralization fixes).

Phased plan to clean up the Angular frontend.

---

## ✅ DONE

| Commit | Phase | What |
|---|---|---|
| `ce3a228` | Base | Earlier polish of copy/footer/login, SEO meta, Kafka fix, initial seed. |
| `011203e` | **1 · Admin + security** | Admin link hidden from non-admins, JWT expiration, 401/403 interceptor, anti-open-redirect `returnUrl`. |
| `e81efb2` | **2 · Cart + Toasts** | `ToastService` + `ToastHostComponent`, add-to-cart/favorites feedback, anonymous→toast+returnUrl, cart/wishlist autosync. |
| `e5e8b2d` | **3 · 100% Spanish** | Literals→`translations.ts`, dynamic `<html lang>`, localized `TitleStrategy`, order states. |
| `4b60b13` | **4 · Catalog** | Seed grown to 8 cats / 12 brands / ~43 products; functional filter chips + sorting + URL-driven; home tiles with deep-link. |
| `f055bda` | **5 · Brand voice + honest ratings** | Copy rewrite with a "direct and honest" voice (banned-words list applied across home/footer/nav/catalog/auth/product). Tagline *"Buena tecnología, sin postureo."* / *"Good tech, no hype."* **Killed the fake ★4.8**: cards have no rating; the detail view shows the average + number of reviews only if they exist, otherwise "Be the first to review"; hero metrics → real value props (24-48h shipping, 30-day returns, 100% encrypted payment). Alt-text and SEO meta corrected. |
| `c0bb0da` | **6a · Dark mode** | `ThemeService` (signal + localStorage + prefers-color-scheme), anti-FOUC script in `index.html`, sun/moon toggle in the header. Dark mode was "dead" (`dark:` classes everywhere but nothing was adding `.dark`); now it works and persists. |
| `f810a99` | **6b · Two-tier typography** | **Fraunces** (variable display serif) for headlines + DM Sans for body. Base rule on `h1/h2/h3`, `display` family in Tailwind, hero/sections/titles from `font-black`→`font-semibold`. Goodbye to "everything font-black". |
| `d3db823` | **6c · Brand placeholder** | Product images without a photo no longer fall back to a random Unsplash image: inline brand SVG (Aurora monogram). Image/gallery logic centralized in `core/util/product-media.ts`. |
| `e4276dd` | **7 · Accessibility + cleanup** | Skip-link (first tab stop), focus moved to content on SPA route changes, scroll restoration, **focus-trap** in the mobile filters modal (focus enters, Tab wraps, Escape closes, focus is restored), real alt-text from `ProductImage.altText`. Dedup: shared `firstActiveVariantId()`. |
| `5287e64` | **Polish** | **Styled 404** page (in the storefront chrome, EN/ES, light/dark) instead of redirecting silently; routes reordered (admin given priority). Brand favicon (SVG). Bundle budget raised to 700kB → **build with no warnings**. |
| `4a51de2` | **Bugfix (review)** | The product detail read the slug only once (`snapshot`); navigating product→product left the previous one on screen. It now subscribes to `paramMap` and resets state per product. |
| `8bb52f0` | **Review #1** | aria-labels on the 3 search inputs; dark-mode parity on payment (the success/failure color was lost in dark); 8 page h1s from `font-black`→`font-semibold` (consistency); "ADMIN" badge via i18n. |
| `9c7d2e9` | **Review #2** | Admin sign-out reachable on mobile (previously only in the sidebar hidden `<lg`); mobile filters modal with `max-height`+scroll (no longer ran off-screen); **error state in reviews** (a failure is no longer shown as "no reviews"); dead code removed (unused icon fields, `design-tokens.ts`). Security review: no findings. |
| `daa9326` | **Performance** | **Lazy-loading** of all feature routes (code-splitting). Initial bundle **579kB → 429kB** (~111kB transfer), back under the original 500kB budget. |
| `6aec870` | **Review #3** | UX/commerce/detail: cart and wishlist with **brand placeholder** (previously an identical stock photo for everything); cart badge hidden when empty; detail price following the selected variant; real error state + retry in wishlist; search reset on a catalog with no results; `returnUrl` preserved between login↔register; success CTA on payment; admin skeleton 8→7; cart thumbnail constrained on mobile; **ThemeService fix** (it persisted on every run, freezing the OS preference → now only on toggle); gallery dedupe (avoids NG0955). |

| `cac7277` | **Feature: real ratings (full-stack)** | The cards had no social proof. Now the backend exposes `averageRating`+`reviewCount` in list/search/detail via **a single batched aggregation query** (`ProductRatingStats` projection, **no N+1**); the card shows `★ average (count)` **only if there are real reviews**. Backend tests 12/12. Verified end-to-end (review published → API + card + detail). |
| `5268e59` | **Test harness (frontend)** | There was no test runner. Wired the modern Angular builder with **Vitest** (jsdom, no browser) + first suite (**17 tests green**): open-redirect guard, cart error mapping, product-media, `ThemeService`, and a component. `npm test` runs once (CI-friendly), `npm run test:watch` for development. |
| `870d20f` | **Editorial hero (frontend-design)** | Redesigned the hero from a generic "centered + image-card" layout into an **editorial** composition: oversized Fraunces headline (fluid clamp), eyebrow with an amber rule, asymmetric layout (subtitle/CTA column next to a framed image with an honest stock badge), inline trust strip (24-48h · 30 days · encrypted payment), warm background with subtle **grain** + staggered reveal on load (respects `prefers-reduced-motion`). Consistent with the system; verified light/dark/mobile. |

**Three rounds of adversarial review** (12 agents: a11y, i18n, bugs, design, responsive, state coverage, security, dead code, UX/microcopy, commerce, visual detail, correctness of the new code) — **24 findings, all fixed and verified**. Security review: no findings.

Session verification: green production build on every commit + live **Playwright** (44 products / 0 i18n leaks; theme toggle; skip-link first tab stop; complete focus-trap; 404; authenticated journey register→cart→checkout→orders→wishlist in light and dark).

---

## ⬜ FUTURE IDEAS (optional)

- ~~Real rating on cards~~ ✅ **done** (`cac7277`, full-stack, no N+1).
- **Admin**: the dashboard was not re-verified visually tonight (a user could not be promoted to ADMIN due to a guardrail). Its changes were minimal (only the global headline font). Worth a quick pass with a real admin.
- ~~Test harness~~ ✅ **done** (`5268e59`, Vitest + jsdom, 17 tests). Expandable: more component/service coverage and, if desired, e2e with Playwright.
- **Editorial imagery**: the decorative photos (hero, auth, promo) are still Unsplash; they look fine, but a curated, owned brand set would lift it even further.

---

## ▶️ How to bring it up and verify (tested this session)

```powershell
# 1) Infra (Docker)
docker compose up -d postgres kafka redis mailpit
# If you restarted containers and the backend can't connect to Postgres: docker restart aurora_postgres

# 2) Backend (in backend/) — health at http://localhost:8080/actuator/health
.\mvnw.cmd spring-boot:run

# 3) Frontend (in frontend/) — http://localhost:4200
$env:NODE_OPTIONS="--max-old-space-size=4096"   # avoids Node's OOM
npm start

# 4) Seed (empty DB). The admin must register first via /register; the seed promotes them:
docker exec -i aurora_postgres psql -U aurora_user -d aurora_marketplace < seed-data.sql
```

Visual verification: Playwright is installed in `frontend/node_modules` (not committed). The temporary `frontend/__*.mjs` scripts used this session are in `.git/info/exclude` (not committed).

---

## ⚠️ Gotchas

- **Push to `main` blocked** by the environment guardrail → the work goes to the `feat/professional-overhaul` branch + PR. The `gh` CLI is not installed.
- **Postgres port-forward (5433)** breaks when containers restart → `docker restart aurora_postgres`.
- **Node OOM** in build/serve → `NODE_OPTIONS=--max-old-space-size=4096`.
- **`npm run build` while `ng serve` is running** triggers a transient error overlay (they share `.angular/cache`); the dev server recovers on its own.
- **6 old "electronics" products** from the old seed still coexist with the new catalog (idempotent). Wipe+reseed if you want a clean catalog.
