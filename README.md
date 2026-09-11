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
make build       # builds the backend and frontend images
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
not reveal whether an account exists). This app-level gate works even if a
reverse-proxy or Cloudflare Access layer in front of the site is bypassed, and
it complements (rather than replaces) network-level controls such as an EC2
security group that only exposes ports 22/443.

## Deploying to a Public Server (EC2) with Amazon SES

On a public server you should **not** run Mailpit (its web UI would expose every
email, including password-reset links and ticket QR codes, to anyone who can
reach the port). Instead, the backend sends email through **Amazon SES** using
its SMTP interface. The same Docker image and `compose.yaml` are used for both
deployments, only the environment changes.

### How the two deployments differ

| | Home network | Public server (EC2) |
| --- | --- | --- |
| Start command | `make up` (`docker compose --profile local up -d`) | `make up-aws` (`docker compose up -d`) |
| Mailpit | Started (web UI on `8025`) | **Not started** (no `local` profile) |
| Teardown command | `make down` / `make clean` (`docker compose --profile local down [-v]`) | The **same** command (because Mailpit was never created, the `--profile local` portion contributes nothing to the rest of the command's behavior) |
| Email transport | Mailpit SMTP (`mailpit:1025`, no auth/TLS) | Amazon SES SMTP (`email-smtp.<region>.amazonaws.com:587`, auth + STARTTLS) |
| `MAIL_FROM_ADDRESS` | `noreply@ticketproject.local` (default) | A **verified** SES identity/domain |

Because `SMTP_*` values in `compose.yaml` default to Mailpit, a home-network
deployment needs no extra configuration; an EC2 deployment just overrides them
in `.env`.

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

### Configure `.env` on the EC2 instance

Uncomment/set the SMTP block (added by `scripts/init-secrets.sh`):

```
SMTP_HOST=email-smtp.us-east-1.amazonaws.com
SMTP_PORT=587
SMTP_USERNAME=<SES SMTP username>
SMTP_PASSWORD=<SES SMTP password>
SMTP_AUTH=true
SMTP_STARTTLS=true
MAIL_FROM_ADDRESS=noreply@yourdomain.com
```

Then start the stack **without** Mailpit:

```
make up-aws      # equivalent to: docker compose up -d
```

Confirm Mailpit is absent with `docker compose ps` (there should be no
`ticketproject-mailpit` container). The backend still starts because its
`depends_on: mailpit` is marked `required: false`.

> Also remember to set `FRONTEND_BASE_URL` to your real public HTTPS URL and
> use a publicly-trusted certificate (e.g. Caddy's automatic Let's Encrypt or
> Cloudflare) instead of the mkcert LAN certificate used on the home network.

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
