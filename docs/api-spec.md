# Booking Portal API Specification

Base path: `/api`
Auth model: session cookie
CSRF model: state-changing methods require matching `Origin` or `Referer` host
Content type: `application/json` unless multipart/stream endpoints noted

## 1. Authentication
### POST `/api/auth/login`
- Body:
```json
{"username":"string","password":"string"}
```
- Success: `200` + user summary
- Errors: `400` missing fields, `401` invalid credentials
- Notes: rotates session on successful login

### POST `/api/auth/register`
- Body:
```json
{"username":"string","email":"string","password":"string","fullName":"string","phone":"string"}
```
- Success: `200`
- Errors: `400` validation / uniqueness failures

### POST `/api/auth/logout`
- Success: `200`

### GET `/api/auth/me`
- Success: `200`
- Errors: `401` unauthenticated

## 2. Listings and Search
### GET `/api/listings`
- Returns active listings

### GET `/api/listings/{id}`
- Success: `200`
- Errors: `404`

### GET `/api/listings/my`
- Roles: PHOTOGRAPHER, SERVICE_PROVIDER, ADMINISTRATOR

### POST `/api/listings`
- Roles: provider/admin
- Body: listing payload

### PUT `/api/listings/{id}`
- Roles: owner/admin
- Body: listing payload

### GET `/api/listings/search`
- Query params (optional):
  - `keyword`, `category`, `minPrice`, `maxPrice`
  - `location`, `locationState`, `locationCity`, `locationNeighborhood`
  - `theme`, `transportMode`, `minRating`, `availableDate`
  - `sortBy` (`newest|price_asc|price_desc|rating|duration`)
  - `page` (default `1`), `size` (default `20`)
- Response shape:
```json
{"items":[],"page":1,"size":20,"total":0,"totalPages":0}
```

### GET `/api/listings/search/suggestions`
- Query params: `limit` (default `15`)
- Returns ranked popular terms from local store

## 3. Time Slots
### GET `/api/timeslots/listing/{listingId}`

### GET `/api/timeslots/listing/{listingId}/available`
- Query params: `start` (ISO date), `end` (ISO date)

### POST `/api/timeslots`
- Roles: owner/admin

## 4. Orders
All state-changing order endpoints require header:
- `Idempotency-Key: <client-generated-key>`

### GET `/api/orders`
- Returns role-scoped order list

### GET `/api/orders/{id}`
- Errors: `403`, `404`

### GET `/api/orders/{id}/audit`
- Returns order action history

### POST `/api/orders`
- Body:
```json
{"listingId":1,"timeSlotId":1,"addressId":1,"deliveryMode":"ONSITE|PICKUP|COURIER","notes":"..."}
```
- Notes:
  - courier requires `addressId`
  - sets `deliveryEta` or `pickupEta` depending on mode

### POST `/api/orders/{id}/confirm`
### POST `/api/orders/{id}/pay`
- Body:
```json
{"amount":150.0,"paymentReference":"REF-123"}
```
### POST `/api/orders/{id}/check-in`
### POST `/api/orders/{id}/check-out`
### POST `/api/orders/{id}/complete`
### POST `/api/orders/{id}/cancel`
- Body:
```json
{"reason":"..."}
```
### POST `/api/orders/{id}/refund`
- Body:
```json
{"amount":100.0,"reason":"..."}
```
### POST `/api/orders/{id}/reschedule`
- Body:
```json
{"newTimeSlotId":5}
```

## 5. Addresses
### GET `/api/addresses`
### GET `/api/addresses/{id}`
### POST `/api/addresses`
### PUT `/api/addresses/{id}`
### DELETE `/api/addresses/{id}`
- Validation: US ZIP format and ZIP/state consistency
- Default rule: single default address per user (DB enforced)

## 6. Messaging
### GET `/api/messages/conversations`
### GET `/api/messages/conversations/{id}`
- Marks messages as read for caller

### POST `/api/messages/send`
- Body:
```json
{"recipientId":2,"content":"text","orderId":1}
```

### POST `/api/messages/conversations/{id}/reply`
- Body:
```json
{"content":"text"}
```

### POST `/api/messages/conversations/{id}/image`
- Multipart: `file`
- Constraints: JPEG/PNG only, max 5 MB

### GET `/api/messages/attachments/{id}/download`
- Participant/admin only

### GET `/api/messages/stream`
- Content type: `text/event-stream`
- SSE events: `connected`, `new-message`

## 7. Notifications
### GET `/api/notifications`
### POST `/api/notifications/{id}/read`
### POST `/api/notifications/{id}/archive`

### GET `/api/notifications/preferences`
### PUT `/api/notifications/preferences`
- Compliance preference is forced true

### GET `/api/notifications/export`
- Role: ADMINISTRATOR

### POST `/api/notifications/export`
- Role: ADMINISTRATOR
- Body:
```json
{"ids":[1,2,3]}
```

## 8. Users and Governance
### GET `/api/users`
- Role: ADMINISTRATOR

### GET `/api/users/{id}`
- Self or admin

### PATCH `/api/users/{id}`
### PUT `/api/users/{id}`
### PATCH `/api/users/{id}/enabled`
- Role: ADMINISTRATOR

### GET `/api/users/photographers`
- Public authenticated discovery DTO (sensitive fields excluded)

### GET `/api/users/providers`
- Generalized provider discovery DTO

## 9. Blacklist
### GET `/api/blacklist`
### GET `/api/blacklist/{id}`
### GET `/api/blacklist/user/{userId}`
### POST `/api/blacklist`
### POST `/api/blacklist/{id}/lift`
- Role: ADMINISTRATOR

## 10. Points
### GET `/api/points/balance`
### GET `/api/points/history`
### GET `/api/points/leaderboard`

### GET `/api/points/rules`
### POST `/api/points/rules`
### PUT `/api/points/rules/{id}`
### POST `/api/points/adjust`
### GET `/api/points/adjustments`
### POST `/api/points/award`
- Admin-only endpoints as enforced by role guard

## 11. Shared Error Patterns
Common response body:
```json
{"error":"human-readable message"}
```
Likely status codes:
- `400` validation/business rule failure
- `401` unauthenticated
- `403` forbidden/disabled/blacklisted/CSRF failure
- `404` resource not found
- `409` conflict (e.g., lifecycle transition constraints)
- `500` internal error
