# Booking Portal

A full-stack photography booking platform that connects customers with photographers for portrait, family, and product sessions. Customers discover listings, book time slots, communicate with photographers via real-time chat, and earn loyalty points. Photographers manage their availability, confirm sessions, and receive structured notifications. Administrators oversee the entire platform through blacklisting, points rule configuration, and notification export.

## Architecture & Tech Stack

* **Frontend:** jQuery 3.7.1 SPA (bundled locally — no CDN), HTML5, CSS3
* **Backend:** Java 17, Spring Boot 3.2.5 (Spring MVC), MyBatis 3.0.3
* **Database:** MySQL 8.0 (production), H2 in-memory (tests)
* **Security:** BCrypt password hashing, AES-256-GCM field encryption (PBKDF2-derived key), session-based auth, blacklist enforcement
* **Containerization:** Docker & Docker Compose (required)

## Project Structure

```text
.
├── src/main/
│   ├── java/com/booking/
│   │   ├── config/         # AppConfig, WebConfig, EncryptionConfig, SecretsValidator, DataInitializer
│   │   ├── controller/     # REST controllers (13 files)
│   │   ├── domain/         # Entity classes, enums, DTOs (19 files)
│   │   ├── filter/         # AuthFilter (session + enabled + blacklist enforcement)
│   │   ├── mapper/         # MyBatis mapper interfaces (17 files)
│   │   ├── service/        # Business logic services (15 files)
│   │   └── util/           # FieldEncryptor, MaskUtil, RoleGuard, SessionUtil
│   └── resources/
│       ├── application.yml # Config (secrets via env vars, no hardcoded values)
│       ├── mapper/         # MyBatis XML (17 files)
│       └── static/         # jQuery SPA (index.html, css/style.css, js/app.js)
├── unit_tests/             # Unit tests (Mockito, no Spring context)
├── api_tests/              # API integration tests (full Spring Boot + H2) and E2E browser tests
├── docker/
│   ├── init-secrets.sh     # Generates all runtime secrets to tmpfs volume (runs once)
│   ├── mysql-entrypoint.sh # Sources secrets, delegates to MySQL entrypoint
│   ├── entrypoint.sh       # Sources secrets, validates, launches Spring Boot
│   └── mysql/              # SQL migration scripts (01-schema.sql … 09-migration-v7.sql)
├── docker-compose.yml      # Multi-container orchestration — MANDATORY
├── Dockerfile              # Production multi-stage build
├── Dockerfile.test         # Test runner container
├── run_tests.sh            # Standardized test execution script — MANDATORY
└── README.md               # Project documentation — MANDATORY
```

## Prerequisites

To ensure a consistent environment, this project is designed to run entirely within containers. You must have the following installed:

* [Docker](https://docs.docker.com/get-docker/) (with the Docker daemon running)
* [Docker Compose](https://docs.docker.com/compose/install/) (`docker-compose` CLI must be available)

No host-installed JDK, Maven, or MySQL is required.

## Running the Application

1. **Build and start the full stack:**
   ```bash
   docker-compose up
   ```
   The stack is fully self-contained. An `init-secrets` container runs first and generates all runtime credentials (MySQL password, encryption key) to a shared `tmpfs` volume — nothing is persisted to the host filesystem.

2. **Access the app:**
   * Web UI: `http://localhost:8080`
   * Health check: `http://localhost:8080/actuator/health`

3. **Stop the application:**
   ```bash
   docker-compose down
   ```

4. **Full reset (regenerates all secrets and clears all data):**
   ```bash
   docker-compose down -v && docker-compose up
   ```

### Verification Flow

Once the stack is up, run the following commands to confirm the application is working end-to-end.

**Step 1 — Health check (no auth required):**
```bash
curl -s http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

**Step 2 — Login and capture the session cookie:**
```bash
curl -s -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"cust1","password":"password123"}'
# Expected: {"id":4,"username":"cust1","roleName":"CUSTOMER", ...}
```

**Step 3 — Fetch loyalty-points balance:**
```bash
curl -s -b cookies.txt http://localhost:8080/api/points/balance \
  -H "X-Requested-With: XMLHttpRequest"
# Expected: {"balance":<number>}
```

**Step 4 — Create an address and verify the returned ID:**
```bash
curl -s -b cookies.txt -X POST http://localhost:8080/api/addresses \
  -H "Content-Type: application/json" \
  -H "X-Requested-With: XMLHttpRequest" \
  -d '{"label":"Curl Test","street":"1 Main St","city":"Los Angeles","state":"CA","postalCode":"90001","country":"US","isDefault":false}'
# Expected: {"id":<N>,"label":"Curl Test","street":"1 Main St", ...}
```

Copy the `id` from the response and substitute it in the next command.

**Step 5 — Retrieve the address by dynamic ID:**
```bash
curl -s -b cookies.txt http://localhost:8080/api/addresses/<N> \
  -H "X-Requested-With: XMLHttpRequest"
# Expected: {"id":<N>,"label":"Curl Test", ...}
```

**Step 6 — UI walkthrough:**
1. Open `http://localhost:8080` in a browser.
2. Log in as `cust1` / `password123` — the dashboard should load with available listings.
3. Click any listing to open the detail view and confirm the booking form is rendered.
4. Navigate to **My Addresses** — the address created in Step 4 should appear with label "Curl Test".
5. Log out; you should be redirected to the login page.

| Service | Container | Lifecycle | Purpose |
|---------|-----------|-----------|---------|
| **Init Secrets** | `booking-init-secrets` | Runs once, then exits | Generates all credentials to `/run/secrets/booking/secrets.env` |
| **MySQL 8.0** | `booking-mysql` | Long-running | Persistent data store (port **3306**) |
| **Web Application** | `booking-webapp` | Long-running | Spring Boot app + jQuery SPA (port **8080**) |

## Testing

All unit and API integration tests are executed through a single standardized shell script. Tests run inside a Maven container with an H2 in-memory database — no MySQL or host Java installation is needed.

Make the script executable, then run it:

```bash
chmod +x run_tests.sh
./run_tests.sh              # Full suite (unit + API + E2E), enforces ≥90% line coverage
./run_tests.sh unit         # Unit tests only
./run_tests.sh api          # API + E2E tests (also runs unit phase first)
```

The script exits `0` on success and `1` on failure, making it suitable for CI/CD pipelines.

### Test Layers

| Layer | Tool | Scope |
|-------|------|-------|
| **Unit** | JUnit 5 + Mockito | Pure logic, no Spring context — services, guards, utilities |
| **API Integration (MockMvc)** | Spring Boot Test + MockMvc | Full HTTP stack, H2 database, all controller endpoints, deep invariants, boundary conditions |
| **API Integration (Over-the-wire)** | Spring Boot Test + RestTemplate | Real TCP/HTTP transport — cookies, CSRF headers, status lines, redirects |
| **E2E Browser** | HtmlUnit 4.x + jQuery SPA | Real embedded server, full JavaScript execution, DOM assertions + backend API verification |

All API integration tests are fully order-independent: every test creates its own time slots and orders with UUID-based idempotency keys.

### Coverage Gate

JaCoCo enforces **≥ 90% line coverage** on the merged (unit + API) execution data. Reports are written to `test-results/jacoco-unit/`, `test-results/jacoco-api/`, and `test-results/jacoco-merged/` after each run.

## Seeded Credentials

The database is pre-seeded with the following accounts on first startup. All passwords are **`password123`**.

| Role | Username | Email | Notes |
| :--- | :--- | :--- | :--- |
| **ADMINISTRATOR** | `admin` | `admin@bookingportal.com` | Full system access: blacklist, points rules, notifications export, user management |
| **PHOTOGRAPHER** | `photo1` | `photo1@bookingportal.com` | Owns "Studio Portrait" and "Product Shots" listings; manages own orders |
| **PHOTOGRAPHER** | `photo2` | `photo2@bookingportal.com` | Owns "Outdoor Family" listing; used for cross-provider access-control tests |
| **CUSTOMER** | `cust1` | `cust1@bookingportal.com` | Has two seeded addresses (Home, Work); creates and manages bookings |
| **CUSTOMER** | `cust2` | `cust2@bookingportal.com` | Second customer for isolation and authorization testing |

> **WARNING: These are demo credentials for local development only. Never deploy to production with these users or this password. In production, rotate all secrets, disable seed accounts, and set `ENCRYPTION_KEY` to a cryptographically random value of at least 32 characters — the application enforces this at startup and will refuse to boot with a shorter key.**
