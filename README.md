# Poker Online Multiplayer

Poker Online Multiplayer is a real-time Texas Hold'em web application built as a university Network Programming project. It combines a React and TypeScript client with a Java 26 Spring Boot modular monolith. REST handles authentication, queries, and lifecycle commands; authenticated WebSocket/STOMP connections carry room, gameplay, chat, presence, and notification updates.

The server is authoritative for cards, chips, pots, legal actions, turns, timers, winners, room membership, and game lifecycle. The browser sends intentions and renders authoritative snapshots and events.

## Release status

The **v1.0.0-rc1** tag is the frozen safety checkpoint. The working tree contains the contract-alignment candidate being validated for v1.0.0-rc2. The complete multiplayer flow is implemented from account registration and room creation through live play, reconnect recovery, session completion, persisted results, player analytics, and administration.

| Quality gate | Verified result |
|---|---:|
| Backend tests with real `TEST_DB_*` configuration | 633 passed, 0 failures, 0 errors, 0 skipped |
| Frontend tests | 208 passed across 46 files |
| Backend package | Passed |
| Frontend production build | Passed |
| Frontend lint | Passed |

The release label describes the current product state. The source manifests retain their existing development coordinates: backend `0.0.1-SNAPSHOT` and frontend `0.0.1`.

## Implemented features

- Account registration, login, access-token refresh, logout, protected routes, and editable player profiles.
- Persistent Account Chips with server-controlled buy-in, Table Chips, cash-out, and insufficient-balance validation.
- Public and password-protected private rooms with authoritative membership, 6-9 seats, spectators, live seat occupancy, and atomic seat assignment.
- Spectator-to-player **Take a seat** conversion without leaving the room or creating a duplicate membership. Existing authorized private-room members do not re-enter the room password.
- Waiting-room readiness and an explicit host-controlled start. Readiness alone never starts a game.
- Server-authoritative Texas Hold'em with blinds, dealing, betting, raises, calls, checks, folds, all-ins, automatic timeout actions, community cards, showdown, contribution-aware pots, side pots, uncalled-bet returns, ties, and odd-chip settlement.
- Private hole-card delivery, legal-action prompts, turn timers, completed-hand result dwell, automatic next hands, bust-out detection, and terminal Game Session completion.
- Active-game discovery, cold snapshot hydration, browser-refresh recovery, disconnect grace, reconnect, and historical final-hand snapshots.
- Startup reconciliation for process-crashed sessions: unrecoverable `ACTIVE` sessions become `ABORTED`, active memberships cash out exactly once, and stranded rooms leave `PLAYING` without misreporting a completed poker result.
- Authoritative Leave Game handling with deferred departure during an active hand and exactly-once Table Chip return.
- Persistent room chat with recent-history hydration, authenticated sender identity, idempotent client message IDs, and live delivery.
- Friend requests, friend lists, connection-derived presence, and private notifications.
- Player-facing lobby, room, poker-table, friends, rankings, rating history, performance analytics, and profile screens.
- Admin-only monitoring and moderation screens backed by the overview, user, room, game, hand, termination, and audit-log APIs.
- Strict role separation: `PLAYER` accounts use the poker application, while `ADMIN` accounts use a dedicated administration workspace and cannot participate in rooms, games, chat, or player social flows.

## Architecture

The backend is a client-server modular monolith organized by business feature:

```text
backend/src/main/java/com/ptit/poker/
├── common
├── auth
├── player
├── social
├── room
├── game
├── ranking
├── analytics
└── admin
```

Each feature owns its rules and persistence boundary. Controllers translate transport data and call application services. Domain code stays independent of Spring, HTTP, WebSocket, and JPA where practical. Cross-feature work uses narrow application ports, safe projections, and immutable internal events instead of another feature's repositories.

```text
React client
├── REST: authentication, profiles, rooms, game discovery/snapshots,
│         explicit start/leave, chat history, friends, and rankings
└── WebSocket/STOMP: room changes, game commands/events, private cards,
                     chat, presence, and notifications
                         │
                         ▼
Spring Boot modular monolith
├── application services and transactional lifecycle boundaries
├── authoritative poker engine and per-game runtime locks
├── turn, reconnect, and hand-transition schedulers
├── Spring Security and JWT/STOMP authorization
└── JPA/JDBC persistence + Flyway → MySQL 8
```

The frontend keeps server state in TanStack Query. Zustand is limited to client-owned session or transient state. Access tokens remain in memory; the opaque refresh token follows the implemented session-scoped browser-storage contract.

## Authoritative game and room model

### Account Chips and Table Chips

**Account Chips** are the player's persistent balance. **Table Chips** are transferred from that balance during an authoritative room buy-in. Individual poker actions change only Table Chips. Leaving legally or finishing a session returns the remaining Table Chips to the account exactly once.

Seat availability displayed by the client is guidance from the current room snapshot. The backend still locks and validates the room, seat, membership, and account balance atomically when a player confirms.

### Game Session and Poker Hand

A **Game Session** is one continuous period of play in a room. It contains one or more **Poker Hands**. Each hand has its own deal, board, actions, commitments, pots, awards, and result. The session continues with eligible players after the completed-hand dwell; it finishes when fewer than two eligible players remain and no reconnect grace can preserve the session.

### Host-controlled start

The room creator is the initial host and active spectator. Seated players toggle between `NOT_READY` and `READY`, but readiness has no game-start side effect.

```text
players take seats
      ↓
all seated players become READY
      ↓
room remains WAITING
      ↓
host presses Start game
      ↓
server revalidates host, WAITING status, at least two seated players,
all-ready state, valid stacks, and absence of an active session
      ↓
one Game Session starts and GAME_STARTED is published
```

Non-host starts are rejected, and duplicate or concurrent start requests cannot create multiple sessions.

### Disconnect, reconnect, and recovery

Authenticated STOMP connections drive online presence. Multiple tabs or devices are reference-counted in the current single-server process, and stale persisted presence is reset on server startup.

An active player who disconnects retains the seat during the configured 60-second reconnect grace. The runtime continues authoritatively; an expired turn produces `CHECK` when legal and otherwise `FOLD`. On refresh or login, the client queries the user's active game, hydrates an authoritative snapshot, restores public and private state, and reconnects to the same table. An expired reconnect grace finalizes departure according to the current hand lifecycle.

### Leave Game

Leaving during an active hand does not delete the player from that hand or withdraw committed chips:

```text
Leave game → LEAVING → authoritative fold/settlement
           → exclusion from later hands
           → remaining Table Chips returned once
           → membership finalized → client returns to Rooms
```

A repeated departure request while the first remains pending is idempotent. Waiting-room departure remains a separate immediate room-exit operation, including host transfer or empty-room closure.

### Chat

Room chat is available only to active members of a non-closed room. The server derives sender identity from the authenticated session, validates message content, and uses `(room_id, sender_user_id, client_message_id)` as the idempotency key. REST returns up to 100 recent messages; the client defaults to 50, merges history with `CHAT_MESSAGE` events, and removes duplicates.

## REST and WebSocket/STOMP

Representative REST endpoints under `/api/v1` include:

| Area | Endpoints |
|---|---|
| Authentication | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Shared identity | `GET /me` for `PLAYER` and `ADMIN` |
| Player profile | `PATCH /me` for `PLAYER` |
| Rooms | `GET/POST /rooms`, `GET /rooms/{roomId}`, `POST /rooms/{roomId}/join`, `/leave` |
| Games | `POST /games/rooms/{roomId}/start`, `POST /games/{gameId}/leave` |
| Recovery | `GET /games/{gameId}/snapshot`, `/games/active/room/{roomId}`, `/games/active/me` |
| Chat history | `GET /rooms/{roomId}/chat/messages?limit=50` |
| Social and rankings | `/friend-requests`, `/friends`, `/rankings/me`, `/rankings/leaderboard`, `/rankings/me/history` |
| Performance | `/players/me/statistics`, `/analytics/me/summary`, `/analytics/me/daily`, `/analytics/me/weekly` |
| Administration | `/admin/overview`, `/admin/users`, `/admin/rooms`, `/admin/games`, `/admin/audit-log`, and detail/moderation routes |

The native STOMP endpoint is `/ws`; SockJS is not used. The access token is sent in the STOMP `CONNECT` `Authorization` header, never in the URL. Every subscription and command requires an active `PLAYER`; admin sessions do not start the player realtime bootstrap.

| Direction | Destination | Purpose |
|---|---|---|
| Client → server | `/app/game/{gameId}/action` | Turn-bound poker action intentions with action IDs |
| Client → server | `/app/room/{roomId}/ready` | Readiness changes only |
| Client → server | `/app/room/{roomId}/chat` | Idempotent room chat commands |
| Server → clients | `/topic/lobby` | Lobby room changes |
| Server → members | `/topic/room/{roomId}` | Room lifecycle, discovery, and chat events |
| Server → observers | `/topic/game/{gameId}` | Public authoritative game events |
| Server → user | `/user/queue/private` | Hole cards and private game state |
| Server → user | `/user/queue/notifications` | Friendship and presence notifications |

Game events use typed envelopes and include `GAME_STARTED`, `HAND_STARTED`, `HOLE_CARDS`, `COMMUNITY_CARDS`, `YOUR_TURN`, `PLAYER_ACTION`, `TIMER_UPDATE`, `GAME_STATE_UPDATE`, `SHOWDOWN`, `GAME_RESULT`, `HAND_FINISHED`, and `COMMAND_ERROR`. Spectators receive public state but never hole cards.

## Technology stack

### Backend

- Java 26 and Spring Boot 3.5.16
- Spring MVC, Spring Security, Jakarta Validation, Spring Data JPA/Hibernate
- Native WebSocket/STOMP with `SimpMessagingTemplate`
- JJWT 0.13.0
- Flyway and MySQL 8.x
- Maven Wrapper 3.3.4 using Maven 3.9.16
- JUnit 5, Mockito, Spring Boot Test, and Spring Security Test

### Frontend

- React 19.2, TypeScript 7, and Vite 8
- React Router 7
- TanStack Query 5 and Zustand 5
- React Hook Form 7 and Zod 4
- Tailwind CSS 4
- `@stomp/stompjs` 7
- Vitest 4, React Testing Library 16, and ESLint 10

## Local development

### Requirements

- Java 26
- MySQL 8.x
- Node.js 22 and npm 10

Create separate development and test schemas:

```sql
CREATE DATABASE poker_online;
CREATE DATABASE poker_online_test;
```

Use `poker_online` for local application data. Use `poker_online_test` only for integration tests. The test safety initializer rejects a `TEST_DB_URL` whose database name is not exactly `poker_online_test`; Flyway clean is disabled.

### Start the backend

From `backend`, set credentials only in the local process environment:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_URL = 'jdbc:mysql://localhost:3306/poker_online'
$env:DB_USERNAME = '<your-database-user>'
$env:DB_PASSWORD = '<your-database-password>'
```

Generate a local Base64-encoded HMAC signing key without printing or committing a fixed secret:

```powershell
$jwtBytes = New-Object byte[] 64
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

Then start Spring Boot:

```powershell
.\mvnw.cmd spring-boot:run
```

Optional `JWT_ACCESS_TOKEN_EXPIRATION` and `JWT_REFRESH_TOKEN_EXPIRATION` values use Spring duration syntax and default to `15m` and `30d`. The repository's default `bootstrap` profile deliberately starts without database-backed application endpoints; use `local` for the working application.

### Start the frontend

From `frontend`:

```powershell
npm ci
Copy-Item .env.example .env
npm run dev
```

The app is available at `http://localhost:5173`. The safe defaults are `VITE_API_BASE_URL=/api/v1` and `VITE_WS_URL=/ws`. Vite proxies `/api` and `/ws` to the backend at `localhost:8080`.

Vite variables are public browser configuration. Never place database passwords, JWT signing keys, access tokens, refresh tokens, or private keys in frontend environment files.

## Database migrations

Flyway is the only schema authority, and Hibernate runs with `ddl-auto=validate`. The schema currently contains 12 versioned migrations:

| Version | Schema area |
|---|---|
| V1-V4 | Users, profiles, refresh tokens, rooms, and room memberships |
| V5 | Game Sessions, Poker Hands, hand players, actions, pots, and awards |
| V6-V8 | Player statistics, rankings/history, and daily/weekly statistics |
| V9-V11 | Admin audit log, friendships, and persistent chat messages |
| V12 | Unique persistent `game_sessions.game_id` runtime identity |

V12 enables stable active-game discovery and historical snapshot lookup across live navigation and cold hydration.

## Tests and quality commands

### Backend

From `backend`, configure the dedicated integration database in the current shell:

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/poker_online_test'
$env:TEST_DB_USERNAME = '<your-test-database-user>'
$env:TEST_DB_PASSWORD = '<your-test-database-password>'

.\mvnw.cmd test
.\mvnw.cmd package
```

MySQL and real STOMP integration tests are enabled only when all three `TEST_DB_*` variables are present. A run with missing variables skips those tests and is not equivalent to the verified RC gate.

Run an individual integration class with:

```powershell
.\mvnw.cmd -Dtest=GameRuntimeMySqlIntegrationTests test
```

### Frontend

From `frontend`:

```powershell
npm test
npm run build
npm run lint
```

`npm test` runs Vitest once. `npm run build` performs the TypeScript project build before creating the Vite production bundle.

## Documentation

- [Architecture](docs/architecture/architecture.md)
- [Module boundaries](docs/architecture/module-boundaries.md)
- [Concurrency model](docs/architecture/concurrency.md)
- [REST API](docs/api/rest-api.md)
- [WebSocket/STOMP protocol](docs/api/websocket-protocol.md)
- [Frontend development guide](frontend/README.md)
- [Logical ER model](docs/database/erd.md)
- [Data dictionary](docs/database/data-dictionary.md)
- [Test plan](docs/testing/test-plan.md)
- [Repository engineering rules](AGENTS.md)
