# Makefile for the Ticket Project Docker deployment (home network and public
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
# Requires TUNNEL_TOKEN, SITE_HOST, ACME_EMAIL, CLOUDFLARE_API_TOKEN and
# CF_ACCESS_* to be set in .env (see scripts/init-secrets.sh and the README).
up-aws: ## Start the stack for a public server (EC2): Let's Encrypt cert, Cloudflare Tunnel + Access, no Mailpit, email via Amazon SES.
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
	docker compose --profile local down -v --remove-orphans
	-docker rmi -f ticketproject-caddy:latest
	docker image prune -f
	docker builder prune -f
	rm -f .env
	rm -rf certs
	@echo "Full reset complete: containers, volumes, built images, build cache, .env, and certs/ removed."
	@echo "Rebuild from scratch with: make bootstrap && make build"
