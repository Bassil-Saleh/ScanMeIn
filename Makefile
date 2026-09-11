# Makefile for the Ticket Project Docker deployment (home network and public
# server). The deployment type is selected by the target, which sets TLS_MODE:
#   make up       -> TLS_MODE=local  (home/LAN, mkcert certificate, + Mailpit)
#   make up-aws   -> TLS_MODE=public (public server, Let's Encrypt via the
#                    Cloudflare DNS-01 challenge, no Mailpit, email via SES)
#
# Common targets:
#   make bootstrap  Generate .env secrets and TLS certs (first time only).
#   make build      Build the backend, frontend, and custom Caddy Docker images.
#   make up         Start the full stack in the background (home network).
#   make up-aws     Start the stack for a public server (EC2, Let's Encrypt).
#   make down       Stop and remove the stack (data volume is preserved).
#                   Teardown always activates the "local" profile so the Mailpit
#                   container is removed too; otherwise it stays attached to the
#                   ticketnet network and Docker refuses to delete that network
#                   ("Network ticket_project_ticketnet Resource is still in use").
#   make logs       Tail logs from all services (also activates the "local"
#                   profile, otherwise Mailpit's logs are skipped entirely).
#   make status     Show the status of all services.

.PHONY: bootstrap build up up-aws down logs status clean help

help: ## Show this help.
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-12s\033[0m %s\n", $$1, $$2}'

bootstrap: ## Generate .env secrets and TLS certificates (first time only).
	./scripts/init-secrets.sh
	./scripts/make-certs.sh

build: ## Build the backend, frontend, and custom Caddy Docker images.
	docker compose build

up: ## Start the full stack in the background (home network, includes Mailpit).
	TLS_MODE=local docker compose --profile local up -d

up-aws: ## Start the stack for a public server (EC2): Let's Encrypt cert, no Mailpit, email via Amazon SES.
	TLS_MODE=public docker compose up -d

down: ## Stop and remove the stack (data volume preserved).
	docker compose --profile local down

logs: ## Tail logs from all services (includes Mailpit).
	docker compose --profile local logs -f

status: ## Show the status of all services.
	docker compose ps

clean: ## Stop the stack AND delete the MariaDB data volume.
	docker compose --profile local down -v
	@echo "Note: this removed the mariadb_data volume (database data)."
