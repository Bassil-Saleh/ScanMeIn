#!/usr/bin/env bash
#
# init-secrets.sh
#
# Generates the gitignored .env file containing all the secrets and
# environment-specific settings required by the Docker Compose stack, for BOTH
# deployment types:
#
#   * home / LAN   (TLS_MODE=local)  mkcert certificate, Mailpit, devices
#                                    connect straight to Caddy.
#   * public / EC2 (TLS_MODE=public) Let's Encrypt certificate via the Cloudflare
#                                    DNS-01 challenge, Cloudflare Tunnel as the
#                                    only ingress, Cloudflare Access enforced at
#                                    the origin, email via Amazon SES.
#
# Every value can be supplied up front, which makes the script usable
# non-interactively (e.g. in a single bootstrap command over SSH):
#
#   ./scripts/init-secrets.sh \
#       --site-host scanmein.online --tls-mode public \
#       --acme-email dev@scanmein.online \
#       --cloudflare-api-token <token> --tunnel-token <token> \
#       --cf-access-team-domain <team>.cloudflareaccess.com \
#       --cf-access-aud <aud-tag> \
#       --allowed-emails you@example.com,friend@example.com \
#       --smtp-host email-smtp.us-east-2.amazonaws.com --smtp-port 587 \
#       --smtp-username <user> --smtp-password <pass> \
#       --smtp-auth true --smtp-starttls true \
#       --mail-from noreply@scanmein.online
#
# Environment variables named exactly like the .env keys work too, and are handy
# for secrets you would rather not keep in your shell history:
#
#   SITE_HOST=scanmein.online CLOUDFLARE_API_TOKEN=<token> ./scripts/init-secrets.sh
#
# Precedence is: command-line flag > environment variable > autodetect/prompt.
# When run from a terminal, whatever is still missing is asked for; when there is
# no terminal, nothing is asked and the report at the end lists what is empty.
#
# This script is idempotent: if .env already exists it will NOT overwrite it,
# so your existing secrets and database data stay intact. Delete .env and
# re-run if you truly want to start over from scratch.

set -euo pipefail

# Always operate relative to the repository root (one level above scripts/).
cd "$(dirname "$0")/.."

ENV_FILE=".env"

usage() {
    cat <<'USAGE'
Usage: scripts/init-secrets.sh [options]

Generates the gitignored .env file for the Docker Compose stack. Never
overwrites an existing .env.

Options (each one can also be given as an environment variable named like the
.env key it sets, e.g. SITE_HOST=... ):
  --site-host <fqdn>              Hostname Caddy serves / the public site address.
  --tls-mode <local|public>       Certificate source. Autodetected: an Amazon EC2
                                  instance defaults to 'public', everything else
                                  to 'local'.
  --caddy-bind-addr <ip>          Interface Caddy publishes 80/443 on
                                  (default: 0.0.0.0 local, 127.0.0.1 public).
  --tunnel-token <token>          Cloudflare Tunnel connector token (public).
  --cf-access-mode <on|off>       Origin-side Cloudflare Access JWT validation
                                  (default: off local, on public).
  --cf-access-team-domain <host>  Zero Trust team domain, e.g.
                                  scanmein.cloudflareaccess.com (public).
  --cf-access-aud <tag>           Access application AUD tag (public).
  --acme-email <email>            Let's Encrypt contact address (public).
  --cloudflare-api-token <token>  Scoped Cloudflare API token for DNS-01 (public).
  --allowed-emails <list>         App-level allowlist (ALLOWED_EMAIL_DOMAINS).
  --mail-from <address>           Outgoing mail sender (MAIL_FROM_ADDRESS).
  --smtp-host|-port|-username|-password|-auth|-starttls <value>
                                  SMTP settings (Amazon SES on a public server).
  -h, --help                      Show this help.
USAGE
}

# --- Current values (whatever the environment already provides) --------------
SITE_HOST="${SITE_HOST:-}"
TLS_MODE="${TLS_MODE:-}"
CADDY_BIND_ADDR="${CADDY_BIND_ADDR:-}"
TUNNEL_TOKEN="${TUNNEL_TOKEN:-}"
CF_ACCESS_MODE="${CF_ACCESS_MODE:-}"
CF_ACCESS_TEAM_DOMAIN="${CF_ACCESS_TEAM_DOMAIN:-}"
CF_ACCESS_AUD="${CF_ACCESS_AUD:-}"
ACME_EMAIL="${ACME_EMAIL:-}"
CLOUDFLARE_API_TOKEN="${CLOUDFLARE_API_TOKEN:-}"
ALLOWED_EMAIL_DOMAINS="${ALLOWED_EMAIL_DOMAINS:-}"
MAIL_FROM_ADDRESS="${MAIL_FROM_ADDRESS:-}"
MAILPIT_UI_PORT="${MAILPIT_UI_PORT:-8025}"
SMTP_HOST="${SMTP_HOST:-}"
SMTP_PORT="${SMTP_PORT:-}"
SMTP_USERNAME="${SMTP_USERNAME:-}"
SMTP_PASSWORD="${SMTP_PASSWORD:-}"
SMTP_AUTH="${SMTP_AUTH:-}"
SMTP_STARTTLS="${SMTP_STARTTLS:-}"

# --- Command-line flags (highest precedence) --------------------------------
while [ $# -gt 0 ]; do
    case "$1" in
        --site-host)             SITE_HOST="$2"; shift 2 ;;
        --tls-mode)              TLS_MODE="$2"; shift 2 ;;
        --caddy-bind-addr)       CADDY_BIND_ADDR="$2"; shift 2 ;;
        --tunnel-token)          TUNNEL_TOKEN="$2"; shift 2 ;;
        --cf-access-mode)        CF_ACCESS_MODE="$2"; shift 2 ;;
        --cf-access-team-domain) CF_ACCESS_TEAM_DOMAIN="$2"; shift 2 ;;
        --cf-access-aud)         CF_ACCESS_AUD="$2"; shift 2 ;;
        --acme-email)            ACME_EMAIL="$2"; shift 2 ;;
        --cloudflare-api-token)  CLOUDFLARE_API_TOKEN="$2"; shift 2 ;;
        --allowed-emails)        ALLOWED_EMAIL_DOMAINS="$2"; shift 2 ;;
        --mail-from)             MAIL_FROM_ADDRESS="$2"; shift 2 ;;
        --smtp-host)             SMTP_HOST="$2"; shift 2 ;;
        --smtp-port)             SMTP_PORT="$2"; shift 2 ;;
        --smtp-username)         SMTP_USERNAME="$2"; shift 2 ;;
        --smtp-password)         SMTP_PASSWORD="$2"; shift 2 ;;
        --smtp-auth)             SMTP_AUTH="$2"; shift 2 ;;
        --smtp-starttls)         SMTP_STARTTLS="$2"; shift 2 ;;
        -h|--help)               usage; exit 0 ;;
        *) echo "ERROR: unknown option '$1'." >&2; usage >&2; exit 1 ;;
    esac
done

if [ -f "$ENV_FILE" ]; then
    echo ".env already exists; leaving it untouched."
    echo "Delete $ENV_FILE and re-run this script to regenerate secrets."
    exit 0
fi

# --- Helpers -----------------------------------------------------------------

# DMI files used to detect the hosting environment. Overridable purely so that
# the EC2 code path can be tested on a machine that is not an EC2 instance, e.g.
#   INIT_SECRETS_DMI_VENDOR_FILE=/tmp/fake-vendor ./scripts/init-secrets.sh
# (with "Amazon EC2" written into /tmp/fake-vendor). Production runs use the real
# paths, which are world-readable on EC2's Ubuntu AMIs.
DMI_VENDOR_FILE="${INIT_SECRETS_DMI_VENDOR_FILE:-/sys/class/dmi/id/sys_vendor}"
DMI_UUID_FILE="${INIT_SECRETS_DMI_UUID_FILE:-/sys/hypervisor/uuid}"

# is_ec2: true when this machine is an Amazon EC2 instance. Amazon exposes the
# vendor through DMI; a /sys/hypervisor/uuid starting with "ec2" is the fallback
# on instances that do not publish the DMI vendor string.
is_ec2() {
    local vendor="" uuid=""
    [ -r "$DMI_VENDOR_FILE" ] && vendor="$(cat "$DMI_VENDOR_FILE" 2>/dev/null || true)"
    [ -r "$DMI_UUID_FILE" ] && uuid="$(cat "$DMI_UUID_FILE" 2>/dev/null || true)"
    case "$vendor" in *"Amazon EC2"*) return 0 ;; esac
    case "$uuid" in ec2*) return 0 ;; esac
    return 1
}

# looks_like_internal_name: cloud instances are handed an INTERNAL hostname such
# as ip-172-31-8-42.us-east-2.compute.internal. That name is only resolvable
# inside the provider's network, so it must never be used as the public SITE_HOST.
looks_like_internal_name() {
    case "$1" in
        ip-[0-9]*) return 0 ;;
        *.internal|*.internal.*|*.localdomain) return 0 ;;
        *.*.amazonaws.com) return 0 ;;
    esac
    return 1
}

# is_valid_hostname: permissive RFC-1123 style check (dots allowed, no scheme,
# no path, no spaces) - accepts LAN names like ticketproject.local too.
is_valid_hostname() {
    printf '%s' "$1" | grep -Eq '^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(\.([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?))*$'
}

# is_public_fqdn: a hostname a public certificate authority can issue for.
is_public_fqdn() {
    local h="$1"
    is_valid_hostname "$h" || return 1
    case "$h" in *.*) ;; *) return 1 ;; esac   # must contain at least one dot
    case "$h" in *.local) return 1 ;; esac     # mDNS name, not publicly resolvable
    looks_like_internal_name "$h" && return 1
    return 0
}

# normalize_host: trim whitespace and strip a pasted scheme/trailing slash, so
# "https://scanmein.online/" and "scanmein.online" both work.
normalize_host() {
    printf '%s' "$1" | tr -d '[:space:]' | sed -e 's#^[Hh][Tt][Tt][Pp][Ss]\?://##' -e 's#/*$##'
}

# ask <VAR> "<prompt>" [secret]: prompt for a missing value, but only when a
# terminal is attached and the value is not already set. Secrets are read with
# echo disabled. Never fails the script (read can hit EOF in a pipe).
ask() {
    local __var="$1" __prompt="$2" __secret="${3:-}" __value=""
    [ -t 0 ] || return 0
    [ -n "${!__var}" ] && return 0
    if [ "$__secret" = "secret" ]; then
        printf '%s: ' "$__prompt" >&2
        read -rs __value || true
        printf '\n' >&2
    else
        printf '%s: ' "$__prompt" >&2
        read -r __value || true
    fi
    printf -v "$__var" '%s' "$__value"
}


echo "Generating $ENV_FILE ..."

# --- 1. Deployment type ------------------------------------------------------
# An Amazon EC2 instance is a public server by definition: it needs a real
# certificate and a real hostname, so 'public' is the safe default there.
# Anywhere else the LAN/mkcert path stays the default, exactly as before.
# Override either way with --tls-mode or the TLS_MODE environment variable.
if [ -z "$TLS_MODE" ]; then
    if is_ec2; then
        TLS_MODE="public"
        echo "Detected an Amazon EC2 instance -> defaulting to TLS_MODE=public."
    else
        TLS_MODE="local"
    fi
fi
case "$TLS_MODE" in
    local|public) ;;
    *) echo "ERROR: TLS_MODE must be 'local' or 'public' (got '$TLS_MODE')." >&2; exit 1 ;;
esac

# --- 2. SITE_HOST ------------------------------------------------------------
# On a cloud instance `hostname -f` returns something like
# ip-172-31-8-42.us-east-2.compute.internal: an INTERNAL name that the internet
# cannot resolve and that no certificate authority will issue a certificate for.
# It is therefore never used for a public deployment - the real FQDN has to come
# from --site-host / $SITE_HOST / the interactive prompt. For a home/LAN
# deployment the machine's own hostname is still the correct default.
MACHINE_HOSTNAME="$(hostname -f 2>/dev/null || hostname 2>/dev/null || true)"

if [ -z "$SITE_HOST" ]; then
    if [ "$TLS_MODE" = "public" ]; then
        if [ -n "$MACHINE_HOSTNAME" ]; then
            echo "Note: 'hostname -f' on this machine is '$MACHINE_HOSTNAME',"
            echo "      which is internal-only and cannot be used as SITE_HOST."
        fi
        ask SITE_HOST "Public FQDN of the site (e.g. scanmein.online)"
    else
        SITE_HOST="$MACHINE_HOSTNAME"
    fi
fi

SITE_HOST="$(normalize_host "$SITE_HOST")"

if [ -z "$SITE_HOST" ]; then
    echo "ERROR: SITE_HOST could not be determined." >&2
    echo "Pass it explicitly, e.g.:" >&2
    echo "  ./scripts/init-secrets.sh --site-host scanmein.online --tls-mode public" >&2
    echo "or, on a home/LAN machine, give this machine a resolvable name first:" >&2
    echo "  sudo hostnamectl set-hostname <your-hostname>.local" >&2
    echo "  sudo apt install -y avahi-daemon   # for mDNS resolution over the LAN" >&2
    exit 1
fi

if [ "$TLS_MODE" = "public" ]; then
    if ! is_public_fqdn "$SITE_HOST"; then
        echo "ERROR: SITE_HOST='$SITE_HOST' is not a usable public FQDN." >&2
        echo "It must be the address visitors use (e.g. scanmein.online): at least one" >&2
        echo "dot, no scheme, and not an internal or mDNS name such as" >&2
        echo "'ip-172-31-8-42.us-east-2.compute.internal' or 'ticketproject.local'." >&2
        exit 1
    fi
    if ! getent hosts "$SITE_HOST" >/dev/null 2>&1; then
        echo "WARNING: '$SITE_HOST' does not resolve from here yet."
        echo "         That is expected if the DNS record / tunnel public hostname has"
        echo "         not been created yet. The ZONE must already exist in Cloudflare,"
        echo "         otherwise the DNS-01 challenge cannot succeed."
    fi
else
    if ! is_valid_hostname "$SITE_HOST"; then
        echo "ERROR: SITE_HOST='$SITE_HOST' is not a valid hostname." >&2
        exit 1
    fi
    if is_ec2 || looks_like_internal_name "$SITE_HOST"; then
        echo "WARNING: TLS_MODE=local on a machine named '$SITE_HOST'."
        echo "         A LAN/mkcert deployment is usually not what you want on a cloud"
        echo "         instance; consider --tls-mode public --site-host <your-fqdn>."
    fi
fi

# Public HTTPS URL of the frontend; used to build the links in outgoing emails.
FRONTEND_BASE_URL="https://${SITE_HOST}"


# --- 3. Deployment-specific defaults ----------------------------------------
if [ "$TLS_MODE" = "public" ]; then
    # The Cloudflare Tunnel connector reaches Caddy over the internal Docker
    # network, so Caddy needs no publicly reachable port at all: binding to
    # loopback removes the last way to reach the origin without going through
    # Cloudflare (and Cloudflare Access).
    [ -n "$CADDY_BIND_ADDR" ] || CADDY_BIND_ADDR="127.0.0.1"
    # Secure by default: the origin demands proof that a request came through
    # Cloudflare Access. Switch it off deliberately (--cf-access-mode off) once
    # the site is opened to the general public.
    [ -n "$CF_ACCESS_MODE" ] || CF_ACCESS_MODE="on"
    # Amazon SES SMTP always authenticates over STARTTLS on port 587.
    [ -n "$SMTP_PORT" ]     || SMTP_PORT="587"
    [ -n "$SMTP_AUTH" ]     || SMTP_AUTH="true"
    [ -n "$SMTP_STARTTLS" ] || SMTP_STARTTLS="true"

    # People paste the full URL from the dashboard more often than the bare host.
    CF_ACCESS_TEAM_DOMAIN="$(normalize_host "$CF_ACCESS_TEAM_DOMAIN")"

    ask ACME_EMAIL "Let's Encrypt contact email (optional but recommended)"
    ask CLOUDFLARE_API_TOKEN "Cloudflare API token (Zone.Zone:Read + Zone.DNS:Edit)" secret
    ask TUNNEL_TOKEN "Cloudflare Tunnel token" secret
    ask CF_ACCESS_TEAM_DOMAIN "Zero Trust team domain (e.g. scanmein.cloudflareaccess.com)"
    ask CF_ACCESS_AUD "Cloudflare Access application AUD tag"
    ask ALLOWED_EMAIL_DOMAINS "App email allowlist (comma-separated, optional)"
    ask MAIL_FROM_ADDRESS "Outgoing mail sender (verified SES identity, optional)"
    ask SMTP_HOST "Amazon SES SMTP host (e.g. email-smtp.us-east-2.amazonaws.com)"
    ask SMTP_USERNAME "Amazon SES SMTP username"
    ask SMTP_PASSWORD "Amazon SES SMTP password" secret
else
    # Devices on the LAN connect straight to Caddy, and there is no Cloudflare
    # Access application in front of a home deployment. The SMTP values stay
    # empty so compose.yaml keeps pointing the backend at the Mailpit container.
    [ -n "$CADDY_BIND_ADDR" ] || CADDY_BIND_ADDR="0.0.0.0"
    [ -n "$CF_ACCESS_MODE" ] || CF_ACCESS_MODE="off"
fi

case "$CF_ACCESS_MODE" in
    on|off) ;;
    *) echo "ERROR: CF_ACCESS_MODE must be 'on' or 'off' (got '$CF_ACCESS_MODE')." >&2; exit 1 ;;
esac
case "$CADDY_BIND_ADDR" in
    0.0.0.0|127.0.0.1|::|localhost) ;;
    *) echo "ERROR: CADDY_BIND_ADDR must be an address such as 0.0.0.0 or 127.0.0.1 (got '$CADDY_BIND_ADDR')." >&2; exit 1 ;;
esac


# --- 4. Write .env -----------------------------------------------------------
# The here-doc below is deliberately UNQUOTED (<<EOF) so that the computed values
# and the freshly generated secrets are expanded into the file. It therefore
# contains no literal $ or backtick that would be expanded by accident.
cat > "$ENV_FILE" <<EOF
# ---------------------------------------------------------------------------
# Generated by scripts/init-secrets.sh on $(date -u +"%Y-%m-%dT%H:%M:%SZ")
# for a ${TLS_MODE} deployment.
# This file is gitignored and chmod 600. Do NOT commit it.
# ---------------------------------------------------------------------------

# --- Site hostname ---------------------------------------------------------
# The hostname Caddy serves, i.e. the address visitors use. On a home network it
# must resolve to this machine (e.g. via mDNS/Avahi); for a public deployment it
# is a real FQDN whose DNS zone is managed by Cloudflare.
# It is deliberately NOT taken from 'hostname -f' on a cloud instance, because
# that returns an internal-only name (ip-172-31-8-42.us-east-2.compute.internal)
# which the internet cannot resolve and for which no certificate can be issued.
SITE_HOST=${SITE_HOST}

# Public HTTPS URL of the frontend; used to build links in outgoing emails.
FRONTEND_BASE_URL=${FRONTEND_BASE_URL}

# --- TLS / certificate source ----------------------------------------------
# Selects which certificate Caddy serves (see compose.yaml + Caddyfile):
#   local  -> the mkcert certificate generated by scripts/make-certs.sh
#             (home / LAN deployment).
#   public -> a real Let's Encrypt certificate obtained via the Cloudflare
#             DNS-01 challenge (public server, e.g. EC2). Needs ACME_EMAIL and
#             CLOUDFLARE_API_TOKEN below plus a real public SITE_HOST.
# The Makefile sets this automatically ('make up' -> local, 'make up-aws' ->
# public); you only need to edit it for a plain 'docker compose up'.
TLS_MODE=${TLS_MODE}

# --- Which interface Caddy publishes 80/443 on ------------------------------
#   127.0.0.1 -> public deployment behind a Cloudflare Tunnel. The cloudflared
#                container reaches Caddy over the internal Docker network, so
#                there is no inbound path to the origin at all - not from the
#                internet and not by this host's own public IP address. Debug it
#                remotely with 'ssh -L 8443:127.0.0.1:443 <host>'.
#   0.0.0.0   -> home/LAN deployment, where devices connect to Caddy directly.
CADDY_BIND_ADDR=${CADDY_BIND_ADDR}

# --- Cloudflare Tunnel (public deployment) ---------------------------------
# Connector token from Zero Trust -> Networks -> Tunnels -> <your tunnel> ->
# Install (Docker). The public hostname configured there must point at
# https://caddy:443 with the Origin Server Name / HTTP Host Header set to
# SITE_HOST, so that Caddy's Let's Encrypt certificate is verified on that hop
# instead of being skipped. 'make up-aws' refuses to start while this is empty.
TUNNEL_TOKEN=${TUNNEL_TOKEN}

# --- Cloudflare Access: who may use the site at all -------------------------
# on  -> Caddy requires a valid, signed Cloudflare Access JWT on every request
#        (caddy/access-on.caddy), so a request that did not come through Access
#        is rejected at the origin instead of being served.
# off -> no origin check: a home/LAN deployment, or the site deliberately opened
#        to the general public (which needs no certificate or DNS change).
CF_ACCESS_MODE=${CF_ACCESS_MODE}
# Zero Trust team domain WITHOUT the scheme, e.g. scanmein.cloudflareaccess.com
# (it is both the Access JWT issuer and the JWKS host).
CF_ACCESS_TEAM_DOMAIN=${CF_ACCESS_TEAM_DOMAIN}
# Application Audience (AUD) tag: Access -> Applications -> <your app> ->
# Overview. Every request must carry a JWT issued for THIS application.
CF_ACCESS_AUD=${CF_ACCESS_AUD}



# --- Let's Encrypt via Cloudflare (only used when TLS_MODE=public) ----------
# Contact email Let's Encrypt uses for certificate expiry/revocation notices
# (recommended). Leave empty to issue the certificate without a contact email.
ACME_EMAIL=${ACME_EMAIL}

# Scoped Cloudflare API token with the permissions Zone.Zone:Read and
# Zone.DNS:Edit for the zone that hosts SITE_HOST. Create it at
# https://dash.cloudflare.com/profile/api-tokens. Keep it secret.
CLOUDFLARE_API_TOKEN=${CLOUDFLARE_API_TOKEN}

# Port to expose the Mailpit web UI on (only used with the 'local' profile).
MAILPIT_UI_PORT=${MAILPIT_UI_PORT}

# --- MariaDB ---------------------------------------------------------------
DB_NAME=ticketproject_database
DB_USERNAME=ticketproject
DB_PASSWORD=$(openssl rand -hex 24)
MARIADB_ROOT_PASSWORD=$(openssl rand -hex 24)

# --- Application secrets (Base64, 32 random bytes each) --------------------
# Used for encrypting sensitive database fields.
APP_ENCRYPTION_KEY_BASE64=$(openssl rand -base64 32)
# Used for computing blind indexes.
APP_BLIND_INDEX_KEY_BASE64=$(openssl rand -base64 32)
# Used for signing JSON Web Tokens.
APP_JWT_SECRET_BASE64=$(openssl rand -base64 32)

# --- CORS ------------------------------------------------------------------
# Leave empty to keep the API same-origin only (recommended). Add a comma
# separated list of origins if you run a separate frontend against this API.
CORS_ALLOWED_ORIGINS=

# --- Email allowlist -------------------------------------------------------
# Comma-separated email addresses and/or domains allowed to use the site, e.g.
#   you@yourdomain.com,yourdomain.com,friend@gmail.com
# A bare domain (yourdomain.com) also matches its subdomains; matching is
# case-insensitive. Only allowed addresses may register, log in, register for
# events, receive invitations, or receive any email. Leave EMPTY to disable the
# app-level allowlist (open registration), which is what you want on a trusted
# home network.
#
# This gate is INDEPENDENT of Cloudflare Access: Access decides who may reach the
# site at all, this list decides which of those people may hold an account in it.
# Keep the two lists in sync - somebody you invite must appear in BOTH to be able
# to open an invitation link.
ALLOWED_EMAIL_DOMAINS=${ALLOWED_EMAIL_DOMAINS}

# --- Email transport (SMTP) --------------------------------------------------
# Empty values mean "use the Mailpit container" (see compose.yaml), which is only
# started with the 'local' profile on a home network.
#
# On a public server such as EC2 there is no Mailpit: fill these in with your
# Amazon SES SMTP endpoint and credentials, and make sure MAIL_FROM_ADDRESS is a
# verified SES identity/domain. SES SMTP credentials are region-scoped, so they
# must come from the region your instance runs in. An SES SANDBOX account can
# only send to VERIFIED recipients until you request production access.
SMTP_HOST=${SMTP_HOST}
SMTP_PORT=${SMTP_PORT}
SMTP_USERNAME=${SMTP_USERNAME}
SMTP_PASSWORD=${SMTP_PASSWORD}
SMTP_AUTH=${SMTP_AUTH}
SMTP_STARTTLS=${SMTP_STARTTLS}
MAIL_FROM_ADDRESS=${MAIL_FROM_ADDRESS}
EOF

chmod 600 "$ENV_FILE"


# --- 5. Report ---------------------------------------------------------------
echo "Done. Created $ENV_FILE with fresh secrets (chmod 600)."
echo

MISSING=0
report_value() { # <label> <value> <consequence when empty>
    if [ -n "$2" ]; then
        printf '  [x] %-24s set\n' "$1"
    else
        printf '  [ ] %-24s EMPTY - %s\n' "$1" "$3"
        MISSING=$((MISSING + 1))
    fi
}

if [ "$TLS_MODE" = "public" ]; then
    echo "Public deployment (SITE_HOST=${SITE_HOST}) - what is configured so far:"
    report_value "TUNNEL_TOKEN" "$TUNNEL_TOKEN" \
        "'make up-aws' refuses to start until the Cloudflare Tunnel token is in .env"
    report_value "CLOUDFLARE_API_TOKEN" "$CLOUDFLARE_API_TOKEN" \
        "Caddy cannot solve the DNS-01 challenge, so no certificate is issued"
    report_value "ACME_EMAIL" "$ACME_EMAIL" \
        "optional, but you then get no certificate expiry/revocation notices"
    if [ "$CF_ACCESS_MODE" = "on" ]; then
        report_value "CF_ACCESS_TEAM_DOMAIN" "$CF_ACCESS_TEAM_DOMAIN" \
            "'make up-aws' refuses to start; the Access issuer/JWKS host is unknown"
        report_value "CF_ACCESS_AUD" "$CF_ACCESS_AUD" \
            "'make up-aws' refuses to start; the Access application is unknown"
    fi
    report_value "SMTP_HOST" "$SMTP_HOST" \
        "the backend would try to reach a Mailpit container that is not running"
    report_value "SMTP_USERNAME" "$SMTP_USERNAME" "Amazon SES authentication will fail"
    report_value "SMTP_PASSWORD" "$SMTP_PASSWORD" "Amazon SES authentication will fail"
    report_value "MAIL_FROM_ADDRESS" "$MAIL_FROM_ADDRESS" \
        "the default sender is not a verified SES identity, so SES rejects the mail"
    report_value "ALLOWED_EMAIL_DOMAINS" "$ALLOWED_EMAIL_DOMAINS" \
        "the app-level allowlist is OFF: anyone past Access may register an account"
    echo
    if [ "$MISSING" -gt 0 ]; then
        echo "  ${MISSING} value(s) are still empty. Edit ${ENV_FILE} to fill them in,"
        echo "  or delete it and re-run this script with the matching flags."
    fi
    echo "  Next: make build && make up-aws"
    echo "  Then: docker compose logs -f caddy cloudflared   # certificate + tunnel"
else
    echo "Home/LAN deployment (SITE_HOST=${SITE_HOST})."
    if [ -n "$ALLOWED_EMAIL_DOMAINS" ]; then
        echo "  Note: ALLOWED_EMAIL_DOMAINS is set, so only those addresses may use"
        echo "        the site. Leave it empty for open registration at home."
    fi
    echo "  Next: run scripts/make-certs.sh, then 'make up'."
fi
