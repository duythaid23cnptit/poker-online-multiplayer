# Initial REST API Proposal

## Conventions

- Base path: `/api/v1`; JSON over HTTPS.
- Authentication: `Authorization: Bearer <access-token>` except registration, login, and refresh. Tokens/passwords never appear in URLs.
- IDs are opaque UUID strings. Times are ISO-8601 UTC instants. Chip amounts are integral, non-negative values represented as JSON numbers within the agreed 64-bit range.
- Validate payloads with Jakarta Validation. Unknown/invalid enum values return `400`.
- Collection endpoints use cursor pagination where feeds can grow; responses include `items` and `nextCursor`.
- Mutating requests that may be retried should accept `Idempotency-Key`; game actions use `clientActionId` through STOMP.
- Resource conflicts/stale writes return `409`; authorization failures return `403`; missing or intentionally concealed resources return `404`; validation returns `400` or `422` according to the final convention.

Standard error:

```json
{
  "code": "ROOM_FULL",
  "message": "The room has no available seats.",
  "correlationId": "opaque-id",
  "fieldErrors": [{ "field": "displayName", "code": "SIZE" }]
}
```

The message is safe for users; stack traces and secrets are never returned. The exact cookie-versus-response-body refresh-token mechanism remains a security design decision. If cookies are used, apply Secure, HttpOnly, SameSite, and CSRF protections.

## Authentication

| Method | Path | Purpose | Request / response summary |
|---|---|---|---|
| POST | `/auth/register` | Create account/profile atomically | username, password, optional email, display name → safe current-user response (`201`) |
| POST | `/auth/login` | Authenticate by username | username/password → JWT access token and opaque refresh token |
| POST | `/auth/refresh` | Issue a new access token | opaque refresh token → JWT access token; no rotation in the Phase 3 baseline |
| POST | `/auth/logout` | Revoke the supplied refresh token owned by the authenticated principal | bearer access token + refresh token → `204` |

Registration and login validate input. Login uses a generic invalid-credentials response for unknown usernames and wrong passwords; locked accounts are forbidden. Rate limiting is planned but not implemented in Phase 3.

## Player and profile

| Method | Path | Purpose |
|---|---|---|
| GET | `/me` | Current account/profile resolved exclusively from the authenticated principal |
| PATCH | `/me` | Update only the current user's display name and optional avatar URL |
| GET | `/players/{playerId}` | Public player summary |
| GET | `/players/{playerId}/match-history` | Paginated completed sessions/hands visible to requester |
| GET | `/players/{playerId}/statistics` | Public statistics projection |

## Social

| Method | Path | Purpose |
|---|---|---|
| GET | `/friends` | Friend list with current presence projection |
| GET | `/friend-requests?direction=incoming|outgoing` | Pending requests |
| POST | `/friend-requests` | Send request `{ "recipientId": "..." }` |
| POST | `/friend-requests/{requestId}/accept` | Accept request |
| POST | `/friend-requests/{requestId}/reject` | Reject request |
| DELETE | `/friends/{friendId}` | Remove friendship |
| GET | `/rooms/{roomId}/messages` | Authorized paginated room chat history |

Realtime friend notifications/presence and new chat messages use STOMP.

## Lobby and rooms

Phase 4 uses authenticated `/api/v1/rooms` endpoints. All identities come from the JWT principal; requests never accept an owner or member user ID.

| Method | Path | Phase 4 behavior |
|---|---|---|
| `GET` | `/api/v1/rooms` | Sanitized `WAITING` lobby summaries, ordered by latest activity |
| `POST` | `/api/v1/rooms` | Creates a `WAITING` public/private room and an owner spectator membership |
| `GET` | `/api/v1/rooms/{roomId}` | Sanitized snapshot for an active member |
| `POST` | `/api/v1/rooms/{roomId}/join` | Joins/rejoins as spectator or seated player; seated join atomically transfers the configured buy-in |
| `POST` | `/api/v1/rooms/{roomId}/leave` | Leaves a `WAITING` room, cashes out, releases the seat, and applies owner succession |

`CreateRoomRequest` contains `name`, `roomType`, `maxPlayers`, `smallBlind`, `bigBlind`, `buyIn`, and an optional private-room `password`. Public rooms reject passwords. Private passwords are BCrypt hashes at rest and neither raw values nor hashes appear in responses or events.

`JoinRoomRequest` contains `spectator`, optional `seatNumber`, optional `buyInAmount`, and optional private-room `password`. A seated join requires a free seat within `1..maxPlayers` and exactly the configured buy-in. A spectator has a null seat and zero Table Chips. Controlled errors include `ROOM_NOT_FOUND`, `ROOM_NOT_JOINABLE`, `INVALID_ROOM_PASSWORD`, `ALREADY_JOINED`, `ROOM_FULL`, `INVALID_SEAT`, `SEAT_OCCUPIED`, `INVALID_BUY_IN`, and `INSUFFICIENT_CHIPS`.

In `WAITING`, leave returns all remaining Table Chips to Account Chips atomically. If the owner leaves, ownership transfers to the earliest remaining active membership; an empty room becomes `CLOSED`. Active-game leave is deliberately rejected until the Poker Engine owns that transition.

| Method | Path | Purpose |
|---|---|---|
| GET | `/rooms` | Filtered/paginated public lobby list |
| POST | `/rooms` | Create room (name, capacity 6–9, visibility, optional password, game settings) in `WAITING` |
| GET | `/rooms/{roomId}` | Sanitized room snapshot for authorized viewer |
| POST | `/rooms/{roomId}/join` | Join as player with buy-in or as spectator; optional password only in body |
| POST | `/rooms/{roomId}/leave` | Leave room |
| PATCH | `/rooms/{roomId}` | Owner updates allowed pre-game settings |
| POST | `/rooms/{roomId}/transfer-owner` | Transfer ownership if policy allows |
| DELETE | `/rooms/{roomId}` | Close an eligible room |

Ready/unready can be a STOMP command for responsive room updates. REST may later expose a fallback, but both transports must call the same use case.

Example create request:

```json
{
  "name": "Friday Table",
  "capacity": 6,
  "visibility": "PRIVATE",
  "password": "request-only-secret",
  "smallBlind": 10,
  "bigBlind": 20,
  "minBuyIn": 2000
}
```

Responses expose `passwordProtected: true`, never the password or hash.

For a player join, `buyIn` is an Account Chip amount transferred atomically to Table Chips by the server. The server rejects insufficient Account Chips and never trusts a client-reported balance. A legal leave returns remaining Table Chips to Account Chips. Room states are `WAITING`, `PLAYING`, `FINISHED`, and `CLOSED`; room-player states are `NOT_READY`, `READY`, `PLAYING`, `SPECTATING`, `DISCONNECTED`, and `LEAVING`.

Leaving in `WAITING` is immediate. Leaving during an active Poker Hand causes an authoritative fold, preserves committed pot chips, sets `LEAVING`, and defers removal/cash-out until hand completion. If a waiting-room owner leaves, ownership transfers to an eligible player or the empty room closes; an active hand is never terminated merely because its owner leaves or disconnects.

## Games and history

| Method | Path | Purpose |
|---|---|---|
| GET | `/games/{gameId}/snapshot` | Role-filtered authoritative recovery/bootstrap snapshot |
| GET | `/games/{gameId}` | Session metadata/status |
| GET | `/games/{gameId}/hands` | Paginated completed hand summaries |
| GET | `/games/{gameId}/hands/{handId}` | Authorized hand detail/action history |
| GET | `/games/{gameId}/actions` | Auditable, visibility-filtered action feed |

Live poker actions are sent only through the documented serialized command path, initially STOMP. Snapshot responses contain `stateVersion`, current `turnId` if applicable, `serverTime`, and `turnDeadline`, and are filtered so only the requesting seated player receives their own hole cards.

## Ranking and analytics

| Method | Path | Purpose |
|---|---|---|
| GET | `/leaderboard` | Paginated/filterable current rankings |
| GET | `/players/{playerId}/ranking-history` | Rank history |
| GET | `/analytics/summary` | Authorized current platform summary |
| GET | `/analytics/daily` | Authorized daily series |
| GET | `/analytics/weekly` | Authorized weekly series |

Visibility of analytics remains unresolved: public aggregate metrics should be separated from admin-only operational metrics.

## Administration

All endpoints require an admin role and produce an audit record.

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/users` | Search/page users |
| GET | `/admin/users/{userId}` | Moderation detail |
| POST | `/admin/users/{userId}/lock` | Lock with reason and optional expiry |
| POST | `/admin/users/{userId}/unlock` | Unlock with reason |
| GET | `/admin/rooms` | Monitor rooms |
| GET | `/admin/games` | Monitor active/recent games |
| GET | `/admin/statistics` | Operational dashboard |
| POST | `/admin/games/{gameId}/terminate` | Exceptional audited termination, subject to policy |

## Deferred contract decisions

- Account identifier/login policy and verification/recovery requirements.
- Refresh-token rotation and broader session/device management. Phase 3 returns a non-rotating opaque refresh token, persists only its SHA-256 hash, and revokes it on logout.
- Cursor format, consistent `400` versus `422`, and formal OpenAPI publication.
- Buy-in limits, rebuy eligibility/limits, and insufficient-funds/error policy. The Account Chip to Table Chip model itself is approved.
- Chat retention/moderation, history visibility, and spectator permissions.
- Admin termination/refund semantics.
