# =============================================================================
# Multi-stage build for a Caddy binary with the Cloudflare DNS provider module
# compiled in.
#
# The Cloudflare DNS module (dns.providers.cloudflare) is what lets Caddy solve
# the ACME DNS-01 challenge, which is required for the PUBLIC deployment
# (TLS_MODE=public): a real Let's Encrypt certificate can be obtained even when
# the hostname is proxied through Cloudflare and port 80 is never exposed.
#
# On the HOME/LAN deployment (TLS_MODE=local) the module is simply never used
# (Caddy serves the mkcert certificate instead), but compiling it in keeps a
# single image (and therefore a single compose.yaml) valid for both
# deployments. This matches the project's "same image, only environment
# changes" deployment philosophy.
#
# The module version is PINNED (rather than left at @latest) so that rebuilds
# are reproducible. Bump it deliberately when a newer release is needed.
# =============================================================================

# Stage 1 (build): the official Caddy builder image ships xcaddy, which compiles
# Caddy with the requested plugins.
FROM caddy:2-builder AS builder
RUN xcaddy build \
    --with github.com/caddy-dns/cloudflare@v0.2.4

# Stage 2 (run): overlay the newly-built binary onto the slim Alpine Caddy image
# so the final image stays small and keeps the stock entrypoint/defaults.
FROM caddy:2-alpine
COPY --from=builder /usr/bin/caddy /usr/bin/caddy