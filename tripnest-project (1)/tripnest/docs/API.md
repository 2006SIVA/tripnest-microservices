# TripNest REST API

All client APIs use the same origin, `/api/v1`, and JSON unless stated otherwise. Send `Authorization: Bearer <JWT>` for authenticated routes. The API reverse-proxy is exposed through Nginx on port 8080; the six service containers are not published directly. Springdoc OpenAPI JSON and Swagger UI are available inside each service container at `/v3/api-docs` and `/swagger-ui/index.html` for local container inspection. This document is the public gateway route map.

## Authentication and users

| Method | Route | Access | Description |
|---|---|---|---|
| POST | `/auth/register` | Public | Create a traveller; JSON: `{ "name", "email", "password", "phone?" }`. Password is 8–72 characters. Returns a signed token and user. |
| POST | `/auth/login` | Public | Authenticate with `{ "email", "password" }`; returns a token and user. |
| GET | `/auth/profile` | Traveller/Admin | Read the authenticated user's profile. |
| PUT | `/auth/profile` | Traveller/Admin | Update `{ "name", "phone?" }`. |
| PUT | `/auth/profile/password` | Traveller/Admin | Change `{ "currentPassword", "newPassword" }`. |
| GET | `/auth/admin/users` | Admin | List up to 500 account summaries; password hashes are never returned. |

JWT claims are signed server-side and expire after 15 minutes; the browser holds them in session storage for the active session. Client-supplied user IDs/roles are not trusted for booking ownership.

## Catalogue

| Method | Route | Access | Request/filters |
|---|---|---|---|
| GET | `/destinations?q=&location=&category=&offset=` | Public | Search/filter destinations. |
| GET | `/destinations/{id}` | Public | Destination plus its active packages. |
| POST / PUT | `/destinations` / `/destinations/{id}` | Admin | `{ "name", "location", "category", "description", "startingPrice" }`. |
| DELETE | `/destinations/{id}` | Admin | Soft-remove destination and its packages. |
| GET | `/packages?destinationId=&q=` | Public | Search packages and their destination/price/inclusion data. |
| GET | `/packages/{id}` | Public | Package details. |
| POST / PUT / DELETE | `/packages` / `/packages/{id}` | Admin | `{ "destinationId", "name", "description", "inclusions", "price" }`; delete is soft. |
| GET | `/hotels?q=&location=&checkIn=&checkOut=&rooms=` | Public | Date-aware hotel inventory search. Provide both dates or neither. |
| GET | `/hotels/{id}?checkIn=&checkOut=&rooms=` | Public | Hotel details and availability for the requested range. |
| POST / PUT / DELETE | `/hotels` / `/hotels/{id}` | Admin | `{ "name", "location", "description", "nightlyPrice", "rooms" }`; delete is soft. |
| GET | `/transport?q=&travelDate=&seats=` | Public | Search seats by date and requested seats. |
| GET | `/transport/{id}?travelDate=&seats=` | Public | Option details and availability. |
| POST / PUT / DELETE | `/transport` / `/transport/{id}` | Admin | `{ "name", "origin", "destination", "mode", "price", "availableSeats" }`; delete is soft. |

## Bookings and payments

| Method | Route | Access | Description |
|---|---|---|---|
| POST | `/bookings` | Traveller/Admin | Create a booking. JSON: `{ "destinationId", "packageId?", "hotelId?", "transportId?", "startDate", "endDate", "guests" }`. Dates use ISO `YYYY-MM-DD`; end must follow start; guests 1–20. A destination can be booked at its administrator-managed starting price without extras; a selected package/hotel/transport is combined with the destination. Package association, price and date availability are revalidated server-side. |
| GET | `/bookings` | Traveller/Admin | Current user's history (latest 200). |
| GET | `/bookings/{id}` | Owner/Admin | Read a booking; another traveller receives 403. |
| PUT | `/bookings/{id}` | Owner/Admin | Update dates and guests for a pending, unpaid booking; inventory is adjusted and price recalculated from server-side snapshots. |
| DELETE | `/bookings/{id}` | Owner/Admin | Cancel an eligible pending/confirmed booking and release inventory. |
| GET | `/bookings/admin` | Admin | List latest 500 bookings across travellers. |
| PATCH | `/bookings/admin/{id}/status` | Admin | `{ "status": "PENDING_PAYMENT" | "CONFIRMED" | "CANCELLED" }`; confirmation is rejected until successful payment. |
| POST | `/payments` | Traveller/Admin | Simulated charge. JSON: `{ "bookingId", "outcome": "SUCCESS" | "FAILURE" }`. The server obtains amount/owner/status from the booking service; a client cannot supply an amount/card number. Returns provider `MOCK`, `realCharge: false`, a reference and confirmation code on success. |
| GET | `/payments?bookingId={id}` | Owner/Admin | Payment attempts for an owned booking. |
| GET | `/payments/admin` | Admin | Latest 500 payment attempts. |

Internal hotel/transport reservations and payment-result callbacks are service-token-protected. Although their routes appear under `/api/v1`, callers must present the shared secret header `X-Service-Token`; they are intended only for Compose-internal service-to-service traffic. Do not expose this secret to the browser.

Errors use JSON such as `{ "error": "VALIDATION_ERROR", "message": "...", "details": { ... } }`. `401` means authentication is missing/invalid; `403` means the authenticated role/owner is not permitted; `404` means the resource is not found; `409` means a state or inventory conflict; `503` means an internal dependency did not complete.

The payment interface is intentionally replaceable, but the included `MockPaymentProvider` does not connect to a financial processor. Mock attempts are serialized per booking and successful outcomes are unique/retry-safe. A real adapter would additionally need signed provider webhooks, provider-level idempotency keys, secure secret handling, cancellation/refund semantics, and an explicit deployment review.
