# Audit Report 1 - Fix Check

Date: 2026-04-10
Mode: Static verification only (no runtime execution)
Source baseline: .tmp/audit_report-1.md

## 1) Overall Result
- Verified issues: 9
- Fixed (static evidence): 9
- Partially fixed: 0
- Not fixed: 0

Conclusion: All issues listed in audit_report-1.md have static evidence of remediation in current code/config.

## 2) Verification Method
- Compared each original issue with current repository state.
- Reviewed implementation/config/documentation lines directly tied to each finding.
- Did not run application, Docker, or tests.
- Runtime-only claims remain out of scope and are marked as requiring manual verification.

## 3) Issue-by-Issue Fix Check

### Issue 1: Hardcoded DB credentials and encryption key in committed runtime config
- Previous status: High / Fail
- Current status: Fixed (static)
- Evidence:
  - docker-compose now uses init-secrets + tmpfs volume, no inline DB passwords: repo/docker-compose.yml:3, repo/docker-compose.yml:26, repo/docker-compose.yml:29, repo/docker-compose.yml:58
  - application config resolves secrets via env placeholders: repo/src/main/resources/application.yml:6, repo/src/main/resources/application.yml:7, repo/src/main/resources/application.yml:8, repo/src/main/resources/application.yml:45
  - README documents no inline credentials and generated secrets flow: repo/README.md:47
- Notes:
  - Static evidence confirms the hardcoded-secret pattern from the previous audit is removed.

### Issue 2: Disabled accounts enforced only at login (not per request for active sessions)
- Previous status: High / Partial Fail
- Current status: Fixed (static)
- Evidence:
  - AuthFilter now revalidates enabled state from DB on every request: repo/src/main/java/com/booking/filter/AuthFilter.java:53, repo/src/main/java/com/booking/filter/AuthFilter.java:55
  - Disabled users trigger session invalidation + 403: repo/src/main/java/com/booking/filter/AuthFilter.java:56, repo/src/main/java/com/booking/filter/AuthFilter.java:57, repo/src/main/java/com/booking/filter/AuthFilter.java:58
- Notes:
  - The previously identified gap is now directly closed in request-path enforcement.

### Issue 3: Parallel legacy booking API weakening strict order architecture
- Previous status: High / Partial Fail
- Current status: Fixed (static)
- Evidence:
  - Active canonical workflow is mapped to /api/orders: repo/src/main/java/com/booking/controller/OrderController.java:18
  - BookingController class is no longer present in current controller tree (static file inventory check).
  - README states legacy surfaces were physically deleted and /api/bookings replaced by /api/orders: repo/README.md:296, repo/README.md:298, repo/README.md:302
- Notes:
  - Residual legacy table artifacts still exist in historical SQL bootstrap files, but no active Java API/controller surface for /api/bookings was found.

### Issue 4: Encryption implementation contradicted AES-256 claim (16-byte key truncation)
- Previous status: Medium / Fail
- Current status: Fixed (static)
- Evidence:
  - AES key length now explicitly 32 bytes (256-bit): repo/src/main/java/com/booking/util/FieldEncryptor.java:18
  - Key minimum enforces >= 32 chars: repo/src/main/java/com/booking/util/FieldEncryptor.java:28, repo/src/main/java/com/booking/util/FieldEncryptor.java:29
  - PBKDF2 derivation to 256-bit key: repo/src/main/java/com/booking/util/FieldEncryptor.java:33, repo/src/main/java/com/booking/util/FieldEncryptor.java:34
- Notes:
  - Prior 16-byte truncation behavior is no longer present in current implementation.

### Issue 5: Real-time chat requirement implemented as polling
- Previous status: Medium / Partial Fail
- Current status: Fixed (static)
- Evidence:
  - Frontend now uses SSE EventSource and labels replacement of legacy polling: repo/src/main/resources/static/js/app.js:503, repo/src/main/resources/static/js/app.js:504, repo/src/main/resources/static/js/app.js:508
  - Server-side SSE stream endpoint exists at /api/messages/stream: repo/src/main/java/com/booking/controller/ChatSseController.java:23, repo/src/main/java/com/booking/controller/ChatSseController.java:28
- Notes:
  - Runtime latency/connection behavior still requires manual verification in a live environment.

### Issue 6: Search suggestions were local recent history (not local popular terms store)
- Previous status: Medium / Partial Fail
- Current status: Fixed (static)
- Evidence:
  - Frontend now reads suggestions from server endpoint: repo/src/main/resources/static/js/app.js:69, repo/src/main/resources/static/js/app.js:73
  - Search calls record terms server-side: repo/src/main/java/com/booking/controller/ListingController.java:51, repo/src/main/java/com/booking/controller/ListingController.java:53
  - Suggestions endpoint returns ranked popular terms: repo/src/main/java/com/booking/controller/ListingController.java:60, repo/src/main/java/com/booking/controller/ListingController.java:62
  - DB support added for term frequency/recency ranking: repo/docker/mysql/08-migration-v6.sql:7, repo/docker/mysql/08-migration-v6.sql:10, repo/docker/mysql/08-migration-v6.sql:11, repo/docker/mysql/08-migration-v6.sql:15
- Notes:
  - Behavior now aligns with server-side popular-term semantics.

### Issue 7: Single-default-address invariant not DB-enforced
- Previous status: Medium / Partial Fail
- Current status: Fixed (static)
- Evidence:
  - BEFORE INSERT trigger blocks second default per user: repo/docker/mysql/08-migration-v6.sql:27, repo/docker/mysql/08-migration-v6.sql:31, repo/docker/mysql/08-migration-v6.sql:33
  - BEFORE UPDATE trigger blocks transitions causing multiple defaults: repo/docker/mysql/08-migration-v6.sql:40, repo/docker/mysql/08-migration-v6.sql:44, repo/docker/mysql/08-migration-v6.sql:46
- Notes:
  - DB-layer enforcement now exists as requested.

### Issue 8: Photographer list endpoint exposed full user entities broadly
- Previous status: Medium / Partial Fail
- Current status: Fixed (static)
- Evidence:
  - Endpoint now maps to PhotographerDto instead of returning User entities: repo/src/main/java/com/booking/controller/UserController.java:3, repo/src/main/java/com/booking/controller/UserController.java:32, repo/src/main/java/com/booking/controller/UserController.java:34, repo/src/main/java/com/booking/controller/UserController.java:37
- Notes:
  - This reduces exposure of email/phone/enabled/role internals in photographer discovery responses.

### Issue 9: README and implementation drift on cryptographic detail/semantics
- Previous status: Low / Partial Fail
- Current status: Fixed (static)
- Evidence:
  - README documents AES-256 + PBKDF2 and 32-char minimum enforcement: repo/README.md:368, repo/README.md:390, repo/README.md:428, repo/README.md:443
  - Implementation now matches those claims: repo/src/main/java/com/booking/util/FieldEncryptor.java:18, repo/src/main/java/com/booking/util/FieldEncryptor.java:28, repo/src/main/java/com/booking/util/FieldEncryptor.java:33
- Notes:
  - Current docs and code are materially aligned for the previously flagged crypto mismatch.

## 4) Residual Risks / Manual Verification Items
- SSE reliability under disconnect/reconnect pressure should be runtime-tested.
- Trigger behavior under heavy concurrent address writes should be integration-tested against target MySQL runtime settings.
- Secret generation and injection flow should be validated in real deployment pipelines (permissions, rotation, backup handling).

## 5) Final Statement
Based on static inspection of current updates, all issues from audit_report-1.md are now fixed at code/config/design level.
