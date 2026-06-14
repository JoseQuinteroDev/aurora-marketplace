# Aurora Marketplace — Saneamiento del Frontend (registro de progreso)

> Nota de trabajo para retomar otro día. No está commiteado: súbelo tú si quieres.
> Última actualización: 2026-06-14.

Plan por fases para sanear el frontend Angular. Convención por fase:
rama `feat/phase-N-...` → commit(s) atómicos → `git checkout main && git merge --ff-only` → `git push origin main`.

---

## ✅ HECHO (en `main`, ya en GitHub)

| Commit | Fase | Qué |
|---|---|---|
| `ce3a228` | Base | Polish de copy/footer/login, meta SEO en index.html, fix Kafka KRaft en compose, `.gitignore`, seed inicial (cambios previos tuyos sin commitear, separados en su propio commit). |
| `011203e` | **1 · Admin + seguridad** | Enlace "Admin" oculto a no-admins (`@if (auth.isAdmin())`); expiración de JWT en `isAuthenticated()/isAdmin()`; interceptor maneja 401 (logout + `/login?returnUrl=`) y 403 (→ inicio); `logout()` navega; guards y login/registro respetan `returnUrl` con helper anti-open-redirect `safeInternalUrl`. |
| `e81efb2` | **2 · Carrito + Toasts** | `ToastService` + `ToastHostComponent` (aria-live) en ambos layouts; añadir-al-carrito/buy-now/favoritos con toast de éxito y errores reales del backend (sin stock, inactivo…); anónimo → toast "inicia sesión" + returnUrl; feedback en mutaciones del carrito; `CartService`/`WishlistService` se autosincronizan con la sesión vía `effect` (badge correcto sin recargar); `AuthService` purga sesión caducada al arrancar. |
| `e5e8b2d` | **3 · Español al 100%** | Todos los literales movidos a `translations.ts` (ES+EN): home entera, footer, admin (shell+dashboard, que no tenían i18n), catálogo, auth, product-detail; `<html lang>` dinámico; `TitleStrategy` que localiza el título de pestaña; estados de pedido (`order.status.*`); placeholders neutros. |
| `4b60b13` | **4 · Catálogo + más productos** | `seed-data.sql` ampliado a 8 categorías / 12 marcas / ~43 productos (cada uno con variante activa + inventario + imagen); chips de categoría/marca **funcionales** (filtro client-side por slug) + orden (destacados/precio/nombre) + recuento + "quitar filtros", todo dirigido por URL (`?q/?category/?brand/?sort`); tiles de la home enlazan a `/catalog?category=<slug>`. |

Cada fase verificada con build de producción + **Playwright** en vivo (Fase 1/2: 7/7, Fase 3 i18n: 12/12, Fase 4: 7/7).

---

## ⬜ PENDIENTE

### Fase 5 — Copy comercial y voz de marca
- Definir una voz: 1 tagline, 1 propuesta de valor, lista de palabras prohibidas (curated, edit, serene, calm, drop, frictionless…).
- Reescribir benefits/promises/hero con valor concreto (envío, devoluciones, pago, soporte) en ES+EN.
- Quitar el **rating "4.8" falso** de cada tarjeta y del detalle (`product-card.ts:43`, `product-detail.ts` `averageRating()`); mostrar "Nuevo" o nada si no hay reseñas reales.
- Unificar el tono del admin con el de la tienda.

### Fase 6 — Rediseño visual (tu mayor queja)
- Sistema tipográfico de dos niveles (display + body; dejar de usar `font-black` para todo).
- **Activar el dark mode** (hoy está muerto: hay clases `dark:` por todas partes pero nada añade la clase `.dark`; falta `ThemeService` + toggle).
- Hero editorial con productos reales; variedad de secciones (rail de bestsellers, deals, prueba social) en vez de 5 bandas iguales.
- Menos glass/blur y blobs de gradiente; placeholder de marca (SVG) en vez de Unsplash aleatorio.
- Product-card rediseñada. Usar el skill `frontend-design`.

### Fase 7 — Accesibilidad + limpieza
- Focus-trap en el panel de filtros móvil; foco al cambiar de ruta; alt-text real desde `ProductImage.altText`.
- Extraer la lógica duplicada (resolver variante/imagen, flujo añadir-al-carrito) entre `product-card` y `product-detail` a un sitio común.

---

## ▶️ Cómo levantar y verificar el entorno (probado)

```powershell
# 1) Infra (Docker)
docker compose up -d postgres kafka redis mailpit

# 2) Backend (en backend/) — health en http://localhost:8080/actuator/health
.\mvnw.cmd spring-boot:run

# 3) Frontend (en frontend/) — http://localhost:4200
$env:NODE_OPTIONS="--max-old-space-size=4096"   # evita el OOM de Node
npm start

# 4) Seed de datos (BD vacía no trae nada). El usuario admin debe registrarse
#    primero vía POST /api/auth/register (para que el hash BCrypt sea válido);
#    el seed lo promociona a ADMIN. Luego:
docker exec -i aurora_postgres psql -U aurora_user -d aurora_marketplace < seed-data.sql
# (es idempotente: salta productos cuyo slug ya existe)
```

Usuario admin de prueba: `admin@aurora.test` (regístralo y el seed le pone rol ADMIN).

---

## ⚠️ Problemas conocidos / gotchas

- **Reenvío de puertos de Docker (5433) se rompe** si reinicias los contenedores: el backend del host falla con `EOFException` / "connection failed" aunque `docker exec ... psql` funcione. **Solución:** `docker restart aurora_postgres` y vuelve a arrancar el backend con config por defecto.
- **OOM de Node** al hacer `ng build`/`npm start`: exporta `NODE_OPTIONS=--max-old-space-size=4096`.
- **No hay runner de tests** (`ng test` no tiene Karma/Jasmine). Verificación: build de producción + Playwright (se instala con `npm i --no-save playwright` y se borra el script tras usarlo). Montar un harness de tests (Vitest/Karma) es una tarea pendiente opcional.
- **Warning de presupuesto de bundle** (~37 kB sobre 500 kB): solo warning, el build pasa. Sube el presupuesto en `angular.json` si molesta.
- **6 productos "electronics" antiguos** del seed viejo coexisten con el nuevo catálogo (el seed es idempotente y los respetó). Si quieres un catálogo limpio: wipe de las tablas de catálogo + re-seed (es data de prueba).
- El backend que quedó corriendo se arrancó dentro de una sesión de Claude; relánzalo tú con `.\mvnw.cmd spring-boot:run` si hace falta.
