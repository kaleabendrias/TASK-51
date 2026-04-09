# Booking Portal

Offline-first photography booking platform. Java 17 / Spring MVC / MyBatis / MySQL / jQuery.

## One-Command Startup

```bash
docker compose up
```

The stack is fully self-contained. No `.env` files, no host-installed JDK, no external APIs.

| Service | Container | Port | Purpose |
|---------|-----------|------|---------|
| **Web Application** | `booking-webapp` | **8080** | Spring Boot app, jQuery SPA |
| **MySQL 8.0** | `booking-mysql` | **3306** | Persistent data store |

Open **http://localhost:8080** after startup.

## Initialization Behavior

On first `docker compose up`:

1. MySQL container starts and runs init scripts in order:
   - `01-schema.sql` — tables, indexes, foreign keys
   - `02-seed.sql` — roles, users (with placeholder passwords), services, sample bookings
   - `03-migration-v2.sql` — orders, listings, time slots, messaging, blacklist, points
   - `04-seed-v2.sql` — sample listings, time slots, addresses
   - `05-migration-v3.sql` — notification preferences, chat attachments, points rules
2. MySQL health check (`mysqladmin ping`) must pass before webapp starts.
3. Webapp starts and `DataInitializer` replaces all `$PLACEHOLDER$` password hashes with real BCrypt hashes for `password123`.
4. Scheduled tasks begin: auto-close unpaid orders (2 min), auto-lift expired blacklists (5 min), clean idempotency tokens (15 min).
5. Webapp health check (`/actuator/health`) confirms MySQL connectivity.

To reset all data: `docker compose down -v && docker compose up`

## Default Test Users

All passwords are **`password123`**.

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
unit_tests/             # 225 unit tests (Mockito, no Spring context)
  java/com/booking/unit/   23 test classes
  resources/               H2 schema + seed data

api_tests/              # 105 integration tests (full Spring Boot + H2)
  java/com/booking/api/    9 test classes (including base class)
  resources/               application-test.yml, schema, seed data
```

Tests **must** live in `unit_tests/` or `api_tests/`. The Maven enforcer plugin fails the build if any test file appears under `src/test/java`.

### Coverage Gate

JaCoCo enforces **>= 90% line coverage** on the merged (unit + API) execution data. Coverage reports are written to `test-results/` after each run.

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

# Filtered search
curl -s -b /tmp/c.txt 'http://localhost:8080/api/listings/search?keyword=portrait&category=PORTRAIT&minPrice=100&maxPrice=200&location=Downtown'

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
curl -s -b /tmp/photo.txt -X POST http://localhost:8080/api/orders/1/confirm

# Record payment (customer)
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/orders/1/pay \
  -H 'Content-Type: application/json' \
  -d '{"amount":150.00,"paymentReference":"PAY-REF-123"}'

# Check-in / Check-out / Complete (photographer)
curl -s -b /tmp/photo.txt -X POST http://localhost:8080/api/orders/1/check-in
curl -s -b /tmp/photo.txt -X POST http://localhost:8080/api/orders/1/check-out
curl -s -b /tmp/photo.txt -X POST http://localhost:8080/api/orders/1/complete

# Cancel with auto-refund
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/orders/1/cancel \
  -H 'Content-Type: application/json' \
  -d '{"reason":"Changed my mind"}'

# Reschedule
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/orders/1/reschedule \
  -H 'Content-Type: application/json' \
  -d '{"newTimeSlotId":5}'

# Audit trail
curl -s -b /tmp/c.txt http://localhost:8080/api/orders/1/audit
```

### Messaging

```bash
# Send message
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
┌─────────────────────────────────────────────────────┐
│  Frontend (jQuery SPA)                              │
│  static/index.html + css/style.css + js/app.js     │
├─────────────────────────────────────────────────────┤
│  Controller Layer          /api/*                   │
│  AuthController, OrderController, ListingController │
│  MessageController, PointsController, etc.          │
│  ── thin: delegates to services, no business logic  │
├─────────────────────────────────────────────────────┤
│  Service Layer             @Service @Transactional  │
│  OrderService, BlacklistService, PointsService      │
│  ── all business rules, FSM, validation, access     │
├─────────────────────────────────────────────────────┤
│  Repository/Mapper Layer   @Mapper (MyBatis)        │
│  OrderMapper, UserMapper, ListingMapper, etc.       │
│  ── SQL only, no business logic                     │
├─────────────────────────────────────────────────────┤
│  Domain Model Layer                                 │
│  Order, User, Listing, OrderStatus (FSM enum), etc. │
│  ── POJOs + state machine definitions               │
├─────────────────────────────────────────────────────┤
│  MySQL 8.0                                          │
│  5 migration scripts, InnoDB, utf8mb4               │
└─────────────────────────────────────────────────────┘
```

No business rules leak into controllers (they call service methods and return results). No business logic in SQL mappers (they execute parameterized queries only).

### Transactional Guarantees

- **Order creation**: `@Transactional` — reserves time slot via `SELECT ... FOR UPDATE` row lock, inserts order, records audit action. If any step fails, the slot reservation is rolled back via compensating `releaseSlot()`.
- **Reschedule**: reserves new slot first, then releases old slot. If the old-slot release fails, the new slot is released (compensating rollback).
- **Cancel from PAID state**: atomically cancels the order, releases the slot, and records an auto-refund in a single transaction.
- **Points operations**: `@Transactional` — atomically inserts a ledger entry and updates the user's balance.
- **Row-level locking**: `time_slots` uses `SELECT ... FOR UPDATE` plus an optimistic `version` column to prevent oversell under concurrent load.

### Idempotency

Every order action accepts an `Idempotency-Key` header:

1. First request claims the token (stored with 10-minute TTL).
2. On success, the response body is cached against the token.
3. Duplicate requests within the window return the cached response (same status code and body).
4. In-flight duplicates receive HTTP 409.
5. Expired tokens are cleaned up by a scheduled task every 15 minutes.

### Data Protection

| Mechanism | Where |
|-----------|-------|
| **BCrypt hashing** | User passwords (`password_hash` column) |
| **AES-256-GCM encryption** | Notification recipient fields (email/phone stored encrypted) |
| **@JsonIgnore** | `passwordHash` on User, `paymentReference` on Order |
| **Response masking** | Notification recipients masked to `al***@example.com` / `***0004` |
| **Session-based auth** | HttpSession with `currentUser` attribute; AuthFilter enforces on `/api/*` |
| **Blacklist enforcement** | AuthFilter checks active blacklist on every authenticated request |

### Offline Constraints

- All assets (jQuery 3.7.1, CSS, JS) are bundled locally — no CDN dependencies.
- Email/SMS notifications are queued locally in `notification_queue` with status `QUEUED` — no outbound integration.
- No external API calls of any kind. The system operates fully offline.
- File uploads stored on local filesystem (`/app/uploads` volume).

---

## API Contract Reference

### Authentication

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| POST | `/api/auth/login` | No | `{"username":"x","password":"y"}` | `{"id":1,"username":"x","email":"...","fullName":"...","role":"CUSTOMER"}` |
| POST | `/api/auth/register` | No | `{"username":"x","email":"y","password":"z","fullName":"w","phone":"p"}` | `{"message":"Registration successful","userId":6}` |
| POST | `/api/auth/logout` | Yes | — | `{"message":"Logged out"}` |
| GET | `/api/auth/me` | Yes | — | Same as login response |

### Orders

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/orders` | Yes | — | Array of orders (role-filtered) |
| GET | `/api/orders/{id}` | Yes | — | Order object |
| GET | `/api/orders/{id}/audit` | Yes | — | Array of order actions |
| POST | `/api/orders` | Cust | `{"listingId":1,"timeSlotId":1,"addressId":1,"notes":"..."}` | Order with status `CREATED` |
| POST | `/api/orders/{id}/confirm` | Photo | — | Order with status `CONFIRMED` |
| POST | `/api/orders/{id}/pay` | Cust | `{"amount":150.0,"paymentReference":"REF"}` | Order with status `PAID` |
| POST | `/api/orders/{id}/check-in` | Photo | — | Order with status `CHECKED_IN` |
| POST | `/api/orders/{id}/check-out` | Photo | — | Order with status `CHECKED_OUT` |
| POST | `/api/orders/{id}/complete` | Photo | — | Order with status `COMPLETED` |
| POST | `/api/orders/{id}/cancel` | Any | `{"reason":"text"}` | Order with status `CANCELLED` |
| POST | `/api/orders/{id}/refund` | Admin | `{"amount":150.0,"reason":"text"}` | Order with status `REFUNDED` |
| POST | `/api/orders/{id}/reschedule` | Cust | `{"newTimeSlotId":5}` | Order with updated slot |

### Listings & Time Slots

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/listings` | Yes | — | Active listings |
| GET | `/api/listings/search?keyword=&category=&minPrice=&maxPrice=&location=` | Yes | Query params | Filtered listings |
| GET | `/api/listings/{id}` | Yes | — | Listing detail |
| GET | `/api/listings/my` | Photo | — | Own listings |
| POST | `/api/listings` | Photo | `{"title":"x","price":100,"durationMinutes":60,"category":"PORTRAIT"}` | Created listing |
| GET | `/api/timeslots/listing/{id}/available?start=&end=` | Yes | Date params | Available slots |
| POST | `/api/timeslots` | Photo | `{"listingId":1,"slotDate":"2026-06-15","startTime":"09:00","endTime":"10:00","capacity":1}` | Created slot |

### Addresses

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/addresses` | Yes | — | User's addresses |
| POST | `/api/addresses` | Yes | `{"label":"Home","street":"123 Main","city":"X","state":"IL","postalCode":"62701","country":"US","isDefault":true}` | Created address |
| PUT | `/api/addresses/{id}` | Owner | Same as POST | Updated address |
| DELETE | `/api/addresses/{id}` | Owner | — | `{"message":"Address deleted"}` |

### Messages

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/messages/conversations` | Yes | — | Conversations with unread counts |
| GET | `/api/messages/conversations/{id}` | Participant | — | Messages with attachment info |
| POST | `/api/messages/send` | Yes | `{"recipientId":2,"content":"Hi","orderId":1}` | Created message |
| POST | `/api/messages/conversations/{id}/reply` | Participant | `{"content":"Reply text"}` | Created message |
| POST | `/api/messages/conversations/{id}/image` | Participant | Multipart (JPEG/PNG, max 5 MB) | `{"message":{...},"attachment":{...}}` |

### Notifications

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/notifications` | Yes | — | Notifications (recipients masked) |
| GET | `/api/notifications/preferences` | Yes | — | Preference toggles |
| PUT | `/api/notifications/preferences` | Yes | `{"orderUpdates":true,"holds":true,"reminders":false,"approvals":true,"compliance":false,"muteNonCritical":true}` | Updated prefs (compliance forced true) |

### Blacklist (Admin Only)

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/blacklist` | Admin | — | All entries |
| POST | `/api/blacklist` | Admin | `{"userId":5,"reason":"text","durationDays":7}` | Created entry |
| POST | `/api/blacklist/{id}/lift` | Admin | `{"reason":"text"}` | Lifted entry |
| GET | `/api/blacklist/user/{id}` | Admin | — | `{"blacklisted":true/false,"entry":{...}}` |

### Points (Mixed Access)

| Method | Endpoint | Auth | Request | Response |
|--------|----------|------|---------|----------|
| GET | `/api/points/balance` | Yes | — | `{"balance":30}` |
| GET | `/api/points/history` | Yes | — | Ledger entries |
| GET | `/api/points/leaderboard` | Yes | — | Ranked users |
| POST | `/api/points/adjust` | Admin | `{"userId":4,"points":50,"reason":"Mandatory note"}` | `{"balanceBefore":30,"balanceAfter":80}` |
| GET | `/api/points/adjustments` | Admin | — | Immutable audit log |
| GET | `/api/points/rules` | Admin | — | Configurable rules |
| POST | `/api/points/rules` | Admin | `{"name":"X","points":10,"scope":"TEAM","triggerEvent":"EVT"}` | Created rule |

### Health

| Method | Endpoint | Auth | Response |
|--------|----------|------|----------|
| GET | `/actuator/health` | No | `{"status":"UP","components":{"db":{"status":"UP"},...}}` |

---

## Repository Structure

```
.
├── docker-compose.yml          # One-command startup
├── Dockerfile                  # Production multi-stage build
├── Dockerfile.test             # Test runner container
├── run_tests.sh                # Quality gate script
├── pom.xml                     # Maven build with enforcer + JaCoCo
├── docker/mysql/
│   ├── 01-schema.sql           # Core tables
│   ├── 02-seed.sql             # Roles, users, services
│   ├── 03-migration-v2.sql     # Orders, listings, messaging, blacklist, points
│   ├── 04-seed-v2.sql          # Sample listings, time slots, addresses
│   └── 05-migration-v3.sql     # Notification prefs, chat attachments, points rules
├── src/main/
│   ├── java/com/booking/
│   │   ├── config/             # AppConfig, WebConfig, DataInitializer
│   │   ├── controller/         # REST controllers (15 files)
│   │   ├── domain/             # Entity classes + enums (18 files)
│   │   ├── filter/             # AuthFilter (session + blacklist check)
│   │   ├── mapper/             # MyBatis mapper interfaces (16 files)
│   │   ├── service/            # Business logic services (13 files)
│   │   └── util/               # FieldEncryptor, MaskUtil, RoleGuard, SessionUtil
│   └── resources/
│       ├── application.yml
│       ├── mapper/             # MyBatis XML (16 files)
│       └── static/             # jQuery SPA (HTML, CSS, JS)
├── unit_tests/                 # 225 unit tests
│   ├── java/com/booking/unit/
│   └── resources/
└── api_tests/                  # 105 API integration tests
    ├── java/com/booking/api/
    └── resources/
```
