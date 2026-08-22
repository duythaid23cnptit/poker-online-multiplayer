# Database Entity-Relationship Model

## Phase 2 implemented schema

The following tables are implemented by Flyway migrations `V1` through `V4`. All IDs and chip values use `BIGINT`; identifiers are database-generated. Tables use InnoDB, `utf8mb4`, and `utf8mb4_0900_ai_ci` for MySQL 8.x.

```mermaid
erDiagram
  USERS ||--o| PLAYER_PROFILES : has
  USERS ||--o{ REFRESH_TOKENS : owns
  USERS ||--o{ ROOMS : owns
  USERS ||--o{ ROOM_PLAYERS : participates
  ROOMS ||--o{ ROOM_PLAYERS : contains

  USERS {
    bigint id PK
    varchar username UK
    varchar password_hash
    varchar email UK
    varchar role
    varchar account_status
    bigint account_chips
    datetime created_at
    datetime updated_at
    datetime last_login_at
  }
  PLAYER_PROFILES {
    bigint id PK
    bigint user_id FK,UK
    varchar display_name
    varchar avatar_url
    varchar online_status
    datetime created_at
    datetime updated_at
  }
  REFRESH_TOKENS {
    bigint id PK
    bigint user_id FK
    varchar token_hash UK
    datetime expires_at
    datetime revoked_at
    datetime created_at
  }
  ROOMS {
    bigint id PK
    varchar name
    bigint owner_user_id FK
    varchar room_type
    varchar password_hash
    int max_players
    bigint small_blind
    bigint big_blind
    bigint buy_in
    varchar status
    datetime created_at
    datetime updated_at
    datetime last_activity_at
  }
  ROOM_PLAYERS {
    bigint id PK
    bigint room_id FK
    bigint user_id FK
    int seat_number
    varchar player_state
    bigint table_chips
    datetime joined_at
    datetime left_at
  }
```

## Balance model

`users.account_chips` is the persistent Account Chip balance. `room_players.table_chips` is the Table Chip balance assigned inside one room. They are deliberately separate non-negative fields. Future buy-in/cash-out application services must transfer between them atomically and authoritatively; the Phase 2 schema does not implement gameplay or transfer logic. Because no initial economic grant is approved, the storage default for Account Chips is technically safe `0`.

## Implemented relationships and constraints

- `player_profiles.user_id` uniquely references `users.id`, producing zero or one profile per user.
- `refresh_tokens.user_id` references `users.id`; only token hashes are stored and are unique.
- `rooms.owner_user_id` references `users.id` with restricted deletion.
- `room_players.room_id` and `room_players.user_id` reference their owners.
- `(room_id, user_id)` prevents duplicate membership rows; `(room_id, seat_number)` prevents duplicate occupied seats while allowing multiple `NULL` spectator seats.
- User role is `PLAYER` or `ADMIN`; account status is `ACTIVE` or `LOCKED`.
- Presence is `ONLINE`, `IN_GAME`, or `OFFLINE`; persisted presence is only the last known projection, while realtime presence remains server/runtime-managed.
- Room type is `PUBLIC` or `PRIVATE`; public rooms cannot contain a password hash.
- Room state is `WAITING`, `PLAYING`, `FINISHED`, or `CLOSED`.
- Room-player state is `NOT_READY`, `READY`, `PLAYING`, `SPECTATING`, `DISCONNECTED`, or `LEAVING`.
- Account/Table Chips cannot be negative. Room capacity is 6–9, blinds and buy-in are positive, and the big blind exceeds the small blind.

## Planned, not implemented in Phase 2

The frozen logical model still plans `friendships`, `chat_messages`, `game_sessions`, `poker_hands`, `hand_players`, `player_actions`, `pots`, `player_statistics`, `player_rankings`, `ranking_history`, `daily_statistics`, and `weekly_statistics`. They have no Phase 2 migrations or JPA mappings.

A Game Session will represent continuous play in one room and contain many individual Poker Hands. That distinction remains authoritative even though neither table is implemented yet.
