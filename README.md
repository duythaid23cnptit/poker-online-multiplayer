# Poker Online Multiplayer

A university Network Programming project for real-time multiplayer Texas Hold'em. The system uses a React/TypeScript client and a Java 26 Spring Boot 3.5.16 modular-monolith server, communicating through REST and authenticated WebSocket/STOMP. Game state is server-authoritative.

## Current status

Phases 0 through 9B, 10A, 10B.1, and 10B.2 are frozen. Phase 10B.3 implements authenticated, persisted, post-commit room-chat delivery over STOMP. Phase 10C adds connection-derived account presence and accepted-friend status notifications; real-MySQL/STOMP validation remains required before either phase can be frozen. Chat REST history and the full frontend remain later work.

Presence is `ONLINE` while an account has at least one authenticated STOMP session and `OFFLINE` when it has none. Multiple tabs/devices are reference-counted by a thread-safe, single-server in-memory registry. The persisted player-profile projection is reset to `OFFLINE` on server startup so a process crash cannot leave stale online state. Online/offline friend status is a specification requirement; multi-session correctness and startup reconciliation are supporting implementation enhancements.

The approved baseline distinguishes persistent Account Chips from Table Chips transferred through server-controlled buy-in/cash-out, and distinguishes a continuous Game Session from each Poker Hand it contains. Room/player lifecycle, 60-second reconnect handling, and authoritative timeout/leave behavior are specified in the architecture documents.

## Documentation

- [Architecture](docs/architecture/architecture.md)
- [Module boundaries](docs/architecture/module-boundaries.md)
- [Concurrency model](docs/architecture/concurrency.md)
- [REST API proposal](docs/api/rest-api.md)
- [WebSocket/STOMP protocol](docs/api/websocket-protocol.md)
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
npm run build
npm test
```
