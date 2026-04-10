# Audit Report 2 Fix Check

Date: 2026-04-10
Mode: Static-only verification (no runtime execution)
Source baseline: [.tmp/audit_report-2.md](.tmp/audit_report-2.md)

## Verdict
- Overall fix status: All previously listed issues are statically resolved (7/7 marked Fixed).
- Boundary: This is code/config/test evidence only; runtime behavior remains Manual Verification Required.

## Issue-by-Issue Fix Check

### 1) High: Session-based authentication lacked explicit CSRF hardening controls
- Previous status: Suspected Risk
- Current status: Fixed
- Fix evidence:
  - Dedicated CSRF filter implemented with Origin/Referer validation for state-changing methods: [src/main/java/com/booking/filter/CsrfFilter.java](repo/src/main/java/com/booking/filter/CsrfFilter.java#L17), [src/main/java/com/booking/filter/CsrfFilter.java](repo/src/main/java/com/booking/filter/CsrfFilter.java#L25), [src/main/java/com/booking/filter/CsrfFilter.java](repo/src/main/java/com/booking/filter/CsrfFilter.java#L57)
  - CSRF filter registered for API routes before auth filter: [src/main/java/com/booking/config/WebConfig.java](repo/src/main/java/com/booking/config/WebConfig.java#L21), [src/main/java/com/booking/config/WebConfig.java](repo/src/main/java/com/booking/config/WebConfig.java#L25)
  - API tests cover missing origin, foreign origin, matching origin, referer fallback: [api_tests/java/com/booking/api/FoundationFixesApiIT.java](repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java#L33), [api_tests/java/com/booking/api/FoundationFixesApiIT.java](repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java#L44), [api_tests/java/com/booking/api/FoundationFixesApiIT.java](repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java#L55), [api_tests/java/com/booking/api/FoundationFixesApiIT.java](repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java#L65)

### 2) High: Login flow did not rotate session on authentication (session fixation risk)
- Previous status: Partial Fail
- Current status: Fixed
- Fix evidence:
  - Login now invalidates old session and creates a fresh session: [src/main/java/com/booking/controller/AuthController.java](repo/src/main/java/com/booking/controller/AuthController.java#L38), [src/main/java/com/booking/controller/AuthController.java](repo/src/main/java/com/booking/controller/AuthController.java#L43)
  - Session fixation test added: [api_tests/java/com/booking/api/FoundationFixesApiIT.java](repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java#L94), [api_tests/java/com/booking/api/FoundationFixesApiIT.java](repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java#L108)

### 3) Medium: Idempotency cache key mismatch for not-found order path
- Previous status: Fail
- Current status: Fixed
- Fix evidence:
  - Not-found path now records response with scoped orderId (not null): [src/main/java/com/booking/controller/OrderController.java](repo/src/main/java/com/booking/controller/OrderController.java#L171), [src/main/java/com/booking/controller/OrderController.java](repo/src/main/java/com/booking/controller/OrderController.java#L180)
  - Replay test for 404 idempotency behavior added: [api_tests/java/com/booking/api/FoundationFixesApiIT.java](repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java#L210), [api_tests/java/com/booking/api/FoundationFixesApiIT.java](repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java#L217)

### 4) Medium: Points balance updates were non-atomic under concurrency
- Previous status: Partial Fail
- Current status: Fixed
- Fix evidence:
  - Service now uses atomic SQL balance mutation paths: [src/main/java/com/booking/service/PointsService.java](repo/src/main/java/com/booking/service/PointsService.java#L103), [src/main/java/com/booking/service/PointsService.java](repo/src/main/java/com/booking/service/PointsService.java#L123)
  - Mapper defines atomic update statements: [src/main/resources/mapper/PointsLedgerMapper.xml](repo/src/main/resources/mapper/PointsLedgerMapper.xml#L23), [src/main/resources/mapper/PointsLedgerMapper.xml](repo/src/main/resources/mapper/PointsLedgerMapper.xml#L27)
  - Concurrent points test added: [api_tests/java/com/booking/api/FoundationFixesApiIT.java](repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java#L132), [api_tests/java/com/booking/api/FoundationFixesApiIT.java](repo/api_tests/java/com/booking/api/FoundationFixesApiIT.java#L180)

### 5) Medium: Seller model narrower than requested (photographer-only)
- Previous status: Partial Fail
- Current status: Fixed
- Fix evidence:
  - New role introduced: SERVICE_PROVIDER in seed/migration: [docker/mysql/02-seed.sql](repo/docker/mysql/02-seed.sql#L9), [docker/mysql/09-migration-v7.sql](repo/docker/mysql/09-migration-v7.sql#L9)
  - Access checks now include SERVICE_PROVIDER in listing/order flows: [src/main/java/com/booking/controller/ListingController.java](repo/src/main/java/com/booking/controller/ListingController.java#L75), [src/main/java/com/booking/service/OrderService.java](repo/src/main/java/com/booking/service/OrderService.java#L155)
  - Provider discovery DTO/endpoint present: [src/main/java/com/booking/controller/UserController.java](repo/src/main/java/com/booking/controller/UserController.java#L41), [src/main/java/com/booking/domain/ProviderDto.java](repo/src/main/java/com/booking/domain/ProviderDto.java#L5)

### 6) Medium: Delivery/pickup ETA semantics only partially implemented
- Previous status: Partial Fail
- Current status: Fixed
- Fix evidence:
  - Explicit ETA fields added to schema: [docker/mysql/09-migration-v7.sql](repo/docker/mysql/09-migration-v7.sql#L5), [docker/mysql/09-migration-v7.sql](repo/docker/mysql/09-migration-v7.sql#L6)
  - Domain + mapper include `deliveryEta` and `pickupEta`: [src/main/java/com/booking/domain/Order.java](repo/src/main/java/com/booking/domain/Order.java#L24), [src/main/resources/mapper/OrderMapper.xml](repo/src/main/resources/mapper/OrderMapper.xml#L23), [src/main/resources/mapper/OrderMapper.xml](repo/src/main/resources/mapper/OrderMapper.xml#L84)
  - Order creation populates ETA based on delivery mode: [src/main/java/com/booking/service/OrderService.java](repo/src/main/java/com/booking/service/OrderService.java#L127), [src/main/java/com/booking/service/OrderService.java](repo/src/main/java/com/booking/service/OrderService.java#L129), [src/main/java/com/booking/service/OrderService.java](repo/src/main/java/com/booking/service/OrderService.java#L131)

### 7) Low: Legacy V1 schema artifacts remained despite API removal
- Previous status: Partial Fail
- Current status: Fixed
- Fix evidence:
  - V1 baseline now only includes core bootstrap tables (`roles`, `users`, `schema_version`), with no `services/bookings/attachments` tables: [docker/mysql/01-schema.sql](repo/docker/mysql/01-schema.sql#L6), [docker/mysql/01-schema.sql](repo/docker/mysql/01-schema.sql#L13), [docker/mysql/01-schema.sql](repo/docker/mysql/01-schema.sql#L30)
  - Seed file no longer inserts legacy booking/service records: [docker/mysql/02-seed.sql](repo/docker/mysql/02-seed.sql#L1), [docker/mysql/02-seed.sql](repo/docker/mysql/02-seed.sql#L13)

## Summary Table

| Prior Issue # | Prior Severity | Current Status |
|---|---|---|
| 1 | High | Fixed |
| 2 | High | Fixed |
| 3 | Medium | Fixed |
| 4 | Medium | Fixed |
| 5 | Medium | Fixed |
| 6 | Medium | Fixed |
| 7 | Low | Fixed |

## Static Boundary Note
- This fix check confirms code/config/test-level remediation only.
- Runtime exploit resistance, production network posture, and operational behavior are Manual Verification Required.
