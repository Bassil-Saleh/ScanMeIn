#!/usr/bin/env bash
#
# make-certs.sh
#
# Generates a locally-trusted TLS certificate for the app hostname using
# mkcert, and installs mkcert's local root CA into THIS machine's trust store.
#
# To make other devices (e.g. your phone) trust the certificate, install the
# root CA printed at the end of this script onto each device.

set -euo pipefail

cd "$(dirname "$0")/.."

# Read SITE_HOST from .env; fail fast if it is missing or empty rather than
# silently falling back to a hard-coded default hostname.
SITE_HOST=""
if [ -f .env ]; then
    SITE_HOST="$(grep -E '^SITE_HOST=' .env | tail -n 1 | cut -d '=' -f 2- || true)"
fi

if [ -z "$SITE_HOST" ]; then
    echo "ERROR: site host name (SITE_HOST) is missing from the .env file." >&2
    echo "Run scripts/init-secrets.sh (or 'make bootstrap') to generate it first." >&2
    exit 1
fi

# A public deployment (TLS_MODE=public) obtains a real Let's Encrypt certificate
# via Caddy's Cloudflare DNS-01 challenge, so the mkcert LAN certificate is not
# used and does not need to be generated. Read TLS_MODE from .env (default local).
TLS_MODE="local"
if [ -f .env ]; then
    TLS_MODE="$(grep -E '^TLS_MODE=' .env | tail -n 1 | cut -d '=' -f 2- || true)"
fi
[ -n "$TLS_MODE" ] || TLS_MODE="local"
if [ "$TLS_MODE" = "public" ]; then
    echo "TLS_MODE=public: skipping mkcert certificate generation."
    echo "Caddy will obtain a Let's Encrypt certificate for ${SITE_HOST} via the"
    echo "Cloudflare DNS-01 challenge (see caddy/public.caddy and the README)."
    exit 0
fi

if ! command -v mkcert >/dev/null 2>&1; then
    echo "ERROR: mkcert is not installed." >&2
    echo "Install it first, e.g.:" >&2
    echo "  Ubuntu/Debian: sudo apt install -y mkcert libnss3-tools" >&2
    echo "  macOS:         brew install mkcert" >&2
    exit 1
fi

mkdir -p certs

echo "Installing mkcert's local root CA into this machine's trust store..."
mkcert -install

echo "Generating a certificate for ${SITE_HOST} ..."
mkcert \
    -cert-file "certs/${SITE_HOST}.pem" \
    -key-file "certs/${SITE_HOST}-key.pem" \
    "${SITE_HOST}"

echo
echo "Certificate written to certs/${SITE_HOST}.pem"
echo "Private key written to certs/${SITE_HOST}-key.pem"
echo
echo "To trust this certificate on OTHER devices (phones, laptops), install the"
echo "root CA located at: $(mkcert -CAROOT)/rootCA.pem"
echo "  Android: Settings -> Security -> Install certificate (choose user CA)."
echo "  iOS:     Install the profile, then enable full trust under"
echo "           Settings -> General -> About -> Certificate Trust Settings."
