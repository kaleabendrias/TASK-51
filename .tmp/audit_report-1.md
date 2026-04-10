# Delivery Acceptance and Project Architecture Audit

Date: 2026-04-10
Audit Mode: Static-only (no runtime execution)

## 1. Verdict
- Overall conclusion: Partial Pass

## 2. Scope and Static Verification Boundary
- What was reviewed:
  - Documentation, manifests, config, startup/test scripts
  - Backend controllers/services/filters/domain/mappers/SQL migrations
  - Frontend static assets (jQuery SPA: HTML/CSS/JS)
  - Unit and API test source trees and existing coverage artifacts
- What was not reviewed:
  - Runtime behavior in a live environment
  - Browser interaction, container networking, DB process behavior under real load
- What was intentionally not executed:
  - Project startup
  - Docker compose
  - Tests
  - Any external service
- Claims requiring manual verification:
  - Actual runtime correctness of scheduled tasks and transaction rollback behavior
  - Real browser UX behavior and responsiveness under user interaction
  - True concurrent safety under production MySQL isolation/locking settings

## 3. Repository / Requirement Mapping Summary
- Prompt core goal mapped:
  - Offline local booking portal with Customer/Photographer/Admin role flows
  - Search/filter/paginate listings, order lifecycle FSM, idempotency, messaging with image upload, notifications, blacklist governance, points/awards rules, data protection
- Main implementation areas mapped:
  - UI: src/main/resources/static/index.html + js/app.js + css/style.css
  - Security/session: src/main/java/com/booking/filter/AuthFilter.java, util/RoleGuard.java
  - Core lifecycle: controller/OrderController.java + service/OrderService.java + mapper/OrderMapper.xml
  - Inventory lock/idempotency: service/TimeSlotService.java, mapper/TimeSlotMapper.xml, service/IdempotencyService.java
  - Messaging/notifications/points/blacklist/address: corresponding controller/service/mapper modules
  - Test suites: unit_tests and api_tests, plus test-results/jacoco-merged/jacoco.csv

## 4. Section-by-section Review

### 1. Hard Gates

#### 1.1 Documentation and static verifiability
- Conclusion: Pass
- Rationale: README provides startup, configuration assumptions, endpoint examples, and test entry points. Build/test wiring is present and statically consistent.
- Evidence:
  - README startup and test instructions: README.md:5, README.md:8, README.md:70
  - Maven test layout and coverage gate: pom.xml:49, pom.xml:102, pom.xml:192
  - Test runner script present: run_tests.sh:1

#### 1.2 Material deviation from prompt
- Conclusion: Partial Pass
- Rationale: Core prompt flows are implemented, but legacy parallel API surface remains active (/api/bookings, /api/services, /api/attachments) with a different booking model, which weakens architectural focus and can bypass strict order-centric constraints required by the prompt.
- Evidence:
  - Legacy booking endpoints active: src/main/java/com/booking/controller/BookingController.java:15
  - Legacy booking lifecycle (PENDING/CONFIRMED/IN_PROGRESS/COMPLETED) separate from strict order FSM: src/main/java/com/booking/domain/BookingStatus.java:3
  - Strict order FSM/idempotent workflow exists separately: src/main/java/com/booking/controller/OrderController.java:61, src/main/java/com/booking/domain/OrderStatus.java:6

### 2. Delivery Completeness

#### 2.1 Coverage of core explicit requirements
- Conclusion: Partial Pass
- Rationale:
  - Implemented: role-based landing, listing search with hierarchical destination and filters, pagination/sort, order lifecycle, idempotency, address CRUD with ZIP/state checks, chat with JPEG/PNG <=5MB, notifications with mute/compliance rules, blacklist default duration and auto-lift, points scopes and tie-break ordering.
  - Partially met: prompt says real-time chat; implementation uses client polling every 5 seconds.
  - Partially met: prompt says suggestions from locally stored popular terms; implementation uses per-browser local recent history only.
- Evidence:
  - Role-based landing: src/main/resources/static/js/app.js:93
  - Hierarchical/filter search API: src/main/java/com/booking/controller/ListingController.java:36
  - ZIP/state validation: src/main/java/com/booking/service/AddressService.java:17, src/main/java/com/booking/service/AddressService.java:106
  - Chat image constraints: src/main/java/com/booking/controller/MessageController.java:35, src/main/java/com/booking/controller/MessageController.java:134
  - Idempotency by action/order: src/main/java/com/booking/service/IdempotencyService.java:13, src/main/java/com/booking/service/IdempotencyService.java:43
  - Blacklist default 7 days and auto-lift schedule: src/main/java/com/booking/service/BlacklistService.java:19, src/main/java/com/booking/service/ScheduledTaskService.java:40
  - Points tie-breaker ordering: src/main/resources/mapper/PointsLedgerMapper.xml:31
  - Polling (not push real-time): src/main/resources/static/js/app.js:504, src/main/resources/static/js/app.js:506
  - Suggestions via localStorage recent terms: src/main/resources/static/js/app.js:71, src/main/resources/static/js/app.js:73

#### 2.2 Basic 0-to-1 end-to-end deliverable
- Conclusion: Pass
- Rationale: Repository contains coherent full-stack structure, persistence schema/migrations, UI, API, and substantial tests. Not a fragment/demo-only drop.
- Evidence:
  - Structured modules and assets: README.md:403, src/main/java/com/booking/controller/OrderController.java:17, src/main/resources/static/index.html:1
  - SQL schema/migrations: docker/mysql/01-schema.sql:1, docker/mysql/03-migration-v2.sql:1

### 3. Engineering and Architecture Quality

#### 3.1 Structure and decomposition
- Conclusion: Partial Pass
- Rationale: Layering is generally clear (controller/service/mapper/domain), but duplicated business domains (bookings vs orders; services vs listings) increase complexity and risk of divergent behavior.
- Evidence:
  - Order domain: src/main/java/com/booking/service/OrderService.java:16
  - Parallel booking domain: src/main/java/com/booking/service/BookingService.java:14

#### 3.2 Maintainability and extensibility
- Conclusion: Partial Pass
- Rationale: Many flows are maintainable and test-backed, but architectural duplication and mixed old/new APIs create long-term maintenance risk.
- Evidence:
  - Parallel controller sets: src/main/java/com/booking/controller/OrderController.java:17, src/main/java/com/booking/controller/BookingController.java:14

### 4. Engineering Details and Professionalism

#### 4.1 Error handling, logging, validation, API design
- Conclusion: Partial Pass
- Rationale:
  - Strengths: meaningful exception mapping, input validation, retry and archival states, audit trails.
  - Risks: security-sensitive configuration secrets hardcoded; disablement enforcement gap for already-authenticated sessions.
- Evidence:
  - Global exception mapping: src/main/java/com/booking/controller/GlobalExceptionHandler.java:11
  - Notification retry/terminal states: src/main/java/com/booking/service/NotificationService.java:23, src/main/java/com/booking/service/NotificationService.java:86
  - Hardcoded secrets: docker-compose.yml:8, docker-compose.yml:46, src/main/resources/application.yml:8
  - Session check without enabled revalidation: src/main/java/com/booking/filter/AuthFilter.java:43

#### 4.2 Product-like delivery vs demo shape
- Conclusion: Pass
- Rationale: Includes role-specific UI, persistence, lifecycle workflows, governance features, and broad automated test inventory.
- Evidence:
  - Role nav/app pages: src/main/resources/static/js/app.js:96
  - Test inventory and coverage artifact: api_tests/java/com/booking/api/OrderWorkflowApiIT.java:1, unit_tests/java/com/booking/unit/OrderServiceTest.java:1, test-results/jacoco-merged/jacoco.csv:1

### 5. Prompt Understanding and Requirement Fit

#### 5.1 Correct business understanding and constraints
- Conclusion: Partial Pass
- Rationale: Most explicit requirements are implemented, but there are notable fit gaps (real-time semantics, popular-term semantics) and architecture drift via legacy parallel flow.
- Evidence:
  - Core order FSM present: src/main/java/com/booking/domain/OrderStatus.java:6
  - Polling chat: src/main/resources/static/js/app.js:504
  - Suggestion implementation from local recent terms: src/main/resources/static/js/app.js:73
  - Legacy booking route still active: src/main/java/com/booking/controller/BookingController.java:15

### 6. Aesthetics (frontend)

#### 6.1 Visual/interaction quality
- Conclusion: Pass
- Rationale: UI has clear visual hierarchy, role-specific navigation, status badges, tables/cards, responsive rules, and interaction feedback (hover, modal states, validation styles).
- Evidence:
  - Responsive layout rules: src/main/resources/static/css/style.css:244
  - Form validation classes and feedback: src/main/resources/static/css/style.css:52
  - Search, cards, pagination, status tracker components: src/main/resources/static/css/style.css:136, src/main/resources/static/css/style.css:145, src/main/resources/static/css/style.css:193

## 5. Issues / Suggestions (Severity-Rated)

### Blocker/High

1) Severity: High
- Title: Hardcoded database credentials and encryption key in committed runtime config
- Conclusion: Fail
- Evidence:
  - docker-compose.yml:8
  - docker-compose.yml:11
  - docker-compose.yml:45
  - docker-compose.yml:46
  - src/main/resources/application.yml:8
- Impact:
  - Secret exposure risk and unsafe default deployments.
  - Conflicts with security/compliance expectations for sensitive-data handling.
- Minimum actionable fix:
  - Remove hardcoded secrets from repo-tracked configs; source from environment/secret manager.
  - Provide sample placeholders and secure defaults for local-only usage.

2) Severity: High
- Title: Disabled accounts are only enforced at login, not revalidated per request for existing sessions
- Conclusion: Partial Fail
- Evidence:
  - Auth checks enabled at login only: src/main/java/com/booking/service/AuthService.java:22
  - Request filter checks session + blacklist but not enabled state: src/main/java/com/booking/filter/AuthFilter.java:43, src/main/java/com/booking/filter/AuthFilter.java:52
  - Admin disable endpoint exists: src/main/java/com/booking/controller/UserController.java:74, src/main/java/com/booking/service/UserService.java:53
- Impact:
  - A user disabled by admin could keep using an already-established session until logout/session expiry.
- Minimum actionable fix:
  - In AuthFilter, reload user enabled state from DB each request (or short-TTL cache) and reject disabled users with 403/401.

3) Severity: High
- Title: Parallel legacy booking API materially weakens prompt-required strict order architecture
- Conclusion: Partial Fail
- Evidence:
  - Legacy route active: src/main/java/com/booking/controller/BookingController.java:15
  - Legacy create/status model: src/main/java/com/booking/service/BookingService.java:40, src/main/java/com/booking/service/BookingService.java:49, src/main/java/com/booking/service/BookingService.java:72
  - Prompt-required order idempotency/strict action model in separate path: src/main/java/com/booking/controller/OrderController.java:61
- Impact:
  - Multiple booking paradigms increase risk of inconsistent business rules and governance bypass.
- Minimum actionable fix:
  - Deprecate/remove /api/bookings flow or gate it as internal migration-only; unify all booking lifecycle operations behind /api/orders FSM + idempotency model.

### Medium

4) Severity: Medium
- Title: Encryption implementation contradicts documented AES-256 claim and truncates keys to 16 bytes
- Conclusion: Fail
- Evidence:
  - README claim: README.md:289
  - Implementation uses 16-byte key material: src/main/java/com/booking/util/FieldEncryptor.java:26
  - Minimum key length only 16 chars: src/main/java/com/booking/util/FieldEncryptor.java:23
- Impact:
  - Security posture/documentation mismatch; potential false assurance in audits.
- Minimum actionable fix:
  - Enforce 32-byte key for AES-256, derive with HKDF/PBKDF2, and update README to match actual cryptography.

5) Severity: Medium
- Title: “Real-time chat” requirement implemented as polling
- Conclusion: Partial Fail
- Evidence:
  - Polling loop every 5s: src/main/resources/static/js/app.js:504, src/main/resources/static/js/app.js:506
- Impact:
  - Latency and UX mismatch vs stated real-time requirement.
- Minimum actionable fix:
  - Add WebSocket/SSE push channel for message delivery/read-state updates.

6) Severity: Medium
- Title: Search suggestions use local recent history, not local popular-terms store
- Conclusion: Partial Fail
- Evidence:
  - Local browser storage strategy: src/main/resources/static/js/app.js:71, src/main/resources/static/js/app.js:73, src/main/resources/static/js/app.js:74
- Impact:
  - Requirement semantics (popular terms) only partially met.
- Minimum actionable fix:
  - Persist and rank query terms in local DB (frequency/recency), expose API for suggestions.

7) Severity: Medium
- Title: Single-default-address invariant not DB-enforced
- Conclusion: Partial Fail
- Evidence:
  - Address table lacks uniqueness constraint per user for default row: docker/mysql/03-migration-v2.sql:115, docker/mysql/03-migration-v2.sql:124
  - App logic clears defaults before set: src/main/java/com/booking/service/AddressService.java:43, src/main/resources/mapper/AddressMapper.xml:29
- Impact:
  - Concurrent updates can create multiple default addresses.
- Minimum actionable fix:
  - Add DB constraint/index strategy to enforce at most one default address per user (or transactional locking approach).

8) Severity: Medium
- Title: Photographer list endpoint exposes full user records to any authenticated role
- Conclusion: Partial Fail
- Evidence:
  - Endpoint without role restriction: src/main/java/com/booking/controller/UserController.java:30
  - Returns direct User entities: src/main/java/com/booking/controller/UserController.java:32
- Impact:
  - Overexposure of profile fields (e.g., email, enabled status) beyond least-privilege needs.
- Minimum actionable fix:
  - Return a minimal DTO for public photographer discovery and limit fields.

### Low

9) Severity: Low
- Title: README and implementation drift on cryptographic detail and some semantics
- Conclusion: Partial Fail
- Evidence:
  - AES-256 wording: README.md:289
  - 16-byte key implementation: src/main/java/com/booking/util/FieldEncryptor.java:26
- Impact:
  - Review/audit confusion.
- Minimum actionable fix:
  - Keep docs and code assertions synchronized; add security architecture note.

## 6. Security Review Summary

- authentication entry points: Partial Pass
  - Evidence: src/main/java/com/booking/controller/AuthController.java:22, src/main/java/com/booking/filter/AuthFilter.java:43
  - Reasoning: login/logout/me implemented; session guard exists. Gap: disabled-user revalidation not enforced in request filter.

- route-level authorization: Partial Pass
  - Evidence: src/main/java/com/booking/controller/PointsController.java:51, src/main/java/com/booking/controller/BlacklistController.java:24, src/main/java/com/booking/controller/UserController.java:20
  - Reasoning: many admin gates are present. Gap: some endpoints are broad by design (e.g., photographers list).

- object-level authorization: Pass
  - Evidence: src/main/java/com/booking/service/OrderService.java:68, src/main/java/com/booking/service/MessageService.java:123, src/main/java/com/booking/controller/MessageController.java:173, src/main/java/com/booking/controller/AttachmentController.java:69
  - Reasoning: ownership/participant checks exist on key resources.

- function-level authorization: Partial Pass
  - Evidence: src/main/java/com/booking/service/OrderService.java:149, src/main/java/com/booking/service/OrderService.java:162, src/main/java/com/booking/service/OrderService.java:243
  - Reasoning: role-constrained actions are implemented in order flow; parallel legacy flow increases policy surface.

- tenant/user data isolation: Partial Pass
  - Evidence: src/main/java/com/booking/service/OrderService.java:57, src/main/java/com/booking/service/BookingService.java:34, src/main/java/com/booking/controller/AddressController.java:31
  - Reasoning: per-role filtering/ownership checks are broadly present. Residual risk from broad user DTO exposure and legacy endpoints.

- admin/internal/debug protection: Partial Pass
  - Evidence: src/main/resources/application.yml:39, src/main/resources/application.yml:40, api_tests/java/com/booking/api/BookingServiceAttachmentApiIT.java:212
  - Reasoning: health details are configured as authorized-only, but runtime enforcement depends on security setup not fully provable statically; /actuator/health status is publicly reachable by design.

## 7. Tests and Logging Review

- Unit tests
  - Conclusion: Pass
  - Evidence: unit_tests/java/com/booking/unit/OrderServiceTest.java:1, unit_tests/java/com/booking/unit/IdempotencyServiceTest.java:1, unit_tests/java/com/booking/unit/NotificationDispatchTest.java:1
  - Notes: Broad service/util coverage including validation, state transitions, idempotency, notifications.

- API/integration tests
  - Conclusion: Pass
  - Evidence: api_tests/java/com/booking/api/OrderWorkflowApiIT.java:1, api_tests/java/com/booking/api/RbacApiIT.java:1, api_tests/java/com/booking/api/SecurityHardeningApiIT.java:1
  - Notes: Includes auth, RBAC, workflow, IDOR cases, constraints, and some concurrency simulation.

- Logging categories/observability
  - Conclusion: Partial Pass
  - Evidence: src/main/java/com/booking/service/ScheduledTaskService.java:31, src/main/java/com/booking/service/NotificationService.java:90, src/main/java/com/booking/service/BlacklistService.java:83
  - Notes: Operational logs exist for schedulers/retries/blacklist actions. Structured context is modest.

- Sensitive-data leakage risk in logs/responses
  - Conclusion: Partial Pass
  - Evidence: src/main/java/com/booking/domain/User.java:35, src/main/java/com/booking/domain/Order.java:49, src/main/java/com/booking/service/NotificationService.java:39
  - Notes: Password hash/payment reference protected; recipient masking implemented. Residual risk from broad user endpoint responses and hardcoded secrets in config files.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- Unit tests and API tests exist: Yes
- Frameworks: JUnit 5, Spring MockMvc, Mockito
- Entry points:
  - Maven Surefire/Failsafe config: pom.xml:102, pom.xml:113
  - Scripted runner: run_tests.sh:1
- Documentation test commands: README.md:70
- Static coverage artifact present: test-results/jacoco-merged/jacoco.csv:1

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Auth login + unauthenticated 401 | api_tests/java/com/booking/api/AuthApiIT.java:14, api_tests/java/com/booking/api/AuthApiIT.java:76 | 401 on unauthenticated /api/orders | sufficient | None material | N/A |
| RBAC route controls (admin-only endpoints) | api_tests/java/com/booking/api/RbacApiIT.java:19, api_tests/java/com/booking/api/RbacApiIT.java:61 | customer forbidden, admin allowed | sufficient | None material | N/A |
| Order lifecycle FSM | api_tests/java/com/booking/api/OrderWorkflowApiIT.java:66 | CREATED->CONFIRMED->PAID->CHECKED_IN->CHECKED_OUT->COMPLETED assertions | sufficient | None material | N/A |
| Idempotency duplicate handling | api_tests/java/com/booking/api/OrderWorkflowApiIT.java:34, unit_tests/java/com/booking/unit/IdempotencyServiceTest.java:66 | repeated key behavior and scoped token checks | basically covered | No explicit stale-token replay boundary test in API suite | Add API test for token expiry boundary and post-expiry reacceptance |
| Oversell prevention / slot contention | api_tests/java/com/booking/api/SecurityHardeningApiIT.java:169, api_tests/java/com/booking/api/StrictAuthAndLifecycleApiIT.java:148 | capacity-1 slot second request fails; parallel threads one success | basically covered | Real DB isolation edge cases still runtime-dependent | Add deterministic DB-level contention tests with explicit transaction timing controls |
| Object-level access (orders, attachments/chat) | api_tests/java/com/booking/api/OrderWorkflowApiIT.java:183, api_tests/java/com/booking/api/SecurityHardeningApiIT.java:56 | wrong photographer/customer forbidden, non-participant denied download | sufficient | None material | N/A |
| Address validation (ZIP/state consistency) | unit_tests/java/com/booking/unit/AddressServiceTest.java:63, api_tests/java/com/booking/api/SecurityHardeningApiIT.java:122 | invalid mismatch rejected | sufficient | None material | N/A |
| Notifications mute/compliance behavior | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:88, unit_tests/java/com/booking/unit/NotificationExportTest.java:69 | compliance forced true / never muted | sufficient | None material | N/A |
| Blacklist enforcement | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:187 | blacklisted user gets forbidden API response | basically covered | Session behavior around disabled users not explicitly tested | Add API test: disable user after login, verify current session is rejected |
| Search filters + pagination/sort | api_tests/java/com/booking/api/SearchFilterApiIT.java:14, api_tests/java/com/booking/api/ContractAndConstraintApiIT.java:124 | items array + filter query assertions + sort checks | basically covered | Limited edge coverage for extreme page/size and invalid sort fuzz | Add boundary tests for page/size extremes and invalid parameter combinations |
| Points rules scopes and tie-breakers | unit_tests/java/com/booking/unit/PointsScopeTest.java:19, api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:120 | scope fan-out and leaderboard endpoint checks | insufficient | No concurrent balance-race detection test | Add concurrent award/deduct test asserting ledger+balance consistency |

### 8.3 Security Coverage Audit
- authentication: Pass
  - Tests cover successful/failed login and unauthenticated 401.
- route authorization: Pass
  - Tests cover forbidden access for non-admin roles across major admin routes.
- object-level authorization: Pass
  - Tests cover cross-user order and chat-attachment access denial.
- tenant/data isolation: Partial Pass
  - Order/address isolation tested; broad DTO exposure risk not specifically tested.
- admin/internal protection: Partial Pass
  - Admin checks tested for app endpoints; actuator role/detail enforcement cannot be fully proven statically.

### 8.4 Final Coverage Judgment
- Partial Pass
- Boundary explanation:
  - Major happy paths and many negative security paths are covered.
  - Uncovered or weakly covered risks remain where severe defects could pass tests, notably disabled-session enforcement and points balance race consistency under concurrent updates.

## 9. Final Notes
- The project is substantial and largely aligned with the prompt, but not ready for full acceptance due to security/configuration hardening gaps and architectural drift from a single strict order workflow.
- Strong conclusions above are based on static file-level evidence only; runtime claims remain bounded as noted.
