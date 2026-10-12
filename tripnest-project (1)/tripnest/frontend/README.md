# TripNest Independent UI Layer

TripNest now has a micro-frontend style UI layer with seven separately buildable applications:

- `shell` — entry/navigation shell
- `user-ui` — User domain UI
- `travel-ui` — Travel/destination/package UI
- `hotel-ui` — Hotel domain UI
- `transport-ui` — Transport domain UI
- `booking-ui` — Booking domain UI
- `payment-ui` — Payment domain UI

Each application has its own `package.json`, Vite build and port. Each calls the backend through the API Gateway (`VITE_API_BASE`, default `http://localhost:8080`).

Ports: 5173-5179.

Example:

```bash
cd frontend/booking-ui
pnpm install
pnpm build
pnpm dev
```

This is independent deployment at the UI application level: changing `hotel-ui` does not require rebuilding `booking-ui`.
