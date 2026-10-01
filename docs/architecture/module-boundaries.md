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

Owns user identity/profile data exposed to the application, display names/avatars, persistent Account Chip balance and its ledger boundary, account status administration rules, and online-presence projection. Authentication secrets remain in `auth`; durable poker statistics remain in `analytics`.

May depend on: `common`; consume auth lifecycle events. Other modules may request a safe player summary through an application interface.

### `social`

Owns friend requests, acceptance/rejection, friendship lifecycle, friend list, friend presence view, and room chat policy/history. It checks membership via a room application port before accepting room chat.

May depend on: `common`, safe `player` projections, and a narrow `room` membership query. It must not access auth secrets or game state.

Social stores exactly one row per canonical user pair, enforced by generated lower/higher user IDs and a database unique constraint. Direction remains explicit for pending authorization. The application service owns transactional lifecycle decisions behind `FriendshipPersistencePort` and `SocialPlayerQueryPort`. Its JDBC adapter uses validated `INSERT IGNORE`, verifies a zero-row insert by selecting the canonical row `FOR UPDATE`, and row-locks accept/reject decisions; every actual update increments `version`. Crossed pending requests transition the existing row to accepted, rejected pairs reopen in place, and accepted removal hard-deletes the row.

Social application/domain code does not import auth/player persistence types. The player adapter returns only batched safe summaries (`userId`, `displayName`, `avatarUrl`), avoiding per-friend profile queries. REST obtains the requester only from the authenticated principal. Friendship changes publish immutable application events inside the transaction and deliver them through a `SocialNotificationPort` only in `AFTER_COMMIT`. The STOMP adapter resolves the recipient's username and targets that principal's private user queue; broker delivery is best effort and never changes a committed REST result.

Immutable room-chat history belongs to `social/chat`. The application service depends on a narrow `ChatRoomAccessPort` projection for current membership, account activity, and non-closed room checks, and reuses the existing safe social player projection; it does not import room/auth/player entities or repositories. Chat is independent of game turns and runtime locks. `(room_id, sender_user_id, client_message_id)` is the concurrent idempotency boundary. A thin authenticated STOMP handler publishes `ChatMessageCreated` only for newly inserted messages; an `AFTER_COMMIT` listener calls `ChatRealtimePort`, whose STOMP adapter owns `SimpMessagingTemplate`. Delivery failure is contained after commit, retries never republish existing messages, and the REST history adapter supplies recovery.

Account presence stays in a narrow social application boundary while reusing the player-owned persisted `PresenceStatus` projection. The WebSocket lifecycle adapter reuses the authenticated principal and the existing connection registry, then emits a neutral `common` authenticated-connection transition only at the registry's first/last-session edges. Game reconnect handling and social presence consume that in-process fact independently; the presence listener contains its own failure so it cannot suppress game handling. `PresencePersistencePort` isolates profile writes, and `AcceptedFriendQueryPort` resolves accepted recipients directly in storage rather than scanning users. An immutable `PresenceChanged` event is published inside the transaction, then an `AFTER_COMMIT` listener calls `FriendPresenceNotificationPort`; only its STOMP adapter depends on `SimpMessagingTemplate`. Recipient lookup and broker failures are contained after commit.

The active-session registry stores session-to-user and user-to-session-set mappings in thread-safe concurrent collections. It is deliberately process-local because deployment is currently one Spring Boot server; distributed presence is outside scope. Duplicate and concurrent callbacks cannot create negative counts or duplicate first/last transitions. At startup, the process reconciles all stale non-offline profile projections to `OFFLINE`, since no live WebSocket session survives a JVM restart. This account/social state is independent of room/game disconnect and reconnect semantics and naturally continues to feed existing persisted-presence analytics reads.

### `room`

Owns lobby rooms, public/private access, password hashing/verification, capacity (6–9 seats), owner, `room_players`, Table Chip balances, buy-in/cash-out orchestration, seats, spectators, and room lifecycle before/around a game. Room states are `WAITING`, `PLAYING`, `FINISHED`, and `CLOSED`; player states are `NOT_READY`, `READY`, `PLAYING`, `SPECTATING`, `DISCONNECTED`, and `LEAVING`. It exposes membership/role queries and emits room lifecycle events.

May depend on: `common` and explicit `player` ports for atomic Account Chip transfers and safe projections. It asks `game` through an application contract whether a game may start/stop, without importing engine internals. Passwords are never exposed. Waiting-room owner departure transfers ownership to an eligible player or closes an empty room; active hands survive owner departure.

### `game`

Owns active Game Sessions, their multiple Poker Hands, pure Poker Engine concepts, deck/deal, phases, turns, betting, pot/side-pot calculation, showdown, authoritative in-hand Table Chip changes, action audit, game snapshots, and recovery metadata. Leaving during a hand is an authoritative fold with committed chips retained until pot settlement.

May depend on: `common`, safe player identity summaries, and immutable room/start configuration supplied through an application contract/event. It publishes outcome events consumed by ranking/analytics. It never calls ranking or analytics directly.

### `ranking`

Owns competitive player rating, multiplayer Elo calculation, leaderboard, current rankings, and ranking history in `player_rankings` and `ranking_history`. It consumes authoritative completed-session events idempotently.

May depend on: `common` and safe player summaries. It consumes `game` event contracts but must not mutate or query active engine state.

### `analytics`

Owns player aggregate statistics and daily/weekly time-bucket metrics. It derives data from completed-game history through explicit read ports.

May depend on: `common` plus public event/projection contracts from player, room, game, and ranking. It cannot change their state.

### `admin`

Owns admin-facing use cases, authorization policies, dashboard composition, user suspension/reactivation orchestration, room/game monitoring, and audit views. It is an orchestration/read module, not the owner of user, room, or game records.

May depend on: `common` and explicit administration/query ports from auth, player, room, game, ranking, and analytics. Moderation commands enter the owning module through narrow ports.

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
Read-only queries do not access the runtime. Moderation uses the narrow
`AdminGameModerationPort` rather than direct `ActiveGameContext` access.
### Admin moderation boundaries

`AdminController` depends only on admin application services. `AdminUserModerationPort`, `AdminRoomModerationPort`, `AdminGameModerationPort`, and `AdminAuditPort` isolate persistence and runtime adapters. The admin module may coordinate authoritative room/game use cases through these ports; it does not edit poker history, statistics, rankings, analytics, chips, or engine mathematics directly.
