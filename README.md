# SmartPark — Smart Parking System

Real-time slot management platform — live slot availability tracking, automated
slot allocation, gate entry codes, time-boxed reservations with auto-expiry and
hourly billing, all behind JWT-secured role-based access control.

- **Backend:** Java 17, Spring Boot 3 (Web, Security, Data JPA, WebSocket/STOMP, Scheduling), H2 (default) or MySQL
- **Frontend:** React 18, Vite, React Router, Axios, STOMP over SockJS

## Features

- **Sensor/gate simulator (IoT layer)** — a background simulator keeps the facility
  alive with realistic traffic: vehicles park themselves, stay a few minutes and leave
  through the exit gate. It never starves real users (a buffer of slots is kept free;
  simulated vehicles are evicted first under pressure) and respects a target occupancy
  share. Admin can toggle it or run a cycle on demand.
- **Gate controller API (ANPR)** — `POST /api/gates/entry` simulates the barrier
  camera: a plate with a reservation is checked in automatically, an unknown plate
  becomes a walk-in allocation (when space allows), exit settles the bill. Barrier
  counters track entries/exits, detect **tailgating** (two vehicles within 3 s) and
  cross-check against real slot occupancy — anomalies hit the activity feed.
- **Advance bookings** — schedule a window up to 7 days ahead; the allocation engine
  assigns the best matching slot shortly before start (nothing sits idle all day),
  with a 15-minute retry grace if the lot is full.
- **Season passes** — monthly passes for a zone or the whole facility (10 % off for
  3+ months, 20 % for 12). Covered sessions bill ₹0 automatically at check-out.
- **Automated slot allocation engine** — reservations pick the best available slot
  automatically (lowest zone, then lowest slot number) under a database row lock
  (`SELECT ... FOR UPDATE`), so concurrent requests can never double-book. Optional
  zone and slot-type preferences.
- **Slot types** — STANDARD, EV_CHARGING, ACCESSIBLE and COMPACT slots, filterable
  during reservation and shown on the board.
- **Entry codes with auto-expiry** — every reservation gets a 6-digit gate code with a
  time-boxed entry window (default 15 min, configurable). A scheduled sweeper warns the
  driver 5 minutes before expiry, then cancels unclaimed reservations and releases the slot.
- **Smart waitlist** — when the lot is full, users queue with optional zone/type
  preferences. The moment a slot frees up, the first matching queued user is
  auto-allocated a reservation with a fresh entry code.
- **Hourly billing with extensions** — zones are priced per hour (seeded: A ₹60, B ₹40,
  C ₹25). Billing uses the greater of actual parked time and the committed duration
  (expected + extension hours), rounded up per hour. Parked users can extend by +1h/+2h.
- **Overstay detection** — vehicles parked past their committed duration are flagged
  for attendants.
- **Notification center** — reservation created/cancelled, checked in/out with receipt,
  extensions, expiry warnings, waitlist allocation: all delivered to the in-app bell
  (unread badges, mark-all-read).
- **Saved vehicles** — up to 5 vehicle profiles per user, one-tap pick when reserving.
- **JWT authentication + login protection** — stateless HS256 tokens, BCrypt passwords,
  5-strike rate limiting with a 10-minute lockout.
- **Admin user management** — list users, change roles, disable/enable accounts.
  Disabled users can't log in and active sessions are blocked immediately.
- **Admin announcements** — broadcast a message to every connected user as a live
  banner + toast over WebSocket (`/topic/announcements`).
- **Analytics** — total revenue, revenue by zone, demand heatmap (check-ins by hour)
  with a "quietest window" hint, 7-day revenue trend, occupancy, sessions today,
  average duration, per-slot utilization.
- **Session log + CSV export** — paginated, filterable history (status / zone / date
  range) for admins; full-history CSV export for admins and per-user export.
- **Receipts** — per-session receipt modal with a scannable code card.
- **Role-based access control** — `ADMIN`, `ATTENDANT`, `USER`:
  - **USER**: live board with price estimates, typed allocation, waitlist, entry codes
    with countdown, extension, cancel, receipts, history export, spending stats.
  - **ATTENDANT**: verify entry codes, check vehicles in/out with automatic billing,
    overstay flags, search by code/vehicle/slot, toggle maintenance.
  - **ADMIN**: everything attendants can do, plus slot CRUD with custom rates/types,
    announcements, user management, analytics and the session log.
- **Seeded demo history** — 8 completed sessions across past days so the admin
  dashboard shows live analytics on first run.

## Project layout

```
backend/    Spring Boot application (port 8080)
frontend/   React application (port 5173, proxies /api and /ws to the backend)
```

## Run it

### 1. Backend

Requires Java 17+ and Maven.

```bash
cd backend
mvn spring-boot:run
```

The API starts on http://localhost:8080 with an in-memory H2 database, pre-seeded
with demo accounts and 24 priced slots (zones A, B, C):

| Role      | Email                 | Password       |
|-----------|-----------------------|----------------|
| ADMIN     | admin@parking.com     | admin123       |
| ATTENDANT | attendant@parking.com | attendant123   |
| USER      | user@parking.com      | user123        |

H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:parkingdb`, user `sa`).

To use MySQL instead:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql   # set DB_USERNAME / DB_PASSWORD as needed
```

### 2. Frontend

Requires Node 18+.

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 — sign in with one of the demo accounts (the login page has
tap-to-autofill demo chips).

## Try the full flow

1. Sign in as **user@parking.com** — the board shows zones with rates. Reserve a slot
   and get a 6-digit entry code with a live countdown.
2. In another window sign in as **attendant@parking.com** — find the reservation
   (search by code), **Check in** → slot turns `OCCUPIED` live everywhere.
3. **Check out** → the session is billed automatically and the slot frees up.
4. Miss the 15-minute entry window instead, and watch the sweeper release the slot.
5. As **admin@parking.com**: revenue by zone, occupancy analytics, slot CRUD with
   custom hourly rates.

## API overview

All endpoints except `/api/auth/**` require `Authorization: Bearer <jwt>`.

| Method | Endpoint                        | Roles             | Description |
|--------|---------------------------------|-------------------|-------------|
| POST   | `/api/auth/register`            | public            | Create a USER account |
| POST   | `/api/auth/login`               | public            | Login, returns JWT |
| GET    | `/api/slots`                    | any authenticated | Live slot board + counts + hourly rates |
| POST   | `/api/slots`                    | ADMIN             | Create a slot (optional custom rate) |
| PATCH  | `/api/slots/{id}/status`        | ADMIN, ATTENDANT  | Set `AVAILABLE` / `MAINTENANCE` |
| DELETE | `/api/slots/{id}`               | ADMIN             | Delete a slot |
| POST   | `/api/reservations`             | any authenticated | Auto-allocate + reserve `{vehicleNumber, zone?}` → entry code |
| GET    | `/api/reservations/my`          | any authenticated | Own reservation history with amounts |
| GET    | `/api/reservations/my/stats`    | any authenticated | Sessions count, total spent, active flag |
| GET    | `/api/reservations`             | ADMIN, ATTENDANT  | Active reservations (awaiting entry / parked) |
| POST   | `/api/reservations/{id}/cancel` | owner or ADMIN    | Cancel a reservation |
| POST   | `/api/reservations/{id}/check-in`  | ADMIN, ATTENDANT | Slot → `OCCUPIED` |
| POST   | `/api/reservations/{id}/check-out` | ADMIN, ATTENDANT | Slot → `AVAILABLE`, session billed and completed |
| POST   | `/api/reservations/{id}/extend`    | owner or ADMIN   | Extend a parked session (+1..12h) |
| GET    | `/api/reservations/my/export`      | any authenticated | Own history as CSV |
| GET    | `/api/vehicles` · `POST` · `DELETE /{id}` | any authenticated | Saved vehicle profiles |
| GET    | `/api/waitlist/my` · `POST` · `DELETE /{id}` | any authenticated | Smart waitlist |
| GET    | `/api/notifications/my` · `POST /read-all` | any authenticated | Notification center |
| GET    | `/api/admin/users` · `PATCH /users/{id}/role` · `PATCH /users/{id}/enabled` | ADMIN | User management |
| POST   | `/api/admin/announcements`         | ADMIN             | Broadcast to all connected clients |
| GET    | `/api/reports/sessions`            | ADMIN             | Filtered, paginated session log |
| GET    | `/api/reports/export`              | ADMIN             | Full history CSV |
| GET    | `/api/reports/usage`               | ADMIN             | Revenue, zone split, heatmap, 7-day trend |

**WebSocket:** STOMP over SockJS at `/ws` — subscribe to `/topic/slots` for the live
board and `/topic/announcements` for admin broadcasts.

## Configuration

Backend (`application.yml`):

- `app.jwt.secret` / env `APP_JWT_SECRET` — JWT signing key (≥ 64 chars in production)
- `app.jwt.expiration-ms` — token lifetime (default 24 h)
- `app.reservation.hold-minutes` / env `APP_RESERVATION_HOLDMINUTES` — entry window
  before auto-expiry (default 15)

## Architecture notes

- Slot state transitions are enforced: `AVAILABLE → RESERVED → OCCUPIED → AVAILABLE`
  (or `→ MAINTENANCE` when free). Direct status edits to `RESERVED`/`OCCUPIED` are
  rejected; those states are only reachable through the reservation flow.
- Board broadcasts are sent **after transaction commit**, so clients never see state
  that could still roll back.
- One active reservation per user and per vehicle; slot changes that would orphan an
  active reservation are refused.
- The expiry sweeper runs every 30 seconds and releases unclaimed slots without any
  manual intervention — keeping turnover high.

