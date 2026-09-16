# Ticket Project

## Project Demo:

### Homepage:

![Homepage](https://pub-60aaad6e8e644991aa7688bb88a9a11b.r2.dev/08-27-2026/Homepage.png)

### Account Creation, Verification, Login

![Account Creation, Verification, Login Walkthrough](https://pub-60aaad6e8e644991aa7688bb88a9a11b.r2.dev/08-27-2026/SignUp.webp)

### Event Creation

![Event Creation Walkthrough](https://pub-60aaad6e8e644991aa7688bb88a9a11b.r2.dev/08-27-2026/CreateEvent.webp)

### Event Registration, Receiving A Ticket

![Event Registration, Receiving A Ticket Walkthrough](https://pub-60aaad6e8e644991aa7688bb88a9a11b.r2.dev/08-27-2026/RegisterForEvent.webp)

### Scanning Tickets, Viewing Scanned Tickets

![Scanning Tickets, Viewing Scanned Tickets Walkthrough](https://pub-60aaad6e8e644991aa7688bb88a9a11b.r2.dev/08-27-2026/ScanTicketOnMobile.webp)

## Running on a Home-Network Server (Docker Compose)

This project ships with a Docker Compose stack so you can run the entire
application (MariaDB, Mailpit, the Spring Boot backend, the React frontend,
and an HTTPS reverse proxy using Caddy) on a dedicated machine on your home
network and reach it from any device (phones, laptops) over Wi-Fi.

This is useful for:

- Testing the app on multiple devices.
- Opening the emails your app sends (including ticket QR codes) directly on
  your phone via Mailpit's web UI, instead of copying files around by hand.
- Reproducible, automated setup of every dependency.

### One-time host preparation

1. Install Docker Engine and the Compose plugin using the Docker installation script:
   ```
   # Download the script
   curl -fsSL https://get.docker.com -o install-docker.sh
   # Verify the script's content
   cat install-docker.sh
   # Run the script with --dry-run to verify the steps it executes
   sh install-docker.sh --dry-run
   # Run the script either as root or using sudo to perform the installation
   sudo sh install-docker.sh
   ```
2. Install `mkcert` (and the NSS tools it needs):
   ```
   sudo apt install -y mkcert libnss3-tools
   ```
3. (Recommended) Give this machine a hostname other devices can resolve over
   mDNS, e.g. `ticketproject.local`. On Ubuntu/Debian, install Avahi and set
   the hostname to match `SITE_HOST` in `.env`:
   ```
   sudo apt install -y avahi-daemon
   ```
4. (Recommended) Reserve this machine's IP address in your router's DHCP
   settings so it does not change.

### First-time setup

From the repository root:

```
make bootstrap   # generates .env secrets + TLS certificates
make build       # builds the backend, frontend, and Caddy images
make up          # starts the whole stack
```

### Trusting the certificate on your devices

`make bootstrap` prints the path to mkcert's root CA (`rootCA.pem`). Install
that CA on each device you want to test from so they trust
`https://ticketproject.local` (or whatever else you set your hostname to):

- **Android:** Settings → Security → Install certificate (choose user CA).
- **iOS:** Install the profile, then enable full trust under
  Settings → General → About → Certificate Trust Settings.

Once trusted, `https://ticketproject.local` (or `https://whatever_else_your_hostname_is`)
is a secure context, so the camera-based QR ticket scanner works on your phone.

### Using it

Assuming your chosen hostname is `ticketproject.local`:

| What | Where |
| --- | --- |
| App (HTTPS) | `https://ticketproject.local` |
| Mailpit web UI (emails + QR codes), `local` profile only | `http://ticketproject.local:8025` |
| Swagger API docs | `https://ticketproject.local/swagger-ui/index.html` |

Manage the stack with `make status`, `make logs`, `make down`.
Run `make help` to list all targets.

> Secrets live in the gitignored `.env` file. Delete it and re-run
> `make bootstrap` only if you want fresh secrets (this does NOT delete
> database data; use `make clean` for that).

### Restricting access with an email allowlist (public deployments)

By default registration is open, which is what you want on a trusted home
network. Before deploying the stack to a public server (e.g. an EC2 instance)
where you do **not** want strangers creating accounts, set the
`ALLOWED_EMAIL_DOMAINS` value in `.env` to a comma-separated list of the email
addresses and/or domains that are allowed to use the site:

```
# Only these people can register, log in, register for events,
# receive invitations, or receive any email from the app.
ALLOWED_EMAIL_DOMAINS=you@yourdomain.com,yourdomain.com,friend@gmail.com
```

- An entry containing `@` (e.g. `you@yourdomain.com`) must match exactly.
- A bare domain (e.g. `yourdomain.com`) matches any address at that domain and
  its subdomains. Matching is case-insensitive.
- Leaving it **empty** disables the allowlist (open registration).

After editing `.env`, run `make up-aws` to apply.
Anyone whose email is not on the list receives a `403 Forbidden` when trying to
register, and login attempts for disallowed addresses fail with the same generic
"invalid credentials" message used for a wrong password (so the response does
not reveal whether an account exists).

This allowlist is the **application-level** gate: it decides which of the people
who can reach the site may hold an account in it. It is *not* what stops a
stranger from loading the site in the first place - that is the job of the
Cloudflare Tunnel + Cloudflare Access layers described in the next section. The
two lists should be kept in sync, and remember that somebody you invite has to
appear in **both** of them to be able to open an invitation link.

## Deploying to a Public Server (EC2) with Amazon SES + Cloudflare Access

On a public server you should **not** run Mailpit (its web UI would expose every
email, including password-reset links and ticket QR codes, to anyone who can
reach the port). Instead, the backend sends email through **Amazon SES** using
its SMTP interface. The same Docker images, `compose.yaml` and `Caddyfile` are
used for both deployments - only the environment changes.

A public deployment also differs in how traffic reaches it: its **only** ingress
is a **Cloudflare Tunnel** connector that dials *out* to Cloudflare, and every
request arriving through that tunnel must carry a valid **Cloudflare Access**
JWT, which Caddy verifies at the origin. The result is that the site cannot be
reached by its raw IP address (there is no inbound port to reach), and nobody who
is not on your email allowlist can use it. See *"Locking the site down"* below
for how each layer contributes and what to configure in each dashboard.

### How the two deployments differ

| | Home network | Public server (EC2) |
| --- | --- | --- |
| Start command | `make up` (`TLS_MODE=local docker compose --profile local up -d`) | `make up-aws` (`TLS_MODE=public docker compose --profile tunnel up -d`) |
| Compose profiles | `local` (Mailpit) | `tunnel` (cloudflared) |
| `TLS_MODE` | `local` | `public` |
| Certificate (Caddy) | mkcert LAN cert from `certs/` | Let's Encrypt via Cloudflare DNS-01 |
| Ingress | Devices connect straight to Caddy on the LAN | **Cloudflare Tunnel only** - the `cloudflared` container dials out; no inbound 80/443 |
| `CADDY_BIND_ADDR` | `0.0.0.0` (reachable on the LAN) | `127.0.0.1` (loopback only; the tunnel reaches Caddy on the internal Docker network) |
| Who may use the site | Anyone on your network | Only the emails in your **Cloudflare Access** policy, and Caddy re-validates the Access JWT (`CF_ACCESS_MODE=on`) |
| EC2 security group | n/a | Inbound **22/tcp from your IP only**; no 80/443 rule at all |
| Mailpit | Started (web UI on `8025`) | **Not started** (no `local` profile) |
| Email transport | Mailpit SMTP (`mailpit:1025`, no auth/TLS) | Amazon SES SMTP (`email-smtp.<region>.amazonaws.com:587`, auth + STARTTLS) |
| `MAIL_FROM_ADDRESS` | `noreply@ticketproject.local` (default) | A **verified** SES identity/domain |
| Teardown command | `make down` / `make clean` | The **same** commands: they activate *both* profiles, so Mailpit and cloudflared are removed whichever deployment created them |

Because `SMTP_*` values in `compose.yaml` default to Mailpit and `CADDY_BIND_ADDR`
defaults to `0.0.0.0`, a home-network deployment needs no extra configuration; an
EC2 deployment just overrides them in `.env` (which `scripts/init-secrets.sh`
does for you when it detects it is running on EC2).

### Provisioning the EC2 instance

1. **Launch the instance.** Ubuntu Server 24.04 LTS, `t3.small` (2 GB) -
   a 1 GB instance works but needs the swap file from step 5, since `make build`
   runs Maven + the JDK and npm + `tsc` + Vite. Give it 20-30 GB of gp3 storage
   (Docker images + the MariaDB volume). If you choose an ARM instance
   (`t4g.*`), everything still works; just build the images on an ARM host or
   with `docker buildx --platform linux/arm64`.
2. **Key pair.** Create a new ed25519 key pair and download the `.pem`
   (`chmod 400` it locally).
3. **Security group.** Inbound: **`22/tcp` from your current public IP only**
   (`x.x.x.x/32`). Do **not** add rules for 80/443 - the tunnel connector dials
   out, so nothing needs to be reachable from the internet, and that is what
   makes the raw IP address useless to an attacker. Outbound: all (ACME, the
   Cloudflare API/JWKS, SES, Docker Hub).
4. **Instance settings.** Set *Metadata version* to **V2 only** (IMDSv2 required)
   so instance credentials cannot be read via an SSRF bug, and keep EBS
   encryption on. Optional but recommended: an instance role with
   `AmazonSSMManagedInstanceCore`, so you always have a fallback shell (Session
   Manager) if your home IP changes and the `22/tcp` rule no longer matches.
5. **First login, then harden SSH.** Connect as the stock `ubuntu` user and run
   the hardening script from the repository (clone it first, or copy the single
   file over). It creates your own admin user, installs your public key, disables
   root/password/keyboard-interactive login, restricts SSH to that one user,
   validates the config with `sshd -t` *before* applying it, and reloads sshd
   without dropping your session:
   ```
   git clone <your-repo-url> Ticket_Project && cd Ticket_Project
   sudo ./scripts/ec2/harden-ssh.sh --user <yourname> --with-swap 2G
   ```
   Add `--with-fail2ban` if you want brute-force attempts banned. **Keep that
   session open and verify login from a second terminal before closing it** -
   the script prints the exact revert command. Reconnect as your new user
   afterwards, and install Docker:
   ```
   curl -fsSL https://get.docker.com -o install-docker.sh
   cat install-docker.sh          # verify what it does
   sudo sh install-docker.sh
   sudo usermod -aG docker $USER  # then log out and back in
   ```
6. **No Elastic IP is required.** The hostname is a proxied CNAME to the tunnel,
   so the instance's address never needs to be published or stable. (If you
   prefer the classic "public A record + open 443" design instead of a tunnel,
   you do need an Elastic IP and a `443/tcp` rule restricted to Cloudflare's IP
   ranges - see the note at the end of *"Locking the site down"*.)

### One-time Amazon SES setup

1. **Verify your sender identity.** In the SES console, verify the domain you
   own (recommended: add the DKIM records SES gives you) or a single email
   address. Your `MAIL_FROM_ADDRESS` must match a verified identity, e.g.
   `noreply@yourdomain.com`.
2. **Create SMTP credentials.** In SES → *SMTP settings*, create SMTP
   credentials. This provisions an IAM user scoped to sending email. Keep the
   username/password secret: they go in `.env` (gitignored, `chmod 600`).
3. **Note your region + endpoint.** The SMTP host is
   `email-smtp.<region>.amazonaws.com` (e.g. `us-east-1`). Use port `587` with
   STARTTLS.
4. **Sandbox vs production.** New SES accounts are in the **sandbox**: you can
   only send to *verified* recipients. This pairs well with
   `ALLOWED_EMAIL_DOMAINS`. To send to anyone, request production access from
   the SES console.

### The SES values in `.env`

These are the SMTP settings the backend needs on a public server. (The complete
`.env` - including the Cloudflare Tunnel and Access values - is generated in one
step later on; see *"Generating `.env` on the EC2 instance"*.)

```
SMTP_HOST=email-smtp.<region>.amazonaws.com
SMTP_PORT=587
SMTP_USERNAME=<SES SMTP username>
SMTP_PASSWORD=<SES SMTP password>
SMTP_AUTH=true
SMTP_STARTTLS=true
MAIL_FROM_ADDRESS=noreply@scanmein.online   # must be a verified SES identity
```

`scripts/init-secrets.sh --tls-mode public` fills in `587`/`true`/`true` for you
and takes the rest as flags (or asks for them). Mailpit is **not** started on a
public deployment: `make up-aws` does not activate the `local` profile, and the
backend still starts because its `depends_on: mailpit` is marked
`required: false`. Confirm with `make status` that there is no
`ticketproject-mailpit` container.

### Public HTTPS certificate (Let's Encrypt via Cloudflare DNS-01)

On a public server you must serve a **publicly-trusted** certificate instead of
the mkcert LAN certificate used at home. The stack obtains one automatically
from **Let's Encrypt** using Caddy's **DNS-01** challenge through Cloudflare,
which is what lets it work when:

- the hostname is proxied through Cloudflare (orange-cloud), and/or
- inbound port 80 is not open to the internet at all - which is exactly the case
  here, since the security group allows `22/tcp` only and all traffic arrives
  through an outbound Cloudflare Tunnel. DNS-01 needs no inbound connection,
  only outbound access to the ACME server and the Cloudflare API.

The same `compose.yaml`, `Caddyfile`, and custom Caddy image are used for both
deployments; only `TLS_MODE` (and a few extra variables) change. `TLS_MODE` is
set by the Makefile target (`make up` forces `local`, `make up-aws` forces
`public`), so you normally do not have to touch it.

#### One-time Cloudflare setup

1. **DNS zone.** `SITE_HOST` must live in a zone managed by Cloudflare
   (`scanmein.online`). You do **not** need an A record pointing at the instance:
   the tunnel's public hostname creates a proxied CNAME for you, which is why the
   instance's IP address never has to be published.
2. **API token** (for the certificate). Create one at
   <https://dash.cloudflare.com/profile/api-tokens> with **Zone → Zone → Read**
   and **Zone → DNS → Edit** for that zone. Caddy uses it to solve the DNS-01
   challenge. Keep it secret.
3. **Zero Trust team domain.** In the Zero Trust dashboard note your team domain
   (`<team>.cloudflareaccess.com`). It is both the *issuer* of the Access JWTs and
   the host of the public keys Caddy verifies them against.
4. **Cloudflare Tunnel.** Zero Trust → Networks → Tunnels → *Create a tunnel*
   (Cloudflared) and copy the **token** - it becomes `TUNNEL_TOKEN` in `.env`.
   Under *Public hostname*, add `scanmein.online` with:
   - Service type **HTTPS**, URL **`caddy:443`** (`caddy` is the compose service
     name; the connector reaches it over the internal Docker network), and
   - *Additional application settings* → **Origin Server Name** *and* **HTTP Host
     Header** = `scanmein.online`.
   Setting the Origin Server Name makes `cloudflared` **verify** Caddy's real
   Let's Encrypt certificate on that hop, so do not enable "No TLS Verify".
5. **Cloudflare Access application.** Zero Trust → Access → Applications → *Add*
   → *Self-hosted*:
   - Public hostname `scanmein.online`; set the session duration to about a week
     so an expired session does not interrupt the SPA mid-use.
   - Exactly one policy: **Allow** → Include → **Emails** = your allowlist (or
     *Emails ending in* a domain). Delete or replace any default "allow everyone"
     policy - whatever no policy matches is denied.
   - Login method: **One-time PIN** is the simplest way to gate on a plain list of
     email addresses.
   - Copy the **Application Audience (AUD)** tag from the application overview -
     it becomes `CF_ACCESS_AUD` in `.env`.
6. **(Recommended) Service token for scripts.** Access → Service Auth → *Create
   Service Token*, then add a second policy to the application with the action
   **Service Auth** that includes it. Non-browser clients cannot complete an
   interactive login, so they authenticate with headers instead - which is what
   the `curl` helpers in `api-scripts/` need once the site is behind Access:
   ```
   curl -H "CF-Access-Client-Id: <id>" -H "CF-Access-Client-Secret: <secret>" \
        https://scanmein.online/api/v1/events
   ```
7. **SSL/TLS mode** → **Full (strict)**, so Cloudflare validates the origin's
   Let's Encrypt certificate instead of accepting anything.

### Locking the site down: Cloudflare Tunnel + Cloudflare Access

While the project is in development, only a specific list of people may use it.
Five independent layers produce that, and it is worth knowing what each one is
responsible for:

| # | Layer | Where | What it stops | Configured by |
| --- | --- | --- | --- | --- |
| 1 | Security group | AWS | Any TCP connection to the instance except SSH from your IP. There is no inbound 80/443 rule, so the raw IP address leads nowhere. | EC2 console |
| 2 | Cloudflare Tunnel | EC2 → Cloudflare | Any route that does not go through Cloudflare: the only way in is the *outbound* connection the `cloudflared` container holds open, and Caddy's ports are bound to loopback (`CADDY_BIND_ADDR=127.0.0.1`). | `TUNNEL_TOKEN`, `tunnel` profile |
| 3 | Cloudflare Access | Cloudflare edge | Everybody who is not on the email allowlist: they get a login page and never reach the origin. | Access application + policy |
| 4 | Access JWT validation | Caddy (origin) | A request that reaches the origin *without* having passed Access - a misconfigured hostname, a second tunnel, another container on the host, or an inbound rule somebody adds later. Caddy verifies the signature, issuer, audience and expiry and returns `401` otherwise. | `CF_ACCESS_MODE=on`, `caddy/access-on.caddy` |
| 5 | App email allowlist | Spring Boot | Which of the people who did get in may hold an account: register, log in, be invited, receive email. | `ALLOWED_EMAIL_DOMAINS` |

**Why layer 4 exists when the tunnel already blocks direct access.** Cloudflare
Access only protects requests that travel *through* Cloudflare. Layer 4 turns
"trust the network path" into "verify a cryptographic proof", so the guarantee
does not silently disappear if the network path ever changes. It is implemented
in `caddy/access-on.caddy` using the `jwtauth` directive from
[`ggicci/caddy-jwt`](https://github.com/ggicci/caddy-jwt), compiled into the
custom Caddy image by `caddy.Dockerfile`, and verifies each request's
`Cf-Access-Jwt-Assertion` header (or `CF_Authorization` cookie) against your
team's published keys at
`https://<team>.cloudflareaccess.com/cdn-cgi/access/certs`. Two details worth
knowing:

- The check runs **before** any routing, so the SPA, `/api/*`, `/swagger-ui` and
  `/v3/api-docs` are all protected - not just the API.
- Your frontend also sends its own `Authorization: Bearer <app JWT>` to `/api`.
  That token is simply one more candidate that fails Access verification and is
  skipped, so the two authentication schemes never interfere.

Unsigned headers such as `Cf-Access-Authenticated-User-Email` are deleted by the
`Caddyfile` before anything is proxied: only the signed JWT counts as proof of
identity.

> **Troubleshooting.** This check is deliberately fail-closed: if Caddy cannot
> reach `https://<team>.cloudflareaccess.com/cdn-cgi/access/certs`, or if
> `CF_ACCESS_AUD` does not match the application that issued the token, every
> request gets a `401` (and Caddy may refuse to start at all). `docker compose
> logs caddy` says why - look for `invalid token`, `invalid audience` or a JWKS
> fetch error. While debugging you can take the check out of the path with
> `CF_ACCESS_MODE=off` in `.env` followed by `make up-aws`; layers 1-3 still
> apply, but do not leave it off on a public deployment.

**Opening the site to the public later** takes two steps and no downtime: widen
or disable the Access policy in the dashboard, set `CF_ACCESS_MODE=off` in
`.env`, and run `make up-aws`. Because the certificate is obtained via DNS-01 and
stored on the origin, none of this changes TLS or DNS.

> If you ever prefer the classic design - a public A record with 443 open instead
> of a tunnel - layers 1 and 2 change but layer 4 becomes essential: restrict
> `443/tcp` to Cloudflare's published ranges
> (`curl https://api.cloudflare.com/client/v4/ips`, and re-check it
> occasionally), set `CADDY_BIND_ADDR=0.0.0.0`, and keep `CF_ACCESS_MODE=on` so a
> request arriving through somebody else's Cloudflare zone is still rejected.

### Generating `.env` on the EC2 instance

Run this **on the instance**, from the repository root. `scripts/init-secrets.sh`
detects that it is running on EC2 and therefore refuses to use `hostname -f`
(which there yields an internal name such as
`ip-172-31-8-42.us-east-2.compute.internal`), defaults to `TLS_MODE=public`,
`CADDY_BIND_ADDR=127.0.0.1` and `CF_ACCESS_MODE=on`, and generates fresh database
and application secrets:

```
./scripts/init-secrets.sh \
    --site-host scanmein.online \
    --acme-email dev@scanmein.online \
    --cloudflare-api-token <zone-read + dns-edit token> \
    --tunnel-token <cloudflared token> \
    --cf-access-team-domain <team>.cloudflareaccess.com \
    --cf-access-aud <application AUD tag> \
    --allowed-emails you@example.com,friend@example.com \
    --mail-from noreply@scanmein.online \
    --smtp-host email-smtp.<region>.amazonaws.com \
    --smtp-username <SES SMTP username> \
    --smtp-password <SES SMTP password>
```

Every flag can also be given as an environment variable named like the `.env` key
it sets (handy for secrets you would rather not keep in your shell history), and
anything you leave out is asked for when the script runs in a terminal. It finishes
with a checklist of whatever is still empty; `make up-aws` refuses to start until
`TUNNEL_TOKEN` and - while `CF_ACCESS_MODE=on` - both `CF_ACCESS_*` values are set.
The file is gitignored and `chmod 600`. Among the generated secrets it contains:

```
SITE_HOST=scanmein.online
FRONTEND_BASE_URL=https://scanmein.online
TLS_MODE=public
CADDY_BIND_ADDR=127.0.0.1
TUNNEL_TOKEN=<cloudflared token>
CF_ACCESS_MODE=on
CF_ACCESS_TEAM_DOMAIN=<team>.cloudflareaccess.com
CF_ACCESS_AUD=<application AUD tag>
ACME_EMAIL=dev@scanmein.online
CLOUDFLARE_API_TOKEN=<scoped token>
ALLOWED_EMAIL_DOMAINS=you@example.com,friend@example.com
SMTP_HOST=email-smtp.<region>.amazonaws.com
```

> `.env` also holds the keys that encrypt rows inside the `mariadb_data` volume,
> so the two belong together. `make clean` deletes the volume and `make
> distclean` deletes the volume *and* `.env`, precisely so that rotating keys can
> never orphan existing data. Back both up if the data matters to you.

### Build and start

```
make build       # backend, frontend, and the custom Caddy image (Cloudflare DNS + JWT modules)
make up-aws      # TLS_MODE=public, tunnel profile, no Mailpit, email via Amazon SES
```

Then watch the certificate and the tunnel come up:

```
docker compose logs -f caddy cloudflared
```

The first start performs the DNS-01 challenge and stores the certificate (and the
ACME account) in the `caddy_data` named volume; renewals then happen
automatically. **Do not delete `caddy_data`** unless you intend to re-issue the
certificate (Let's Encrypt enforces rate limits). `cloudflared` should log
`Registered tunnel connection` a few times, and `make status` should show five
running containers - with `ticketproject-cloudflared` present and
`ticketproject-mailpit` absent.

> On a 1 GB instance `make build` can be killed by the OOM reaper, because it
> runs Maven + the JDK and npm + `tsc` + Vite. Add a swap file first
> (`sudo ./scripts/ec2/harden-ssh.sh --with-swap 2G` does it), or build the images
> on a bigger machine and bring them over with `docker save` / `docker load`.

### Verifying the lockdown

From a machine that is **not** on your allowlist:

```
# 1. The site is only reachable through the Cloudflare Access login:
curl -sI https://scanmein.online | head -1     # 302 -> <team>.cloudflareaccess.com

# 2. The raw IP address leads nowhere (no inbound 80/443 exists at all):
curl -sS -m 5 -k https://<instance-ip>/        # timed out / connection refused

# 3. SSH accepts only your admin user, from your IP only:
ssh -o BatchMode=yes ubuntu@<instance-ip>      # Permission denied (publickey)
```

From the instance itself, to prove layer 4 independently of the network path - a
request straight to Caddy that carries no Access JWT must be rejected:

```
curl -s -o /dev/null -w '%{http_code}\n' -k \
     --resolve scanmein.online:443:127.0.0.1 https://scanmein.online/api/v1/events
# 401
docker compose logs caddy | tail -5            # "invalid token" / no JWT found
```

After you log in through Access in a browser, the same request carries the
`Cf-Access-Jwt-Assertion` header that Cloudflare signed and succeeds.

## Local Development Setup (without Docker)

1. Clone this Git repository to your local machine.
2. Create a MariaDB database on your local machine.
3. Use the following command to generate three secret encryption
keys in Base64 encoding. They will be used by the application for
encrypting/decrypting sensitive information stored in some database
tables, as well as for computing blind indexes:

```
openssl rand -base64 32
```

4. If the file `src/main/resources/application.properties`
doesn't already exist, create it with the following contents:

```
# Add this line to the top of the file
spring.application.name=webapp

# If you're running this application locally,
# you can set this to http://localhost:8080
app.config.base-url=base_url_to_your_machine
# If you're running this application locally,
# you can set this to http://localhost:5173
app.config.frontend-base-url=base_url_to_your_frontend_machine

# Add your MariaDB database name, username and password
spring.datasource.url=jdbc:mariadb://localhost:3306/your_database_name
spring.datasource.username=your_username
spring.datasource.password=your_password

# MariaDB Driver
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver

# update -> update schema to match entities
# create -> drop all tables and recreate them on every startup
# create-drop -> like create, but also drops on shutdown
# validate -> check if entities match the schema, throw an error if there's a mismatch
# none -> do nothing
spring.jpa.hibernate.ddl-auto=update

# JPA options (feel free to edit as you wish)
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect

# For encrypting and encrypting database fields with sensitive information
app.encryption.key-base64=your_first_base64_encoded_secret_key_goes_here
# For computing blind indexes
app.blind-index.key-base64=your_second_base64_encoded_secret_key_goes_here
# For signing and authenticating JSON Web Tokens.
app.jwt.secret-base64=your_third_base64_encoded_secret_key_goes_here

# SMTP server config used by this application to send out emails to users
spring.mail.host=your_smtp_hostname_goes_here
spring.mail.port=your_smtp_port_goes_here
spring.mail.username=your_smtp_username_goes_here
spring.mail.password=your_smtp_password_goes_here
# Enable/disable SMTP authentication.
# Set this to true or false based on your needs.
spring.mail.properties.mail.smtp.auth=true_or_false
# Enable/disable StartTLS encryption.
# Set this to true or false based on your needs.
spring.mail.properties.mail.smtp.starttls.enable=true_or_false
```

5. Navigate to the `frontend/` directory and run this command
to install the application's frontend dependencies:
```
npm ci
```

6a. To run the application's backend, use this command:
```
./mvnw spring-boot:run
```

6b. To run the application's backend tests, use this command:
```
./mvnw test
```

6c. To run the application's frontend, use this command:
```
npm run dev
```

## Project Dependencies:
For backend dependencies, see `pom.xml`.
For frontend dependencies, see `frontend/package.json`
and `frontend/package-lock.json`.

## API Documentation:
After starting the application, replace the word `server` in any of
the below links with the server name or IP address of the machine
which this application's backend is running on (if you are running this
application locally, then `server` would be replaced with `localhost`),
then navigate to the link of your choice to view the API documentation
in your desired format. 

- Swagger UI: <http://server:8080/swagger-ui/index.html>
- JSON Format: <http://server:8080/v3/api-docs>
- YAML Format: <http://server:8080/v3/api-docs.yaml>
