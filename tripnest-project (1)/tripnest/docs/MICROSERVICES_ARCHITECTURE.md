# TripNest Microservices Architecture

TripNest is organized as independently deployable domain services. Each business service owns its data and communicates with other services through HTTP APIs.

## Services

| Component | Responsibility | Database |
|---|---|---|
| user-service | registration, login, profile, users/admin | userdb |
| travel-service | destinations and travel packages | traveldb |
| hotel-service | hotel catalogue and reservations | hoteldb |
| transport-service | transport catalogue and reservations | transportdb |
| booking-service | booking lifecycle and booking snapshots | bookingdb |
| payment-service | payment records/status | paymentdb |
| api-gateway | single API entry point and routing | none |
| web | React static frontend | none |

## Database-per-service

Every business service has its own PostgreSQL container, database, credentials and persistent volume. No service should query another service's database directly.

## Communication

External requests enter through `api-gateway`:

- `/api/v1/auth/**` -> user-service
- `/api/v1/users/**` -> user-service
- `/api/v1/destinations/**` -> travel-service
- `/api/v1/packages/**` -> travel-service
- `/api/v1/hotels/**` -> hotel-service
- `/api/v1/transport/**` -> transport-service
- `/api/v1/bookings/**` -> booking-service
- `/api/v1/payments/**` -> payment-service

Booking uses service APIs for travel, hotel and transport information. Payment uses the booking service API. There are no cross-service database joins.

## Independent deployment

Each Spring Boot service is packaged into its own JAR and Docker image by the generic service Docker build. The service listens on port 8080 inside its own container, while the gateway provides stable external routing.

The `shared` Maven module contains cross-cutting security primitives (JWT verification, internal service authentication and common exception handling), not business entities or repositories. It therefore does not create a shared business database or couple service data models.

## Request flow

```text
Browser
   |
   v
Web container (React/static files)
   |
   v
API Gateway
   |
   +--> User Service ------> userdb
   +--> Travel Service ----> traveldb
   +--> Hotel Service -----> hoteldb
   +--> Transport Service -> transportdb
   +--> Booking Service ---> bookingdb
   |         |
   |         +--> Travel/Hotel/Transport APIs
   |
   +--> Payment Service ---> paymentdb
             |
             +--> Booking API
```
