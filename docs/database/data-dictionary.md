# Database Data Dictionary

## Conventions

Phase 2 tables use MySQL 8.x, InnoDB, `utf8mb4`, and `utf8mb4_0900_ai_ci`. Primary keys and chip values are `BIGINT`; chips never use floating point. JPA uses Java `Instant`, Hibernate is configured with JDBC timezone `UTC`, connections set the MySQL session timezone to `+00:00`, and SQL stores instants in `DATETIME(6)`. All enum values are readable strings, never ordinals.

Flyway is schema authority and Hibernate uses `ddl-auto=validate`. Constraint and index names are explicit. No trigger or database gameplay logic exists.

## Implemented tables

### `users` — owner: auth

Authentication identity plus account-level state. The player module may participate later through an explicit application contract for Account Chip transfers; it does not directly own this table.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `id` | `BIGINT` | PK, auto-increment | User identifier |
| `username` | `VARCHAR(50)` | required, unique | Login name |
| `password_hash` | `VARCHAR(255)` | required | Password hash; never plaintext |
| `email` | `VARCHAR(255)` | nullable, unique when present | Optional normalized email |
| `role` | `VARCHAR(20)` | `PLAYER` | `PLAYER` or `ADMIN` |
| `account_status` | `VARCHAR(20)` | `ACTIVE` | `ACTIVE` or `LOCKED` |
| `account_chips` | `BIGINT` | `0` | Persistent Account Chips; check `>= 0` |
| `created_at` | `DATETIME(6)` | current UTC time | Creation instant |
| `updated_at` | `DATETIME(6)` | current UTC time, updated automatically | Last update instant |
| `last_login_at` | `DATETIME(6)` | nullable | Last successful login instant |

Constraints/indexes: `pk_users`, `uk_users_username`, `uk_users_email`, and checks for role, status, and non-negative Account Chips. The `0` default prevents an implicit chip grant; a later approved application policy must establish any starting allocation.

### `player_profiles` — owner: player

Player-facing profile information, separate from authentication identity and future statistics.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `id` | `BIGINT` | PK, auto-increment | Profile identifier |
| `user_id` | `BIGINT` | required, unique, FK | Owning user |
| `display_name` | `VARCHAR(100)` | required | Public display name |
| `avatar_url` | `VARCHAR(2048)` | nullable | Avatar reference |
| `online_status` | `VARCHAR(20)` | `OFFLINE` | Last known `ONLINE`, `IN_GAME`, or `OFFLINE` projection |
| `created_at` | `DATETIME(6)` | current UTC time | Creation instant |
| `updated_at` | `DATETIME(6)` | current UTC time, updated automatically | Last update instant |

Constraints/indexes: `pk_player_profiles`, `uk_player_profiles_user_id`, `fk_player_profiles_user`, presence check, and `idx_player_profiles_online_status`. Realtime presence is runtime/server-managed; the stored value is not an authoritative live connection registry.

### `refresh_tokens` — owner: auth

Persistence preparation for the frozen refresh/rotation/logout architecture. Phase 2 implements no JWT issuance or validation.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `id` | `BIGINT` | PK, auto-increment | Token record identifier |
| `user_id` | `BIGINT` | required, FK | Owning user |
| `token_hash` | `VARCHAR(255)` | required, unique | Secure representation; never plaintext token |
| `expires_at` | `DATETIME(6)` | required | Expiry instant |
| `revoked_at` | `DATETIME(6)` | nullable | Revocation instant |
| `created_at` | `DATETIME(6)` | current UTC time | Creation instant |

Constraints/indexes: `pk_refresh_tokens`, `uk_refresh_tokens_token_hash`, `fk_refresh_tokens_user`, `idx_refresh_tokens_user_id`, and `idx_refresh_tokens_expires_at`.

### `rooms` — owner: room

Durable room configuration and lifecycle metadata. It does not implement room behavior.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `id` | `BIGINT` | PK, auto-increment | Room identifier |
| `name` | `VARCHAR(100)` | required | Room name |
| `owner_user_id` | `BIGINT` | required, FK | Current owner |
| `room_type` | `VARCHAR(20)` | required | `PUBLIC` or `PRIVATE` |
| `password_hash` | `VARCHAR(255)` | nullable | Private-room password hash; never plaintext; must be null for public rooms |
| `max_players` | `INT` | required | Player capacity, check 6–9 |
| `small_blind` | `BIGINT` | required | Positive configured small blind |
| `big_blind` | `BIGINT` | required | Greater than small blind |
| `buy_in` | `BIGINT` | required | Positive configured buy-in |
| `status` | `VARCHAR(20)` | `WAITING` | `WAITING`, `PLAYING`, `FINISHED`, or `CLOSED` |
| `created_at` | `DATETIME(6)` | current UTC time | Creation instant |
| `updated_at` | `DATETIME(6)` | current UTC time, updated automatically | Last update instant |
| `last_activity_at` | `DATETIME(6)` | required | Latest relevant room activity instant |

Constraints/indexes: `pk_rooms`, `fk_rooms_owner_user`, checks for type/public password, capacity, blinds, buy-in and status, plus `idx_rooms_owner_user_id`, `idx_rooms_status_type`, and `idx_rooms_last_activity_at`.

### `room_players` — owner: room

Persistent membership, seat state, and Table Chips. This exact approved table name is retained.

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `id` | `BIGINT` | PK, auto-increment | Membership identifier |
| `room_id` | `BIGINT` | required, FK | Room |
| `user_id` | `BIGINT` | required, FK | User |
| `seat_number` | `INT` | nullable | Seat 1–9; null supports spectators |
| `player_state` | `VARCHAR(20)` | required | Approved room-player state |
| `table_chips` | `BIGINT` | `0` | Table Chips, check `>= 0` |
| `joined_at` | `DATETIME(6)` | current UTC time | Join instant |
| `left_at` | `DATETIME(6)` | nullable | Legal removal instant |

Constraints/indexes: `pk_room_players`, `uk_room_players_room_user`, `uk_room_players_room_seat`, both foreign keys, checks for seat/state/Table Chips, `idx_room_players_user_id`, and `idx_room_players_room_state`. Multiple spectators are allowed because MySQL unique indexes permit multiple null seat numbers. A future leave/rejoin policy must decide whether to reuse or archive the unique membership row.

## Account Chips versus Table Chips

- `users.account_chips` persists the account-owned balance.
- `room_players.table_chips` persists the room-assigned balance.
- Future buy-in transfers Account Chips to Table Chips; legal cash-out transfers remaining Table Chips back.
- Only server application services may perform these transfers, atomically and with later audit support. Phase 2 supplies storage constraints only.

## Phase 6A gameplay-history tables — owner: game

- `game_sessions`: room FK, readable `ACTIVE`/`FINISHED`/`ABORTED` status, start/end timestamps. Multiple historical sessions per room are allowed.
- `poker_hands`: session FK, positive per-session `hand_number`, dealer/blind seats and amounts, final phase/end reason, timestamps, and canonical board-card string. `(game_session_id, hand_number)` is unique.
- `hand_players`: per-hand user/seat snapshot, starting/ending/committed chips, `ACTIVE`/`FOLDED`/`ALL_IN` participation, independent connectivity/leaving flags, and private canonical hole-card string. User and seat are unique per hand.
- `player_actions`: server-accepted action audit with explicit positive `action_sequence`, street, six readable action types, authoritative financial results, command identifiers, and timestamp. `(poker_hand_id, action_sequence)` is unique.
- `pots`: ordered `MAIN`/`SIDE` layers with positive amount and contribution cap. `(poker_hand_id, pot_index)` is unique and index zero is reserved for MAIN.
- `pot_awards`: normalized winner/payout rows supporting tied pots and explicit odd-chip amounts. `(pot_id, user_id)` is unique.
- `uncalled_bet_returns`: explicit positive unmatched-bet refunds, separate from contested pots. `(poker_hand_id, user_id)` is unique.

Card strings use stable two-character rank/suit codes separated by commas; Java objects and native serialization are never stored. Hole-card columns remain persistence-private and are not generic public projections.

All gameplay chip values have database checks, seats are restricted to 1–9, enums are constrained readable strings, chronological semantic ordering uses hand/action/pot sequence columns, and foreign keys retain room/user ownership.

## Planned tables

`chat_messages` remains planned. Friendship persistence and the statistics, ranking, and time-bucket projection tables are implemented by later migrations described below.

Gameplay persistence retains the distinction that one Game Session contains many Poker Hands. Runtime hand finalization and transaction orchestration are intentionally not implemented by Phase 6A.
## Player statistics (`player_statistics`)

`player_statistics` is an idempotently replaceable projection derived only from completed gameplay history. Its primary key and foreign key are `user_id`; counters, chip totals, the largest actual `pot_awards.amount_awarded`, average participated-session seconds, and the last projection timestamp are stored. Flyway V6 owns this table. It is not a ranking or leaderboard table.
## Rankings (`player_rankings`, `ranking_history`)

Flyway V7 adds current competitive ratings and immutable per-session changes. Ratings start at 1000, never fall below zero, and history is unique by `(user_id, game_session_id)`. Leaderboard rank is computed, not stored.
# Time-bucket analytics (Flyway V8)

`daily_statistics` is keyed by `(user_id, stat_date)` and `weekly_statistics` by
`(user_id, week_start_date)`. Both store non-negative hand-result counters,
positive/negative chip totals, signed `net_chips`, the largest actual pot award,
per-session playing-time approximation, distinct participating sessions, and
`updated_at`. Both user foreign keys cascade on user deletion. The composite
primary keys support current-player date-range reads; no global date index is
needed.

The rows are derived projections. Gameplay history in `game_sessions`,
`poker_hands`, `hand_players`, `pots`, and `pot_awards` remains authoritative.
## `admin_audit_log` (Flyway V9)

Append-only record of successful administrative state changes. Columns are `id`, acting `admin_user_id`, whitelisted `action_type`, whitelisted `target_type`, optional `target_id`, bounded optional `reason`, optional `request_id`, safe `metadata_json`, and microsecond `created_at`. The acting user has a restrictive foreign key to `users`. Indexes support actor/time and target/time reads. No update/delete API exists; metadata must never contain credentials, tokens, private-room secrets, or cards.

## `friendships` (Flyway V10) — owner: social

| Column | Type | Null/default | Meaning |
|---|---|---|---|
| `id` | `BIGINT` | PK, auto-increment | Friendship/request identifier |
| `requester_user_id` | `BIGINT` | required, FK | Original/current request sender |
| `recipient_user_id` | `BIGINT` | required, FK | Original/current request recipient |
| `lower_user_id` | `BIGINT` | stored generated, FK | `LEAST(requester_user_id, recipient_user_id)` |
| `higher_user_id` | `BIGINT` | stored generated, FK | `GREATEST(requester_user_id, recipient_user_id)` |
| `status` | `VARCHAR(20)` | required | `PENDING`, `ACCEPTED`, or `REJECTED` |
| `created_at` | `DATETIME(6)` | current UTC time | Current request creation/reopen instant |
| `responded_at` | `DATETIME(6)` | nullable | Accept/reject instant |
| `version` | `BIGINT` | `0` | Optimistic transition version |

`uk_friendships_canonical_pair(lower_user_id, higher_user_id)` is the authoritative one-row-per-unordered-pair invariant. Checks reject self-pairs, noncanonical order, and unknown statuses. All four user columns reference `users.id` with restrictive deletion. Incoming and outgoing request reads use `(recipient_user_id, status, created_at)` and `(requester_user_id, status, created_at)` indexes; accepted symmetric lookups can use MySQL index merge over those participant indexes. Generated canonical columns are mapped read-only by JPA.
