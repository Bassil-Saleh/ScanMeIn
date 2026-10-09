# Makefile for the ScanMeIn Docker deployment (home network and public
# server). The deployment type is selected by the target, which sets TLS_MODE
# and the Docker Compose profiles:
#   make up       -> TLS_MODE=local,  --profile local  (home/LAN: mkcert
#                    certificate + Mailpit, no Cloudflare Tunnel)
#   make up-aws   -> TLS_MODE=public, --profile tunnel (public server, e.g. EC2:
#                    Let's Encrypt via the Cloudflare DNS-01 challenge, ingress
#                    through a Cloudflare Tunnel, no Mailpit, email via SES)
#
# Common targets:
#   make bootstrap  Generate .env secrets and TLS certs (first time only).
#   make build      Build the backend, frontend, and custom Caddy Docker images.
#   make up         Start the full stack in the background (home network).
#   make up-aws     Start the stack for a public server (EC2 + Cloudflare Access).
#   make down       Stop and remove the stack (data volume is preserved).
#                   Teardown always activates BOTH the "local" and the "tunnel"
#                   profile so the Mailpit and cloudflared containers are removed
#                   too; otherwise they stay attached to the ticketnet network and
#                   Docker refuses to delete that network ("Network
#                   ticket_project_ticketnet Resource is still in use").
#   make logs       Tail logs from all services (also activates both profiles,
#                   otherwise Mailpit's and cloudflared's logs are skipped).
#   make status     Show the status of all services (both profiles activated).

.PHONY: bootstrap build up up-aws down logs status clean distclean help

# Every profile declared in compose.yaml. Teardown and inspection targets
# activate all of them: a container belonging to a profile that is NOT active in
# the current invocation is invisible to Compose, so `down` would leave Mailpit or
# cloudflared attached to ticketnet and Docker would then refuse to remove that
# network ("Resource is still in use").
ALL_PROFILES := --profile local --profile tunnel

help: ## Show this help.
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-12s\033[0m %s\n", $$1, $$2}'

bootstrap: ## Generate .env secrets and TLS certificates (first time only).
	./scripts/init-secrets.sh
	./scripts/make-certs.sh

build: ## Build the backend, frontend, and custom Caddy Docker images.
	docker compose build

up: ## Start the full stack in the background (home network, includes Mailpit).
	TLS_MODE=local docker compose --profile local up -d

# Public deployment (e.g. EC2):
#   TLS_MODE=public     -> Caddy obtains a Let's Encrypt certificate through the
#                          Cloudflare DNS-01 challenge (caddy/public.caddy), so
#                          no inbound port 80 is ever needed.
#   --profile tunnel    -> the cloudflared connector becomes the ONLY public
#                          ingress; it dials out to Cloudflare, so the security
#                          group needs no 80/443 rule and the origin cannot be
#                          reached by its raw IP address. Cloudflare Access
#                          authenticates users at the edge, and Caddy re-validates
#                          the Access JWT at the origin (CF_ACCESS_MODE=on).
# The preflight checks below exist because compose.yaml cannot enforce them
# itself: it must interpolate TUNNEL_TOKEN with an empty default, otherwise every
# other target (including the home-network `make up`/`make down`) would fail on a
# machine that has no tunnel token, since Compose interpolates all services
# before applying profiles.
#
# They also enforce "exactly one connector per tunnel", which is a failure mode
# that is invisible from inside the stack: the Cloudflare edge load-balances every
# request across ALL replicas of a tunnel, so a second connector that cannot reach
# the origin answers 502 Bad Gateway for its share of the traffic while the
# dashboard still reports it as Healthy - and no container logs anything, because
# those requests never arrive. The usual source is a host-level cloudflared left
# over from an earlier manual setup (`cloudflared service install <token>`), which
# cannot resolve the Docker service name `caddy` at all. See the README section
# "Troubleshooting: Bad Gateway (502) - a duplicate tunnel connector".
define check_aws_env
	@test -f .env || { echo "ERROR: no .env file. Run 'make bootstrap' first (on a public server, e.g.:"; echo "  ./scripts/init-secrets.sh --site-host <your-fqdn> --tls-mode public ...)"; exit 1; }
	@grep -Eq '^TUNNEL_TOKEN=.+' .env || { echo "ERROR: TUNNEL_TOKEN is missing or empty in .env."; echo "Create a tunnel in the Cloudflare Zero Trust dashboard (Networks -> Tunnels ->"; echo "Create a tunnel), add a public hostname pointing at https://caddy:443, and paste"; echo "the connector token into .env. See the README."; exit 1; }
	@if grep -Eq '^CF_ACCESS_MODE=on' .env; then \
		grep -Eq '^CF_ACCESS_TEAM_DOMAIN=.+' .env || { echo "ERROR: CF_ACCESS_MODE=on but CF_ACCESS_TEAM_DOMAIN is missing or empty in .env"; echo "(the Zero Trust team domain, without the scheme, e.g. scanmein.cloudflareaccess.com)."; exit 1; }; \
		grep -Eq '^CF_ACCESS_AUD=.+' .env || { echo "ERROR: CF_ACCESS_MODE=on but CF_ACCESS_AUD is missing or empty in .env"; echo "(the Application Audience tag of your Access application)."; exit 1; }; \
	fi
	@if systemctl list-units --type=service --state=running --no-legend 2>/dev/null | awk '{print $$1}' | grep -qi '^cloudflared' \
	   || [ -e /etc/cloudflared/token ]; then \
		echo "ERROR: a host-level cloudflared connector (or its tunnel token file) is"; \
		echo "present on this instance, while this stack runs its own connector as the"; \
		echo "'cloudflared' compose service. Exactly ONE connector may hold a connection"; \
		echo "to this tunnel: the edge load-balances across every replica, and a replica"; \
		echo "that cannot reach https://caddy:443 answers 502 Bad Gateway for its share"; \
		echo "of the traffic while still showing as Healthy in the dashboard. Remove it:"; \
		echo "  sudo cloudflared service uninstall"; \
		echo "  sudo apt-get remove -y cloudflared"; \
		echo "  sudo rm -rf /etc/cloudflared"; \
		exit 1; \
	fi
	@for pid in $$(pgrep -x cloudflared 2>/dev/null); do \
		if ! grep -qE 'docker|containerd' /proc/$$pid/cgroup 2>/dev/null; then \
			echo "ERROR: a host-level cloudflared process is running (PID $$pid)."; \
			echo "Stop it: only the 'cloudflared' container may connect to this tunnel"; \
			echo "(a host process cannot resolve the Docker service name 'caddy', so every"; \
			echo "request the edge sends it fails with 502 Bad Gateway)."; \
			exit 1; \
		fi; \
	done
	@extra=$$(docker ps --filter ancestor=cloudflare/cloudflared --format '{{.Names}}' 2>/dev/null | grep -v '^ticketproject-cloudflared$$' || true); \
	if [ -n "$$extra" ]; then \
		echo "ERROR: extra cloudflared container(s) are running: $$extra"; \
		echo "Only ticketproject-cloudflared may hold a connection to this tunnel."; \
		exit 1; \
	fi
	@command -v cloudflared >/dev/null 2>&1 && echo "WARNING: a cloudflared binary is installed on this host - do not run it with this tunnel's token (see the note above)." || true
endef

up-aws: ## Start the stack for a public server (EC2): Let's Encrypt cert, Cloudflare Tunnel + Access, no Mailpit, email via Amazon SES.
	$(check_aws_env)
	TLS_MODE=public docker compose --profile tunnel up -d

down: ## Stop and remove the stack (data volume preserved).
	docker compose $(ALL_PROFILES) down

logs: ## Tail logs from all services (includes Mailpit and cloudflared).
	docker compose $(ALL_PROFILES) logs -f

status: ## Show the status of all services.
	docker compose $(ALL_PROFILES) ps

clean: ## Stop the stack AND delete the MariaDB data volume.
	docker compose $(ALL_PROFILES) down -v
	@echo "Note: this removed the mariadb_data volume (database data)."

# Full reset for re-testing a deployment on a clean slate. It removes everything
# 'clean' does PLUS the built images, the build cache, the generated .env, and
# the TLS certs. Because both .env (secrets) and mariadb_data (encrypted rows)
# are deleted together, rotating the keys never orphans existing data.
distclean: ## Full reset: remove the stack, its volumes, built images, build cache, plus .env and certs/.
	docker compose $(ALL_PROFILES) down -v --remove-orphans
	-docker rmi -f ticketproject-caddy:latest
	docker image prune -f
	docker builder prune -f
	rm -f .env
	rm -rf certs
	@echo "Full reset complete: containers, volumes, built images, build cache, .env, and certs/ removed."
	@echo "Rebuild from scratch with: make bootstrap && make build"
