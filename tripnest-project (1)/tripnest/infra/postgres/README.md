# Database isolation

TripNest now uses one PostgreSQL container per microservice. The old shared PostgreSQL bootstrap script is no longer used.

Each service owns its database and credentials:
- user-service -> userdb
- travel-service -> traveldb
- hotel-service -> hoteldb
- transport-service -> transportdb
- booking-service -> bookingdb
- payment-service -> paymentdb

Services must access another service through its API, never through another service's database.
