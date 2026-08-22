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

## Planned tables

The following remain planned and are not present in the Phase 2 schema: `friendships`, `chat_messages`, `game_sessions`, `poker_hands`, `hand_players`, `player_actions`, `pots`, `player_statistics`, `player_rankings`, `ranking_history`, `daily_statistics`, and `weekly_statistics`.

Future persistence must retain the distinction that one Game Session contains many Poker Hands. No placeholders, migrations, entities, or repositories for these future tables are created in Phase 2.
