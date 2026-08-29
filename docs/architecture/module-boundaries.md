# Module Boundaries

## Dependency principles

Each feature owns its domain and tables. Within a module, dependencies point from `api` and `infrastructure` toward `application`/`domain`; the domain remains framework-independent where practical.

Cross-module collaboration uses narrow application ports, read-only projections, identifiers, or immutable internal events. Modules must not import another module's infrastructure/JPA types or mutate its tables directly. `common` may be used by every module but may depend on none of them. Cycles are forbidden.

## Modules

### `common`

Owns shared technical primitives: API error envelope, ID/time abstractions, pagination conventions, security principal type, event-envelope infrastructure, and correlation/observability utilities. It does not own user, room, poker, ranking, or other feature rules.

Allowed dependencies: Java/framework libraries only. No business module dependencies.

### `auth`

Owns registration credentials, login/logout, password hashing policy, access/refresh token lifecycle, account lock checks during authentication, and authenticated principal production. Password hashes and refresh-token records belong here.

May depend on: `common`; a narrow player/account provisioning port or event for creating the initial profile. It must not depend on social, room, or game.

### `player`

Owns user identity/profile data exposed to the application, display names/avatars, persistent Account Chip balance and its ledger boundary, account status administration rules, and online-presence projection. Authentication secrets remain in `auth`; durable poker statistics remain in `ranking`.

May depend on: `common`; consume auth lifecycle events. Other modules may request a safe player summary through an application interface.

### `social`

Owns friend requests, acceptance/rejection, friendship lifecycle, friend list, friend presence view, and room chat policy/history. It checks membership via a room application port before accepting room chat.

May depend on: `common`, safe `player` projections, and a narrow `room` membership query. It must not access auth secrets or game state.

Phase 10A stores exactly one row per canonical user pair, enforced by generated lower/higher user IDs and a database unique constraint. Direction remains explicit for pending authorization. The Phase 10A.2 application service owns transactional lifecycle decisions behind `FriendshipPersistencePort` and `SocialPlayerQueryPort`. Its JDBC adapter uses validated `INSERT IGNORE`, verifies a zero-row insert by selecting the canonical row `FOR UPDATE`, and row-locks accept/reject decisions; every actual update increments `version`. Crossed pending requests transition the existing row to accepted, rejected pairs reopen in place, and accepted removal hard-deletes the row.

Social application/domain code does not import auth/player persistence types. The player adapter returns only batched safe summaries (`userId`, `displayName`, `avatarUrl`), avoiding per-friend profile queries. REST obtains the requester only from the authenticated principal. Notifications, presence refinement, and chat remain outside Phase 10A.2.

### `room`

Owns lobby rooms, public/private access, password hashing/verification, capacity (6–9 seats), owner, `room_players`, Table Chip balances, buy-in/cash-out orchestration, seats, spectators, and room lifecycle before/around a game. Room states are `WAITING`, `PLAYING`, `FINISHED`, and `CLOSED`; player states are `NOT_READY`, `READY`, `PLAYING`, `SPECTATING`, `DISCONNECTED`, and `LEAVING`. It exposes membership/role queries and emits room lifecycle events.

May depend on: `common` and explicit `player` ports for atomic Account Chip transfers and safe projections. It asks `game` through an application contract whether a game may start/stop, without importing engine internals. Passwords are never exposed. Waiting-room owner departure transfers ownership to an eligible player or closes an empty room; active hands survive owner departure.

### `game`

Owns active Game Sessions, their multiple Poker Hands, pure Poker Engine concepts, deck/deal, phases, turns, betting, pot/side-pot calculation, showdown, authoritative in-hand Table Chip changes, action audit, game snapshots, and recovery metadata. Leaving during a hand is an authoritative fold with committed chips retained until pot settlement.

May depend on: `common`, safe player identity summaries, and immutable room/start configuration supplied through an application contract/event. It publishes outcome events consumed by ranking/analytics. It never calls ranking or analytics directly.

### `ranking`

Owns player aggregate statistics, rank calculation, leaderboard, current rankings, and ranking history. It consumes authoritative completed-hand/session events idempotently.

May depend on: `common` and safe player summaries. It consumes `game` event contracts but must not mutate or query active engine state.

### `analytics`

Owns daily/weekly aggregate metrics and operational read models: online players, active rooms/games, spectators, average room occupancy, and average duration. It derives data from stable events or explicitly exposed projections.

May depend on: `common` plus public event/projection contracts from player, room, game, and ranking. It cannot change their state.

### `admin`

Owns admin-facing use cases, authorization policies, dashboard composition, user lock/unlock orchestration, room/game monitoring, and server-statistics views. It is an orchestration/read module, not the owner of user, room, or game records.

May depend on: `common` and explicit administration/query ports from auth, player, room, game, ranking, and analytics. Commands such as lock-user go through the owning module.

## Dependency summary

```text
all modules ---> common
auth ---------> player provisioning contract/event
social -------> player summary, room membership query
room ---------> player summary, game lifecycle port/event
game ---------> player summary, room start configuration
ranking ------> game outcome events, player summary
analytics ----> player/room/game/ranking events or projections
admin --------> explicit admin/query ports of owning modules
```

The arrows describe allowed contract-level use, not permission to import internal domain or infrastructure packages. To avoid a room/game cycle, define start as a handoff: room publishes a validated start request/configuration; game publishes lifecycle/status outcomes; each side translates the other's public contract at its boundary.

## Data ownership and enforcement

- Only an owning module writes its tables.
- Foreign identifiers across modules are scalar IDs rather than cross-module JPA entity associations.
- Java package visibility, architecture tests (for example ArchUnit if approved later), and build/module conventions should enforce boundaries.
- Shared transaction needs are orchestrated through application contracts; do not solve them by sharing repositories.
- Internal events carry stable facts and an event ID for idempotent consumers.
### Game to analytics

Game publishes the narrow `GameSessionFinishedEvent`; analytics handles it after commit through `PlayerStatisticsRefreshPort`. Analytics reads history through `PlayerStatisticsHistoryPort` and never mutates game entities or repositories. Game does not depend on analytics persistence.
# Time-bucket analytics boundary

The analytics application layer reads completed-hand and pot-award facts only
through `TimeBucketAnalyticsHistoryPort`. Its JDBC adapter may query gameplay
tables read-only; analytics does not depend on game repositories or expose game
or persistence entities. The game module publishes `GameSessionFinishedEvent`
and does not call analytics directly.
# Admin query boundary

`com.ptit.poker.admin` may read cross-feature persisted reporting data only
through `AdminReadPort`. Its JDBC adapter depends on existing schemas but does
not expose feature JPA entities. Other modules do not depend on admin. Live
runtime access is intentionally absent from Phase 9A; any future need requires
a narrow runtime query port rather than `ActiveGameContext` access.
### Admin moderation boundaries

`AdminController` depends only on admin application services. `AdminUserModerationPort`, `AdminRoomModerationPort`, `AdminGameModerationPort`, and `AdminAuditPort` isolate persistence and runtime adapters. The admin module may coordinate authoritative room/game use cases through these ports; it does not edit poker history, statistics, rankings, analytics, chips, or engine mathematics directly.
