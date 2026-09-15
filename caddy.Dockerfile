# =============================================================================
# Multi-stage build for a Caddy binary with the extra modules this stack needs
# compiled in.
#
# 1. The Cloudflare DNS module (dns.providers.cloudflare) is what lets Caddy solve
#    the ACME DNS-01 challenge, which is required for the PUBLIC deployment
#    (TLS_MODE=public): a real Let's Encrypt certificate can be obtained even when
#    the hostname is proxied through Cloudflare and port 80 is never exposed.
#
# 2. The JWT module (github.com/ggicci/caddy-jwt) provides the `jwtauth` directive
#    used by caddy/access-on.caddy to verify Cloudflare Access JWTs at the ORIGIN
#    (CF_ACCESS_MODE=on). Cloudflare Access only protects requests that arrive
#    through Cloudflare's edge, so this is what stops somebody who reaches the
#    origin by another route from skipping the Access login entirely.
#
# On the HOME/LAN deployment (TLS_MODE=local, CF_ACCESS_MODE=off) neither module
# is exercised - Caddy serves the mkcert certificate and performs no JWT check -
# but compiling both in keeps a single image (and therefore a single compose.yaml)
# valid for every deployment. This matches the project's "same image, only
# environment changes" deployment philosophy.
#
# The module versions are PINNED (rather than left at @latest) so that rebuilds
# are reproducible. Bump them deliberately when a newer release is needed:
#   * caddy-dns/cloudflare v0.2.4
#   * caddy-jwt v1.4.0 - needs Go >= 1.25 (provided by the current caddy:2-builder)
#     and Caddy >= 2.10.1; if you downgrade the builder base image below that,
#     pin caddy-jwt@v1.1.0 (Go >= 1.20, Caddy >= 2.8.0) instead.
# =============================================================================

# Stage 1 (build): the official Caddy builder image ships xcaddy, which compiles
# Caddy with the requested plugins.
FROM caddy:2-builder AS builder
RUN xcaddy build \
    --with github.com/caddy-dns/cloudflare@v0.2.4 \
    --with github.com/ggicci/caddy-jwt@v1.4.0

# Stage 2 (run): overlay the newly-built binary onto the slim Alpine Caddy image
# so the final image stays small and keeps the stock entrypoint/defaults.
FROM caddy:2-alpine
COPY --from=builder /usr/bin/caddy /usr/bin/caddy