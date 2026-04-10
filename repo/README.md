# Booking Portal

Offline-first photography booking platform. Java 17 / Spring MVC / MyBatis / MySQL / jQuery.

## One-Command Startup

```bash
docker compose up
```

The stack is fully self-contained. No `.env` files, no host-installed JDK, no external APIs. All credentials and encryption keys are generated at runtime by the container entrypoint script (`docker/entrypoint.sh`) using `/dev/urandom` — nothing is persisted to the host filesystem.

| Service | Container | Port | Purpose |
|---------|-----------|------|---------|
| **Web Application** | `booking-webapp` | **8080** | Spring Boot app, jQuery SPA |
| **MySQL 8.0** | `booking-mysql` | **3306** | Persistent data store |

Open **http://localhost:8080** after startup.

## Initialization Behavior

On first `docker compose up`:

1. The entrypoint script (`docker/entrypoint.sh`) generates all runtime secrets via `/dev/urandom`:
   - `MYSQL_ROOT_PASSWORD` — 24 random bytes, hex-encoded (48 chars)
   - `MYSQL_PASSWORD` — 24 random bytes, hex-encoded (48 chars)
   - `ENCRYPTION_KEY` — 32 random bytes, hex-encoded (64 chars, exceeds the 32-character minimum)
2. MySQL container starts and runs init scripts in order:
   - `01-schema.sql` — tables, indexes, foreign keys
   - `02-seed.sql` — roles, users (with placeholder passwords), services
   - `03-migration-v2.sql` — orders, listings, time slots, messaging, blacklist, points
   - `04-seed-v2.sql` — sample listings, time slots, addresses
   - `05-migration-v3.sql` — notification preferences, chat attachments, points rules
   - `06-migration-v4.sql` — delivery modes, listing metadata
   - `07-migration-v5.sql` — structured location hierarchy
   - `08-migration-v6.sql` — search terms store, address default-uniqueness triggers
3. MySQL health check (`mysqladmin ping`) must pass before webapp starts.
4. Webapp starts and `DataInitializer` replaces all `$PLACEHOLDER$` password hashes with real BCrypt hashes for `password123`.
5. `EncryptionConfig` calls `FieldEncryptor.configure(ENCRYPTION_KEY)`. **The application will refuse to start if ENCRYPTION_KEY is shorter than 32 characters.**
6. Scheduled tasks begin: auto-close unpaid orders (2 min), auto-lift expired blacklists (5 min), notification retry (3 min), clean idempotency tokens (15 min).
7. Webapp health check (`/actuator/health`) confirms MySQL connectivity.

To reset all data: `docker compose down -v && docker compose up`

## Default Test Users

All passwords are **`password123`**.

> **WARNING: These are demo credentials for local development only. Never deploy to production with these users or passwords. In production, rotate all secrets, disable seed accounts, and set `ENCRYPTION_KEY` to a cryptographically random value of at least 32 characters. The application enforces this minimum at startup and will fail to boot with a shorter key.**

| Username | Role | ID | Purpose |
|----------|------|----|---------|
| `admin` | ADMINISTRATOR | 1 | Full system access, blacklist, points rules |
| `jphoto` | PHOTOGRAPHER | 2 | Owns listings 1, 2, 5; manages own orders |
| `sphoto` | PHOTOGRAPHER | 3 | Owns listings 3, 4; separate from jphoto's data |
| `customer1` | CUSTOMER | 4 | Has addresses, creates orders |
| `customer2` | CUSTOMER | 5 | Second customer for access-control testing |

## Running Tests

```bash
./run_tests.sh          # Full suite (unit + API), 90% coverage gate
./run_tests.sh unit     # Unit tests only
./run_tests.sh api      # Both suites (API requires unit phase)
```

Only requires **Docker**. Tests run inside a Maven container with an H2 in-memory database — no MySQL needed.

### Test Layout

```
unit_tests/             # ~284 unit tests (Mockito, no Spring context)
  java/com/booking/unit/   32 test classes
  resources/               H2 schema + seed data

api_tests/              # ~163 integration tests (full Spring Boot + H2)
  java/com/booking/api/    14 test classes (including base class)
  resources/               application-test.yml, schema, seed data
```

Tests **must** live in `unit_tests/` or `api_tests/`. The Maven enforcer plugin fails the build if any test file appears under `src/test/java`.

### Coverage Gate

JaCoCo enforces **>= 90% line coverage** on the merged (unit + API) execution data. Legacy classes behind retired 410 endpoints (e.g. `AttachmentService`, `BookingService`) are excluded from the coverage check. Coverage reports are written to `test-results/` after each run.

## Verification Steps

### Login

```bash
# Admin login
curl -s -c /tmp/c.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"password123"}'
# Returns: {"id":1,"username":"admin","role":"ADMINISTRATOR",...}

# Customer login
curl -s -c /tmp/c.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer1","password":"password123"}'
```

### Search Listings

```bash
# All active listings
curl -s -b /tmp/c.txt 'http://localhost:8080/api/listings/search'

# Filtered search (records "portrait" as a server-side popular search term)
curl -s -b /tmp/c.txt 'http://localhost:8080/api/listings/search?keyword=portrait&category=PORTRAIT&minPrice=100&maxPrice=200&location=Downtown'

# Server-side search suggestions (ranked by global frequency and recency)
curl -s -b /tmp/c.txt 'http://localhost:8080/api/listings/search/suggestions?limit=15'

# Available time slots for listing 1, next 30 days
curl -s -b /tmp/c.txt 'http://localhost:8080/api/timeslots/listing/1/available?start=2026-05-01&end=2026-06-30'
```

### Booking (Order Lifecycle)

```bash
# Create order (customer)
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: my-unique-key-1' \
  -d '{"listingId":1,"timeSlotId":1,"addressId":1,"notes":"Headshots"}'
# Returns: {"id":1,"orderNumber":"ORD-XXXX","status":"CREATED","paymentDeadline":"..."}

# Confirm (photographer)
curl -s -b /tmp/photo.txt -X POST http://localhost:8080/api/orders/1/confirm \
  -H 'Idempotency-Key: confirm-1'

# Record payment (customer)
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/orders/1/pay \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: pay-1' \
  -d '{"amount":150.00,"paymentReference":"PAY-REF-123"}'

# Check-in / Check-out / Complete (photographer)
curl -s -b /tmp/photo.txt -X POST http://localhost:8080/api/orders/1/check-in \
  -H 'Idempotency-Key: ci-1'
curl -s -b /tmp/photo.txt -X POST http://localhost:8080/api/orders/1/check-out \
  -H 'Idempotency-Key: co-1'
curl -s -b /tmp/photo.txt -X POST http://localhost:8080/api/orders/1/complete \
  -H 'Idempotency-Key: complete-1'

# Cancel with auto-refund
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/orders/1/cancel \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: cancel-1' \
  -d '{"reason":"Changed my mind"}'

# Reschedule
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/orders/1/reschedule \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: resched-1' \
  -d '{"newTimeSlotId":5}'

# Audit trail
curl -s -b /tmp/c.txt http://localhost:8080/api/orders/1/audit
```

### Real-Time Messaging (SSE)

```bash
# Send message (order-scoped)
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/messages/send \
  -H 'Content-Type: application/json' \
  -d '{"recipientId":2,"content":"Hello!","orderId":1}'

# List conversations (includes unread count)
curl -s -b /tmp/c.txt http://localhost:8080/api/messages/conversations

# Read messages (marks as read)
curl -s -b /tmp/c.txt http://localhost:8080/api/messages/conversations/1

# Upload image (JPEG/PNG only, max 5 MB)
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/messages/conversations/1/image \
  -F "file=@photo.jpg"

# SSE stream for real-time push notifications (replaces legacy 5-second polling)
curl -s -b /tmp/c.txt -N http://localhost:8080/api/messages/stream
# Emits: event: new-message\ndata: {"conversationId":1,"messageId":42,...}
```

### Notifications

```bash
# List notifications (recipients are masked)
curl -s -b /tmp/c.txt http://localhost:8080/api/notifications
# Returns: [{"channel":"EMAIL","recipient":"al***@example.com",...}]

# Get preferences
curl -s -b /tmp/c.txt http://localhost:8080/api/notifications/preferences

# Update preferences (compliance is always forced true)
curl -s -b /tmp/c.txt -X PUT http://localhost:8080/api/notifications/preferences \
  -H 'Content-Type: application/json' \
  -d '{"orderUpdates":true,"holds":false,"reminders":false,"approvals":true,"compliance":false,"muteNonCritical":true}'
# compliance returns true regardless of input
```

### Blacklist Automation

```bash
# Blacklist a user (admin only, default 7 days)
curl -s -b /tmp/admin.txt -X POST http://localhost:8080/api/blacklist \
  -H 'Content-Type: application/json' \
  -d '{"userId":5,"reason":"Repeated no-shows","durationDays":3}'
# Returns: {"expiresAt":"2026-04-12T...","active":true}

# Blacklisted user gets 403 on all API calls (enforced by AuthFilter)

# Manually lift
curl -s -b /tmp/admin.txt -X POST http://localhost:8080/api/blacklist/1/lift \
  -H 'Content-Type: application/json' \
  -d '{"reason":"Warned, reinstated"}'

# Automatic: expired entries are lifted every 5 minutes by ScheduledTaskService
```

### Points Calculation

```bash
# Balance
curl -s -b /tmp/c.txt http://localhost:8080/api/points/balance
# Returns: {"balance":30}

# History
curl -s -b /tmp/c.txt http://localhost:8080/api/points/history
# Returns: [{"points":10,"action":"ORDER_PAYMENT",...},{"points":20,"action":"ORDER_COMPLETED",...}]

# Leaderboard (sorted by points desc, then completion count desc, then earliest completion asc)
curl -s -b /tmp/c.txt http://localhost:8080/api/points/leaderboard

# Admin: manual adjustment (mandatory reason, immutable audit)
curl -s -b /tmp/admin.txt -X POST http://localhost:8080/api/points/adjust \
  -H 'Content-Type: application/json' \
  -d '{"userId":4,"points":50,"reason":"Customer loyalty bonus"}'
# Empty reason returns 400

# Admin: points rules (INDIVIDUAL, CLASS, DEPARTMENT, TEAM scopes)
curl -s -b /tmp/admin.txt http://localhost:8080/api/points/rules
curl -s -b /tmp/admin.txt -X POST http://localhost:8080/api/points/rules \
  -H 'Content-Type: application/json' \
  -d '{"name":"TEAM_BONUS","points":100,"scope":"TEAM","triggerEvent":"TEAM_GOAL","active":true}'

# Admin: immutable adjustment audit log
curl -s -b /tmp/admin.txt http://localhost:8080/api/points/adjustments
```

---

## Architecture

### Layer Boundaries

```
+---------------------------------------------------------+
|  Frontend (jQuery SPA)                                  |
|  static/index.html + css/style.css + js/app.js         |
+---------------------------------------------------------+
|  Controller Layer          /api/*                       |
|  AuthController, OrderController, ListingController     |
|  MessageController, ChatSseController, PointsController |
|  -- thin: delegates to services, no business logic      |
+---------------------------------------------------------+
|  Service Layer             @Service @Transactional      |
|  OrderService, BlacklistService, PointsService          |
|  SearchTermService, MessageService, AddressService      |
|  -- all business rules, FSM, validation, access         |
+---------------------------------------------------------+
|  Repository/Mapper Layer   @Mapper (MyBatis)            |
|  OrderMapper, UserMapper, ListingMapper, etc.           |
|  -- SQL only, no business logic                         |
+---------------------------------------------------------+
|  Domain Model Layer                                     |
|  Order, User, Listing, OrderStatus (FSM enum),          |
|  PhotographerDto (read-only), SearchTerm, etc.          |
|  -- POJOs + state machine definitions                   |
+---------------------------------------------------------+
|  MySQL 8.0                                              |
|  8 migration scripts, InnoDB, utf8mb4                   |
+---------------------------------------------------------+
```

No business rules leak into controllers (they call service methods and return results). No business logic in SQL mappers (they execute parameterized queries only).

### Retired Legacy Endpoints

The following endpoints have been fully removed and return **410 GONE**. All booking operations are unified under the `/api/orders` FSM:

| Retired Surface | Replacement | Error Message |
|----------------|-------------|---------------|
| `/api/bookings/**` | `/api/orders` | `Use /api/orders instead.` |
| `/api/services/**` | `/api/listings` | `Use /api/listings instead.` |
| `/api/attachments/**` | `/api/messages` | `Use /api/messages for file sharing.` |

### Transactional Guarantees

- **Order creation**: `@Transactional` -- reserves time slot via `SELECT ... FOR UPDATE` row lock, inserts order, records audit action. If any step fails, the slot reservation is rolled back via compensating `releaseSlot()`.
- **Reschedule**: reserves new slot first, then releases old slot. If the old-slot release fails, the new slot is released (compensating rollback).
- **Cancel from PAID state**: atomically cancels the order, releases the slot, and records an auto-refund in a single transaction.
- **Points operations**: `@Transactional` -- atomically inserts a ledger entry and updates the user's balance.
- **Address default management**: `@Transactional` with `SELECT ... FOR UPDATE` row lock on all user addresses before clearing the previous default, preventing concurrent requests from creating multiple defaults. A database-level `BEFORE INSERT` / `BEFORE UPDATE` trigger pair enforces the one-default-per-user invariant as a final safety net.
- **Row-level locking**: `time_slots` uses `SELECT ... FOR UPDATE` plus an optimistic `version` column to prevent oversell under concurrent load.

### Idempotency

Every order action accepts an `Idempotency-Key` header:

1. First request claims the token (stored with 10-minute TTL).
2. On success, the response body is cached against the token.
3. Duplicate requests within the window return the cached response (same status code and body).
4. In-flight duplicates receive HTTP 409.
5. Expired tokens are cleaned up by a scheduled task every 15 minutes.

### Security Architecture

#### Authentication and Session Enforcement

| Layer | Mechanism |
|-------|-----------|
| **Password storage** | BCrypt (via `spring-security-crypto`) |
| **Session management** | `HttpSession` with `currentUser` attribute |
| **Request-level auth** | `AuthFilter` on `/api/*` (except `/api/auth/*`) |
| **Enabled-status check** | Every request re-queries `UserMapper.findById()` -- admin disablement takes effect immediately, not just at next login |
| **Blacklist enforcement** | `AuthFilter` checks `BlacklistMapper.findActiveByUserId()` on every request; returns 403 if active |

#### Cryptographic Sub-System

Field-level encryption is implemented in `FieldEncryptor.java` (`com.booking.util.FieldEncryptor`). The system encrypts sensitive data at rest, including notification recipient fields (email addresses and phone numbers stored in `notification_queue`) and user phone numbers in the `users` table.

**Cipher specification:**

| Parameter | Value | Source constant |
|-----------|-------|-----------------|
| Algorithm | `AES/GCM/NoPadding` | `ALGORITHM` |
| Key length | 256 bits (32 bytes) | `AES_KEY_LENGTH = 32` |
| GCM authentication tag | 128 bits | `GCM_TAG_LENGTH = 128` |
| Initialization vector | 12 bytes, randomly generated per encryption via `java.security.SecureRandom` | `IV_LENGTH = 12` |
| Ciphertext format | `Base64(IV [12 bytes] || ciphertext || GCM tag)` | -- |

**Key derivation (PBKDF2):**

The raw `ENCRYPTION_KEY` string is never used directly as the AES key. It is fed through PBKDF2 to derive the actual 256-bit encryption key:

| Parameter | Value | Source |
|-----------|-------|--------|
| Algorithm | `PBKDF2WithHmacSHA256` | `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` |
| Iteration count | **100,000** | `PBKDF2_ITERATIONS = 100_000` |
| Salt | `BookingPortalFieldEncryptorSalt!` (32 bytes, UTF-8 encoded, static) | `PBKDF2_SALT` constant |
| Output key length | 256 bits | `AES_KEY_LENGTH * 8 = 256` |
| Input key spec | `PBEKeySpec(key.toCharArray(), salt, iterations, keyLengthBits)` | `FieldEncryptor.configure()` |

The salt is a static application-level constant (`"BookingPortalFieldEncryptorSalt!"` encoded as UTF-8). It is not randomly generated per deployment. This is acceptable because the PBKDF2 input is itself a high-entropy secret (the `ENCRYPTION_KEY`), and the salt's purpose here is to namespace the derivation rather than to protect against rainbow tables over low-entropy passwords.

**Minimum key length enforcement:**

`FieldEncryptor.configure()` rejects any key shorter than **32 characters** at startup:

```java
if (key == null || key.length() < 32) {
    throw new IllegalArgumentException(
        "Encryption key must be at least 32 characters for AES-256");
}
```

This validation runs during Spring's `@PostConstruct` phase (via `EncryptionConfig`). If `ENCRYPTION_KEY` is missing or too short, the application context fails to initialize and the webapp will not start. The Docker entrypoint generates a 64-character hex string (32 bytes from `/dev/urandom`), which satisfies this requirement.

> **WARNING: The `ENCRYPTION_KEY` must be at least 32 characters. The application will refuse to start with a shorter key. In production, use a cryptographically random value and manage it through your secrets infrastructure (e.g., Docker secrets, Vault, AWS Secrets Manager). Do not commit encryption keys to version control.**

**Key derivation is deterministic:** configuring `FieldEncryptor` twice with the same input key produces the same derived AES key, so previously encrypted data remains decryptable after application restarts. Changing the `ENCRYPTION_KEY` will render all previously encrypted fields unreadable.

#### Data Protection Summary

| Mechanism | Where | Implementation |
|-----------|-------|----------------|
| **BCrypt hashing** | User passwords (`password_hash` column) | `spring-security-crypto` BCryptPasswordEncoder |
| **AES-256-GCM encryption** | Notification recipients, user phone numbers | `FieldEncryptor` with PBKDF2-derived key (see above) |
| **`@JsonIgnore`** | `passwordHash` on User, `paymentReference` on Order | Jackson annotation |
| **Photographer DTO** | `GET /api/users/photographers` returns `PhotographerDto` (id, username, fullName only) | Shields email, phone, enabled, roleId from non-admin callers |
| **Response masking** | Notification recipients masked to `al***@example.com` / `***0004` | `MaskUtil` |
| **Session-based auth** | HttpSession with `currentUser` attribute; AuthFilter enforces on `/api/*` | `AuthFilter` |
| **Enabled-status revalidation** | AuthFilter checks `user.enabled` from database on every request | Immediate enforcement of admin disablement |
| **Blacklist enforcement** | AuthFilter checks active blacklist on every authenticated request | 403 if blacklisted |

### Offline Constraints

- All assets (jQuery 3.7.1, CSS, JS) are bundled locally -- no CDN dependencies.
- Email/SMS notifications are queued locally in `notification_queue` with status `QUEUED` -- no outbound integration.
- No external API calls of any kind. The system operates fully offline.
- File uploads stored on local filesystem (`/app/uploads` volume).

---

## API Contract Reference

### Authentication

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| POST | `/api/auth/login` | No | `{"username":"x","password":"y"}` | `{"id":1,"username":"x","email":"...","fullName":"...","role":"CUSTOMER"}` |
| POST | `/api/auth/register` | No | `{"username":"x","email":"y","password":"z","fullName":"w","phone":"p"}` | `{"message":"Registration successful","userId":6}` |
| POST | `/api/auth/logout` | Yes | -- | `{"message":"Logged out"}` |
| GET | `/api/auth/me` | Yes | -- | Same as login response |

### Orders

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/orders` | Yes | -- | Array of orders (role-filtered) |
| GET | `/api/orders/{id}` | Yes | -- | Order object |
| GET | `/api/orders/{id}/audit` | Yes | -- | Array of order actions |
| POST | `/api/orders` | Cust | `{"listingId":1,"timeSlotId":1,"addressId":1,"notes":"...","deliveryMode":"ONSITE"}` | Order with status `CREATED` |
| POST | `/api/orders/{id}/confirm` | Photo | -- | Order with status `CONFIRMED` |
| POST | `/api/orders/{id}/pay` | Cust | `{"amount":150.0,"paymentReference":"REF"}` | Order with status `PAID` |
| POST | `/api/orders/{id}/check-in` | Photo | -- | Order with status `CHECKED_IN` |
| POST | `/api/orders/{id}/check-out` | Photo | -- | Order with status `CHECKED_OUT` |
| POST | `/api/orders/{id}/complete` | Photo | -- | Order with status `COMPLETED` |
| POST | `/api/orders/{id}/cancel` | Any | `{"reason":"text"}` | Order with status `CANCELLED` |
| POST | `/api/orders/{id}/refund` | Admin | `{"amount":150.0,"reason":"text"}` | Order with status `REFUNDED` |
| POST | `/api/orders/{id}/reschedule` | Cust | `{"newTimeSlotId":5}` | Order with updated slot |

### Listings & Time Slots

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/listings` | Yes | -- | Active listings |
| GET | `/api/listings/search` | Yes | Query params: `keyword`, `category`, `minPrice`, `maxPrice`, `location`, `locationState`, `locationCity`, `locationNeighborhood`, `theme`, `transportMode`, `minRating`, `availableDate`, `sortBy`, `page`, `size` | Paginated results `{items, page, size, total, totalPages}` |
| GET | `/api/listings/search/suggestions` | Yes | `?limit=15` | Array of popular search terms |
| GET | `/api/listings/{id}` | Yes | -- | Listing detail |
| GET | `/api/listings/my` | Photo | -- | Own listings |
| POST | `/api/listings` | Photo | `{"title":"x","price":100,"durationMinutes":60,"category":"PORTRAIT"}` | Created listing |
| GET | `/api/timeslots/listing/{id}/available?start=&end=` | Yes | Date params | Available slots |
| POST | `/api/timeslots` | Photo | `{"listingId":1,"slotDate":"2026-06-15","startTime":"09:00","endTime":"10:00","capacity":1}` | Created slot |

### Users

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/users` | Admin | -- | Full user list (admin view) |
| GET | `/api/users/photographers` | Yes | -- | `PhotographerDto` array: `[{"id":2,"username":"jphoto","fullName":"..."}]` (no email/phone/enabled) |
| GET | `/api/users/{id}` | Self/Admin | -- | Full user object |
| PATCH | `/api/users/{id}` | Admin | Partial fields | `{"message":"User updated"}` |
| PUT | `/api/users/{id}` | Admin | Full fields | `{"message":"User updated"}` |
| PATCH | `/api/users/{id}/enabled` | Admin | `{"enabled":false}` | `{"message":"User status updated"}` |

### Addresses

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/addresses` | Yes | -- | User's addresses |
| POST | `/api/addresses` | Yes | `{"label":"Home","street":"123 Main","city":"X","state":"IL","postalCode":"62701","country":"US","isDefault":true}` | Created address |
| PUT | `/api/addresses/{id}` | Owner | Same as POST | Updated address |
| DELETE | `/api/addresses/{id}` | Owner | -- | `{"message":"Address deleted"}` |

### Messages

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/messages/conversations` | Yes | -- | Conversations with unread counts |
| GET | `/api/messages/conversations/{id}` | Participant | -- | Messages with attachment info |
| POST | `/api/messages/send` | Yes | `{"recipientId":2,"content":"Hi","orderId":1}` | Created message |
| POST | `/api/messages/conversations/{id}/reply` | Participant | `{"content":"Reply text"}` | Created message |
| POST | `/api/messages/conversations/{id}/image` | Participant | Multipart (JPEG/PNG, max 5 MB) | `{"message":{...},"attachment":{...}}` |
| GET | `/api/messages/stream` | Yes | -- | SSE stream (`event: new-message`) |

### Notifications

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/notifications` | Yes | -- | Notifications (recipients masked) |
| GET | `/api/notifications/preferences` | Yes | -- | Preference toggles |
| PUT | `/api/notifications/preferences` | Yes | `{"orderUpdates":true,"holds":true,"reminders":false,"approvals":true,"compliance":false,"muteNonCritical":true}` | Updated prefs (compliance forced true) |

### Blacklist (Admin Only)

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/blacklist` | Admin | -- | All entries |
| POST | `/api/blacklist` | Admin | `{"userId":5,"reason":"text","durationDays":7}` | Created entry |
| POST | `/api/blacklist/{id}/lift` | Admin | `{"reason":"text"}` | Lifted entry |
| GET | `/api/blacklist/user/{id}` | Admin | -- | `{"blacklisted":true/false,"entry":{...}}` |

### Points (Mixed Access)

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/points/balance` | Yes | -- | `{"balance":30}` |
| GET | `/api/points/history` | Yes | -- | Ledger entries |
| GET | `/api/points/leaderboard` | Yes | -- | Ranked users |
| POST | `/api/points/adjust` | Admin | `{"userId":4,"points":50,"reason":"Mandatory note"}` | `{"balanceBefore":30,"balanceAfter":80}` |
| GET | `/api/points/adjustments` | Admin | -- | Immutable audit log |
| GET | `/api/points/rules` | Admin | -- | Configurable rules |
| POST | `/api/points/rules` | Admin | `{"name":"X","points":10,"scope":"TEAM","triggerEvent":"EVT"}` | Created rule |

### Health

| Method | Endpoint | Auth | Response |
|--------|----------|------|----------|
| GET | `/actuator/health` | No | `{"status":"UP"}` (component details require admin auth) |

---

## Repository Structure

```
.
+-- docker-compose.yml          # One-command startup (no hardcoded secrets)
+-- Dockerfile                  # Production multi-stage build + entrypoint
+-- Dockerfile.test             # Test runner container
+-- run_tests.sh                # Quality gate script
+-- pom.xml                     # Maven build with enforcer + JaCoCo
+-- docker/
|   +-- entrypoint.sh           # Runtime secret generation via /dev/urandom
|   +-- mysql/
|       +-- 01-schema.sql       # Core tables
|       +-- 02-seed.sql         # Roles, users, services
|       +-- 03-migration-v2.sql # Orders, listings, messaging, blacklist, points
|       +-- 04-seed-v2.sql      # Sample listings, time slots, addresses
|       +-- 05-migration-v3.sql # Notification prefs, chat attachments, points rules
|       +-- 06-migration-v4.sql # Delivery modes, listing metadata
|       +-- 07-migration-v5.sql # Structured location hierarchy
|       +-- 08-migration-v6.sql # Search terms store, address default triggers
+-- src/main/
|   +-- java/com/booking/
|   |   +-- config/             # AppConfig, WebConfig, EncryptionConfig, DataInitializer
|   |   +-- controller/         # REST controllers (15 files, 3 are 410 stubs)
|   |   +-- domain/             # Entity classes + enums + DTOs (19 files)
|   |   +-- filter/             # AuthFilter (session + enabled check + blacklist)
|   |   +-- mapper/             # MyBatis mapper interfaces (17 files)
|   |   +-- service/            # Business logic services (14 files)
|   |   +-- util/               # FieldEncryptor, MaskUtil, RoleGuard, SessionUtil
|   +-- resources/
|       +-- application.yml     # Config (secrets via env vars, not hardcoded)
|       +-- mapper/             # MyBatis XML (17 files)
|       +-- static/             # jQuery SPA (HTML, CSS, JS)
+-- unit_tests/                 # ~284 unit tests
|   +-- java/com/booking/unit/  # 32 test classes
|   +-- resources/
+-- api_tests/                  # ~163 API integration tests
    +-- java/com/booking/api/   # 14 test classes
    +-- resources/
```
