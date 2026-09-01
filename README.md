# Poker Online Multiplayer

A university Network Programming project for real-time multiplayer Texas Hold'em. The system uses a React/TypeScript client and a Java 26 Spring Boot 3.5.16 modular-monolith server, communicating through REST and authenticated WebSocket/STOMP. Game state is server-authoritative.

## Current status

The backend through Phase 10C is frozen, including authenticated room-chat delivery, connection-derived presence, friend-status notifications, and the Phase 8B-R ranking-boundary refactor. Phase 11A establishes the frontend foundation: authentication, session restoration, protected routing, an application shell, profile foundation, reusable UI primitives, and a low-level STOMP client foundation. Lobby, room, poker-table, social, ranking, history, statistics, analytics, and admin UI remain later work.

Presence is `ONLINE` while an account has at least one authenticated STOMP session and `OFFLINE` when it has none. Multiple tabs/devices are reference-counted by a thread-safe, single-server in-memory registry. The persisted player-profile projection is reset to `OFFLINE` on server startup so a process crash cannot leave stale online state. Online/offline friend status is a specification requirement; multi-session correctness and startup reconciliation are supporting implementation enhancements.

The approved baseline distinguishes persistent Account Chips from Table Chips transferred through server-controlled buy-in/cash-out, and distinguishes a continuous Game Session from each Poker Hand it contains. Room/player lifecycle, 60-second reconnect handling, and authoritative timeout/leave behavior are specified in the architecture documents.

## Documentation

- [Architecture](docs/architecture/architecture.md)
- [Module boundaries](docs/architecture/module-boundaries.md)
- [Concurrency model](docs/architecture/concurrency.md)
- [REST API proposal](docs/api/rest-api.md)
- [WebSocket/STOMP protocol](docs/api/websocket-protocol.md)
- [Frontend development guide](frontend/README.md)
- [Logical ER model](docs/database/erd.md)
- [Data dictionary](docs/database/data-dictionary.md)
- [Test plan](docs/testing/test-plan.md)

Repository-wide engineering constraints are recorded in [AGENTS.md](AGENTS.md).

## Development

### Backend

From `backend` on Windows:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd package
```

The default `bootstrap` profile starts without a database connection and denies application endpoints. To run Phase 3 locally, activate the `local` profile and provide `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and a Base64-encoded HMAC key in `JWT_SECRET`. Optional `JWT_ACCESS_TOKEN_EXPIRATION` and `JWT_REFRESH_TOKEN_EXPIRATION` values use Spring duration syntax and default to `15m` and `30d`. Do not commit credentials or signing keys.

MySQL integration tests are enabled only when `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD` exist in the current process. `TEST_DB_URL` must target exactly `poker_online_test`; an early guard rejects any other database before the Spring context and Flyway start. The test suite never runs Flyway clean or drops a database.

Example current-shell setup using developer-supplied values:

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/poker_online_test'
$env:TEST_DB_USERNAME = '<your-test-database-user>'
$env:TEST_DB_PASSWORD = '<your-test-database-password>'
.\mvnw.cmd test
```

These assignments affect only the current PowerShell process. Replace placeholders locally and never commit real credentials.

### Frontend

From `frontend`:

```powershell
npm install
npm run dev
npm test
npm run build
npm run lint
```

The frontend requires Node 22 and npm 10. It reads only public Vite configuration from `VITE_API_BASE_URL` and `VITE_WS_URL`; start from `frontend/.env.example` and never put passwords, JWT signing keys, database credentials, or tokens in frontend environment files. The development server proxies REST and WebSocket traffic to the local backend so the browser stays on the supported local origin. See the [frontend development guide](frontend/README.md) for the session, state-management, and local-integration model.
