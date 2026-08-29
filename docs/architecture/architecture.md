# System Architecture

## Status and goals

This document is the Phase 0 architecture proposal. It describes boundaries and contracts; it does not prescribe implementation details that should be proven during later phases.

The system supports multiple concurrent players and spectators in a lobby and poker rooms. It demonstrates REST, authenticated WebSocket/STOMP, broadcast and private unicast, session/connection management, reconnect, synchronized timers, and server-authoritative game processing.

## System context

```text
React browser clients
  |-- HTTPS/JSON: identity, profiles, friends, room discovery/commands,
  |              history, rankings, analytics, administration, snapshots
  `-- WSS/STOMP: presence, notifications, chat, lobby/room/game events,
                 private cards, player commands, timer updates
                         |
              Spring Boot modular monolith
              |-- feature modules
              |-- active-game runtime registry
              `-- JPA/Flyway
                         |
                    MySQL 8.x
```

TLS termination and production deployment topology are intentionally deferred. Both HTTP and WebSocket traffic must be encrypted outside local development.

## Backend architecture

The backend is one deployable Spring Boot process with feature packages: `common`, `auth`, `player`, `social`, `room`, `game`, `ranking`, `analytics`, and `admin`. Each module owns its business rules and persistence model. A typical module may contain:

- `api`: REST/STOMP adapters and transport DTOs;
- `application`: use cases, transactions, authorization orchestration, and ports;
- `domain`: entities/value objects and business rules independent of frameworks;
- `infrastructure`: JPA entities/repositories and adapter implementations.

Not every module needs every package. Calls across features target an application interface, immutable projection, or internal event. Direct cross-module repository access and JPA relationship graphs are avoided.

The game module has two distinct parts: a pure-Java Poker Engine for deterministic rules and an application/runtime layer responsible for identity, rooms, persistence, messaging, clocks, and serialized command execution.

## Frontend architecture

The Vite React application is also organized by feature, with an application shell and shared technical utilities. A proposed shape is:

```text
frontend/src/
  app/                 routing, providers, bootstrap
  features/            auth, player, social, lobby, room, game, ranking, analytics, admin
  shared/              API client, STOMP client, UI primitives, validation, types
  test/                shared test setup
```

TanStack Query owns REST server-state caching. Zustand owns limited client/session UI state and the current realtime projection. React Hook Form and Zod manage forms and boundary validation. The client renders authoritative snapshots/events and may offer predictive UI feedback, but never finalizes a poker action locally.

## HTTP and WebSocket responsibilities

REST is used for request/response operations, durable resource views, initial bootstrapping, pagination, and recovery snapshots. Examples include authentication, profiles, friends, room creation/join, history, rankings, analytics, admin operations, and fetching the latest room/game snapshot.

WebSocket/STOMP is used when ordering and low-latency delivery matter: presence changes, lobby/room updates, chat, game commands/events, private cards, turn deadlines, and notifications. A WebSocket command is an intention and receives an authoritative event or private rejection. It is not a second ungoverned API: it uses the same application services and authorization policies as REST.

REST and STOMP payloads use explicit DTOs. IDs, timestamps, enum values, versions, and error formats must be consistent across both transports.

## Server-authoritative design

Only the server shuffles and deals, calculates legal actions and amounts, changes Account Chip and Table Chip balances, advances turns/phases, builds pots, evaluates hands, declares winners, and calculates ranking results. The client sends a command such as `RAISE` with a requested total/amount plus its identifiers. The game processor validates it against the current state, applies it once, increments `stateVersion`, persists required audit data, and publishes the resulting state/events.

Randomness must be generated server-side with an appropriate secure source. Tests use an injected deterministic deck/random source. Card order and unrevealed cards are never written to public events or logs.

## State and persistence

- MySQL holds durable accounts, social relationships, room records/membership, game/hand history, actions, statistics, and rankings.
- Account Chips are a persistent account balance. A server-authoritative buy-in transfers chips into the player's Table Chip balance; a legal departure returns the remaining Table Chips to Account Chips. Both sides of each transfer must be atomic and auditable.
- A Game Session is continuous play in one room and contains multiple Poker Hands. A Poker Hand is one deal from blind posting through completion; history and statistics never conflate these entities.
- The active game aggregate is held in process for fast serialized mutation, with durable hand/action checkpoints sufficient for audit and defined recovery.
- A single database transaction records each accepted durable transition and its outgoing-event intent. The exact outbox/recovery implementation is a Phase 1 design decision; it must prevent a committed action from silently losing its corresponding client update.
- Derived statistics and rankings update after authoritative hand/session outcomes, preferably through internal events so the game module does not depend on those modules.

## Major runtime flows

### Login and realtime connection

1. Client authenticates over REST and receives short-lived access credentials plus the approved refresh mechanism.
2. Client loads its bootstrap/profile data.
3. Client opens `/ws`, authenticates on STOMP `CONNECT`, and subscribes only to authorized destinations.
4. The server associates connections with the authenticated principal and updates presence across all connections for that user.

### Join a room

1. Client requests room details and sends a REST join command (including a password only for a private-room attempt).
2. The room module validates capacity, `WAITING` state, ban/authorization rules, password, and requested buy-in; it never stores or returns plaintext passwords.
3. The server atomically debits Account Chips, establishes Table Chips, commits the `room_players` record, and publishes a sanitized room/lobby event.
4. Client subscribes to the authorized room topic and fetches a snapshot if event continuity is uncertain.

### Play an action

1. Client sends a game command containing `clientActionId`, `turnId`, expected `stateVersion`, action type, and optional amount.
2. The authenticated command enters that game's serialization boundary.
3. Server rejects duplicates/stale/illegal commands or runs the pure engine transition.
4. Accepted state/history is committed, the version advances, a sanitized public event is broadcast, and any private data is unicasted.

### Disconnect and reconnect

1. Heartbeat/transport loss removes one connection; after all connections are gone, an active room player becomes `DISCONNECTED` and enters the initial 60-second reconnect window.
2. Their seat, Table Chips, and game state are retained and the game continues. If their turn expires, the server applies `AUTO_CHECK` when legal and otherwise `AUTO_FOLD`.
3. On reconnect, the client authenticates, restores its identity/session, locates its active room/game, receives an authoritative snapshot plus only its own hole cards, and resumes realtime events.

### Leave and ownership

1. In a `WAITING` room, a player may leave immediately; remaining Table Chips return atomically to Account Chips.
2. During an active Poker Hand, leaving is treated as a fold, committed chips remain in the pot, and the player becomes `LEAVING` until removal after that hand. Remaining Table Chips are then returned through the legal departure flow.
3. If the owner leaves while `WAITING`, ownership transfers deterministically to another eligible player; if none remains, the room becomes `CLOSED`.
4. Owner departure or disconnection never terminates an active hand.

## Approved lifecycle vocabulary

- Room: `WAITING`, `PLAYING`, `FINISHED`, `CLOSED`.
- Room player: `NOT_READY`, `READY`, `PLAYING`, `SPECTATING`, `DISCONNECTED`, `LEAVING`.

## Architectural risks

- Poker correctness: side pots, split pots, all-in reopening rules, heads-up blinds, and ties require precise rule decisions and exhaustive tests.
- In-memory active state: process failure or multi-instance deployment needs a documented recovery/ownership model. Initial deployment should be single-instance unless game affinity/coordination is designed.
- Database/event consistency: a committed action and failed broadcast can diverge clients; snapshots and an eventual transactional event strategy are required.
- WebSocket authorization/data leakage: destination-level checks and separate private DTOs are mandatory.
- Concurrency/idempotency: REST retries, duplicate STOMP frames, timer races, and reconnect can otherwise double-apply actions.
- Room/game lifecycle edge cases still needing decisions include rebuy limits, spectator permissions, abandoned-game cleanup, and deterministic selection among multiple eligible successor owners.
- Statistics drift: define authoritative source events and make projections replayable/reconcilable.
- Scope: the feature set is large for a university project; deliver vertical slices and prioritize networking and correct core play.

## Proposed repository structure

```text
/
  AGENTS.md
  README.md
  docs/
    architecture/
    api/
    database/
    testing/
  backend/             # introduced in a later phase
  frontend/            # introduced in a later phase
```
## Player-statistics projection

The analytics module reads completed game history through a narrow read port and replaces each player's materialized statistics row. A game-session completion event is handled after the gameplay transaction commits. Projection failures are logged and remain repairable; they cannot roll back authoritative gameplay or settlement.
## Multiplayer Elo ranking

After a game session commits, ranking independently reads participant session-net results. With initial rating 1000 and K=32, every player is compared pairwise against every opponent, expected scores use the standard 400-point logistic formula, actual scores are 1/0.5/0, and averages produce one simultaneously calculated rounded delta. Competition placement uses session net; the rating floor is zero. Participant rows are locked in user-ID order and history makes a session idempotent.
# Daily and weekly analytics projection

Phase 8C consumes `GameSessionFinishedEvent` independently after commit. It
discovers affected player/day and player/week keys, orders them by user, bucket
type, and date, and recomputes each complete projection from authoritative
gameplay history. A concrete projection row is initialized and point-locked
before reading history, preventing a slower stale writer from overwriting a
newer aggregate. Listener failure is logged and cannot roll back gameplay,
statistics, or ranking.

The business timezone is `Asia/Bangkok`. Hands belong to the local date of
`poker_hands.ended_at`; weeks begin Monday. A zero-net completed hand is counted
as `handsTied`. Playing time sums, per represented session, the interval from
its earliest hand start to latest hand finish within the bucket, avoiding gaps
between unrelated sessions.
# Phase 9A admin read model

The admin feature is a read-only query module. Controllers call an application
service, which normalizes and validates filters before using a narrow read port.
The JDBC adapter composes cross-module reports directly into explicit admin
DTOs. Persisted state remains authoritative; Phase 9A does not depend on active
game runtime internals and performs no moderation or balance mutation.

Overview counts all users, ACTIVE accounts, all rooms, WAITING/PLAYING open
rooms, ACTIVE/FINISHED sessions, completed hands, and account-chip balances.
List queries use joined projections or aggregate subqueries and avoid per-item
repository loading.
