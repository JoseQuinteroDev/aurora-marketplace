# Aurora Marketplace — Saneamiento del Frontend (registro de progreso)

> Última actualización: 2026-06-20 (sesión nocturna autónoma).
> El trabajo de esta sesión vive en la rama **`feat/professional-overhaul`** (empujada a GitHub).
> **No se pudo mergear a `main` automáticamente** (el guardrail del entorno bloquea el push directo a la rama por defecto). Para integrarlo: abre el PR en
> `https://github.com/JoseQuinteroDev/aurora-marketplace/pull/new/feat/professional-overhaul`
> y mergéalo (ff). Cada commit es atómico y la rama compila verde en cada paso.

Plan por fases para sanear el frontend Angular.

---

## ✅ HECHO

| Commit | Fase | Qué |
|---|---|---|
| `ce3a228` | Base | Polish previo de copy/footer/login, meta SEO, fix Kafka, seed inicial. |
| `011203e` | **1 · Admin + seguridad** | Enlace Admin oculto a no-admins, expiración JWT, interceptor 401/403, `returnUrl` anti-open-redirect. |
| `e81efb2` | **2 · Carrito + Toasts** | `ToastService` + `ToastHostComponent`, feedback add-to-cart/favoritos, anónimo→toast+returnUrl, autosync carrito/wishlist. |
| `e5e8b2d` | **3 · Español 100%** | Literales→`translations.ts`, `<html lang>` dinámico, `TitleStrategy` localizado, estados de pedido. |
| `4b60b13` | **4 · Catálogo** | Seed a 8 cats / 12 marcas / ~43 productos; chips de filtro funcionales + orden + URL-driven; tiles de home con deep-link. |
| `f055bda` | **5 · Voz de marca + ratings honestos** | Reescritura de copy con voz "directo y honesto" (lista de palabras prohibidas aplicada en home/footer/nav/catálogo/auth/producto). Tagline *"Buena tecnología, sin postureo."* / *"Good tech, no hype."* **Fuera el ★4.8 falso**: tarjetas sin rating; detalle muestra media + nº de opiniones solo si existen, si no "Sé el primero en opinar"; métricas del hero → propuestas de valor reales (24-48h envío, 30 días devolución, 100% pago cifrado). Alt-text y meta SEO corregidos. |
| `c0bb0da` | **6a · Dark mode** | `ThemeService` (signal + localStorage + prefers-color-scheme), script anti-FOUC en `index.html`, toggle sol/luna en el header. El dark mode estaba "muerto" (clases `dark:` por todas partes pero nada añadía `.dark`); ahora funciona y persiste. |
| `f810a99` | **6b · Tipografía de dos niveles** | **Fraunces** (serif display, variable) para titulares + DM Sans para cuerpo. Regla base en `h1/h2/h3`, familia `display` en Tailwind, hero/secciones/títulos de `font-black`→`font-semibold`. Adiós al "todo font-black". |
| `d3db823` | **6c · Placeholder de marca** | Imágenes de producto sin foto ya no caen a Unsplash aleatorio: SVG de marca (monograma Aurora) inline. Lógica de imagen/galería centralizada en `core/util/product-media.ts`. |
| `e4276dd` | **7 · Accesibilidad + limpieza** | Skip-link (primer tab stop), foco al contenido en cambios de ruta SPA, restauración de scroll, **focus-trap** en el modal de filtros móvil (foco entra, Tab hace wrap, Escape cierra, foco se restaura), alt-text real desde `ProductImage.altText`. Dedup: `firstActiveVariantId()` compartido. |
| `5287e64` | **Pulido** | Página **404 con estilo** (en el chrome del storefront, EN/ES, claro/oscuro) en vez de redirigir en silencio; rutas reordenadas (admin con prioridad). Favicon de marca (SVG). Presupuesto de bundle a 700kB → **build sin warnings**. |
| `4a51de2` | **Bugfix (review)** | El detalle de producto leía el slug una sola vez (`snapshot`); navegar producto→producto dejaba el anterior en pantalla. Ahora se suscribe a `paramMap` y resetea el estado por producto. |
| `8bb52f0` | **Review #1** | aria-labels en los 3 inputs de búsqueda; paridad de dark mode en payment (el color éxito/fallo se perdía en oscuro); 8 h1 de páginas de `font-black`→`font-semibold` (coherencia); badge "ADMIN" vía i18n. |
| `9c7d2e9` | **Review #2** | Sign-out del admin accesible en móvil (antes solo en el sidebar oculto `<lg`); modal de filtros móvil con `max-height`+scroll (no se salía de pantalla); **estado de error en reseñas** (un fallo ya no se muestra como "sin reseñas"); eliminado código muerto (campos de icono sin uso, `design-tokens.ts`). Revisión de seguridad: sin hallazgos. |
| `daa9326` | **Rendimiento** | **Lazy-loading** de todas las rutas de feature (code-splitting). Bundle inicial **579kB → 429kB** (~111kB transfer), de nuevo bajo el presupuesto original de 500kB. |
| `6aec870` | **Review #3** | UX/comercio/detalle: carrito y wishlist con **placeholder de marca** (antes una foto de stock idéntica para todo); badge de carrito oculto si está vacío; precio del detalle según la variante seleccionada; estado de error real + reintentar en wishlist; reset de búsqueda en catálogo sin resultados; `returnUrl` preservado entre login↔register; CTA de éxito en pago; skeleton del admin 8→7; miniatura de carrito acotada en móvil; **fix del ThemeService** (persistía en cada run, congelando la preferencia del SO → ahora solo al togglear); dedupe de galería (evita NG0955). |

| `cac7277` | **Feature: ratings reales (full-stack)** | Las tarjetas no tenían prueba social. Ahora el backend expone `averageRating`+`reviewCount` en list/search/detalle mediante **una sola query de agregación por lote** (proyección `ProductRatingStats`, **sin N+1**); la tarjeta muestra `★ media (nº)` **solo si hay reseñas reales**. Tests del backend 12/12. Verificado end-to-end (reseña publicada → API + tarjeta + detalle). |
| `5268e59` | **Harness de tests (frontend)** | No había runner de tests. Cableado el builder moderno de Angular con **Vitest** (jsdom, sin navegador) + primera suite (**17 tests verde**): open-redirect guard, mapeo de errores de carrito, product-media, `ThemeService` y un componente. `npm test` corre una vez (CI-friendly), `npm run test:watch` para desarrollo. |

**Tres rondas de revisión adversarial** (12 agentes: a11y, i18n, bugs, diseño, responsive, cobertura de estados, seguridad, código muerto, UX/microcopy, comercio, detalle visual, correctness del código nuevo) — **24 hallazgos, todos corregidos y verificados**. Revisión de seguridad: sin hallazgos.

Verificación de la sesión: build de producción verde en cada commit + **Playwright** en vivo (44 productos / 0 fugas i18n; toggle de tema; skip-link primer tab stop; focus-trap completo; 404; recorrido autenticado registro→carrito→checkout→pedidos→wishlist en claro y oscuro).

---

## ⬜ IDEAS FUTURAS (opcionales)

- ~~Rating real en tarjetas~~ ✅ **hecho** (`cac7277`, full-stack, sin N+1).
- **Admin**: el dashboard no se re-verificó visualmente esta noche (no se pudo promover un usuario a ADMIN por guardrail). Sus cambios fueron mínimos (solo la fuente global de titulares). Revisar de pasada con un admin real.
- ~~Test harness~~ ✅ **hecho** (`5268e59`, Vitest + jsdom, 17 tests). Ampliable: más cobertura de componentes/servicios y, si se quiere, e2e con Playwright.
- **Imágenes editoriales**: las fotos decorativas (hero, auth, promo) siguen siendo Unsplash; se ven bien, pero un set propio/curado de marca elevaría aún más.

---

## ▶️ Cómo levantar y verificar (probado esta sesión)

```powershell
# 1) Infra (Docker)
docker compose up -d postgres kafka redis mailpit
# Si reiniciaste contenedores y el backend no conecta a Postgres: docker restart aurora_postgres

# 2) Backend (en backend/) — health en http://localhost:8080/actuator/health
.\mvnw.cmd spring-boot:run

# 3) Frontend (en frontend/) — http://localhost:4200
$env:NODE_OPTIONS="--max-old-space-size=4096"   # evita el OOM de Node
npm start

# 4) Seed (BD vacía). El admin debe registrarse primero vía /register; el seed lo promociona:
docker exec -i aurora_postgres psql -U aurora_user -d aurora_marketplace < seed-data.sql
```

Verificación visual: Playwright está instalado en `frontend/node_modules` (no commiteado). Los scripts temporales `frontend/__*.mjs` usados esta sesión están en `.git/info/exclude` (no se commitean).

---

## ⚠️ Gotchas

- **Push a `main` bloqueado** por el guardrail del entorno → el trabajo va a la rama `feat/professional-overhaul` + PR. `gh` CLI no está instalado.
- **Port-forward de Postgres (5433)** se rompe al reiniciar contenedores → `docker restart aurora_postgres`.
- **OOM de Node** en build/serve → `NODE_OPTIONS=--max-old-space-size=4096`.
- **`npm run build` mientras `ng serve` corre** provoca un overlay de error transitorio (comparten `.angular/cache`); el dev server se recupera solo.
- **6 productos "electronics" antiguos** del seed viejo siguen conviviendo con el catálogo nuevo (idempotente). Wipe+reseed si quieres catálogo limpio.
