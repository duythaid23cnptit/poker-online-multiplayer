# Poker Online Multiplayer

A university Network Programming project for real-time multiplayer Texas Hold'em. The planned system uses a React/TypeScript client and a Java 17 Spring Boot modular-monolith server, communicating through REST and authenticated WebSocket/STOMP. Game state is server-authoritative.

## Current status

Phase 0 architecture is frozen. Phase 4 adds authenticated room/lobby REST flows and the `/ws` STOMP foundation on top of Phase 3 authentication. It covers public/private rooms, membership, seats/spectators, ready state, atomic buy-in/cash-out, owner succession, and sanitized post-commit events. The Poker Engine is not implemented.

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
