# Unified Audit Report: Test Coverage + README (Strict Static Mode)

Date: 2026-04-15
Scope: static inspection only (no runtime execution)

## 1) Test Coverage Audit

### Final Verdict
- Verdict: PARTIAL PASS
- Reason: endpoint HTTP coverage is broad, but true no-mock over-the-wire coverage is limited relative to total endpoint surface.

### Backend Endpoint Inventory
Total endpoints identified from controllers: 66

Controllers used:
- src/main/java/com/booking/controller/AddressController.java
- src/main/java/com/booking/controller/AuthController.java
- src/main/java/com/booking/controller/BlacklistController.java
- src/main/java/com/booking/controller/ChatSseController.java
- src/main/java/com/booking/controller/ListingController.java
- src/main/java/com/booking/controller/MessageController.java
- src/main/java/com/booking/controller/NotificationController.java
- src/main/java/com/booking/controller/OrderController.java
- src/main/java/com/booking/controller/PageController.java
- src/main/java/com/booking/controller/PointsController.java
- src/main/java/com/booking/controller/TimeSlotController.java
- src/main/java/com/booking/controller/UserController.java

Endpoint list (METHOD + PATH):
- GET /
- GET /api/addresses
- GET /api/addresses/{id}
- POST /api/addresses
- PUT /api/addresses/{id}
- DELETE /api/addresses/{id}
- POST /api/auth/login
- POST /api/auth/register
- POST /api/auth/logout
- GET /api/auth/me
- GET /api/blacklist
- GET /api/blacklist/{id}
- GET /api/blacklist/user/{userId}
- POST /api/blacklist
- POST /api/blacklist/{id}/lift
- GET /api/listings
- GET /api/listings/search
- GET /api/listings/search/suggestions
- GET /api/listings/{id}
- GET /api/listings/my
- POST /api/listings
- PUT /api/listings/{id}
- GET /api/messages/stream
- GET /api/messages/conversations
- GET /api/messages/conversations/{id}
- POST /api/messages/send
- POST /api/messages/conversations/{id}/reply
- POST /api/messages/conversations/{id}/image
- GET /api/messages/attachments/{id}/download
- GET /api/notifications
- POST /api/notifications/{id}/read
- POST /api/notifications/{id}/archive
- GET /api/notifications/preferences
- PUT /api/notifications/preferences
- GET /api/notifications/export
- POST /api/notifications/export
- GET /api/orders
- GET /api/orders/{id}
- GET /api/orders/{id}/audit
- POST /api/orders
- POST /api/orders/{id}/confirm
- POST /api/orders/{id}/pay
- POST /api/orders/{id}/check-in
- POST /api/orders/{id}/check-out
- POST /api/orders/{id}/complete
- POST /api/orders/{id}/cancel
- POST /api/orders/{id}/refund
- POST /api/orders/{id}/reschedule
- GET /api/points/balance
- GET /api/points/history
- GET /api/points/rules
- POST /api/points/rules
- PUT /api/points/rules/{id}
- POST /api/points/adjust
- GET /api/points/adjustments
- GET /api/points/leaderboard
- POST /api/points/award
- GET /api/timeslots/listing/{listingId}
- GET /api/timeslots/listing/{listingId}/available
- POST /api/timeslots
- GET /api/users
- GET /api/users/photographers
- GET /api/users/providers
- GET /api/users/{id}
- PATCH /api/users/{id}
- PUT /api/users/{id}
- PATCH /api/users/{id}/enabled

### API Test Classification
1. True No-Mock HTTP (embedded server + real TCP transport)
- api_tests/java/com/booking/api/OverTheWireApiIT.java
- api_tests/java/com/booking/api/e2e/BaseE2eIT.java and e2e/* (browser-driven real HTTP)

2. HTTP with Mocking (transport mocked/in-process dispatcher)
- All MockMvc-based API suites extending BaseApiIT:
- api_tests/java/com/booking/api/BaseApiIT.java
- Representative suites: AuthApiIT, OrderWorkflowApiIT, SearchFilterApiIT, SecurityHardeningApiIT, UncoveredEndpointsApiIT, AddressNotifPointsApiIT, etc.

3. Non-HTTP (unit/integration without HTTP)
- None in api_tests/java; non-HTTP tests are under unit_tests/java.

### Mock Detection Rules: Findings
- API test tree contains no explicit business-layer mocking signals:
- No @MockBean, @Mock, Mockito.when/doReturn/doThrow in api_tests/java/com/booking/api (search evidence via grep over that tree).
- MockMvc is used broadly (transport simulation), via api_tests/java/com/booking/api/BaseApiIT.java.

### API Test Mapping Table (Per Endpoint)
Legend:
- covered: yes/no
- test type: true no-mock HTTP | HTTP with mocking

| Endpoint | Covered | Test Type | Evidence |
|---|---|---|---|
| GET / | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java (rootPath_redirectsToIndex), api_tests/java/com/booking/api/BookingServiceAttachmentApiIT.java:45 |
| GET /api/addresses | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:22 |
| GET /api/addresses/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/CoverageBoostApiIT.java:176 |
| POST /api/addresses | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:30 |
| PUT /api/addresses/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:44 |
| DELETE /api/addresses/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:79 |
| POST /api/auth/login | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java (login_validCredentials_returns200AndSetsCookie) |
| POST /api/auth/register | yes | HTTP with mocking | api_tests/java/com/booking/api/AuthApiIT.java:58 |
| POST /api/auth/logout | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java (logout_invalidatesSession_subsequentCallReturns401) |
| GET /api/auth/me | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java (getMe_withValidSession_returns200AndCorrectUser) |
| GET /api/blacklist | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:284 |
| GET /api/blacklist/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/UncoveredEndpointsApiIT.java:354 |
| GET /api/blacklist/user/{userId} | yes | HTTP with mocking | api_tests/java/com/booking/api/CoverageBoostApiIT.java:231 |
| POST /api/blacklist | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:264 |
| POST /api/blacklist/{id}/lift | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:297 |
| GET /api/listings | yes | HTTP with mocking | api_tests/java/com/booking/api/BookingServiceAttachmentApiIT.java:108 |
| GET /api/listings/search | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java (searchListings_withSession_returnsJsonArray) |
| GET /api/listings/search/suggestions | yes | HTTP with mocking | api_tests/java/com/booking/api/SearchFilterApiIT.java:150 |
| GET /api/listings/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/SearchFilterApiIT.java:127 |
| GET /api/listings/my | yes | HTTP with mocking | api_tests/java/com/booking/api/RbacApiIT.java:62 |
| POST /api/listings | yes | HTTP with mocking | api_tests/java/com/booking/api/CoverageBoostApiIT.java:238 |
| PUT /api/listings/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/CoverageBoostApiIT.java:247 |
| GET /api/messages/stream | yes | HTTP with mocking | api_tests/java/com/booking/api/CoverageBoostApiIT.java:277 |
| GET /api/messages/conversations | yes | HTTP with mocking | api_tests/java/com/booking/api/ChatAttachmentApiIT.java:153 |
| GET /api/messages/conversations/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/ChatAttachmentApiIT.java:137 |
| POST /api/messages/send | yes | HTTP with mocking | api_tests/java/com/booking/api/ChatAttachmentApiIT.java:40 |
| POST /api/messages/conversations/{id}/reply | yes | HTTP with mocking | api_tests/java/com/booking/api/UncoveredEndpointsApiIT.java:418 |
| POST /api/messages/conversations/{id}/image | yes | HTTP with mocking | api_tests/java/com/booking/api/ChatAttachmentApiIT.java:73 |
| GET /api/messages/attachments/{id}/download | yes | HTTP with mocking | api_tests/java/com/booking/api/CoverageBoostApiIT.java:157 |
| GET /api/notifications | yes | HTTP with mocking | api_tests/java/com/booking/api/SecurityFixesApiIT.java:204 |
| POST /api/notifications/{id}/read | yes | HTTP with mocking | api_tests/java/com/booking/api/SecurityFixesApiIT.java:211 |
| POST /api/notifications/{id}/archive | yes | HTTP with mocking | api_tests/java/com/booking/api/SecurityFixesApiIT.java:214 |
| GET /api/notifications/preferences | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:93 |
| PUT /api/notifications/preferences | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:102 |
| GET /api/notifications/export | yes | HTTP with mocking | api_tests/java/com/booking/api/StrictAuthAndLifecycleApiIT.java:95 |
| POST /api/notifications/export | yes | HTTP with mocking | api_tests/java/com/booking/api/UncoveredEndpointsApiIT.java:272 |
| GET /api/orders | yes | HTTP with mocking | api_tests/java/com/booking/api/RbacApiIT.java:87 |
| GET /api/orders/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/CoverageBoostApiIT.java:76 |
| GET /api/orders/{id}/audit | yes | HTTP with mocking | api_tests/java/com/booking/api/OrderWorkflowApiIT.java:170 |
| POST /api/orders | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java (createOrder_withAllRequiredFields_returns200AndOrderNumber) |
| POST /api/orders/{id}/confirm | yes | HTTP with mocking | api_tests/java/com/booking/api/OrderWorkflowApiIT.java:138 |
| POST /api/orders/{id}/pay | yes | HTTP with mocking | api_tests/java/com/booking/api/OrderWorkflowApiIT.java:144 |
| POST /api/orders/{id}/check-in | yes | HTTP with mocking | api_tests/java/com/booking/api/OrderWorkflowApiIT.java:152 |
| POST /api/orders/{id}/check-out | yes | HTTP with mocking | api_tests/java/com/booking/api/OrderWorkflowApiIT.java:158 |
| POST /api/orders/{id}/complete | yes | HTTP with mocking | api_tests/java/com/booking/api/OrderWorkflowApiIT.java:164 |
| POST /api/orders/{id}/cancel | yes | HTTP with mocking | api_tests/java/com/booking/api/OrderWorkflowApiIT.java:202 |
| POST /api/orders/{id}/refund | yes | HTTP with mocking | api_tests/java/com/booking/api/CoverageBoostApiIT.java:66 |
| POST /api/orders/{id}/reschedule | yes | HTTP with mocking | api_tests/java/com/booking/api/CoverageBoostApiIT.java:108 |
| GET /api/points/balance | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java (pointsBalance_withSession_returnsNonNegativeBalance) |
| GET /api/points/history | yes | HTTP with mocking | api_tests/java/com/booking/api/CoverageBoostApiIT.java:199 |
| GET /api/points/rules | yes | HTTP with mocking | api_tests/java/com/booking/api/RbacApiIT.java:70 |
| POST /api/points/rules | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:242 |
| PUT /api/points/rules/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/UncoveredEndpointsApiIT.java:141 |
| POST /api/points/adjust | yes | HTTP with mocking | api_tests/java/com/booking/api/FoundationFixesApiIT.java:36 |
| GET /api/points/adjustments | yes | HTTP with mocking | api_tests/java/com/booking/api/RbacApiIT.java:71 |
| GET /api/points/leaderboard | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:177 |
| POST /api/points/award | yes | HTTP with mocking | api_tests/java/com/booking/api/AddressNotifPointsApiIT.java:170 |
| GET /api/timeslots/listing/{listingId} | yes | HTTP with mocking | api_tests/java/com/booking/api/UncoveredEndpointsApiIT.java:318 |
| GET /api/timeslots/listing/{listingId}/available | yes | HTTP with mocking | api_tests/java/com/booking/api/SearchFilterApiIT.java:95 |
| POST /api/timeslots | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java (createOrder_withAllRequiredFields_returns200AndOrderNumber, slot pre-step) |
| GET /api/users | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java (adminEndpoint_withAdminSession_returns200) |
| GET /api/users/photographers | yes | HTTP with mocking | api_tests/java/com/booking/api/SecurityFixesApiIT.java:48 |
| GET /api/users/providers | yes | HTTP with mocking | api_tests/java/com/booking/api/FoundationFixesApiIT.java:253 |
| GET /api/users/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/FinalHardeningApiIT.java:87 |
| PATCH /api/users/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/FinalHardeningApiIT.java:79 |
| PUT /api/users/{id} | yes | HTTP with mocking | api_tests/java/com/booking/api/BookingServiceAttachmentApiIT.java:98 |
| PATCH /api/users/{id}/enabled | yes | HTTP with mocking | api_tests/java/com/booking/api/ContractAndConstraintApiIT.java:43 |

### Coverage Summary
- Total endpoints: 66
- Endpoints with HTTP tests (MockMvc or over-the-wire): 66
- Endpoints with TRUE no-mock HTTP tests (real server + TCP): 9
- HTTP coverage: 100.00% (66/66)
- True API coverage: 13.64% (9/66)

### Unit Test Summary
Unit test files present: 29 under unit_tests/java/com/booking/unit

Covered modules (evidence by file names):
- Controllers: ChatSseControllerTest, GlobalExceptionHandlerTest
- Services: AddressServiceTest, AuthServiceTest, BlacklistServiceTest, IdempotencyServiceTest, ListingServiceTest, MessageServiceTest, NotificationServiceTest, OrderServiceTest, PointsServiceTest, ScheduledTaskServiceTest, SearchTermServiceTest, TimeSlotServiceTest, UserServiceTest
- Filters/guards/middleware: AuthFilterTest, CsrfFilterTest, RoleGuardTest, SessionUtilTest
- Utility/security/domain components: FieldEncryptorTest, MaskUtilTest, SecretsValidatorTest, OrderStatusTest, IdempotencyTokenDomainTest, SearchTermDomainTest, PhotographerDtoTest, NotificationExportTest, NotificationDispatchTest, PointsScopeTest

Important modules not directly unit-tested:
- Most REST controllers (AddressController, AuthController, BlacklistController, ListingController, MessageController, NotificationController, OrderController, PointsController, TimeSlotController, UserController, PageController)
- Mapper layer behavior is primarily integration-covered, not unit-covered

### API Observability Check
- Strong/clear in many tests:
- Explicit method/path in requests (MockMvc or RestTemplate)
- Explicit request payload/query/header assertions in workflow/security tests
- Explicit response assertions (status + jsonPath/body field checks)
- Evidence: api_tests/java/com/booking/api/OrderWorkflowApiIT.java, SearchFilterApiIT.java, SecurityHardeningApiIT.java, OverTheWireApiIT.java

- Weak spots:
- Some tests assert mostly status codes without deep payload contract checks in the same test body
- Evidence examples: multiple status-only RBAC checks in api_tests/java/com/booking/api/RbacApiIT.java

### Tests Check
- Success paths: strong coverage (auth, listings, order lifecycle, points, notifications)
- Failure paths: strong coverage (403, 404, 400, invalid transitions, access control)
- Edge/validation: moderate-to-strong (invalid params, concurrency scenarios, idempotency)
- Auth/permissions: strong breadth across roles
- Integration boundaries: strong in MockMvc + present over-the-wire set
- Over-mocking risk: low in API tests; transport is simulated in MockMvc suite but business path is real
- run_tests.sh review:
- Docker-based execution confirmed (docker build + docker run in run_tests.sh)
- No host package-manager/runtime install commands required in script

### Test Coverage Score (0-100)
- Score: 82/100

### Score Rationale
- + Full endpoint HTTP coverage (66/66)
- + Large and meaningful negative-path/security tests
- + Unit test breadth across core services and filters
- - True no-mock over-the-wire coverage low relative to endpoint count (9/66)
- - Controller-level unit isolation is sparse (most controllers tested via API only)

### Key Gaps
- Increase true over-the-wire endpoint coverage beyond current subset
- Add more explicit response-contract assertions in status-focused RBAC tests
- Consider targeted controller-unit tests for payload/validation edge contracts where integration tests are broad but indirect

### Confidence & Assumptions
- Confidence: High for static endpoint/test mapping and README gate checks
- Assumptions:
- Endpoint inventory is limited to active mappings in src/main/java/com/booking/controller
- Dynamic path construction in tests (string concatenation) is treated as valid exact-path coverage when method and route pattern match

---

## 2) README Audit

### Project Type Detection
- Declared type at top: Not explicitly labeled as one of {backend/fullstack/web/android/ios/desktop}
- Inferred type (strict mode): fullstack
- Evidence: repo/README.md opening description of SPA + backend + DB + Docker stack

### README Location
- Required file exists: PASS
- Evidence: repo/README.md

### Hard Gate Checks
1. Formatting/readability
- PASS
- Evidence: structured headings/tables/code blocks in repo/README.md

2. Startup instructions (fullstack requires docker compose up)
- PASS
- Evidence: repo/README.md section "Running the Application" includes `docker compose up`

3. Access method (URL + port)
- PASS
- Evidence: repo/README.md lists `http://localhost:8080` and health endpoint

4. Verification method
- PARTIAL FAIL
- Evidence: README gives health URL and testing commands, but lacks a concrete minimal manual verification flow (example: login as role X, perform booking action Y, expected result Z) and lacks API request examples in this document.

5. Environment rules (no runtime installs/manual DB setup)
- PASS
- Evidence: README states containerized execution and no host JDK/Maven/MySQL requirement; run path is Docker-first.

6. Demo credentials (all roles if auth exists)
- PASS
- Evidence: credential table includes ADMINISTRATOR, PHOTOGRAPHER, CUSTOMER roles with usernames/emails and password.

### Engineering Quality (README)
- Tech stack clarity: PASS
- Architecture explanation: PASS
- Testing instructions: PASS
- Security/roles explanation: PASS
- Workflow clarity: PARTIAL PASS (startup/test clear; runtime validation workflow could be sharper)
- Presentation quality: PASS

### High Priority Issues
- Missing explicit top-level project-type label (strict schema expects explicit tag)

### Medium Priority Issues
- Verification section is not operationally explicit for acceptance checks (no stepwise user-flow validation or curl/Postman examples)

### Low Priority Issues
- None critical beyond stricter acceptance formatting expectations

### Hard Gate Failures
- Verification Method: PARTIAL FAIL under strict interpretation due lack of concrete acceptance validation steps

### README Verdict
- PARTIAL PASS

---

## Combined Final Verdicts
- Test Coverage Audit: PARTIAL PASS
- README Audit: PARTIAL PASS

Overall combined strict-mode conclusion: PARTIAL PASS
