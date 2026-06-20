# Aurora — "Quiet Luxury" Redesign (design spec)

Date: 2026-06-20. Status: direction approved by the owner (styleboard v1). Branch: `feat/quiet-luxury-redesign`.

**Complete** visual redesign of the Angular storefront toward a *quiet luxury* identity: monochrome ink/bone, a single pine-green accent, plenty of breathing room, impeccable detail, **dark mode by default** (light available via the toggle). Visual layer + identity only; no logic/business changes.

## Locked decisions
- **Direction:** quiet luxury / minimal (refs: Aesop, COS, Bottega).
- **Accent:** pine green — `#2A5A47`, deep `#1F3D32`, bright `#3E7C63`. Only on CTAs, hairlines, monogram, positive states. Never dominant.
- **Default mode:** dark/ink. Light/bone remains available via the toggle.
- **Logo:** concept #1 — "A" monogram as a **summit / ray of dawn** (two thin lines forming the A + pine dot at the apex) + `AURORA` wordmark in small caps with wide tracking (~0.4em).

## Tokens
**Color (CSS vars + tailwind `aurora` palette — the values are rewritten, the names are kept so we don't touch 100+ `dark:` usages):**
- Dark (default): bg `#0E0D0B`, surface `#16140F`, panel `#1A1813`, line `rgba(244,241,234,.12)`.
- Text: bone `#F4F1EA`, dim `#B8B2A6`, muted `#8A857B`.
- Light: bg `#F4F1EA`, surface `#FBF9F4`, ink `#16140F`, muted `#5A564C`, line `rgba(20,19,15,.10)`.
- Pine accent (above). States: positive = pine; error = `#8A3B3B` (clay) light / `#C9756F` on dark; warning = `#9C7A3D`.
- **Mapping of existing tailwind names** → ink=`#0E0D0B` night=`#0E0D0B` charcoal=`#1A1813` mist=`#F4F1EA` pearl/paper=`#FBF9F4` line=`#E7E0D6`(light)/var in dark, muted=`#8A857B`, **amber/gold→pine** (`#2A5A47`/`#3E7C63`), emerald→pine, ocean→pine-bright or muted blue, rose→`#C9756F`.
- (Repainting `amber`/`gold` to pine makes all the existing accent turn green without rewriting every template.)

**Typography (Google Fonts):**
- Display (headlines, logo, product names): **Cormorant Garamond** (weight 500/600, high-contrast). Replaces Fraunces in `font-display`.
- Text/UI: **Hanken Grotesk** (400-700). Replaces DM Sans in `font-sans`.
- Small caps with tracking for eyebrows, prices, and labels (catalog breathing room).

**Shape:**
- Radii: `ui` 3px, `soft` 6px (previously 8/14) — sharper/more restrained.
- Shadows: hairlines + very subtle shadows; remove heavy `shadow-premium/glow` (replace with a thin border + light shadow). Less glass/blur.
- Density: more white space, more airy sections.

## Logo
Reusable `shared/brand-logo` component (inline SVG, `currentColor` for the stroke, fixed pine dot) with `sm`/`md` sizes and an optional wordmark. Favicon (index.html) updated to the same monogram. Product placeholder (`product-media`) repainted to ink/pine.

## Phases (each one: green build + Playwright verification light/dark + commit)
1. **Design system**: tailwind.config (color/ font/ radius/ shadow) + styles.css (vars, hero, base components `.ui-button*`, `.surface-panel`, `.soft-card`, etc.) + ThemeService/index.html default→dark + `brand-logo` + favicon + placeholder.
2. **Layout**: storefront-layout (header with new logo, footer), admin-shell.
3. **Home**: hero + sections in the quiet-luxury language.
4. **Catalog + product detail**.
5. **Auth, cart, checkout, payment, orders, wishlist, 404**.
6. **Final sweep** (contrast/a11y, dark+light, mobile) + merge to `main`.

## Non-goals
- Don't touch the backend, routes, i18n (except copy that no longer fits), tests (kept green), or logic.
- Don't break dark/light, a11y (skip-link, focus-trap), or lazy-loading.
