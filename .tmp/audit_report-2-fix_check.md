# Audit Report 2 Fix Check

Date: 2026-04-10
Mode: Static-only follow-up (no runtime execution)
Reference baseline: .tmp/audit_report-2.md

## 1. Purpose
This follow-up report checks whether each issue listed in audit_report-2.md has been fixed in the current codebase, using static evidence only.

## 2. Scope and Method
- Reviewed only source, config, SQL migrations/seeds, and tests.
- Did not run project, Docker, or tests.
- For each prior issue, assigned one status:
  - Fixed
  - Partially Fixed
  - Not Fixed
- Each decision is backed by file:line evidence.

## 3. Fix Status Summary

| Prior Issue (audit_report-2.md) | Previous Severity | Current Status | Summary |
|---|---|---|---|
| Session-based auth lacked explicit CSRF hardening | High | Fixed | Dedicated CSRF filter now enforced on state-changing /api requests with Origin/Referer validation and test coverage. |
| Login did not rotate session (fixation risk) | High | Fixed | Login now invalidates old session and creates a new one before setting user context; tests added. |
| Idempotency cache key mismatch on 404 path | Medium | Fixed | 404 path now records response using the same scoped key dimensions (includes orderId), with replay test coverage. |
| Points balance updates non-atomic under concurrency | Medium | Fixed | Service now uses atomic SQL balance adjustments; concurrent API test added. |
| Seller model narrower than prompt (photographer-only) | Medium | Partially Fixed | SERVICE_PROVIDER role added in seed roles, but listing authorization logic still only allows PHOTOGRAPHER/ADMINISTRATOR. |
| Delivery/pickup ETA semantics only partially implemented | Medium | Not Fixed | UI still shows duration-based ETA text; no explicit logistics ETA model surfaced. |
| Legacy V1 schema artifacts remained | Low | Fixed | Legacy services/bookings/attachments schema/seed artifacts removed from migration set. |

## 4. Detailed Issue-by-Issue Verification

### Issue 1: CSRF hardening gap
- Previous: High
- Current status: Fixed
- Evidence:
  - New CSRF filter validates Origin/Referer for POST/PUT/PATCH/DELETE: repo/src/main/java/com/booking/filter/CsrfFilter.java:25, repo/src/main/java/com/booking/filter/CsrfFilter.java:48, repo/src/main/java/com/booking/filter/CsrfFilter.java:70
  - CSRF filter registered on /api/* and ordered before auth filter: repo/src/main/java/com/booking/config/WebConfig.java:21, repo/src/main/java/com/booking/config/WebConfig.java:25, repo/src/main/java/com/booking/config/WebConfig.java:30
  - API and unit tests added for CSRF behavior: repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java:33, repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java:44, repo/unit_tests/java/com/booking/unit/CsrfFilterTest.java:42
- Fix-check conclusion:
  - The previously reported explicit CSRF-hardening gap is now addressed by implemented server-side request-origin checks and corresponding tests.

### Issue 2: Session fixation risk at login
- Previous: High
- Current status: Fixed
- Evidence:
  - Login now invalidates existing session and creates a new one: repo/src/main/java/com/booking/controller/AuthController.java:38, repo/src/main/java/com/booking/controller/AuthController.java:41, repo/src/main/java/com/booking/controller/AuthController.java:43
  - Session-rotation tests added: repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java:93, repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java:108, repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java:112
- Fix-check conclusion:
  - The reported session-fixation risk condition (no visible session regeneration) is fixed in current login flow.

### Issue 3: Idempotency mismatch on not-found path
- Previous: Medium
- Current status: Fixed
- Evidence:
  - 404 branch now records with orderId (not null): repo/src/main/java/com/booking/controller/OrderController.java:179, repo/src/main/java/com/booking/controller/OrderController.java:180
  - Scoped check remains keyed by token+action+orderId: repo/src/main/java/com/booking/controller/OrderController.java:171
  - Replay test for 404 idempotency path added: repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java:210, repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java:217
- Fix-check conclusion:
  - Keying inconsistency identified in audit_report-2.md is resolved.

### Issue 4: Non-atomic points balance updates
- Previous: Medium
- Current status: Fixed
- Evidence:
  - Points service switched to atomic DB updates: repo/src/main/java/com/booking/service/PointsService.java:103, repo/src/main/java/com/booking/service/PointsService.java:123
  - Mapper includes atomic SQL operations: repo/src/main/resources/mapper/PointsLedgerMapper.xml:23, repo/src/main/resources/mapper/PointsLedgerMapper.xml:27
  - Concurrent balance correctness API test added: repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java:131, repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java:180
- Fix-check conclusion:
  - The prior lost-update risk from read-modify-write balance handling has been addressed.

### Issue 5: Seller model narrower than prompt
- Previous: Medium
- Current status: Partially Fixed
- Evidence:
  - SERVICE_PROVIDER role added to seed roles: repo/docker/mysql/02-seed.sql:9
  - Listing authorization still restricts create/my-listings paths to PHOTOGRAPHER/ADMINISTRATOR: repo/src/main/java/com/booking/service/ListingService.java:68, repo/src/main/java/com/booking/controller/ListingController.java:75
- Fix-check conclusion:
  - Role taxonomy broadened in seed data, but business authorization and flow semantics remain photographer-centric. Partial improvement only.

### Issue 6: Delivery/pickup ETA semantics partial
- Previous: Medium
- Current status: Not Fixed
- Evidence:
  - Listing detail still presents ETA as duration from scheduled start: repo/src/main/resources/static/js/app.js:265
  - Delivery mode options exist, but no separate logistics ETA model evidence in this UI path: repo/src/main/resources/static/js/app.js:273
- Fix-check conclusion:
  - The originally reported semantic gap remains.

### Issue 7: Legacy V1 schema artifacts remaining
- Previous: Low
- Current status: Fixed
- Evidence:
  - Baseline schema now contains roles/users/schema_version only in V1: repo/docker/mysql/01-schema.sql:6, repo/docker/mysql/01-schema.sql:13, repo/docker/mysql/01-schema.sql:30
  - No remaining CREATE/INSERT statements for legacy services/bookings/attachments across docker/mysql migration set (static grep check returned no matches).
- Fix-check conclusion:
  - The previously reported legacy schema artifact issue appears resolved.

## 5. Overall Fix Check Verdict
- Overall result: Mostly Fixed
- Count:
  - Fixed: 5
  - Partially Fixed: 1
  - Not Fixed: 1
- Remaining material follow-up:
  - Complete provider-role generalization beyond seed data.
  - Implement explicit logistics ETA model for pickup/courier scenarios if strict prompt semantics are required.

## 6. Static Boundary Note
- This report is static-only and does not assert runtime success.
- Any runtime exploitability/performance characteristics remain Manual Verification Required.
