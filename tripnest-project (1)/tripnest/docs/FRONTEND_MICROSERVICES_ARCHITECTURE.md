# TripNest Frontend: Independent UI Architecture

The previous `client` was a single React application containing all domains. It is retained for reference, but the active independent UI layer is under `/frontend`.

```text
                         Browser
                            |
                     frontend/shell
                            |
       +--------------------+--------------------+
       |        |          |        |            |
    user-ui travel-ui hotel-ui transport-ui booking-ui payment-ui
       |        |          |        |            |          |
       +--------------------+--------------------+----------+
                            |
                       API Gateway
                            |
       User Travel Hotel Transport Booking Payment Services
                            |
                  Service-owned databases
```

Each UI has its own package, Vite build, runtime port and deployment boundary. UI-to-service communication goes through the API Gateway instead of direct database access.
