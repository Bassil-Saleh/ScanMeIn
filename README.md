# ScanMeIn

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
| Swagger API docs | Not exposed through Caddy any more - run the backend directly and open `http://localhost:8080/swagger-ui/index.html` (see *"Local Development Setup"*) |

Manage the stack with `make status`, `make logs`, `make down`.
Run `make help` to list all targets.

> Secrets live in the gitignored `.env` file. Delete it and re-run
> `make bootstrap` only if you want fresh secrets (this does NOT delete
> database data; use `make clean` for that).

### Restricting who may use the site with an email allowlist

This stack is deployed as a **public site**: anyone who can reach it can load
the SPA and browse the pages of published events. There is no Cloudflare Access
application in front of it and `.env` says `CF_ACCESS_MODE=off` (see *"Locking
the site down"*), so the application-level email allowlist is the **only** thing
that decides who may hold an account in it.

Set the `ALLOWED_EMAIL_DOMAINS` value in `.env` to a comma-separated list of the
email addresses and/or domains that are allowed to use the site:

```
# Only these people can register, log in, register for events,
# receive invitations, or receive any email from the app.
ALLOWED_EMAIL_DOMAINS=you@yourdomain.com,yourdomain.com,friend@gmail.com
```

- An entry containing `@` (e.g. `you@yourdomain.com`) must match exactly.
- A bare domain (e.g. `yourdomain.com`) matches any address at that domain and
  its subdomains. Matching is case-insensitive.

> **This is the only gate left.** An empty or blank `ALLOWED_EMAIL_DOMAINS`
> disables the allowlist - `AccessControlService` treats empty as "no
> restriction" - which on a public server means **anyone on the internet can
> register**. Check it before and after any deployment:
>
> ```
> grep '^ALLOWED_EMAIL_DOMAINS=' .env
> docker compose exec backend printenv ALLOWED_EMAIL_DOMAINS   # must print the list
> ```

Apply a change with `make up-aws`. The value is read once at startup by
`AccessControlService`, so the **backend** container is the one that has to be
recreated; `make up-aws` does exactly that and restarts only the container whose
environment changed. Adding or removing a person is therefore `.env` plus
`make up-aws` - no dashboard change, no image rebuild.

Where it is enforced:

| Surface | Result for an address that is not on the list |
| --- | --- |
| Account registration (`EventHostService`) | `403 "Access is restricted to approved email addresses."` |
| Login (`SessionService`) | the same generic `401 "Invalid email address or password"` as a wrong password, so an account's existence is not revealed |
| Password reset (`PasswordResetTokenService`) | the same generic success message as a real request, and nothing is sent |
| Changing the account's email (`EventHostService`) | `403` |
| Event registration and invitations (`AttendeeService`) | `403` |
| Every outbound email (`EmailService`) | the send is refused |

The sign-up form renders the `403` inline (`SignUpPage.tsx` displays
`data.message`), so a disallowed visitor gets an explanation instead of a silent
failure.

What the allowlist does **not** do is hide the site. The JavaScript bundle, the
pages of published events (`GET /api/v1/events/{publicId}` is explicitly
unauthenticated), account verification, and the registration / login /
password-reset endpoints are all reachable by anyone - they simply refuse to
complete an action for a non-allowlisted address. Treat everything the frontend
ships as public information. The network layers that decide *whether traffic
reaches this stack at all* are unchanged, and are described in the next section.

## Deploying to a Public Server (EC2) with Amazon SES + Cloudflare Tunnel

On a public server you should **not** run Mailpit (its web UI would expose every
email, including password-reset links and ticket QR codes, to anyone who can
reach the port). Instead, the backend sends email through **Amazon SES** using
its SMTP interface. The same Docker images, `compose.yaml` and `Caddyfile` are
used for both deployments - only the environment changes.

A public deployment also differs in how traffic reaches it: its **only** ingress
is a **Cloudflare Tunnel** connector that dials *out* to Cloudflare, so the site
cannot be reached by its raw IP address (there is no inbound port to reach) and
the hostname never has to appear in DNS as an A record. The site itself is
**public** - it is not behind Cloudflare Access - and the only thing deciding
who may hold an account is the application-level email allowlist
(`ALLOWED_EMAIL_DOMAINS`, see above). Cloudflare Access and the origin-side JWT
check (`CF_ACCESS_MODE=on`) are still in this repository and can be switched
back on at any time: see *"Locking the site down"* below.

### How the two deployments differ

| | Home network | Public server (EC2) |
| --- | --- | --- |
| Start command | `make up` (`TLS_MODE=local docker compose --profile local up -d`) | `make up-aws` (`TLS_MODE=public docker compose --profile tunnel up -d`) |
| Compose profiles | `local` (Mailpit) | `tunnel` (cloudflared) |
| `TLS_MODE` | `local` | `public` |
| Certificate (Caddy) | mkcert LAN cert from `certs/` | Let's Encrypt via Cloudflare DNS-01 |
| Ingress | Devices connect straight to Caddy on the LAN | **Cloudflare Tunnel only** - the `cloudflared` container dials out; no inbound 80/443 |
| `CADDY_BIND_ADDR` | `0.0.0.0` (reachable on the LAN) | `127.0.0.1` (loopback only; the tunnel reaches Caddy on the internal Docker network) |
| Who may use the site | Anyone on your network (`ALLOWED_EMAIL_DOMAINS` stays empty unless you set it) | Anyone may browse; only the emails in `ALLOWED_EMAIL_DOMAINS` may register, log in, or use account features (`CF_ACCESS_MODE=off`; Access is optional) |
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
   a 1 GB instance works but needs a swap file (see the note under *Build and
   start*), since `make build` runs Maven + the JDK and npm + `tsc` + Vite. Give
   it 20-30 GB of gp3 storage (Docker images + the MariaDB volume). If you
   choose an ARM instance (`t4g.*`), everything still works; just build the
   images on an ARM host or with `docker buildx --platform linux/arm64`.
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
5. **First login, then secure the instance.** Connect as the stock `ubuntu` user.
   Cloud images differ in how sshd and their firewall are configured, so harden
   the instance yourself - your provider's documentation is the authority here,
   not a script in this repository. At a minimum: key-only logins (no password,
   no keyboard-interactive), no root login, and SSH reachable from your own IP
   only; consider fail2ban and automatic security updates too. **Keep this
   session open and confirm that a login from a second terminal works before you
   close it** - a mistake here can lock you out of the instance. Then install
   Docker:
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
3. **Zero Trust team domain** (only if you want Cloudflare Access). In the Zero
   Trust dashboard note your team domain (`<team>.cloudflareaccess.com`). It is
   both the *issuer* of the Access JWTs and the host of the public keys Caddy
   verifies them against.
4. **Cloudflare Tunnel.** Zero Trust → Networks → Tunnels → *Create a tunnel*
   (Cloudflared) and copy the **token** - it becomes `TUNNEL_TOKEN` in `.env`.
   Under *Public hostname*, add `scanmein.online` with:
   - Service type **HTTPS**, URL **`caddy:443`** (`caddy` is the compose service
     name; the connector reaches it over the internal Docker network), and
   - *Additional application settings* → **Origin Server Name** *and* **HTTP Host
     Header** = `scanmein.online`.
   Setting the Origin Server Name makes `cloudflared` **verify** Caddy's real
   Let's Encrypt certificate on that hop, so do not enable "No TLS Verify".
   > **One connector, always.** Run the connector ONLY as the `cloudflared`
   > compose service. Do not also install it on the host
   > (`cloudflared service install <token>`) and do not reuse this token on
   > another machine or instance: the edge load-balances every request across
   > *all* replicas of a tunnel, and a replica that cannot reach `caddy:443`
   > answers **502 Bad Gateway** for its share of the traffic while the dashboard
   > still reports it as *Healthy*. `make up-aws` refuses to start while a
   > host-level connector is present - see *"Troubleshooting: Bad Gateway (502) -
   > a duplicate tunnel connector"* below.
5. **(Optional) Cloudflare Access application.** Skip this for the public site
   described in this README - there the app-level email allowlist is the gate,
   and *"Locking the site down"* explains how to add Access later. To add it
   now: Zero Trust → Access → Applications → *Add* → *Self-hosted*:
   - Public hostname `scanmein.online`; set the session duration to about a week
     so an expired session does not interrupt the SPA mid-use.
   - Exactly one policy: **Allow** → Include → **Emails** = your allowlist (or
     *Emails ending in* a domain). Delete or replace any default "allow everyone"
     policy - whatever no policy matches is denied.
   - Login method: **One-time PIN** is the simplest way to gate on a plain list of
     email addresses.
   - Copy the **Application Audience (AUD)** tag from the application overview -
     it becomes `CF_ACCESS_AUD` in `.env`.
6. **(Optional) Service token for scripts** - only needed with Access. Access →
   Service Auth → *Create Service Token*, then add a second policy to the
   application with the action **Service Auth** that includes it. Non-browser
   clients cannot complete an interactive login, so they authenticate with
   headers instead - which is what the `curl` helpers in `api-scripts/` need
   once the site is behind Access:
   ```
   curl -H "CF-Access-Client-Id: <id>" -H "CF-Access-Client-Secret: <secret>" \
        https://scanmein.online/api/v1/events
   ```
7. **SSL/TLS mode** → **Full (strict)**, so Cloudflare validates the origin's
   Let's Encrypt certificate instead of accepting anything.

### Locking the site down: what is public, and what is not

This stack is deployed as a public site, so two independent questions have to be
kept apart:

- **Can a request reach the containers at all?** Answered by the network layers.
  They are still locked down, and nothing in this section changes them.
- **Who may hold an account?** Answered by the app-level email allowlist. On a
  public deployment it is the only gate, so it is the thing to get right.

| # | Layer | Where | What it decides | Configured by |
| --- | --- | --- | --- | --- |
| 1 | Security group | AWS | Any TCP connection to the instance except SSH from your IP. There is no inbound 80/443 rule, so the raw IP address leads nowhere. | EC2 console |
| 2 | Cloudflare Tunnel | EC2 → Cloudflare | The only route in: the *outbound* connection the `cloudflared` container holds open, with Caddy's ports bound to loopback (`CADDY_BIND_ADDR=127.0.0.1`). | `TUNNEL_TOKEN`, `tunnel` profile |
| 3 | Cloudflare edge | Cloudflare | Abuse, not identity: WAF and rate-limiting rules, **if you add them**. None ship with this repository, so treat this as recommended hardening rather than an existing layer. | Cloudflare dashboard |
| 4 | App email allowlist | Spring Boot | **Who may hold an account**: register, log in, be invited, receive email. | `ALLOWED_EMAIL_DOMAINS` |

An earlier revision of this table listed **Cloudflare Access** (at the edge) and
**Access JWT validation** (at the origin, `CF_ACCESS_MODE=on` with
`caddy/access-on.caddy`) as two further layers. Both are **retired for this
deployment**: the site is public by design and `.env` says `CF_ACCESS_MODE=off`.
The machinery is still in the repository, and the procedure to bring it back is
at the end of this section.

#### What is public

- The SPA and its JavaScript bundle. Anyone who can load the site can read every
  byte of the frontend, so no secret may ever be built into it.
- The pages of published events: `GET /api/v1/events/{publicId}` is explicitly
  unauthenticated (`EventController`), while events still in `DRAFT` remain
  visible only to the host who created them.
- The account endpoints - registration (`POST /api/v1/event-hosts`), account
  verification (`GET /api/v1/event-hosts/verification`), login
  (`POST /api/v1/sessions/login`), password reset
  (`POST` and `PATCH /api/v1/password-reset-tokens`) and public event
  registration (`POST /api/v1/attendees/registration`). They are reachable, but
  they answer `403` (registration, invitations, email changes) or a generic
  `401` (login, password reset) for an address that is not on
  `ALLOWED_EMAIL_DOMAINS`.

#### What is not public

- The origin by its IP address, or by any route that does not arrive through the
  Cloudflare Tunnel: there is no inbound port to reach, and Caddy's ports are
  bound to loopback.
- The API documentation. `Caddyfile` answers `404` for `/swagger-ui/*` and
  `/v3/api-docs*`; the backend container still serves them, but there is no
  public route to them. Read the docs by running the backend locally (see
  *"Local Development Setup"*).
- Everything that requires an account, and every email the app sends - the
  allowlist is enforced on recipients too.

#### Optional: putting Cloudflare Access back in front of the origin

1. Zero Trust → Access → Applications → *Add* → *Self-hosted*, hostname
   `scanmein.online`, exactly one policy: **Allow** → Include → **Emails** = your
   allowlist (or *Emails ending in* a domain), login method **One-time PIN**.
   Copy the application's **AUD** tag.
2. Put `CF_ACCESS_MODE=on` and `CF_ACCESS_AUD=<the AUD tag>` in `.env` (and
   `CF_ACCESS_TEAM_DOMAIN`, if it is no longer there), then `make up-aws`.
3. **Do it in that order.** Creating the application first is what makes the
   switch seamless; if Caddy starts demanding a token before the application
   that issues it exists, every request is a `401`.

Once it is on, `caddy/access-on.caddy` verifies each request's
`Cf-Access-Jwt-Assertion` header (or `CF_Authorization` cookie) against your
team's published keys at
`https://<team>.cloudflareaccess.com/cdn-cgi/access/certs`, using the `jwtauth`
directive from [`ggicci/caddy-jwt`](https://github.com/ggicci/caddy-jwt)
compiled into the custom image by `caddy.Dockerfile`. Two details worth knowing:

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
> fetch error. Do not leave `CF_ACCESS_MODE=on` unless an Access application is
> actually in front of the site; `CF_ACCESS_MODE=off` with no application is the
> supported public configuration.

#### Going public again

Put `CF_ACCESS_MODE=off` in `.env` and run `make up-aws`, then delete the Access
application in the dashboard - or replace its policy with a **Bypass** policy
that includes *Everyone*, which keeps the application and its AUD intact and
makes the next switch a single-line change. Leaving `CF_ACCESS_TEAM_DOMAIN` and
`CF_ACCESS_AUD` in `.env` costs nothing while the mode is `off`. Because the
certificate is obtained via DNS-01 and stored on the origin, none of this
touches TLS or DNS.

> If you ever prefer the classic design - a public A record with 443 open
> instead of a tunnel - the network layers change and the origin is no longer
> hidden, so you must restrict `443/tcp` to Cloudflare's published ranges
> (`curl https://api.cloudflare.com/client/v4/ips`, and re-check it
> occasionally), set `CADDY_BIND_ADDR=0.0.0.0`, and either re-enable Cloudflare
> Access (above) or add a WAF rule - otherwise anyone who learns the IP address
> reaches the site directly.

### Generating `.env` on the EC2 instance

Run this **on the instance**, from the repository root. `scripts/init-secrets.sh`
detects that it is running on EC2 and therefore refuses to use `hostname -f`
(which there yields an internal name such as
`ip-172-31-8-42.us-east-2.compute.internal`), defaults to `TLS_MODE=public` and
`CADDY_BIND_ADDR=127.0.0.1`, and generates fresh database and application
secrets. It still **defaults to `CF_ACCESS_MODE=on`**, because that is the safer
default for a brand-new deployment - with no Access application and no origin
check, registration is open to anyone who finds the hostname. For the public
site documented here, pass `--cf-access-mode off`:

```
./scripts/init-secrets.sh \
    --site-host scanmein.online \
    --acme-email dev@scanmein.online \
    --cloudflare-api-token <zone-read + dns-edit token> \
    --tunnel-token <cloudflared token> \
    --cf-access-mode off \
    --allowed-emails you@example.com,friend@example.com \
    --mail-from noreply@scanmein.online \
    --smtp-host email-smtp.<region>.amazonaws.com \
    --smtp-username <SES SMTP username> \
    --smtp-password <SES SMTP password>
```

Pass `--cf-access-mode on`, `--cf-access-team-domain <team>.cloudflareaccess.com`
and `--cf-access-aud <application AUD tag>` instead if you want Cloudflare Access
in front of the site.

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
CF_ACCESS_MODE=off
# Only read while CF_ACCESS_MODE=on. Kept so Access can be switched back on
# without regenerating this file.
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

> `make build` only builds images; it does not restart the stack. Until the
> stack is recreated the old containers keep serving the old build, so run the
> two targets back to back (`make build && make up-aws` here, `make build &&
> make up` on the home-network deployment). A browser that reaches the site
> during that gap can cache an `index.html` that names bundle files the new
> image no longer has, which shows up as a blank page that a rebuild alone will
> not clear - see **Troubleshooting: If the page is blank after a rebuild
> (cached assets)** below.

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
> runs Maven + the JDK and npm + `tsc` + Vite. Add a swap file first, or build
> the images on a bigger machine and bring them over with `docker save` /
> `docker load`.

Before the first `make build` on a small instance (skip this if `swapon --show`
already lists swap), create one:

```
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Verifying the deployment

From a machine that is **not** on your allowlist:

```
# 1. The site is public: no Cloudflare Access login redirect.
curl -sI https://scanmein.online | head -1     # 200 (NOT 302 -> <team>.cloudflareaccess.com)

# 2. The API documentation is not published:
curl -s -o /dev/null -w '%{http_code}\n' https://scanmein.online/swagger-ui/index.html   # 404
curl -s -o /dev/null -w '%{http_code}\n' https://scanmein.online/v3/api-docs             # 404

# 3. The app-level allowlist - not the network - is what stops strangers:
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://scanmein.online/api/v1/event-hosts \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Eve","lastName":"Tester","email":"eve@not-on-the-list.com",
       "password":"a-very-strong-passphrase-42","dateOfBirth":"1995-01-01"}'            # 403

# 4. The raw IP address leads nowhere (no inbound 80/443 exists at all):
curl -sS -m 5 -k https://<instance-ip>/        # timed out / connection refused

# 5. SSH refuses everything but a public key (use whichever user you hardened):
ssh -o BatchMode=yes ubuntu@<instance-ip>      # Permission denied (publickey)
```

From the instance itself, confirm that the allowlist actually reached the running
backend, and that the origin no longer demands an Access JWT:

```
docker compose exec backend printenv ALLOWED_EMAIL_DOMAINS    # must print the list

curl -s -o /dev/null -w '%{http_code}\n' -k \
     --resolve scanmein.online:443:127.0.0.1 https://scanmein.online/api/v1/events
# anything EXCEPT 401 - a 401 here means Caddy still expects an Access JWT
```

Finally, register a throwaway **allowlisted** address in the browser and confirm
that the verification email actually arrives (see the Amazon SES note above).

## Troubleshooting: Bad Gateway (502) - a duplicate tunnel connector

**Symptom.** Requests through Cloudflare fail *intermittently* with a
`502 Bad Gateway` page: some load the site, others do not, and a retry may
succeed. `make status` shows all five containers running, and - the giveaway -
`docker compose logs caddy frontend backend` show **no request at all** for the
times that failed, and no error anywhere. The tunnel and its connectors are
reported as *Healthy* in the Zero Trust dashboard.

**Cause.** More than one connector is attached to the same tunnel. A token-based
connector takes its ingress rules from the tunnel's *remote* configuration, which
here is `{"hostname":"scanmein.online","service":"https://caddy:443"}`. `caddy` is
a Docker Compose service name resolved by Docker's embedded DNS, and that DNS only
exists *inside* the `ticketnet` network - so a connector running on the host can
never reach it:

```
ERR Unable to reach the origin service. The service may be down or it may not be
    responding to traffic from cloudflared: dial tcp: lookup caddy on
    127.0.0.53:53: server misbehaving
```

The edge load-balances every request across **all** replicas of a tunnel, so about
half of them land on that broken replica and fail while the rest are served
normally. Cloudflare's wording for this case: *"the tunnel itself is connected to
the Cloudflare network, but cloudflared cannot reach the origin service defined in
your ingress rule."*

**Why the dashboard does not tell you.** A connector's *Healthy* / *Connected*
status only means its connections to Cloudflare's edge are up - it says nothing
about whether that connector can reach the origin, so a replica that 502s every
request it receives still looks healthy. For contrast, error **1033** ("Argo
Tunnel error") means the edge found *no* healthy connector at all, i.e. the tunnel
is down, whereas **502** means a connector accepted the request and could not
deliver it.

**Diagnose.** On the instance:

```
systemctl status cloudflared --no-pager | head -5   # a HOST-level connector?
ps -eo pid,etime,cmd | grep -i '[c]loudflared'      # /usr/bin/cloudflared + long uptime = host process
sudo journalctl -u cloudflared --since '-3 hours' | tail -20   # the "Unable to reach the origin service" lines
docker compose logs cloudflared | grep 'Generated Connector ID'  # the only ID the dashboard should list
```

Then compare that ID with the connector list in Zero Trust → Networks → Tunnels.
The tells that an entry is *not* your container:

| Field | The compose container | A host-level connector |
| --- | --- | --- |
| Hostname | a 12-hex container id (`f0cc0637b5a8`) | the instance's private DNS name (`ip-172-31-79-182`) |
| Version | exactly the tag pinned in `compose.yaml` (`2026.9.1`) | whatever the distro package updated itself to (`2026.9.3`) |
| Age | since the last `make up-aws` | as old as the manual install |

**Fix.** Remove the extra connector so the compose service is the only one:

```
sudo cloudflared service uninstall      # removes the systemd unit
sudo apt-get remove -y cloudflared      # removes the host binary
sudo rm -rf /etc/cloudflared            # removes config.yml and the token file
systemctl is-active cloudflared         # -> inactive
```

The replica then disappears from the dashboard on its own; if a *Disconnected*
entry lingers, delete it there (the container regenerates its connector ID on
every restart, so stale entries are normal). Verify that every request now reaches
the origin, with an Access service token or the `CF_AppSession` cookie from a
logged-in browser:

```
for i in $(seq 1 12); do curl -s -o /dev/null -w '%{http_code} ' \
  -H "CF-Access-Client-Id: <id>" -H "CF-Access-Client-Secret: <secret>" \
  https://scanmein.online/; done; echo
# before: a mix such as 200 502 502 200 502 ...    after: 200 200 200 200 ...
```

Each of those requests must now appear in `docker compose logs caddy` (the access
log configured in the `Caddyfile`, with token-carrying query parameters redacted)
and in `docker compose logs frontend`.

**Prevent it.** `make up-aws` refuses to start while a host-level `cloudflared`
service, its token file, a host-level `cloudflared` process, or a second
`cloudflared` container is present. If a 502 ever returns with only one connector
in the dashboard, set `CF_LOGLEVEL=debug` in `.env`, run `make up-aws`, reproduce
the failure and read `docker compose logs cloudflared` - then set it back to
`info`. Container logs are bounded (`compose.yaml`: 10 MB x 3 per service), so
verbose logging cannot fill the volume.

## Troubleshooting: If the page is blank after a rebuild (cached assets)

**Symptom.** The site loads through Cloudflare (the certificate is fine
and `make status` shows five running containers), but the page stays blank and
the browser console reports:

```
Loading module from “https://scanmein.online/assets/index-<hash>.js” was blocked
because of a disallowed MIME type (“text/html”).
```

Errors about `static.cloudflareinsights.com/beacon.min.js` (a CORS failure or a
`sha512` integrity mismatch) come from Cloudflare Web Analytics and are not the
cause.

**Cause.** Vite names each built file after a hash of its contents, and
`frontend/nginx.conf` falls back to `index.html` for unknown paths so that
client-side routes resolve (`try_files $uri $uri/ /index.html`). If a hashed
asset is requested while it is not present in the frontend container - the
browser still holds a pre-rebuild `index.html`, or the request arrives while the
stack is being recreated - nginx answers with the SPA shell, i.e. a `200`
`text/html` response for a `.js` URL. Being a cacheable success, that response
gets stored *under the asset URL*: Cloudflare applies its 4-hour default TTL to
`.js` and `.css` because the origin sends no `Cache-Control`, and the browser
caches it as well. The module can then never execute, and because a redeploy that
does not change the frontend source produces the *same* hashed filenames,
rebuilding does not help.

**Fix it now.**

1. Purge the Cloudflare cache: dashboard → **Caching → Configuration → Purge
   Everything**.
2. Force a fresh fetch in the browser (a hard reload, or open the site in a
   private window) so the cached copy is replaced.

**Prevent it.** `frontend/nginx.conf` states the policy explicitly: hashed files
under `/assets/` are `immutable`, `index.html` is `no-cache`, and a missing asset
is a `404` instead of a cacheable `text/html` page. Cloudflare must also be told
to respect those headers - **Caching → Configuration → Browser Cache TTL →
Respect Existing Headers**. No cache rule needs to cache HTML; Vite's hashes
already make caching the assets themselves safe.

**Verify.** From the instance, a missing asset must be a `404` rather than a
`200 text/html` page, and the shell must carry an explicit cache policy:

```
docker exec ticketproject-frontend wget -S -O /dev/null http://127.0.0.1/assets/index-DOESNOTEXIST.js 2>&1 \
  | grep -iE 'HTTP/|Content-Type'

docker exec ticketproject-frontend wget -S -O /dev/null http://127.0.0.1/ 2>&1 \
  | grep -iE 'HTTP/|Content-Type|Cache-Control'
```

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

# Minimum zxcvbn strength score (0-4) a new password must reach before this
# application accepts it. Enforced when creating an account, resetting a
# password, and changing a password.
app.password.min-strength-score=3

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

> **Password strength policy.** Every password this application accepts is first
> scored from `0` (very weak) to `4` (very strong) by
> [zxcvbn](https://github.com/nulab/zxcvbn4j). A password whose score is below
> `app.password.min-strength-score` (default `3`) is rejected with a `400`
> response when creating an account (`POST /api/v1/event-hosts`), resetting a
> password (`PATCH /api/v1/password-reset-tokens`), or changing a password
> (`PATCH /api/v1/event-hosts/password`). The `12..128` character length
> requirement still applies. In the `docker` profile the same value is read from
> the `PASSWORD_MIN_STRENGTH_SCORE` environment variable; a value outside
> `0..4` prevents the backend from starting.

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
