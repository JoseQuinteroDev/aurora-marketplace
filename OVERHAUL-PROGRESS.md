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

Verificación de la sesión: build de producción verde en cada commit + **Playwright** en vivo (44 productos / 0 fugas i18n; toggle de tema; skip-link primer tab stop; focus-trap completo; 404; recorrido autenticado registro→carrito→checkout→pedidos→wishlist en claro y oscuro).

---

## ⬜ IDEAS FUTURAS (opcionales)

- **Rating real en tarjetas**: ampliar el `ProductSummary` DTO del backend con `avgRating`+`reviewCount` para mostrar valoraciones reales también en las tarjetas del catálogo (hoy solo el detalle tiene datos de reseñas).
- **Admin**: el dashboard no se re-verificó visualmente esta noche (no se pudo promover un usuario a ADMIN por guardrail). Sus cambios fueron mínimos (solo la fuente global de titulares). Revisar de pasada con un admin real.
- **Test harness**: sigue sin runner de tests unitarios (`ng test` sin Karma/Jasmine). Montar Vitest/Karma es tarea pendiente opcional.
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
