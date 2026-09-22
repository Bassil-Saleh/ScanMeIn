# =============================================================================
# Multi-stage build for the ScanMeIn React frontend.
#
# Stage 1 (build): uses Node 24 to install dependencies (npm ci) and build
#                  the static production bundle with Vite.
# Stage 2 (run):   a slim nginx image that serves the built static files.
#                  The /api/* path is NOT handled here; Caddy routes API
#                  requests directly to the backend, so nginx only serves
#                  static assets and provides SPA fallback routing.
# =============================================================================

FROM node:24-alpine AS build
WORKDIR /app

# Copy manifests first so dependency installation is cached between builds.
COPY package.json package-lock.json ./
RUN npm ci

# Copy the source and build the production bundle.
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
