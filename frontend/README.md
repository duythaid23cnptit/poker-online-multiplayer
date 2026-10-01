# Poker Online frontend

This React client implements the full player and administrator interface for the current release-candidate source. It renders server-authoritative state and communicates with the Spring Boot backend through typed REST clients and authenticated native WebSocket/STOMP.

Implemented screens include authentication, lobby, room creation/joining, waiting-room membership and host controls, live/historical poker tables, chat, friends and presence, rankings, player performance analytics, profile management, and an admin-only operations console.

## Requirements and commands

- Node.js 22
- npm 10

Use npm with the committed lockfile:

```powershell
npm ci
npm run dev
npm test
npm run build
npm run lint
```

`npm test` is a non-watch Vitest run. `npm run build` runs the TypeScript project build before producing the Vite bundle.

## Local backend integration

Copy the safe public configuration example:

```powershell
Copy-Item .env.example .env
```

| Variable | Safe default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `/api/v1` | REST base path |
| `VITE_WS_URL` | `/ws` | Native WebSocket/STOMP endpoint |

Vite proxies `/api` and `/ws` to `http://localhost:8080` during development. These variables are public browser configuration. Never place database credentials, JWT signing keys, access tokens, refresh tokens, or private keys in frontend environment files.

## Routes

Public routes are `/login` and `/register`. A hydrated `PLAYER` session uses:

| Route | Screen |
|---|---|
| `/app` | Lobby |
| `/app/rooms` | Room discovery, creation, join, spectator, seating, ready, and host start |
| `/app/rooms/:roomId/games/:gameId` | Live or authorized historical table |
| `/app/friends` | Friends, requests, presence, notifications |
| `/app/rankings` | Current rating, leaderboard, rating history |
| `/app/statistics` | Lifetime statistics and daily/weekly analytics |
| `/app/profile` | Current account balance and editable profile |
| `/app/performance` | Alias for player performance/statistics |

An `ADMIN` session uses a separate administration shell at `/admin`, with `/admin/users`, `/admin/rooms`, `/admin/games`, and `/admin/audit`. It contains no player navigation or active-game recovery. Role guards redirect an admin away from `/app/*` and a player away from `/admin/*`; the backend independently enforces `ROLE_PLAYER` and `ROLE_ADMIN`.

## Source organization

```text
src/
  app/          providers, layouts, router
  features/
    admin/      admin REST/query layer, protected console, moderation UI
    auth/       authentication, route guards, session coordination
    friends/    friendship REST state and presence UI
    game/       snapshot/event reconciliation, table, actions, chat
    lobby/      dashboard composition
    notifications/ private realtime notifications
    profile/    current account/profile
    rankings/   leaderboard and rating history
    rooms/      lobby rooms and waiting-room lifecycle
    statistics/ lifetime and time-bucket performance
  shared/       HTTP/STOMP clients, configuration, UI primitives
  styles/       tokens, global/component/utility styles and animation
  test/         shared Vitest setup and fixtures
```

Feature CSS stays beside its feature and is composed through `src/styles/index.css`. The project does not use a monolithic `styles.css` or a separate UI framework.

## State ownership and recovery

TanStack Query owns REST server state. Zustand holds only small client-owned session, notification, and active-game discovery state; it does not duplicate authoritative room or game snapshots.

The game page subscribes before hydration, buffers frames, fetches the role-filtered snapshot, applies it, then consumes newer events. It repeats snapshot hydration after a STOMP reconnect. Direct navigation and F5 follow the same path. Room detail supplies the authoritative user-ID-to-username projection; `Player #id` is used only when identity is unavailable from the authorized current/historical contract.

Room, friendship, and active-game queries are invalidated after reconnect. Waiting-room game discovery uses both `GAME_STARTED` and the active-game REST lookup, avoiding a navigation race when a frame is missed. Chat merges persisted history with realtime frames by message and client-command identity.

## Authentication and credentials

The implemented backend returns a short-lived access token and an opaque refresh token in the login JSON response.

- The access token stays in memory.
- The refresh token is isolated in `sessionStorage`; it is never placed in `localStorage`, query data, route state, logs, or a persisted Zustand store.
- Session bootstrap refreshes once when a refresh token exists, then loads `GET /api/v1/me` before protected content renders.
- Concurrent protected `401` responses share one refresh request and retry once.
- Logout calls the backend when possible, disconnects STOMP, clears credentials and sensitive query data, and routes to Login even after a network failure.

REST sends the access token in the `Authorization` header. STOMP sends it only in the native `CONNECT` header. It never appears in the WebSocket URL.

## Server authority and privacy

The frontend sends intentions and renders authoritative responses. It does not shuffle/deal, calculate winners or pots, advance turns, determine timeout, mutate chips locally, infer usernames, or trust its disabled buttons as authorization.

Seat occupancy and legal action controls are current UX guidance. The server still validates seats, membership, chips, roles, room state, turn ID, action, and amount atomically. Spectators subscribe to public game state but never receive hole cards or `YOUR_TURN`. Private-room passwords are submitted only for initial entry and are never cached for spectator-to-seat conversion.

See the repository [REST contract](../docs/api/rest-api.md), [WebSocket/STOMP contract](../docs/api/websocket-protocol.md), and [traceability matrix](../docs/api/contract-traceability.md).
