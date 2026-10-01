# REST API

The implemented API uses JSON under `/api/v1`. Except for registration, login, refresh, and the health probe, requests require `Authorization: Bearer <access-token>`. User identity always comes from the authenticated principal. Player application routes require `ROLE_PLAYER`; administration routes require `ROLE_ADMIN`. Logout and `GET /me` are shared by both authenticated roles.

The full endpoint-to-frontend audit is maintained in [contract-traceability.md](contract-traceability.md).

## Errors and validation

Application errors use:

```json
{
  "code": "SEAT_OCCUPIED",
  "message": "Seat is occupied",
  "fieldErrors": []
}
```

Jakarta request validation returns `400 VALIDATION_FAILED` with `fieldErrors` containing `field`, validation `code`, and safe `message`. Malformed JSON and invalid path/query values return `400 INVALID_REQUEST`. Missing authentication returns `401 UNAUTHORIZED`; a Spring Security role denial returns `403 FORBIDDEN`. Unexpected failures return `500 INTERNAL_ERROR` without internals.

Controllers that translate a `ResponseStatusException` currently expose the shared wire code `INVALID_REQUEST` with the controller's safe reason. Room, friendship, authentication, and admin-moderation business exceptions preserve their specific codes.

## Authentication and current player

| Method | Path | Authentication | Request | Response | Success |
|---|---|---|---|---|---:|
| POST | `/auth/register` | Public | `username` (3-50, letters/numbers/underscore), `password` (8-72), optional `email`, `displayName` (2-100) | current user | 201 |
| POST | `/auth/login` | Public | `username`, `password` | access token, opaque refresh token, `tokenType: "Bearer"`, expiry seconds | 200 |
| POST | `/auth/refresh` | Public | `refreshToken` | access token, `tokenType: "Bearer"`, expiry seconds | 200 |
| POST | `/auth/logout` | Bearer | `refreshToken` | none | 204 |
| GET | `/me` | Bearer | none | current user/account/profile | 200 |
| PATCH | `/me` | `ROLE_PLAYER` | `displayName`, nullable `avatarUrl` | updated current user | 200 |

The current-user response contains `id`, `username`, nullable `email`, `role` (`PLAYER` or `ADMIN`), `accountStatus` (`ACTIVE` or `LOCKED`), `accountChips`, `displayName`, nullable `avatarUrl`, and `onlineStatus` (`ONLINE`, `IN_GAME`, or `OFFLINE`). Password hashes and tokens never appear in it.

Authentication business codes include `AUTHENTICATION_FAILED`, `ACCOUNT_LOCKED`, `INVALID_REFRESH_TOKEN`, `DUPLICATE_ACCOUNT`, and `PROFILE_NOT_FOUND`.

## Rooms and waiting-room lifecycle

Every route in this section requires `ROLE_PLAYER`.

| Method | Path | Request/query | Response | Success |
|---|---|---|---|---:|
| POST | `/rooms` | `CreateRoomRequest` | room detail | 201 |
| GET | `/rooms` | none | waiting-room summaries | 200 |
| GET | `/rooms/{roomId}` | room ID | room detail | 200 |
| POST | `/rooms/{roomId}/join` | `JoinRoomRequest` | room detail | 200 |
| POST | `/rooms/{roomId}/leave` | room ID | room detail after departure | 200 |

`CreateRoomRequest` contains `name`, `roomType` (`PUBLIC` or `PRIVATE`), `maxPlayers` (6-9), `smallBlind`, `bigBlind`, `buyIn`, and nullable `password`. The big blind must exceed the small blind. A private room requires an 8-72 character password; a public room rejects one.

`JoinRoomRequest` contains `spectator`, nullable `seatNumber`, nullable `buyInAmount`, and nullable `password`. A non-member entering a private room must supply the password. An active authorized spectator converting to a seated player reuses the existing membership and does not reauthenticate the password. The configured fixed buy-in is authoritative.

A room summary contains `id`, `name`, `ownerUserId`, `roomType`, `passwordRequired`, `status`, `seatedPlayers`, `maxPlayers`, blinds, and buy-in. Room detail adds active members with `userId`, authoritative `username`, nullable `seatNumber`, `state`, and `tableChips`.

Waiting-room summaries are authenticated lobby data. Detail for a room outside `WAITING` requires active membership. Room business codes include `ROOM_NOT_FOUND`, `ROOM_DETAIL_FORBIDDEN`, `ROOM_NOT_JOINABLE`, `INVALID_ROOM_PASSWORD`, `ALREADY_JOINED`, `SEAT_REQUIRED`, `INVALID_SEAT`, `SEAT_OCCUPIED`, `ROOM_FULL`, `INVALID_BUY_IN`, `INSUFFICIENT_CHIPS`, `SEAT_OR_MEMBERSHIP_CONFLICT`, `INVALID_ROOM_STATE`, `NOT_ROOM_MEMBER`, `SPECTATOR_CANNOT_READY`, `ACTIVE_GAME_LEAVE_UNAVAILABLE`, and `MEMBERSHIP_NOT_FOUND`.

## Games and recovery

Every route in this section requires `ROLE_PLAYER`, followed by the object-level authorization shown below.

| Method | Path | Authorization | Response | Success |
|---|---|---|---|---:|
| POST | `/games/rooms/{roomId}/start` | Authenticated room host | active-game identity | 200 |
| POST | `/games/{gameId}/leave` | Authenticated `PLAYER` participant | departure status | 200 |
| GET | `/games/{gameId}/snapshot` | Authorized active observer or historical participant | role-filtered game snapshot | 200 |
| GET | `/games/active/room/{roomId}` | Authorized active observer | active-game identity | 200 |
| GET | `/games/active/me` | Authenticated active participant | active-game identity | 200 |

Only the explicit host start route starts a Game Session. The server locks and revalidates room ownership, `WAITING` state, at least two seated players, all seated players `READY`, valid stacks, and the absence of an active session. Readiness changes never call this route or start a game.

The active-game identity is `roomId`, UUID `gameId`, persistent `gameSessionId`, `handId`, and `handNumber`. A snapshot contains those identifiers, `version`, public state, the requester's own `holeCards`, nullable private `turn`, nullable public `timer`, and `participant`. Public state includes dealer/blind seats, phase, current turn user, current bet/minimum raise, board, public player state, `handCompleted`, and `sessionFinished`.

Active observers are current room members. Finished snapshots are available only through the persisted game UUID and historical participation policy. Snapshot privacy never returns another player's hole cards.

Start failures map host denial to 403, a missing room to 404, and lifecycle conflicts to 409. Departure rejects non-player accounts and non-participants with 403 and inactive game state with 409. No active-game lookup returns 404.

## Friends and notifications

Every route in this section requires `ROLE_PLAYER`.

| Method | Path | Request/query | Response | Success |
|---|---|---|---|---:|
| POST | `/friend-requests` | `{ "recipientUserId": number }` | friendship view | 201 for a new row, otherwise 200 |
| GET | `/friend-requests` | required `direction=incoming` or `outgoing` | pending friendship views | 200 |
| POST | `/friend-requests/{requestId}/accept` | request ID | friendship view | 200 |
| POST | `/friend-requests/{requestId}/reject` | request ID | friendship view | 200 |
| GET | `/friends` | none | accepted friendships with presence | 200 |
| DELETE | `/friends/{friendId}` | the other user's ID | none | 204 |

A friendship view contains request metadata and only the other player's safe `userId`, `displayName`, and nullable `avatarUrl`. `presenceStatus` is included only where the server projects it. Email, roles, chips, credentials, room data, and game data are excluded.

Business codes include `SELF_FRIEND_REQUEST`, `PLAYER_NOT_FOUND`, `FRIEND_REQUEST_ALREADY_EXISTS`, `FRIENDSHIP_ALREADY_EXISTS`, `FRIEND_REQUEST_NOT_FOUND`, `FRIEND_REQUEST_NOT_AUTHORIZED`, `FRIEND_REQUEST_NOT_PENDING`, and `FRIENDSHIP_NOT_FOUND`.

## Room chat history

This route requires `ROLE_PLAYER` and active room membership.

| Method | Path | Query | Response | Success |
|---|---|---|---|---:|
| GET | `/rooms/{roomId}/chat/messages` | `limit`, default 50, range 1-100 | chronological recent messages | 200 |

The requester must be an active member, including a spectator, and the room must not be closed. Each message contains `messageId`, `roomId`, UUID `clientMessageId`, normalized `content`, `createdAt`, and safe sender projection. Realtime insertion uses the STOMP contract; history is the recovery source after F5 or reconnect.

## Rankings, statistics, and analytics

Every route in this section requires `ROLE_PLAYER`.

| Method | Path | Query | Response | Success |
|---|---|---|---|---:|
| GET | `/rankings/me` | none | current rating, games, peak, nullable global rank | 200 |
| GET | `/rankings/leaderboard` | `page` default 0, `size` default 20 (1-100) | page with `items`, `page`, `size`, `total`; each item includes `userId`, nullable `username`, nullable `displayName`, `rank`, `rating`, `gamesRated`, and `peakRating` | 200 |
| GET | `/rankings/me/history` | `page` default 0, `size` default 20 (1-100) | rating history array | 200 |
| GET | `/players/me/statistics` | none | authoritative lifetime player statistics | 200 |
| GET | `/analytics/me/daily` | required ISO dates `from`, `to`; max 366-day span | stored daily buckets in ascending order | 200 |
| GET | `/analytics/me/weekly` | required ISO dates `from`, `to`; max 104 normalized weeks | stored Monday-start weekly buckets | 200 |
| GET | `/analytics/me/summary` | none | explicit today/current-week buckets, including zeros | 200 |

Statistics and analytics derive identity from the JWT and accept no player ID. They expose completed-history counts, wins/losses/ties, chip movement, largest pot, play time, sessions, and net chips. Missing history returns zeros or an empty range, never fabricated play.

## Administration

Every route below requires `ROLE_ADMIN`. Hiding the frontend route is convenience only; Spring Security remains authoritative. Read responses exclude secrets, password hashes, private-room password hashes, refresh tokens, and hole cards.

| Method | Path | Query/body | Response | Success |
|---|---|---|---|---:|
| GET | `/admin/overview` | none | platform overview | 200 |
| GET | `/admin/users` | `page`, `size`, `search`, `status`, `role` | user page | 200 |
| GET | `/admin/users/{id}` | user ID | safe user/profile/ranking/statistics detail | 200 |
| POST | `/admin/users/{id}/suspend` | optional `{ "reason": string }` | mutation status | 200 |
| POST | `/admin/users/{id}/reactivate` | optional reason | mutation status | 200 |
| GET | `/admin/rooms` | `page`, `size`, `search`, `status`, `roomType` | room page | 200 |
| GET | `/admin/rooms/{id}` | room ID | room configuration and membership history | 200 |
| POST | `/admin/rooms/{roomId}/players/{userId}/remove` | optional reason | mutation status | 200 |
| POST | `/admin/rooms/{id}/close` | optional reason | mutation status | 200 |
| GET | `/admin/games` | `page`, `size`, `status`, `roomId`, `userId`, `from`, `to` | session page | 200 |
| GET | `/admin/games/{id}` | session ID | safe session/participant detail | 200 |
| GET | `/admin/games/{id}/hands` | `page`, `size` | persisted hand page | 200 |
| POST | `/admin/games/{id}/terminate` | optional reason | mutation status | 200 |
| GET | `/admin/audit-log` | pagination, `adminUserId`, `actionType`, `targetType`, `targetId`, `from`, `to` | audit page | 200 |

List sizes are 1-100. Admin game date ranges are at most 366 days. The supported audit actions are `USER_SUSPENDED`, `USER_REACTIVATED`, `PLAYER_REMOVED_FROM_ROOM`, `ROOM_CLOSED`, and `GAME_TERMINATED`; targets are `USER`, `ROOM`, and `GAME_SESSION`.

Mutation reasons are optional, trimmed, and limited to 500 characters. Mutations are idempotent when the requested state already exists. User status changes and their audit row share a transaction. Active-hand removals and terminations may return `deferred: true`; they enter the normal per-game lock and complete at a safe hand boundary.

## Infrastructure

`GET /actuator/health` is public and exists for process/deployment health checks. It is intentionally absent from the product UI.
