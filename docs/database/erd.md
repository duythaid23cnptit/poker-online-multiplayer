# Logical Entity-Relationship Model

This is a logical Phase 0 model, not a migration. Physical types, indexes, constraints, retention, and normalization details will be finalized before Flyway scripts are created.

```mermaid
erDiagram
  USERS ||--|| PLAYER_PROFILES : has
  USERS ||--o{ REFRESH_TOKENS : authenticates
  USERS ||--o{ FRIENDSHIPS : requester
  USERS ||--o{ FRIENDSHIPS : addressee
  USERS ||--o{ ROOM_PLAYERS : joins
  USERS ||--o{ CHAT_MESSAGES : sends
  USERS ||--o{ ROOMS : owns

  ROOMS ||--o{ ROOM_PLAYERS : contains
  ROOMS ||--o{ CHAT_MESSAGES : contains
  ROOMS ||--o{ GAME_SESSIONS : hosts

  GAME_SESSIONS ||--o{ POKER_HANDS : contains
  GAME_SESSIONS ||--o{ HAND_PLAYERS : involves
  POKER_HANDS ||--o{ HAND_PLAYERS : snapshots
  POKER_HANDS ||--o{ PLAYER_ACTIONS : records
  POKER_HANDS ||--o{ POTS : settles
  USERS ||--o{ HAND_PLAYERS : plays
  USERS ||--o{ PLAYER_ACTIONS : performs
  POTS ||--o{ POT_WINNERS : awards
  USERS ||--o{ POT_WINNERS : wins

  USERS ||--|| PLAYER_STATISTICS : aggregates
  USERS ||--|| PLAYER_RANKINGS : ranks
  USERS ||--o{ RANKING_HISTORY : tracks

  USERS {
    uuid id PK
    string username UK
    string email UK
    string password_hash
    string account_status
  }
  PLAYER_PROFILES {
    uuid user_id PK_FK
    string display_name
    string avatar_url
    bigint account_chip_balance
  }
  REFRESH_TOKENS {
    uuid id PK
    uuid user_id FK
    string token_hash UK
    datetime expires_at
    datetime revoked_at
  }
  FRIENDSHIPS {
    uuid id PK
    uuid requester_id FK
    uuid addressee_id FK
    string status
  }
  ROOMS {
    uuid id PK
    uuid owner_id FK
    string visibility
    string password_hash
    string status
  }
  ROOM_PLAYERS {
    uuid room_id PK_FK
    uuid user_id PK_FK
    string player_state
    int seat_number
    bigint table_chip_balance
  }
  CHAT_MESSAGES {
    uuid id PK
    uuid room_id FK
    uuid sender_id FK
    string body
    datetime sent_at
  }
  GAME_SESSIONS {
    uuid id PK
    uuid room_id FK
    string status
    bigint state_version
  }
  POKER_HANDS {
    uuid id PK
    uuid game_session_id FK
    bigint hand_number
    string phase_status
  }
  HAND_PLAYERS {
    uuid hand_id PK_FK
    uuid user_id PK_FK
    int seat_number
    bigint starting_chips
    bigint ending_chips
  }
  PLAYER_ACTIONS {
    uuid id PK
    uuid hand_id FK
    uuid user_id FK
    uuid client_action_id
    string action_type
    bigint amount
    bigint state_version
  }
  POTS {
    uuid id PK
    uuid hand_id FK
    int pot_number
    bigint amount
  }
  POT_WINNERS {
    uuid pot_id PK_FK
    uuid user_id PK_FK
    bigint awarded_amount
  }
  PLAYER_STATISTICS {
    uuid user_id PK_FK
    bigint games_played
    bigint hands_played
    bigint net_chips
  }
  PLAYER_RANKINGS {
    uuid user_id PK_FK
    bigint rating
    bigint rank_position
  }
  RANKING_HISTORY {
    uuid id PK
    uuid user_id FK
    bigint rating
    bigint rank_position
    datetime recorded_at
  }
```

Daily and weekly statistics are aggregate tables keyed by period rather than direct child entities, so they are omitted from the relationship-heavy diagram. Admin users are represented by roles/authorities associated with users; the exact role schema is deferred rather than embedding an `is_admin` flag prematurely. An admin audit-log table is recommended and described in the dictionary. Supporting junction/ledger tables may be introduced later without changing the approved primary logical names.

## Key logical constraints

- Usernames and normalized emails are unique; sensitive tokens and room passwords are stored only as hashes.
- A friendship pair is unique regardless of request direction, and a user cannot friend themself.
- Room status is one of `WAITING`, `PLAYING`, `FINISHED`, or `CLOSED`.
- One user has at most one active `room_players` row per room and one seat; seat numbers are unique within a room when non-null. Player state is one of `NOT_READY`, `READY`, `PLAYING`, `SPECTATING`, `DISCONNECTED`, or `LEAVING`.
- Account Chip and Table Chip balances are non-negative integral values. Buy-in/cash-out updates are server-authoritative, atomic, and auditable; total chips are conserved across the transfer.
- Room capacity is between 6 and 9. Whether capacity counts seats rather than spectators is assumed: it counts player seats only.
- `(game_session_id, hand_number)` is unique.
- `(hand_id, user_id, client_action_id)` is unique for idempotency; action sequence/state version also has an ordering constraint.
- `(hand_id, pot_number)` is unique. Winner awards across a pot must sum to the pot amount, enforced by domain logic and verified transactionally.
- Statistics/rank projections are reconstructable from authoritative completed game/hand facts.

## Modeling questions before migrations

- Account Chip/Table Chip ledger shape, transfer audit fields, buy-in limits, and rebuy policy. The two-balance model and transfer direction are already approved.
- Card persistence/encryption/retention for audit and recovery without leaking hidden information.
- Whether `hand_players` should reference a session-participant entity to preserve display-name snapshots and guest changes.
- Room ownership transfer and deletion/retention semantics.
- Roles/authorities and admin audit schema.
- Event outbox/checkpoint tables needed for crash recovery and reliable publication.
