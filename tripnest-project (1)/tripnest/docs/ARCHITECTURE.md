# Architecture and data ownership

## Services

| Service | Database | Owns | Calls |
|---|---|---|---|
| `user-service` | `userdb` | Traveller/admin accounts, BCrypt hashes, phone, role | None |
| `travel-service` | `traveldb` | Destinations and travel packages | None |
| `hotel-service` | `hoteldb` | Hotel catalogue, room inventory and dated reservations | None |
| `transport-service` | `transportdb` | Transport options, seats and dated reservations | None |
| `booking-service` | `bookingdb` | Booking owner, date/guest selection, price snapshots, status, payment reference, confirmation code | Reads destination/package/hotel/transport over REST; reserves and releases optional inventory over authenticated internal REST |
| `payment-service` | `paymentdb` | Payment-attempt records | Fetches an owned booking over REST, then reports the mock provider result to booking-service using the internal token |

Compose provisions six separate databases and restricted roles on one local PostgreSQL server. The databases are not shared tables; cross-service joins are intentionally avoided. Flyway owns each service's schema history and seed rows.

## Booking sequence

1. Traveller authenticates. The user service returns a signed 15-minute JWT carrying server-issued user ID/role; the UI holds it only for the current browser session. Services validate issuer, signature and expiry using `JWT_SECRET`; every service fails at startup if `JWT_SECRET` or `SERVICE_SECRET` is absent/short.
2. Traveller submits destination, optional package/hotel/transport IDs, dates and guest count. No total is accepted from the browser.
3. Booking service reads active destination/package and the current hotel/transport prices and availability over REST. It checks that the package belongs to the selected destination and calculates a total from current server-side prices.
4. `BookingBuilder` validates and builds a booking snapshot, including destination-only base pricing. `TravelServiceFactory` constructs zero, one or both optional hotel/transport components.
5. Hotel and transport services independently lock their own inventory rows, verify date/room/seat capacity, and persist active reservations keyed to the booking. If reservation creation fails, release is attempted; if release also fails, the booking is cancelled and `inventory_cleanup_required` is exposed to administrators for retry.
6. Traveller may update dates/guest count while pending and unpaid. The booking service recalculates from stored price snapshots and requests capacity-checked inventory updates. If a failed second update cannot be compensated, the booking is cancelled and marked for the same administrator retry path.
7. Payment service fetches the booking as the authenticated owner; its provider receives a server-calculated amount. A database lock serializes attempts per booking, and a unique index protects successful outcomes. The mock records its requested result and reports it through a service-token-protected callback.
8. Successful mock payment transitions to `CONFIRMED` and creates a confirmation code. A failure leaves it awaiting payment. An eligible cancellation releases active inventory.

## Security boundaries

- Browser receives a short-lived JWT, not `JWT_SECRET`, database passwords or `SERVICE_SECRET`. Session storage is still JavaScript-readable; the demo is bound to localhost and is not presented as a public production deployment.
- Each service has the same configured JWT verifier; ownership checks compare the authenticated principal to the booking/account owner.
- All catalogue mutations and admin listings/status updates require the signed `ADMIN` role.
- Internal reservations/payment-result endpoints require a constant-time-comparison `X-Service-Token` check even though the network security matcher permits service callers.
- PostgreSQL statements use parameterized values; credentials are supplied through environment variables and are not logged.
- HTTP error responses are normalized by shared advice; unexpected failures do not return stack traces in the API body.

## Limitations and extension seams

The provider contract is pluggable, but only a non-charging mock exists. Production payment integration must add a verified provider event/webhook, provider-level idempotency and refunds before enabling real charges. Inter-service inventory is coordinated by a small synchronous saga, not a distributed transaction; failed compensation is recorded and visible to administrators for manual retry, but automatic reconciliation is not included. Product deployment must therefore monitor failed operations and use TLS, backups, secret management, rate limits and alerts.
