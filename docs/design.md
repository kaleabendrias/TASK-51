# Booking Portal Design Document

## 1. Overview
Booking Portal is an offline-first local booking system for service listings, order lifecycle management, messaging, notifications, and governance controls.

Core stack:
- Backend: Java 17, Spring MVC, MyBatis
- Database: MySQL (local Docker deployment)
- Frontend: jQuery SPA (static assets served by backend)
- Security model: session-based auth with API filters for CSRF, auth, disabled-account, and blacklist enforcement

## 2. Business Scope
Primary user roles:
- CUSTOMER: search listings, create/manage orders, addresses, chat, notifications
- PHOTOGRAPHER: manage listings/time slots and fulfill orders
- SERVICE_PROVIDER: generalized non-photographer seller role with provider-equivalent permissions
- ADMINISTRATOR: governance operations (users, blacklist, points rules/adjustments, exports)

Core capabilities:
- Listing discovery with keyword + structured filters + sorting + pagination
- Popular search suggestions from local persisted search term frequency/recency
- Strict order finite-state workflow with idempotent action endpoints
- Inventory slot locking and compensation rollback for failed downstream steps
- Messaging with SSE push and image attachments
- Notification queue, read/archive, retry, export workflow
- Address CRUD with ZIP/state validation and single-default enforcement
- Blacklist with duration and auto-lift
- Points rules, award/deduct, leaderboard tie-breaks, admin adjustment audit records

## 3. Architecture
### 3.1 Layered Decomposition
- Controllers: request handling and response shaping
- Services: business rules and transactional orchestration
- Mappers (MyBatis): persistence SQL and row mapping
- Domain models: transport and business entities
- Filters: cross-cutting request security controls

### 3.2 Runtime Flow
1. Browser accesses static app and authenticates via `/api/auth/login`.
2. Session is established with fixation protection (old session invalidated, new session created).
3. API requests pass CSRF filter (state-changing methods) then auth filter (session, enabled, blacklist).
4. Controllers invoke service methods and mapper operations.
5. Local database persists state transitions, tokens, inventory changes, notifications, and audit artifacts.

### 3.3 Key Components
- Auth and guards:
  - `CsrfFilter`, `AuthFilter`, `SessionUtil`, `RoleGuard`
- Order workflow:
  - `OrderController`, `OrderService`, `OrderStatus`, `IdempotencyService`
- Inventory:
  - `TimeSlotService` row-level lock + optimistic version increment
- Discovery:
  - `ListingService`, `SearchTermService`
- Messaging:
  - `MessageController`, `MessageService`, `ChatSseController`
- Governance:
  - `BlacklistController`, `PointsController`, `UserController`

## 4. Data and Consistency Model
### 4.1 Order Lifecycle
State machine includes:
- CREATED -> CONFIRMED -> PAID -> CHECKED_IN -> CHECKED_OUT -> COMPLETED
- Branches: CANCELLED, REFUNDED, RESCHEDULE

All action endpoints require `Idempotency-Key` and are scoped by action + order.

### 4.2 Inventory Safety
- Slot reservation uses `SELECT ... FOR UPDATE` and version-checked increment.
- Oversell prevention is enforced by both lock and capacity predicate.
- Compensating rollback releases slot on creation/reschedule failures.

### 4.3 Address Default Invariant
- Application-level lock + clear-default sequence
- DB trigger-level guarantee for at most one default address per user

### 4.4 Points Integrity
- Balance mutation uses atomic SQL update operations.
- Ledger entries store post-mutation balance snapshots.
- Manual adjustments require admin and a mandatory reason.

## 5. Security and Compliance Controls
Implemented controls:
- Session authentication for API routes
- CSRF Origin/Referer enforcement on POST/PUT/PATCH/DELETE
- Disabled-account revalidation per request
- Active blacklist enforcement per request
- Role-based and object-level authorization checks
- Password hashing (BCrypt)
- Sensitive field encryption at rest (AES-GCM with PBKDF2-derived key)
- Response masking for phone/notification recipient fields
- Restricted actuator exposure (`health` only)

Offline boundary:
- No third-party login, no external messaging providers by default
- Notification dispatch default implementation is local and export-oriented

## 6. Frontend UX Model
Single-page jQuery app with role-specific navigation.

Main UX areas:
- Authentication and role landing
- Search + suggestions + filter panel + paginated listing grid
- Listing details, available slots, booking options
- Order detail with status tracker and action buttons
- Chat with conversation list, read markers, image upload
- Notification center and preferences
- Address management
- Admin dashboards for users/blacklist/points/services

Responsive behavior is handled in CSS breakpoints for mobile layouts.

## 7. Operational Characteristics
- Startup via Docker Compose with local secrets initialization
- Health checks for MySQL and web app
- Scheduled tasks:
  - auto-close unpaid orders
  - auto-lift expired blacklist entries
  - notification retry processing
  - idempotency token cleanup

## 8. Known Boundaries
- Static architecture confirms implemented controls but does not prove production behavior under real traffic patterns.
- Browser/runtime concerns (network policy, cookie settings, proxy behavior) require deployment-level verification.
