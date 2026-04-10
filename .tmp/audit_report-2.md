# Delivery Acceptance and Project Architecture Audit

Date: 2026-04-10
Audit Mode: Static-only (no runtime execution)

## 1. Verdict
- Overall conclusion: Partial Pass

## 2. Scope and Static Verification Boundary
- What was reviewed:
  - Documentation, manifests, startup/test scripts, Maven config
  - Spring MVC controllers/services/filters/domain/mappers
  - SQL baseline and migrations
  - Frontend static assets (jQuery SPA: HTML/CSS/JS)
  - Unit/API test source trees and existing coverage artifacts
- What was not reviewed:
  - Real runtime behavior in live containers/browser
  - Real DB lock/isolation behavior under production load
  - External-network behavior (intentionally not invoked)
- What was intentionally not executed:
  - Project startup
  - Docker compose
  - Test execution
  - External services
- Claims requiring manual verification:
  - End-to-end browser UX timing and SSE stability under prolonged sessions
  - Real transactional behavior under production MySQL isolation/latency
  - Session fixation and CSRF exploitability in a real browser deployment setup

## 3. Repository / Requirement Mapping Summary
- Prompt core goal mapped:
  - Offline local booking portal with role-based flows, listing search/filter/pagination, strict order FSM with idempotency, messaging with image attachments and read state, notifications, blacklist governance, points/awards, data protection
- Main implementation areas mapped:
  - UI: `src/main/resources/static/index.html`, `src/main/resources/static/js/app.js`, `src/main/resources/static/css/style.css`
  - Auth/session guard: `src/main/java/com/booking/filter/AuthFilter.java`, `src/main/java/com/booking/controller/AuthController.java`
  - Core lifecycle/idempotency/locking: `src/main/java/com/booking/controller/OrderController.java`, `src/main/java/com/booking/service/OrderService.java`, `src/main/java/com/booking/service/IdempotencyService.java`, `src/main/java/com/booking/service/TimeSlotService.java`
  - Messaging/notifications/points/blacklist/address/search-term modules in corresponding controller/service/mapper files
  - Persistence migrations: `docker/mysql/01-schema.sql` through `docker/mysql/08-migration-v6.sql`
  - Tests: `unit_tests` and `api_tests` trees, plus `test-results/jacoco-merged/jacoco.csv`

## 4. Section-by-section Review

### 1. Hard Gates

#### 1.1 Documentation and static verifiability
- Conclusion: Pass
- Rationale: README provides startup/testing guidance and endpoint examples; build/test structure is statically coherent.
- Evidence:
  - Startup command and initialization narrative: `README.md:8`, `README.md:23`
  - Test command documentation: `README.md:68`, `README.md:70`
  - Maven test wiring and coverage gate: `pom.xml:42`, `pom.xml:112`, `pom.xml:188`, `pom.xml:197`
  - Test runner script present: `run_tests.sh:1`

#### 1.2 Material deviation from Prompt
- Conclusion: Partial Pass
- Rationale: Core order-centric architecture is now primary and legacy `/api/bookings` route is removed, but legacy V1 schema artifacts (`services`, `bookings`, `attachments`) remain in baseline migration/seed and add architectural drift noise.
- Evidence:
  - Legacy endpoint removal test: `api_tests/java/com/booking/api/CoverageBoostApiIT.java:193`, `api_tests/java/com/booking/api/CoverageBoostApiIT.java:196`
  - Legacy schema objects still created/seeded: `docker/mysql/01-schema.sql:30`, `docker/mysql/01-schema.sql:40`, `docker/mysql/01-schema.sql:64`, `docker/mysql/02-seed.sql:19`, `docker/mysql/02-seed.sql:27`

### 2. Delivery Completeness

#### 2.1 Coverage of core explicit requirements
- Conclusion: Partial Pass
- Rationale:
  - Implemented: role-based sign-in, hierarchical search filters + sort + pagination, strict order FSM, 10-minute idempotency, address CRUD + ZIP/state validation + default-address enforcement, SSE chat, JPEG/PNG <= 5MB attachment policy, notification preferences with compliance override, blacklist default duration and auto-lift, points scope/tie-break rules.
  - Partial gap: prompt says sellers include photographers and other providers; implementation role model is fixed to CUSTOMER/PHOTOGRAPHER/ADMINISTRATOR.
  - Partial gap: ETA is shown as session duration from start; no explicit delivery/pickup ETA model for physical-item logistics.
- Evidence:
  - Roles in seed schema: `docker/mysql/02-seed.sql:5`, `docker/mysql/02-seed.sql:8`
  - Hierarchical/filter search + pagination/sort backend: `src/main/java/com/booking/controller/ListingController.java:36`, `src/main/java/com/booking/service/ListingService.java:47`, `src/main/resources/mapper/ListingMapper.xml:44`, `src/main/resources/mapper/ListingMapper.xml:80`
  - Server-side popular suggestions: `src/main/java/com/booking/controller/ListingController.java:57`, `src/main/java/com/booking/service/SearchTermService.java:19`, `src/main/resources/mapper/SearchTermMapper.xml:7`
  - Strict FSM + branches: `src/main/java/com/booking/domain/OrderStatus.java:16`, `src/main/java/com/booking/domain/OrderStatus.java:41`
  - Idempotency 10 minutes scoped by action+order: `src/main/java/com/booking/service/IdempotencyService.java:13`, `src/main/java/com/booking/service/IdempotencyService.java:43`, `src/main/java/com/booking/service/IdempotencyService.java:60`
  - Address validation and default uniqueness: `src/main/java/com/booking/service/AddressService.java:93`, `docker/mysql/08-migration-v6.sql:27`, `docker/mysql/08-migration-v6.sql:40`
  - SSE + attachment constraints + read state: `src/main/java/com/booking/controller/ChatSseController.java:27`, `src/main/java/com/booking/controller/MessageController.java:35`, `src/main/java/com/booking/controller/MessageController.java:131`, `src/main/java/com/booking/service/MessageService.java:48`, `src/main/resources/static/js/app.js:503`, `src/main/resources/static/js/app.js:543`
  - Notification preferences/compliance/retry: `src/main/java/com/booking/controller/NotificationController.java:67`, `src/main/java/com/booking/controller/NotificationController.java:73`, `src/main/java/com/booking/service/NotificationService.java:23`, `src/main/java/com/booking/service/NotificationService.java:85`
  - Blacklist governance/default duration: `docker/mysql/03-migration-v2.sql:194`, `src/main/java/com/booking/controller/BlacklistController.java:23`
  - Points scope + leaderboard tie-break: `src/main/java/com/booking/service/PointsService.java:51`, `src/main/resources/mapper/PointsLedgerMapper.xml:31`
  - ETA currently duration-based UI text: `src/main/resources/static/js/app.js:265`

#### 2.2 Basic 0-to-1 end-to-end deliverable
- Conclusion: Pass
- Rationale: Repository contains coherent full-stack application (UI/API/persistence/migrations/tests/docs), not a fragment.
- Evidence:
  - Project shape/docs: `README.md:576`
  - Core API/UI modules: `src/main/java/com/booking/controller/OrderController.java:17`, `src/main/resources/static/index.html:1`
  - Persistence/migrations: `docker/mysql/01-schema.sql:1`, `docker/mysql/08-migration-v6.sql:1`

### 3. Engineering and Architecture Quality

#### 3.1 Structure and decomposition
- Conclusion: Partial Pass
- Rationale: Layered decomposition is clear and mostly cohesive; residual maintainability overhead remains from mixed legacy schema (V1) and newer order-centric schema (V2+).
- Evidence:
  - Layered modules: `src/main/java/com/booking/controller/OrderController.java:17`, `src/main/java/com/booking/service/OrderService.java:16`, `src/main/resources/mapper/OrderMapper.xml:1`
  - Legacy baseline tables still present: `docker/mysql/01-schema.sql:30`, `docker/mysql/01-schema.sql:40`, `docker/mysql/01-schema.sql:64`

#### 3.2 Maintainability and extensibility
- Conclusion: Partial Pass
- Rationale: Many modules are extensible/tested, but some correctness-sensitive paths depend on non-atomic update patterns (points balance) and controller-level idempotency handling inconsistencies.
- Evidence:
  - Points read-modify-write pattern: `src/main/java/com/booking/service/PointsService.java:102`, `src/main/java/com/booking/service/PointsService.java:114`
  - Idempotency write mismatch on not-found path: `src/main/java/com/booking/controller/OrderController.java:171`, `src/main/java/com/booking/controller/OrderController.java:180`

### 4. Engineering Details and Professionalism

#### 4.1 Error handling, logging, validation, API design
- Conclusion: Partial Pass
- Rationale:
  - Strengths: centralized exception mapping, meaningful validation, secret validation, encryption key derivation, notification retry/terminal handling.
  - Material risks: session-based auth lacks visible anti-CSRF and session-rotation hardening in login flow.
- Evidence:
  - Exception mapping: `src/main/java/com/booking/controller/GlobalExceptionHandler.java:13`
  - Secret validation and fail-fast: `src/main/java/com/booking/config/SecretsValidator.java:33`, `src/main/java/com/booking/config/SecretsValidator.java:54`
  - AES-256/PBKDF2 key derivation: `src/main/java/com/booking/util/FieldEncryptor.java:18`, `src/main/java/com/booking/util/FieldEncryptor.java:33`
  - Session login sets current user on existing session without explicit regeneration: `src/main/java/com/booking/controller/AuthController.java:37`
  - Auth filter validates session presence only (no CSRF token/origin check): `src/main/java/com/booking/filter/AuthFilter.java:45`
  - No Spring Security web stack configured (crypto only): `pom.xml:29`

#### 4.2 Product-like delivery vs demo shape
- Conclusion: Pass
- Rationale: System is organized and behaves like a product delivery (role UX, lifecycle workflows, admin governance, audit-like trails, substantial tests).
- Evidence:
  - Role navigation/UI pages: `src/main/resources/static/js/app.js:93`, `src/main/resources/static/js/app.js:113`
  - Workflow and audit trail: `src/main/java/com/booking/controller/OrderController.java:49`, `src/main/java/com/booking/service/OrderService.java:387`
  - Test breadth: `api_tests/java/com/booking/api/StrictAuthAndLifecycleApiIT.java:194`, `unit_tests/java/com/booking/unit/OrderServiceTest.java:1`

### 5. Prompt Understanding and Requirement Fit

#### 5.1 Correct business understanding and constraints
- Conclusion: Partial Pass
- Rationale: Most core constraints are now directly implemented (offline queue/export model, SSE, strict order flow, idempotency, blacklist automation, local popular search terms), but role semantics for non-photographer providers and explicit delivery/pickup ETA semantics remain partially represented.
- Evidence:
  - Offline/no external behavior claim: `README.md:455`
  - Notification local queue + export path: `src/main/java/com/booking/controller/NotificationController.java:83`, `src/main/resources/mapper/NotificationMapper.xml:22`
  - SSE implementation: `src/main/java/com/booking/controller/ChatSseController.java:27`
  - Role set limited to CUSTOMER/PHOTOGRAPHER/ADMINISTRATOR: `docker/mysql/02-seed.sql:5`
  - ETA wording currently duration-based: `src/main/resources/static/js/app.js:265`

### 6. Aesthetics (frontend)

#### 6.1 Visual/interaction quality
- Conclusion: Pass
- Rationale: Statically, UI shows clear hierarchy, consistent spacing/components, responsive rules, validation feedback, and interaction states.
- Evidence:
  - Layout + hierarchy: `src/main/resources/static/index.html:53`, `src/main/resources/static/css/style.css:15`
  - Validation/feedback: `src/main/resources/static/css/style.css:42`, `src/main/resources/static/css/style.css:44`
  - Search suggestions/status tracker/chat/read indicators: `src/main/resources/static/js/app.js:188`, `src/main/resources/static/css/style.css:113`, `src/main/resources/static/js/app.js:543`
  - Responsive rules: `src/main/resources/static/css/style.css:200`
- Manual verification note:
  - Real browser rendering smoothness/performance and long-session interaction quality remain Manual Verification Required.

## 5. Issues / Suggestions (Severity-Rated)

### Blocker/High

1) Severity: High
- Title: Session-based authentication lacks explicit CSRF hardening controls
- Conclusion: Suspected Risk (static evidence indicates gap; exploitability still environment-dependent)
- Evidence:
  - Session-auth guard based on existing session user: `src/main/java/com/booking/filter/AuthFilter.java:45`
  - Login stores authenticated user in HttpSession: `src/main/java/com/booking/controller/AuthController.java:37`
  - No Spring Security web chain/CSRF module configured (only crypto dependency): `pom.xml:29`
- Impact:
  - Browser-based state-changing endpoints may be exposed to request-forgery risk depending on cookie/browser deployment settings.
- Minimum actionable fix:
  - Add explicit CSRF defense (synchronizer token or double-submit cookie) and/or strict Origin/Referer validation for mutating endpoints.
  - If adopting Spring Security web, configure CSRF policy and document exceptions clearly.
- Minimal verification path:
  - Manual security test with cross-origin form/script attempts against mutating endpoints in browser context.

2) Severity: High
- Title: Login flow does not explicitly rotate session on authentication (session fixation risk)
- Conclusion: Partial Fail
- Evidence:
  - Login sets `currentUser` on provided session without explicit session invalidation/regeneration: `src/main/java/com/booking/controller/AuthController.java:23`, `src/main/java/com/booking/controller/AuthController.java:37`
- Impact:
  - If a pre-auth session ID is attacker-controlled, fixation-style account hijack risk increases.
- Minimum actionable fix:
  - In login flow, invalidate old session and create a new session before storing authenticated identity.
  - Add API test covering session ID change after successful login.

### Medium

3) Severity: Medium
- Title: Idempotency cache key mismatch for not-found order path
- Conclusion: Fail
- Evidence:
  - Token lookup uses scoped key with `orderId`: `src/main/java/com/booking/controller/OrderController.java:171`
  - 404 path records response with `orderId=null`: `src/main/java/com/booking/controller/OrderController.java:180`
- Impact:
  - Duplicate replay behavior becomes inconsistent for missing-order requests; same token may not replay cached 404 as intended.
- Minimum actionable fix:
  - Record 404 using the same scoped key dimensions (`orderId` included) used during `checkToken`.

4) Severity: Medium
- Title: Points balance update logic is non-atomic under concurrent awards/deductions
- Conclusion: Partial Fail
- Evidence:
  - Read-modify-write pattern without lock/version check: `src/main/java/com/booking/service/PointsService.java:102`, `src/main/java/com/booking/service/PointsService.java:114`, `src/main/java/com/booking/service/PointsService.java:122`, `src/main/java/com/booking/service/PointsService.java:134`
- Impact:
  - Concurrent updates can lead to lost updates and inaccurate balances/audit expectations.
- Minimum actionable fix:
  - Use DB-atomic balance mutation (`UPDATE ... SET points_balance = points_balance + ?`) with transaction isolation and post-update read, or optimistic locking/version column.
  - Add concurrency-focused API test for simultaneous adjustments/awards.

5) Severity: Medium
- Title: Prompt seller model is narrower than requested (photographer-only role model)
- Conclusion: Partial Fail
- Evidence:
  - Seeded role set fixed to CUSTOMER/PHOTOGRAPHER/ADMINISTRATOR: `docker/mysql/02-seed.sql:5`
  - Listing ownership/business naming tied to photographer-specific fielding: `src/main/java/com/booking/service/ListingService.java:33`, `src/main/resources/mapper/ListingMapper.xml:66`
- Impact:
  - Requirement intent for broader provider types is only partially represented.
- Minimum actionable fix:
  - Generalize seller domain (e.g., provider role/category abstraction) and update role checks/UI labels accordingly.

6) Severity: Medium
- Title: Delivery/pickup ETA semantics are only partially implemented
- Conclusion: Partial Fail
- Evidence:
  - UI ETA display reflects service duration from start time: `src/main/resources/static/js/app.js:265`
  - Order schema has delivery mode but no explicit delivery/pickup ETA fields: `docker/mysql/03-migration-v2.sql:63`, `docker/mysql/06-migration-v4.sql:5`
- Impact:
  - Prompt expectation of explicit estimated delivery/pickup timing for physical fulfillment is not strongly modeled.
- Minimum actionable fix:
  - Add ETA fields/calculation rules for pickup/courier scenarios and expose them in product/order detail APIs/UI.

### Low

7) Severity: Low
- Title: Legacy V1 schema artifacts remain despite API removal
- Conclusion: Partial Fail
- Evidence:
  - Legacy tables remain in baseline schema/seed: `docker/mysql/01-schema.sql:30`, `docker/mysql/01-schema.sql:40`, `docker/mysql/01-schema.sql:64`, `docker/mysql/02-seed.sql:19`
  - Legacy endpoint behavior now 404: `api_tests/java/com/booking/api/CoverageBoostApiIT.java:193`
- Impact:
  - Increases migration complexity and onboarding confusion, though direct runtime surface is reduced.
- Minimum actionable fix:
  - Add migration to deprecate/archive unused legacy tables or clearly annotate long-term compatibility intent.

## 6. Security Review Summary

- authentication entry points: Partial Pass
  - Evidence: `src/main/java/com/booking/controller/AuthController.java:22`, `src/main/java/com/booking/service/AuthService.java:20`, `src/main/java/com/booking/filter/AuthFilter.java:45`
  - Reasoning: Login/logout/me and session guard are present; disabled-account revalidation is enforced per request. Residual risks: CSRF/session-fixation hardening gaps.

- route-level authorization: Pass
  - Evidence: `src/main/java/com/booking/controller/BlacklistController.java:24`, `src/main/java/com/booking/controller/PointsController.java:51`, `src/main/java/com/booking/controller/UserController.java:26`
  - Reasoning: Admin gates are consistently enforced on high-privilege routes.

- object-level authorization: Pass
  - Evidence: `src/main/java/com/booking/service/OrderService.java:67`, `src/main/java/com/booking/service/MessageService.java:128`, `src/main/java/com/booking/controller/MessageController.java:166`
  - Reasoning: Ownership/participant checks are present for core resources (orders, conversations, attachments).

- function-level authorization: Pass
  - Evidence: `src/main/java/com/booking/service/OrderService.java:149`, `src/main/java/com/booking/service/OrderService.java:162`, `src/main/java/com/booking/service/OrderService.java:243`
  - Reasoning: Action-level role constraints exist across lifecycle operations.

- tenant / user isolation: Partial Pass
  - Evidence: `src/main/java/com/booking/controller/OrderController.java:44`, `src/main/java/com/booking/controller/UserController.java:39`, `src/main/java/com/booking/service/AddressService.java:56`
  - Reasoning: Resource isolation is broadly implemented; concurrency consistency in points balance remains a correctness risk.

- admin / internal / debug protection: Partial Pass
  - Evidence: `src/main/resources/application.yml:36`, `src/main/resources/application.yml:39`, `api_tests/java/com/booking/api/SecurityFixesApiIT.java:62`
  - Reasoning: Actuator exposure is narrowed to `health`, and anonymous details are tested hidden; broader runtime hardening still depends on deployment posture.

## 7. Tests and Logging Review

- Unit tests
  - Conclusion: Pass
  - Evidence: `unit_tests/java/com/booking/unit/AuthFilterTest.java:49`, `unit_tests/java/com/booking/unit/IdempotencyServiceTest.java:37`, `unit_tests/java/com/booking/unit/PointsScopeTest.java:24`
  - Notes: Covers auth filter behavior, idempotency token logic, points scope logic, and service-level validations.

- API / integration tests
  - Conclusion: Pass
  - Evidence: `api_tests/java/com/booking/api/OrderWorkflowApiIT.java:71`, `api_tests/java/com/booking/api/StrictAuthAndLifecycleApiIT.java:194`, `api_tests/java/com/booking/api/SecurityHardeningApiIT.java:151`
  - Notes: Covers lifecycle, RBAC, disabled-session enforcement, concurrency oversell prevention, and endpoint hardening.

- Logging categories / observability
  - Conclusion: Partial Pass
  - Evidence: `src/main/java/com/booking/service/OrderService.java:331`, `src/main/java/com/booking/service/NotificationService.java:87`, `src/main/java/com/booking/service/LocalNotificationDispatcher.java:19`
  - Notes: Meaningful operational logs exist; structured context and security-focused audit fields are moderate rather than comprehensive.

- Sensitive-data leakage risk in logs / responses
  - Conclusion: Partial Pass
  - Evidence: `src/main/java/com/booking/domain/User.java:36`, `src/main/java/com/booking/domain/User.java:42`, `src/main/java/com/booking/service/NotificationService.java:212`, `api_tests/java/com/booking/api/CoverageBoostApiIT.java:230`
  - Notes: Password hash and phone are masked/hidden in serialization; photographer DTO test validates sensitive fields exclusion. Residual risk is primarily in broader session-hardening controls rather than obvious response leakage.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- Unit tests and API tests exist: Yes
- Frameworks: JUnit 5, Spring MockMvc, Mockito
- Entry points:
  - Surefire/Failsafe + JaCoCo config: `pom.xml:100`, `pom.xml:112`, `pom.xml:130`, `pom.xml:188`
  - Scripted runner: `run_tests.sh:1`
- Documentation provides test commands: `README.md:68`, `README.md:70`
- Static coverage artifact present: `test-results/jacoco-merged/jacoco.csv:1`

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Unauthenticated API access -> 401 | `unit_tests/java/com/booking/unit/AuthFilterTest.java:43` | `noSessionReturns401` checks filter status 401 | sufficient | None material | N/A |
| Disabled user blocked with existing session | `api_tests/java/com/booking/api/StrictAuthAndLifecycleApiIT.java:194`, `api_tests/java/com/booking/api/CoverageBoostApiIT.java:214` | Existing session transitions from allowed to forbidden after admin disable | sufficient | None material | N/A |
| Strict order lifecycle transitions | `api_tests/java/com/booking/api/OrderWorkflowApiIT.java:71` | Sequence confirm/pay/check-in/check-out/complete assertions | sufficient | None material | N/A |
| Idempotency scope by action+order | `api_tests/java/com/booking/api/SecurityFixesApiIT.java:90`, `unit_tests/java/com/booking/unit/IdempotencyServiceTest.java:62` | Same raw key valid across different action scopes | basically covered | Missing explicit API test for 404 not-found idempotency replay consistency | Add API test: repeated `/{missingId}/confirm` with same key must replay same cached response semantics |
| Oversell/concurrency control | `api_tests/java/com/booking/api/SecurityHardeningApiIT.java:151`, `api_tests/java/com/booking/api/StrictAuthAndLifecycleApiIT.java:185` | Parallel requests: exactly one booking success in contention scenario | sufficient | Runtime DB-isolation edge still environment-dependent | Add deterministic integration test with controlled transaction timing under MySQL profile |
| Address default uniqueness under race | `api_tests/java/com/booking/api/StrictAuthAndLifecycleApiIT.java:222` | Concurrent default-set attempt, assert at most one default | sufficient | None material | N/A |
| Search suggestions from local stored popular terms | `api_tests/java/com/booking/api/StrictAuthAndLifecycleApiIT.java:289`, `api_tests/java/com/booking/api/CoverageBoostApiIT.java:199` | Search first, then suggestions include recorded term | sufficient | None material | N/A |
| SSE messaging endpoint availability | `api_tests/java/com/booking/api/StrictAuthAndLifecycleApiIT.java:304`, `api_tests/java/com/booking/api/CoverageBoostApiIT.java:208` | `/api/messages/stream` returns 200 for authenticated session | basically covered | No prolonged-stream reliability/read-receipt push assertions | Add integration test for emitted `new-message` event and stream reconnection behavior |
| Actuator health anonymous detail masking | `api_tests/java/com/booking/api/SecurityFixesApiIT.java:62` | Asserts `status=UP` and `components` absent | sufficient | Runtime perimeter hardening still deployment-dependent | Add deployment-profile test expectations if future security middleware added |
| Points scope behavior | `unit_tests/java/com/booking/unit/PointsScopeTest.java:24`, `api_tests/java/com/booking/api/SecurityHardeningApiIT.java:226` | Scope fan-out/unit logic and API rule-driven award checks | basically covered | No explicit concurrent balance race test | Add concurrent award/deduct API test asserting ledger+balance consistency |
| CSRF/session-fixation hardening | (no direct test found) | N/A | insufficient | Security hardening path untested | Add browser-style integration tests for forged cross-site state-changing requests and session ID rotation on login |

### 8.3 Security Coverage Audit
- authentication: Pass
  - Tests cover login-protected access and active-session disable rejection (`unit_tests/java/com/booking/unit/AuthFilterTest.java:43`, `api_tests/java/com/booking/api/StrictAuthAndLifecycleApiIT.java:194`).
- route authorization: Pass
  - Admin route constraints are covered in RBAC/API suites (`api_tests/java/com/booking/api/RbacApiIT.java:63`).
- object-level authorization: Pass
  - Tests include message/order ownership/participant constraints (`api_tests/java/com/booking/api/SecurityHardeningApiIT.java:37`, `api_tests/java/com/booking/api/OrderWorkflowApiIT.java:203`).
- tenant / data isolation: Partial Pass
  - Core ownership checks are covered; concurrent points-balance correctness and some cross-session hardening are not deeply tested.
- admin / internal protection: Partial Pass
  - Health detail masking is tested (`api_tests/java/com/booking/api/SecurityFixesApiIT.java:62`), but broader browser-security controls (CSRF/fixation) are not directly covered.

### 8.4 Final Coverage Judgment
- Partial Pass
- Boundary explanation:
  - Major workflow, RBAC, object authorization, idempotency scope, and oversell prevention are substantively covered.
  - Severe defects could still remain undetected in browser-focused security hardening (CSRF/session fixation), points-balance race consistency, and specific idempotency replay edge cases for not-found operations.

## 9. Final Notes
- This revision is materially stronger than earlier states: legacy booking endpoints are removed from API surface, SSE and server-side search-term suggestions are present, disabled-session enforcement exists, and address default uniqueness now has DB-level trigger enforcement.
- Remaining material risks are concentrated in security hardening edge cases and consistency under concurrency rather than broad missing feature areas.
- All conclusions above are static-evidence based and avoid runtime claims beyond what code/tests can support.
