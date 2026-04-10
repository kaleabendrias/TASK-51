# Business Logic Questions Log

This document records requirement ambiguities raised during implementation, the working interpretation used, and the resulting solution in the current codebase.

## 1. Seller Role Scope: Photographer-only or Broader Provider Model?
- Question: The core flow originally centered on photographers, but business wording also implied broader service providers.
- My Understanding: The platform should support photographer and non-photographer sellers under a unified provider concept.
- Solution: Added `SERVICE_PROVIDER` role and aligned listing/order provider permissions and discovery endpoints to include both provider types.

## 2. Delivery/Pickup ETA Semantics
- Question: Should ETA be a UI hint only (duration text) or a persisted operational field?
- My Understanding: ETA should be explicit and persisted for fulfillment modes (pickup/courier), not only inferred from duration text.
- Solution: Added `delivery_eta` and `pickup_eta` fields in order persistence and set values during order creation based on delivery mode.

## 3. Idempotency for Not-Found Order Actions
- Question: For repeated action calls on a non-existent order, should idempotency replay be deterministic?
- My Understanding: Yes, replay should remain consistent for the same key/action/order scope, including 404 outcomes.
- Solution: Ensured 404 responses are recorded with the same scoped key dimensions used by token checks, and added regression tests for replay behavior.

## 4. Points Balance Concurrency Behavior
- Question: Is read-modify-write acceptable for points updates under concurrent admin and workflow operations?
- My Understanding: No, points balance updates must be atomic to avoid lost updates and ledger/balance divergence.
- Solution: Replaced non-atomic updates with atomic SQL balance adjustments and added concurrent award tests.

## 5. CSRF Hardening in Session-Based APIs
- Question: With session cookies, should mutating API routes enforce anti-CSRF checks even in local/offline deployment?
- My Understanding: Yes, state-changing endpoints should enforce request-origin validation.
- Solution: Added `CsrfFilter` enforcing Origin/Referer host checks on POST/PUT/PATCH/DELETE (with auth endpoint exceptions) and test coverage for pass/fail scenarios.

## 6. Session Fixation on Login
- Question: Can login reuse the pre-auth session safely?
- My Understanding: Login should always rotate session identifiers to reduce fixation risk.
- Solution: Updated login flow to invalidate old session and create a fresh authenticated session; added session-rotation tests.

## 7. Legacy Schema Artifacts After API Consolidation
- Question: When legacy booking endpoints were removed, should legacy schema objects remain in baseline?
- My Understanding: Unused legacy schema should be cleaned to reduce maintenance ambiguity and migration noise.
- Solution: Simplified V1 schema/seed footprint to current baseline entities and moved new capabilities into forward migrations.

## 8. Single Default Address Enforcement Under Concurrency
- Question: Is application-layer default-clearing sufficient for enforcing one default address per user?
- My Understanding: No, DB-level enforcement is required to guarantee integrity under concurrent writes.
- Solution: Kept transactional service lock/clear behavior and added DB trigger enforcement for single-default invariant.

## 9. Real-time Messaging Requirement
- Question: Is periodic polling acceptable for real-time coordination?
- My Understanding: Push-based delivery better matches real-time requirement.
- Solution: Implemented SSE stream endpoint and frontend subscription path for push notifications.

## 10. Sensitive Data Exposure in Discovery APIs
- Question: Should user discovery endpoints return full user entities?
- My Understanding: No, discovery endpoints should return minimal DTOs only.
- Solution: Added role-specific DTOs for photographer/provider discovery and excluded sensitive fields (email/phone/enabled/password metadata).
