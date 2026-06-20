# Aurora — Rediseño "Lujo silencioso" (spec de diseño)

Fecha: 2026-06-20. Estado: dirección aprobada por el owner (styleboard v1). Rama: `feat/quiet-luxury-redesign`.

Rediseño visual **completo** de la tienda Angular hacia una identidad *quiet luxury*: monocromo tinta/hueso, un único acento verde pino, mucho aire, detalle impecable, **modo oscuro por defecto** (claro disponible con el toggle). Solo capa visual + identidad; sin cambios de lógica/negocio.

## Decisiones bloqueadas
- **Dirección:** lujo silencioso / minimal (refs: Aesop, COS, Bottega).
- **Acento:** verde pino — `#2A5A47`, deep `#1F3D32`, bright `#3E7C63`. Solo en CTAs, filetes, monograma, estados positivos. Nunca dominante.
- **Modo por defecto:** oscuro/tinta. El claro/hueso sigue vía toggle.
- **Logo:** concepto #1 — monograma "A" como **cumbre / rayo de alba** (dos líneas finas que forman la A + punto pino en el vértice) + wordmark `AURORA` en versalitas con tracking amplio (~0.4em).

## Tokens
**Color (CSS vars + tailwind `aurora` palette — se reescriben los valores, se mantienen los nombres para no tocar 100+ usos `dark:`):**
- Oscuro (default): bg `#0E0D0B`, surface `#16140F`, panel `#1A1813`, línea `rgba(244,241,234,.12)`.
- Texto: bone `#F4F1EA`, dim `#B8B2A6`, muted `#8A857B`.
- Claro: bg `#F4F1EA`, surface `#FBF9F4`, ink `#16140F`, muted `#5A564C`, línea `rgba(20,19,15,.10)`.
- Acento pino (arriba). Estados: positivo = pino; error = `#8A3B3B` (clay) claro / `#C9756F` sobre oscuro; aviso = `#9C7A3D`.
- **Mapeo de nombres tailwind existentes** → ink=`#0E0D0B` night=`#0E0D0B` charcoal=`#1A1813` mist=`#F4F1EA` pearl/paper=`#FBF9F4` line=`#E7E0D6`(claro)/var en dark, muted=`#8A857B`, **amber/gold→pino** (`#2A5A47`/`#3E7C63`), emerald→pino, ocean→pino-bright o muted blue, rose→`#C9756F`.
- (Repintar `amber`/`gold` a pino hace que todo el acento existente pase a verde sin reescribir cada plantilla.)

**Tipografía (Google Fonts):**
- Display (titulares, logo, nombres de producto): **Cormorant Garamond** (peso 500/600, high-contrast). Reemplaza Fraunces en `font-display`.
- Texto/UI: **Hanken Grotesk** (400-700). Reemplaza DM Sans en `font-sans`.
- Versalitas con tracking para eyebrows, precios y labels (aire de catálogo).

**Forma:**
- Radios: `ui` 3px, `soft` 6px (antes 8/14) — más afilado/sobrio.
- Sombras: hairlines + sombras muy sutiles; eliminar `shadow-premium/glow` pesados (sustituir por borde fino + sombra leve). Menos glass/blur.
- Densidad: más espacio en blanco, secciones más aireadas.

## Logo
Componente reutilizable `shared/brand-logo` (SVG inline, `currentColor` para el trazo, punto pino fijo) con tamaños `sm`/`md` y wordmark opcional. Favicon (index.html) actualizado al mismo monograma. Placeholder de producto (`product-media`) repintado a tinta/pino.

## Fases (cada una: build verde + verificación Playwright claro/oscuro + commit)
1. **Sistema de diseño**: tailwind.config (color/ font/ radius/ shadow) + styles.css (vars, hero, componentes base `.ui-button*`, `.surface-panel`, `.soft-card`, etc.) + ThemeService/index.html default→oscuro + `brand-logo` + favicon + placeholder.
2. **Layout**: storefront-layout (header con logo nuevo, footer), admin-shell.
3. **Home**: hero + secciones al lenguaje quiet-luxury.
4. **Catálogo + producto-detalle**.
5. **Auth, carrito, checkout, pago, pedidos, wishlist, 404**.
6. **Barrido final** (contraste/a11y, dark+light, móvil) + merge a `main`.

## No-objetivos
- No tocar backend, rutas, i18n (salvo copy que ya no encaje), tests (se mantienen verdes), ni lógica.
- No romper dark/light, a11y (skip-link, focus-trap), ni el lazy-loading.
