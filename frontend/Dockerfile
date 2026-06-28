# syntax=docker/dockerfile:1
# Multi-stage build: compile the Angular app, then serve the static bundle from a
# hardened, unprivileged nginx. Used by the prod compose overlay / any static host.

# ---- Build stage ----
FROM node:22-alpine AS build
WORKDIR /app

# Install deps from the lockfile first for layer caching.
COPY package.json package-lock.json ./
RUN npm ci

# Build the production bundle. The dev build needs extra heap on some machines.
COPY . .
ENV NODE_OPTIONS=--max-old-space-size=4096
RUN npm run build

# ---- Runtime stage ----
# nginx-unprivileged runs as a non-root user (uid 101) and listens on 8080 by default.
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

# Server config: SPA history fallback, gzip, long-cache for hashed assets, security headers.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Static bundle (Angular emits to dist/<project>/browser).
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
  CMD ["wget", "--spider", "-q", "http://localhost:8080/"]
