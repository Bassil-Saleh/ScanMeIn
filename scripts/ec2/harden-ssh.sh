#!/usr/bin/env bash
#
# harden-ssh.sh
#
# Run this ONCE on the EC2 instance, as root:
#
#   sudo ./scripts/ec2/harden-ssh.sh --user bassil --with-swap 2G
#
# It makes sure that YOU are the only person who can log in over SSH:
#
#   1. creates a non-root admin user whose only way in is your SSH public key
#      (no password is ever set for it),
#   2. disables root login, password login and keyboard-interactive login,
#   3. restricts SSH to that one user (AllowUsers),
#   4. validates the new sshd configuration BEFORE applying it - a broken config
#      would lock you out of the instance - and then RELOADS (not restarts) sshd,
#      so the session you are running this from is never dropped,
#   5. optionally adds a swap file (strongly recommended on a 1 GB instance,
#      where `make build` - Maven, the JDK, npm and Vite - will otherwise be
#      killed by the OOM reaper), plus fail2ban and unattended-upgrades.
#
# It is idempotent: re-running it only rewrites the drop-in configuration.
# --dry-run prints every change without touching the system.
#
# AFTER RUNNING IT: keep this session open, open a SECOND terminal and confirm
# that `ssh <admin user>@<instance>` works before you close this one. The revert
# instructions are printed at the end.
#
# NOTE: this script hardens SSH only. The network-level control (an EC2 security
# group that allows inbound 22/tcp from your IP address alone) is configured in
# the AWS console and is what stops the attempts from ever reaching sshd; this
# script is the second layer. See the README.

set -euo pipefail

SSHD_DROPIN="/etc/ssh/sshd_config.d/99-ticketproject-hardening.conf"
ADMIN_USER="admin"
SSH_KEY=""
SWAP_SIZE=""
WITH_FAIL2BAN=0
WITH_UNATTENDED=1
DRY_RUN=0

usage() {
    cat <<'USAGE'
Usage: sudo scripts/ec2/harden-ssh.sh [options]

Options:
  --user <name>            Admin user to create/keep (default: admin).
  --ssh-key <path|key>     Your SSH PUBLIC key: a path to a file, or the key
                           itself ("ssh-ed25519 AAAA..."). Default: the
                           authorized_keys of the user who invoked sudo (i.e. the
                           stock `ubuntu` user's key on an AWS Ubuntu AMI).
  --with-swap [SIZE]       Create a swap file if none exists (default SIZE: 2G).
                           Recommended on a 1 GB instance so `make build` does not
                           get OOM-killed.
  --with-fail2ban          Install fail2ban with an sshd jail.
  --no-unattended-upgrades Do not enable automatic security updates.
  --dry-run                Print what would change, change nothing.
  -h, --help               Show this help.
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --user)                   ADMIN_USER="$2"; shift 2 ;;
        --ssh-key)                SSH_KEY="$2"; shift 2 ;;
        --with-swap)              if [ "${2:-}" = "--with-fail2ban" ] || [ "${2:-}" = "--dry-run" ] || [ -z "${2:-}" ]; then
                                      SWAP_SIZE="2G"; shift 1
                                  else
                                      SWAP_SIZE="$2"; shift 2
                                  fi ;;
        --with-fail2ban)          WITH_FAIL2BAN=1; shift ;;
        --no-unattended-upgrades) WITH_UNATTENDED=0; shift ;;
        --dry-run)                DRY_RUN=1; shift ;;
        -h|--help)                usage; exit 0 ;;
        *) echo "ERROR: unknown option '$1'." >&2; usage >&2; exit 1 ;;
    esac
done

die() { echo "ERROR: $*" >&2; exit 1; }

# run <command...>: echo it, and execute it unless --dry-run was given.
run() {
    echo "  \$ $*"
    [ "$DRY_RUN" -eq 1 ] && return 0
    "$@"
}

[ "$(id -u)" -eq 0 ] || die "this script must run as root: sudo $0 $*"

# Only Ubuntu/Debian are supported: the paths (sshd_config.d, the ssh service
# name, apt) differ elsewhere and a half-applied SSH hardening is dangerous.
. /etc/os-release
case "${ID:-}" in
    ubuntu|debian) ;;
    *) die "unsupported distribution '${ID:-unknown}'. This script targets the Ubuntu Server AMI." ;;
esac

[ "$DRY_RUN" -eq 1 ] && echo "*** DRY RUN: nothing will be modified. ***"

# --- 1. The public key that will be the ONLY way in --------------------------
# Refusing to continue when no key can be found is deliberate: installing an
# empty authorized_keys while disabling password authentication would lock
# everybody (including you) out of the instance.
if [ -n "$SSH_KEY" ]; then
    if [ -f "$SSH_KEY" ]; then
        PUBKEY="$(cat "$SSH_KEY")"
    else
        PUBKEY="$SSH_KEY"
    fi
else
    INVOKER="${SUDO_USER:-}"
    for candidate in \
        ${INVOKER:+"/home/${INVOKER}/.ssh/authorized_keys"} \
        "/home/ubuntu/.ssh/authorized_keys" \
        "/root/.ssh/authorized_keys"
    do
        if [ -s "$candidate" ]; then
            PUBKEY="$(cat "$candidate")"
            echo "Using the public key from ${candidate}."
            break
        fi
    done
fi

case "${PUBKEY:-}" in
    ssh-rsa\ *|ssh-ed25519\ *|ecdsa-*|ssh-dss\ *) ;;
    "") die "no SSH public key found. Pass one with --ssh-key <path|key>." ;;
    *)  die "'${PUBKEY:0:20}...' does not look like an SSH public key. Pass --ssh-key <path|key>." ;;
esac

# --- 2. The admin user -------------------------------------------------------
echo "==> Ensuring the admin user '${ADMIN_USER}' exists"
if id "$ADMIN_USER" >/dev/null 2>&1; then
    echo "  user '${ADMIN_USER}' already exists."
else
    run useradd --create-home --shell /bin/bash --groups sudo "$ADMIN_USER"
fi
# A locked password means "password login is impossible", which is exactly what we
# want: the public key installed below is the only credential that works.
run passwd --lock "$ADMIN_USER"

# Give that user access to Docker when Docker is already installed, since the
# stack is managed with `make build` / `make up-aws`. Note that membership of the
# docker group is equivalent to root on this host - another reason to keep the
# user list down to a single admin.
if getent group docker >/dev/null 2>&1; then
    run usermod --append --groups docker "$ADMIN_USER"
else
    echo "  NOTE: the 'docker' group does not exist yet. After installing Docker,"
    echo "        run: sudo usermod -aG docker ${ADMIN_USER}"
fi

echo "==> Installing your SSH public key for '${ADMIN_USER}'"
ADMIN_HOME="$(getent passwd "$ADMIN_USER" 2>/dev/null | cut -d: -f6)"
[ -n "$ADMIN_HOME" ] || ADMIN_HOME="/home/${ADMIN_USER}"
run install --directory --owner "$ADMIN_USER" --group "$ADMIN_USER" --mode 700 "${ADMIN_HOME}/.ssh"
if [ "$DRY_RUN" -eq 1 ]; then
    echo "  \$ write ${ADMIN_HOME}/.ssh/authorized_keys (owner ${ADMIN_USER}, mode 600)"
else
    printf '%s\n' "$PUBKEY" > "${ADMIN_HOME}/.ssh/authorized_keys"
    chown "${ADMIN_USER}:${ADMIN_USER}" "${ADMIN_HOME}/.ssh/authorized_keys"
    chmod 600 "${ADMIN_HOME}/.ssh/authorized_keys"
    echo "  wrote ${ADMIN_HOME}/.ssh/authorized_keys"
fi

# --- 3. The sshd configuration ----------------------------------------------
echo "==> Preparing ${SSHD_DROPIN}"
# Ubuntu's /etc/ssh/sshd_config ends with `Include /etc/ssh/sshd_config.d/*.conf`,
# and a drop-in included there overrides the distribution defaults. That keeps
# this script from having to edit sshd_config itself (which package upgrades then
# have to merge). If that Include is missing, refuse rather than guess.
grep -Eq '^[[:space:]]*Include[[:space:]]+/etc/ssh/sshd_config\.d/\*\.conf' /etc/ssh/sshd_config \
    || die "/etc/ssh/sshd_config does not include /etc/ssh/sshd_config.d/*.conf; refusing to guess where to put the hardening."

NEW_CONFIG="# Written by scripts/ec2/harden-ssh.sh on $(date -u +"%Y-%m-%dT%H:%M:%SZ").
# Only '${ADMIN_USER}' with a public key may log in over SSH.
# To revert: sudo rm ${SSHD_DROPIN} && sudo systemctl reload ssh
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
AuthenticationMethods publickey
AllowUsers ${ADMIN_USER}
MaxAuthTries 3
LoginGraceTime 30
X11Forwarding no
"

if [ "$DRY_RUN" -eq 1 ]; then
    echo "  would write:"
    printf '%s' "$NEW_CONFIG" | sed 's/^/    /'
else
    printf '%s' "$NEW_CONFIG" > "$SSHD_DROPIN"
    chmod 600 "$SSHD_DROPIN"
    echo "  wrote ${SSHD_DROPIN}"
fi

# --- 4. Validate BEFORE applying --------------------------------------------
echo "==> Validating the merged sshd configuration"
# A typo here would lock you out of the instance for good (there is no console
# password login on EC2), so sshd is asked to test the FULL merged configuration
# first. If it objects, the drop-in is deleted again and nothing is applied.
if [ "$DRY_RUN" -eq 0 ]; then
    # `sshd -t` refuses to run without its privilege separation directory, which
    # does not exist yet on a machine where sshd has never been started by
    # systemd (a fresh container, or an instance before its first reboot).
    # Creating it is harmless: it is exactly what the ssh unit does at startup,
    # and without this the validation below would fail for an environmental
    # reason and abort the hardening.
    [ -d /run/sshd ] || run mkdir -p /run/sshd
    if ! sshd -t; then
        rm -f "$SSHD_DROPIN"
        die "sshd rejected the new configuration. ${SSHD_DROPIN} was removed and nothing was applied."
    fi
    echo "  sshd -t: configuration is valid."
    echo "==> Reloading sshd (your current session stays connected)"
    if systemctl reload ssh >/dev/null 2>&1 || systemctl reload sshd >/dev/null 2>&1; then
        echo "  sshd reloaded."
    else
        die "could not reload sshd. Inspect 'systemctl status ssh' and ${SSHD_DROPIN}."
    fi
fi

# --- 5. Swap file (optional, but recommended on a 1 GB instance) -------------
if [ -n "$SWAP_SIZE" ]; then
    echo "==> Swap file (${SWAP_SIZE})"
    if swapon --show=NAME --noheadings | grep -q .; then
        echo "  swap is already active: $(swapon --show=NAME --noheadings | tr '\n' ' ')"
    else
        # `make build` on this host runs Maven + the JDK (backend image) and
        # npm + tsc + Vite (frontend image). On 1 GB of RAM that gets killed by
        # the OOM reaper; a swap file turns a crash into a slow build instead.
        run fallocate --length "$SWAP_SIZE" /swapfile
        run chmod 600 /swapfile
        run mkswap /swapfile
        run swapon /swapfile
        if ! grep -q '^/swapfile' /etc/fstab; then
            run sh -c 'echo "/swapfile none swap sw 0 0" >> /etc/fstab'
        fi
        # Keep swappiness low: swap is a safety net for the build, not somewhere
        # MariaDB should live during normal operation.
        if [ ! -f /etc/sysctl.d/99-ticketproject-swap.conf ]; then
            run sh -c 'echo "vm.swappiness=10" > /etc/sysctl.d/99-ticketproject-swap.conf'
        fi
        run sysctl --system
    fi
fi

# --- 6. fail2ban (optional) --------------------------------------------------
if [ "$WITH_FAIL2BAN" -eq 1 ]; then
    echo "==> fail2ban"
    if ! command -v fail2ban-server >/dev/null 2>&1; then
        run apt-get update -qq
        run env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq fail2ban
    fi
    if [ "$DRY_RUN" -eq 0 ]; then
        cat > /etc/fail2ban/jail.d/sshd.local <<'F2B'
# Written by scripts/ec2/harden-ssh.sh. sshd is the only jail needed here,
# because SSH is the only port this instance accepts inbound connections on
# (the site is reached through an outbound Cloudflare Tunnel).
[sshd]
enabled  = true
port     = ssh
maxretry = 3
findtime = 10m
bantime  = 1h
F2B
        echo "  wrote /etc/fail2ban/jail.d/sshd.local"
    else
        echo "  would write /etc/fail2ban/jail.d/sshd.local ([sshd] enabled, maxretry 3, bantime 1h)"
    fi
    run systemctl enable fail2ban
    run systemctl restart fail2ban
fi

# --- 7. Automatic security updates ------------------------------------------
if [ "$WITH_UNATTENDED" -eq 1 ]; then
    echo "==> unattended-upgrades (automatic security updates)"
    if ! dpkg -l unattended-upgrades 2>/dev/null | grep -q '^ii'; then
        run apt-get update -qq
        run env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq unattended-upgrades
    fi
    if [ "$DRY_RUN" -eq 0 ]; then
        cat > /etc/apt/apt.conf.d/20auto-upgrades <<'APT'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT
        echo "  wrote /etc/apt/apt.conf.d/20auto-upgrades"
    else
        echo "  would write /etc/apt/apt.conf.d/20auto-upgrades (daily lists + upgrades)"
    fi
fi

# --- 8. Summary --------------------------------------------------------------
echo
echo "============================================================================="
echo "SSH hardening applied for user '${ADMIN_USER}'."
echo
echo "NOW, before you close this session:"
echo "  1. In a SECOND terminal run:  ssh ${ADMIN_USER}@<instance-ip-or-hostname>"
echo "  2. Confirm you get a shell and that 'sudo -v' works."
echo "  3. Only then close this session."
echo
echo "Also confirm in the AWS console that this instance's security group:"
echo "  * allows inbound 22/tcp ONLY from your current public IP (x.x.x.x/32), and"
echo "  * has NO inbound rule for 80/443 - the Cloudflare Tunnel connector dials"
echo "    OUT, so nothing on this host needs to be reachable from the internet."
echo
echo "To revert this script:"
echo "  sudo rm ${SSHD_DROPIN} && sudo systemctl reload ssh"
echo "============================================================================="

