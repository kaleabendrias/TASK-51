# Test Coverage Audit

Date: 2026-04-17
Mode: Static inspection only (no execution)
Scope: endpoint mappings, API/unit/frontend tests, README, run_tests.sh

## Project Type Detection
- README top does not explicitly declare one canonical label from: backend/fullstack/web/android/ios/desktop.
- Inferred project type (light inspection): fullstack.
- Evidence:
  - repo/README.md: opening line says "A full-stack photography booking platform"
  - repo/src/main/resources/static/js/app.js exists (frontend)
  - repo/src/main/java/com/booking/controller/*.java exists (backend)

## Backend Endpoint Inventory
Resolved from @RequestMapping + method-level mappings under repo/src/main/java/com/booking/controller.

1. GET /
2. POST /api/auth/login
3. POST /api/auth/register
4. POST /api/auth/logout
5. GET /api/auth/me
6. GET /api/addresses
7. GET /api/addresses/{id}
8. POST /api/addresses
9. PUT /api/addresses/{id}
10. DELETE /api/addresses/{id}
11. GET /api/blacklist
12. GET /api/blacklist/{id}
13. GET /api/blacklist/user/{userId}
14. POST /api/blacklist
15. POST /api/blacklist/{id}/lift
16. GET /api/listings
17. GET /api/listings/search
18. GET /api/listings/search/suggestions
19. GET /api/listings/{id}
20. GET /api/listings/my
21. POST /api/listings
22. PUT /api/listings/{id}
23. GET /api/messages/stream
24. GET /api/messages/conversations
25. GET /api/messages/conversations/{id}
26. POST /api/messages/send
27. POST /api/messages/conversations/{id}/reply
28. POST /api/messages/conversations/{id}/image
29. GET /api/messages/attachments/{id}/download
30. GET /api/notifications
31. POST /api/notifications/{id}/read
32. POST /api/notifications/{id}/archive
33. GET /api/notifications/preferences
34. PUT /api/notifications/preferences
35. GET /api/notifications/export
36. POST /api/notifications/export
37. GET /api/orders
38. GET /api/orders/{id}
39. GET /api/orders/{id}/audit
40. POST /api/orders
41. POST /api/orders/{id}/confirm
42. POST /api/orders/{id}/pay
43. POST /api/orders/{id}/check-in
44. POST /api/orders/{id}/check-out
45. POST /api/orders/{id}/complete
46. POST /api/orders/{id}/cancel
47. POST /api/orders/{id}/refund
48. POST /api/orders/{id}/reschedule
49. GET /api/points/balance
50. GET /api/points/history
51. GET /api/points/rules
52. POST /api/points/rules
53. PUT /api/points/rules/{id}
54. POST /api/points/adjust
55. GET /api/points/adjustments
56. GET /api/points/leaderboard
57. POST /api/points/award
58. GET /api/timeslots/listing/{listingId}
59. GET /api/timeslots/listing/{listingId}/available
60. POST /api/timeslots
61. GET /api/users
62. GET /api/users/photographers
63. GET /api/users/providers
64. GET /api/users/{id}
65. PATCH /api/users/{id}
66. PUT /api/users/{id}
67. PATCH /api/users/{id}/enabled

## API Test Mapping Table
Coverage rule applied: endpoint is covered only when tests send request to exact METHOD + PATH.

| Endpoint | Covered | Test type | Test files | Evidence |
|---|---|---|---|---|
| GET / | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | rootPath_redirectsOrServesIndex |
| POST /api/auth/login | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | login_validCredentials_returns200AndSetsCookie |
| POST /api/auth/register | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | register_newUser_returns200AndUserId |
| POST /api/auth/logout | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | logout_invalidatesSession_subsequentCallReturns401 |
| GET /api/auth/me | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getMe_withValidSession_returns200AndCorrectUser |
| GET /api/addresses | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | listAddresses_withSession_returns200AndArray |
| GET /api/addresses/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getAddressById_withSession_returns200AndLabelField |
| POST /api/addresses | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | createAddress_withSession_returns200AndId |
| PUT /api/addresses/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | updateAndDeleteAddress_withSession_returns200 |
| DELETE /api/addresses/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | updateAndDeleteAddress_withSession_returns200 |
| GET /api/blacklist | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | listBlacklist_adminSession_returns200AndArray |
| GET /api/blacklist/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | createAndLiftBlacklistEntry_adminSession_returns200 |
| GET /api/blacklist/user/{userId} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | createAndLiftBlacklistEntry_adminSession_returns200 |
| POST /api/blacklist | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | createAndLiftBlacklistEntry_adminSession_returns200 |
| POST /api/blacklist/{id}/lift | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | createAndLiftBlacklistEntry_adminSession_returns200 |
| GET /api/listings | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | listAllListings_withSession_returns200AndArray |
| GET /api/listings/search | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | searchListings_withSession_returnsJsonPaginatedResult |
| GET /api/listings/search/suggestions | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | searchSuggestions_withSession_returns200AndStringArray |
| GET /api/listings/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getListingById_withSession_returns200AndPriceField |
| GET /api/listings/my | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | myListings_photographerSession_returns200AndOwnListings |
| POST /api/listings | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | createListing_photographerSession_returns200AndId |
| PUT /api/listings/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | updateListing_photographerSession_returns200AndUpdatedTitle |
| GET /api/messages/stream | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | sseStream_withSession_returns200AndEventStreamContentType |
| GET /api/messages/conversations | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | listConversations_withSession_returns200AndArray |
| GET /api/messages/conversations/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | sendMessage_andGetConversation_returns200AndConversationId |
| POST /api/messages/send | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | sendMessage_andGetConversation_returns200AndConversationId |
| POST /api/messages/conversations/{id}/reply | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | replyToConversation_returns200 |
| POST /api/messages/conversations/{id}/image | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | uploadImageToConversation_andDownloadAsParticipant_returns200 |
| GET /api/messages/attachments/{id}/download | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | uploadImageToConversation_andDownloadAsParticipant_returns200 |
| GET /api/notifications | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getNotifications_withSession_returns200AndArray |
| POST /api/notifications/{id}/read | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | markNotificationReadAndArchive_returns200 |
| POST /api/notifications/{id}/archive | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | markNotificationReadAndArchive_returns200 |
| GET /api/notifications/preferences | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getNotificationPreferences_withSession_returns200AndComplianceField |
| PUT /api/notifications/preferences | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | updateNotificationPreferences_withSession_returns200AndForcedCompliance |
| GET /api/notifications/export | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | exportNotifications_adminSession_returns200 |
| POST /api/notifications/export | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | postExportNotifications_adminSession_returns200AndExportCount |
| GET /api/orders | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | listOrders_customerSession_returns200Array |
| GET /api/orders/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getOrderById_returns200AndIdField |
| GET /api/orders/{id}/audit | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getOrderAudit_returns200AndAuditArray |
| POST /api/orders | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | createOrder_withAllRequiredFields_returns200AndOrderNumber |
| POST /api/orders/{id}/confirm | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | orderFullLifecycle_confirmPayCheckinCheckoutComplete_returnsCorrectFinalStatus |
| POST /api/orders/{id}/pay | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | orderFullLifecycle_confirmPayCheckinCheckoutComplete_returnsCorrectFinalStatus |
| POST /api/orders/{id}/check-in | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | orderFullLifecycle_confirmPayCheckinCheckoutComplete_returnsCorrectFinalStatus |
| POST /api/orders/{id}/check-out | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | orderFullLifecycle_confirmPayCheckinCheckoutComplete_returnsCorrectFinalStatus |
| POST /api/orders/{id}/complete | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | orderFullLifecycle_confirmPayCheckinCheckoutComplete_returnsCorrectFinalStatus |
| POST /api/orders/{id}/cancel | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | cancelOrder_beforeConfirm_returns200AndCancelledStatus |
| POST /api/orders/{id}/refund | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | refundOrder_adminSession_returns200AndRefundedStatus |
| POST /api/orders/{id}/reschedule | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | rescheduleOrder_updatesToNewSlot |
| GET /api/points/balance | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | pointsBalance_withSession_returnsNonNegativeBalance |
| GET /api/points/history | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | pointsHistory_withSession_returns200AndArray |
| GET /api/points/rules | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | pointsRules_adminSession_returns200AndAtLeastTwoRules |
| POST /api/points/rules | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | createPointsRule_adminSession_returns200AndRule |
| PUT /api/points/rules/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | updatePointsRule_adminSession_returns200 |
| POST /api/points/adjust | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | adjustPoints_adminSession_returns200AndBalance |
| GET /api/points/adjustments | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getPointsAdjustments_adminSession_returns200AndArray |
| GET /api/points/leaderboard | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | pointsLeaderboard_withSession_returns200AndDescendingOrder |
| POST /api/points/award | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | awardPoints_adminSession_returns200AndResponseBody |
| GET /api/timeslots/listing/{listingId} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getTimeSlotsForListing_withSession_returns200AndArray |
| GET /api/timeslots/listing/{listingId}/available | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getAvailableTimeSlotsForListing_withSession_returns200 |
| POST /api/timeslots | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | helper createSlotOtw invoked by order lifecycle tests |
| GET /api/users | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | adminEndpoint_withAdminSession_returns200AndNonEmptyList |
| GET /api/users/photographers | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | listPhotographers_withSession_returns200AndArray |
| GET /api/users/providers | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | listProviders_withSession_returns200AndArray |
| GET /api/users/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | getUserById_adminSession_returns200AndUsername |
| PATCH /api/users/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | patchUser_adminSession_returns200 |
| PUT /api/users/{id} | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | putUser_adminSession_returns200 |
| PATCH /api/users/{id}/enabled | yes | true no-mock HTTP | api_tests/java/com/booking/api/OverTheWireApiIT.java | toggleUserEnabled_adminSession_returns200 |

## API Test Classification

### 1) True No-Mock HTTP
- api_tests/java/com/booking/api/OverTheWireApiIT.java
  - Evidence: @SpringBootTest(webEnvironment = RANDOM_PORT), RestTemplate, http://localhost:\{port\} calls.
- api_tests/java/com/booking/api/e2e/BaseE2eIT.java and subclasses under api_tests/java/com/booking/api/e2e/
  - Evidence: @SpringBootTest(webEnvironment = RANDOM_PORT), HtmlUnit WebClient against running server.

### 2) HTTP with Mocking (transport-level simulation)
- api_tests/java/com/booking/api/BaseApiIT.java + MockMvc-based subclasses under api_tests/java/com/booking/api/*.java (except OverTheWireApiIT and e2e classes).
  - Evidence: @AutoConfigureMockMvc, MockMvc field.

### 3) Non-HTTP (unit/integration without HTTP)
- unit_tests/java/com/booking/unit/*.java
- frontend_tests/tests/app.spec.js (frontend unit tests)

## Mock Detection

### API test suites
- No direct API-suite DI mocks detected:
  - No @MockBean in api_tests/java/**
  - No Mockito stubs inside API test classes
  - No jest.mock / vi.mock / sinon.stub in API suites
- Transport-level simulation present in MockMvc suites:
  - api_tests/java/com/booking/api/BaseApiIT.java (MockMvc)

### Unit/frontend test suites (non-API no-mock by definition)
- Backend unit tests use Mockito mocks broadly, e.g.:
  - unit_tests/java/com/booking/unit/NotificationServiceTest.java (@Mock + when)
  - unit_tests/java/com/booking/unit/OrderServiceTest.java (@Mock + when)
  - unit_tests/java/com/booking/unit/AuthFilterTest.java (@Mock)
- Frontend unit tests use Jest spies/mocks for browser/ajax boundaries, e.g.:
  - frontend_tests/tests/app.spec.js (jest.spyOn(global.$, 'ajax'))
  - frontend_tests/setup.js (global.$ stub, jsdom bootstrap)

## Coverage Summary

- Total endpoints: 67
- Endpoints with HTTP tests (any HTTP style): 67
- Endpoints with true no-mock HTTP tests: 67

Computed:
- HTTP coverage % = 67 / 67 * 100 = 100.0%
- True API coverage % = 67 / 67 * 100 = 100.0%

## Unit Test Summary

### Backend Unit Tests
Detected direct controller unit test files:
- unit_tests/java/com/booking/unit/AuthControllerTest.java
- unit_tests/java/com/booking/unit/AddressControllerTest.java
- unit_tests/java/com/booking/unit/BlacklistControllerTest.java
- unit_tests/java/com/booking/unit/ChatSseControllerTest.java
- unit_tests/java/com/booking/unit/ListingControllerTest.java
- unit_tests/java/com/booking/unit/MessageControllerTest.java
- unit_tests/java/com/booking/unit/NotificationControllerTest.java
- unit_tests/java/com/booking/unit/OrderControllerTest.java
- unit_tests/java/com/booking/unit/PointsControllerTest.java
- unit_tests/java/com/booking/unit/TimeSlotControllerTest.java
- unit_tests/java/com/booking/unit/UserControllerTest.java

Modules covered:
- Controllers: broad direct coverage (all API controllers except PageController)
- Services: broad direct coverage (AuthServiceTest, OrderServiceTest, ListingServiceTest, MessageServiceTest, AddressServiceTest, PointsServiceTest, BlacklistServiceTest, TimeSlotServiceTest, NotificationServiceTest, SearchTermServiceTest, IdempotencyServiceTest, UserServiceTest, ScheduledTaskServiceTest)
- Repositories/mappers: mostly indirectly covered via mocked service tests; no dedicated mapper contract unit suite
- Auth/guards/middleware: covered (AuthFilterTest, CsrfFilterTest, RoleGuardTest, SessionUtilTest)

Important backend modules NOT unit-tested directly:
- Controller: PageController (no dedicated PageControllerTest)
- Config classes: AppConfig, WebConfig, DataInitializer, EncryptionConfig (no direct unit test files under unit_tests)
- Mapper contract tests: absent as direct unit suites

### Frontend Unit Tests (STRICT REQUIREMENT)
Detection result against strict rules:
- Identifiable frontend test files exist: yes (frontend_tests/tests/app.spec.js)
- Tests target frontend logic/components: yes (Validate/API/App/page modules)
- Framework evident: yes (Jest + jsdom in frontend_tests/package.json)
- Tests import/execute real frontend module: yes (frontend_tests/setup.js loads src/main/resources/static/js/app.js)

Frontend test files:
- frontend_tests/tests/app.spec.js

Framework/tools detected:
- Jest 29
- jest-environment-jsdom

Components/modules covered (frontend):
- Validate helpers (email/phone/zip/required/minLen/stateValid/check/checkForm)
- API wrapper methods (get/post/put/patch/del/upload/idempotency header)
- App navigation/default page routing helpers
- Page/module smoke behavior for SearchPage, ListingDetailPage, OrdersPage, OrderDetailPage, AddressPage, ChatPage, NotifPage, PointsPage, MyListingsPage, PhotoDashPage, AdminDashPage, UsersAdminPage, BlacklistAdminPage, ServicesAdminPage, PointsAdminPage, LoginPage

Important frontend components/modules NOT tested deeply:
- Rich DOM state transitions and user-journey assertions are limited in many cases to "does not throw"
- Complex UI-state correctness (multi-step rendering state, error rendering variants, pagination state invariants) remains shallow compared to backend depth

Mandatory verdict:
- Frontend unit tests: PRESENT

Strict failure rule result:
- fullstack project + frontend tests are present
- mandatory strict check outcome: frontend unit depth is insufficient for critical user-journey/state validation
- CRITICAL GAP: fullstack project with insufficient frontend unit-test depth (strict failure rule triggered)

## Cross-Layer Observation
- Backend and API coverage are significantly deeper than frontend unit depth.
- Evidence of frontend shallowness: numerous smoke assertions based on `not.toThrow` in frontend_tests/tests/app.spec.js (e.g., Store.addSearchTerm, SearchPage.renderResults, App.navigate, LoginPage.init page flows).
- Balance assessment: backend-heavy test strategy with partial compensation from FE<->BE browser E2E, but frontend unit-level behavioral invariants remain under-specified.

## API Observability Check

Status: mixed-strong

Strong evidence:
- OverTheWireApiIT asserts endpoint, request setup, status, and response payload fields across many tests.
- Example references:
  - createOrder_withAllRequiredFields_returns200AndOrderNumber
  - getOrderAudit_returns200AndAuditArray
  - postExportNotifications_adminSession_returns200AndExportCount

Weak spots:
- Several frontend unit tests and some MockMvc tests rely on status/smoke assertions more than deep payload or full behavioral state assertions.

## Tests Check

- Success paths: strong coverage present.
- Failure/authorization paths: broad (401/403/400/404 across auth, users, blacklist, orders, points).
- Edge cases: present (idempotency, security hardening, lifecycle transitions, SSE auth).
- Validation: present.
- Integration boundaries: strong (MockMvc + over-the-wire + browser E2E).
- Assertion quality: uneven; strongest in over-the-wire API tests, shallower in many frontend smoke tests.

run_tests.sh audit:
- Docker-based execution: OK (docker build + docker run)
- Local runtime dependency installs in script: none required

## End-to-End Expectations (fullstack)
- Real FE<->BE E2E tests exist under api_tests/java/com/booking/api/e2e/*.java.
- This compensates for some frontend unit-depth weakness.

## Test Coverage Score (0-100)
- 90 / 100

## Score Rationale
- + Complete endpoint coverage with exact METHOD+PATH evidence
- + True no-mock HTTP coverage for all discovered endpoints
- + Multi-layer strategy: unit + MockMvc + over-the-wire + browser E2E
- + Broad backend unit coverage including most controllers and services
- - Frontend unit tests include many shallow "does not throw" checks
- - Config and mapper contract areas lack direct unit-test depth

## Key Gaps
1. CRITICAL GAP: Frontend unit tests are present but insufficient in depth; many assertions are smoke-only (`not.toThrow`) rather than strict state/output checks in frontend_tests/tests/app.spec.js.
2. Config classes are not directly unit-tested.
3. Mapper-level contract tests are not directly present.

## Confidence & Assumptions
- Confidence: high
- Assumptions:
  - Endpoint inventory scope is limited to repo/src/main/java/com/booking/controller mappings.
  - Static evidence only; no runtime execution performed.

## Test Coverage Verdict
- PARTIAL PASS (high API/backend coverage, but strict-mode CRITICAL GAP exists for frontend unit-test sufficiency)

---

# README Audit

Audited file: repo/README.md

## Hard Gate Evaluation

### Formatting
- PASS
- Markdown structure is clean and sectioned.

### Startup Instructions (backend/fullstack)
- PASS
- Explicit docker-compose up is present.

### Access Method
- PASS
- URL and port provided:
  - http://localhost:8080
  - http://localhost:8080/actuator/health

### Verification Method
- PASS
- Concrete curl verification and UI walkthrough are included.

### Environment Rules (strict: no runtime installs/manual DB setup)
- PASS
- No npm install/pip install/apt-get/manual DB setup steps.
- Docker-contained run path emphasized.

### Demo Credentials (conditional)
- PASS
- README provides username/email/password and roles.
- Roles represented: ADMINISTRATOR, PHOTOGRAPHER, CUSTOMER.

## Engineering Quality

- Tech stack clarity: strong
- Architecture explanation: strong
- Testing instructions: strong (run_tests.sh modes + coverage gate)
- Security/roles explanation: strong
- Workflows and operations: strong (run/stop/reset/verify)
- Presentation quality: high

## High Priority Issues
1. Canonical project-type label is not explicitly declared at the top as one exact token from required set (backend/fullstack/web/android/ios/desktop).

## Medium Priority Issues
1. Verification instructions use cookies.txt but do not include explicit cleanup of temporary cookie artifact.

## Low Priority Issues
1. Endpoint-to-controller traceability map is not explicitly provided in README (operationally optional).

## Hard Gate Failures
- None

## README Verdict
- PASS

## README Compliance Verdict
- PASS (all hard gates passed)

---

# Final Verdicts

1. Test Coverage Audit: PARTIAL PASS (score 90/100; strict-mode CRITICAL GAP on frontend unit-test sufficiency)
2. README Audit: PASS
