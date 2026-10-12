# Implementation plan — Tourism & Travel Booking System

## Product and architecture

Deliver a browser-based trip-planning and booking application matching the supplied SRS. The executable full-stack path will be a React/TypeScript client backed by six independently runnable Spring Boot services that communicate over versioned REST APIs. The service boundaries are User, Destination & Travel Package, Hotel, Transportation, Booking, and Payment. The UI will use relative `/api/v1/...` paths; a reverse-proxy container routes each path to its service, avoiding browser CORS configuration and keeping the client API contract the same in local Compose and managed preview.

Each Spring service owns its own PostgreSQL database and migrations. The Compose demonstration will use one PostgreSQL server with a separately provisioned database and least-privilege account per service to keep a college-demo stack practical; services will have distinct JDBC URLs, schemas, migrations, and credentials and will never query another service's tables. The README will explain how to move a database to its own PostgreSQL instance for independent scaling/failure isolation. Cross-service booking data is resolved through REST calls, not shared-table access.

The Java 21 Maven multi-module reactor will contain shared API/security utilities and six independently executable Spring Boot modules. Use Spring Web, Validation, Spring Security, BCrypt password hashing, signed JWT bearer sessions, Spring Data JPA, Flyway migrations, PostgreSQL runtime drivers, and OpenAPI/Swagger UI. Include Maven Wrapper scripts so the project does not require a system Maven installation. A build stage packages the modules once; Docker Compose runs one container per service, a frontend/reverse-proxy container, and PostgreSQL. Health/readiness endpoints and seeded catalogue data make the stack demonstrable. Keep payment-provider logic behind a Java interface with an explicitly non-production MockPaymentProvider that can deterministically return success/failure; do not collect/store card numbers or imply that a real charge occurred. Real providers and third-party travel feeds remain out of scope as specified by the SRS.

The existing managed project is a React/Vite/Express starter with server hosting available and managed database not enabled. Keep the browser deliverable within that initialized project. The complete authoritative Spring/PostgreSQL environment will be runnable through Docker Compose on a machine with Docker. In the managed Preview, where the sandbox has neither Docker nor Maven, the frontend will retain a clear local demo-mode fallback using seeded sample content; its API client will use the same REST contracts when a real backend is reachable. Never mislabel demo/local-storage actions as durable server bookings or real payments. The README will distinguish Preview demonstration from full Compose operation.

## User-visible requirements

- FR-01–02: traveller registration/login, secure profile view/update and password change; separate administrator role and protected admin actions.
- FR-03–05: keyword/location/category destination search and details; associated package discovery, inclusions, prices and package details.
- FR-06–07: date-aware hotel and transportation search, filtering and selection, with server-side date/availability validation.
- FR-08: create a destination-only booking at its administrator-managed starting price or combine it with optional packages/hotel/transport. Apply `TravelServiceFactory` to create hotel/transport items and `BookingBuilder` to calculate and validate optional components.
- FR-09 & FR-12: traveller-scoped booking history, detail and status; permit updates/cancellation only where lifecycle rules allow; administrators can review/update bookings across travellers.
- FR-10–11: persist payment outcomes; a successful mock payment transitions a pending booking to confirmed and produces a confirmation code. Failed payments do not confirm the booking.
- FR-13–17: administrator CRUD for destinations, packages, hotels and transportation, plus all-user booking oversight/status management. Protect all administrative operations by role.
- NFR: responsive accessible browser UI; input validation and safe errors; hashed passwords and signed/expiring access tokens; no payment-card storage; indexes and bounded search pagination; independent service deployment; health checks/logging; maintainable README/OpenAPI; no external provider/account secrets required for the demo.

## Project structure

- `client/` — React/TypeScript SPA pages, responsive design system, forms and typed REST client. Traveller discovery/booking/history/profile flows and separate admin workspace.
- `server/` — Existing Express/Vite host entry for the managed Preview and health/API proxy boundary; do not make it the authoritative business database/service layer.
- `backend/pom.xml` — Java 21 parent Maven reactor and dependency management.
- `backend/shared/` — shared API errors, JWT/security, validation and service-client utilities (no shared business database).
- `backend/user-service/` — accounts, registration/login/profile/roles and its own migration/database.
- `backend/travel-service/` — destinations and packages, search/details/admin CRUD, and its own migration/database.
- `backend/hotel-service/` — hotel listing/search/availability and admin CRUD, own database.
- `backend/transport-service/` — transport listing/search/availability and admin CRUD, own database.
- `backend/booking-service/` — orchestration via REST, Factory/Builder, lifecycle, history and admin oversight, own database.
- `backend/payment-service/` — pluggable provider, mock outcome processing/payment history, REST callback to booking, own database.
- `infra/` — PostgreSQL database/bootstrap, Nginx path routing, Dockerfiles and Compose wiring.
- `docs/` — REST API catalogue, ER/data-ownership view and FR-to-service traceability; OpenAPI endpoints remain available per service.
- `plan.md`, `TODO.md`, `README.md`, `.env.example` — decisions, outcome criteria, operator setup, demo credentials/secrets and troubleshooting.

## Visual direction

- **Design movement:** Contemporary travel editorial—an independent trip journal translated into a polished booking workspace.
- **Core principles:** destination-led storytelling; crisp, calm scanning; transparent prices and trip status; clear traveller/admin affordances.
- **Color philosophy:** warm paper and deep evergreen establish trust and an outdoor travel character; mineral slate supports dense data; a restrained sun-coral accent marks booking actions and confirmation without making payment screens feel urgent.
- **Layout paradigm:** asymmetric editorial destination feature followed by a left-aligned search rail and horizontal destination/package shelves; practical booking/admin screens use compact side navigation and readable tables rather than decorative center grids.
- **Signature elements:** route-line/wayfinder motif, capsule status chips, destination photo overlays with coordinates/region labels.
- **Interaction philosophy:** progressive trip planning, visible selected components and live price breakdown, explicit review before booking/payment, keyboard-accessible forms and role-aware navigation.
- **Animation:** brief (150–220 ms) fades and small vertical reveals on content entry; reduced-motion respected; no looping or distracting hero effects.
- **Typography:** editorial display serif such as DM Serif Display for large destination headlines; Inter for controls, forms, tables and body text, with tabular numerals for prices.
- **Brand essence:** a single dependable place for independent travellers to turn a destination idea into one coordinated trip; **curious, composed, capable**.
- **Brand voice:** direct and place-aware, specific rather than breathless. Examples: “Make a week of the places you’ve saved.” “Your stay, ride and confirmation—together.”
- **Wordmark/logo:** a custom simple route-pin/compass mark paired with the product wordmark; do not use a default text glyph as the finished mark.
- **Signature brand color:** deep wayfinder evergreen.

## Complete build and run workflow

1. Build a usable web UI in the initialized Vite project and keep its route manifest synchronized with SPA routes.
2. Implement the six Java services with service-owned entity models, PostgreSQL configurations, Flyway migrations, validation, explicit REST DTOs, health endpoints, OpenAPI and integration boundaries. Use REST for cross-service calls and make booking/payment state transitions explicit.
3. Add database bootstrap and non-destructive sample destination/hotel/transport/package seed data. Seed an administrator only from documented local environment settings, hash the password, and require operators to replace demo values outside local use.
4. Add the Nginx API reverse-proxy and multi-service Docker Compose setup, environment example, Maven Wrapper, and a concise quick-start from a clean checkout (`docker compose up --build`). No paid service or API key is required.
5. Add React API-backed flows plus a clearly identified fallback preview mode, and document which data is durable in each mode.
6. Include a focused booking-builder JUnit suite for pricing, selected-item composition, validation, and destination-only totals; run the frontend typecheck, starter tests and production static build, then run Maven verification for every service. Report that full Compose integration and performance-under-load checks require a Docker-equipped host and were not executed in this sandbox.

## Material constraints and choices

- The SRS does not prescribe a database vendor or external payment provider. PostgreSQL and a clearly labelled mock payment provider are chosen for a self-contained demonstration.
- Browser frontend framework is not prescribed; the initialized React/Vite starter is reused. The business API remains Spring Boot REST.
- The sandbox includes JDK 21 and Maven after validation setup, so the complete backend reactor and its JUnit suite can be verified here. Docker is not installed; Compose YAML and bootstrap shell syntax are validated, but container startup must be run on a Docker-equipped host.
- This is a college-project implementation and local demo, not a production booking/payment platform. Do not claim inventory from live hotels/transport vendors, real charges, or production-grade distributed transactions.
