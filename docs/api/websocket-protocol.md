# WebSocket/STOMP Protocol Proposal

## Transport and destinations

- WebSocket/STOMP endpoint: `/ws` (SockJS fallback is not assumed).
- Client send prefix: `/app`.
- Broadcast subscriptions: `/topic/lobby`, `/topic/room/{roomId}`, `/topic/game/{gameId}`.
- Authenticated private subscriptions: `/user/queue/private`, `/user/queue/notifications`, and optionally `/user/queue/errors`.
- Heartbeats are configured on client and server. Payload size, send-buffer, rate, and idle limits must be bounded.

Subscriptions are authorized by destination: lobby visibility, room membership/spectator status, and game participation are checked on every `SUBSCRIBE`. Authorization is repeated on every `SEND`. Guessing an ID must not grant access.

## Authentication

The client obtains an access credential via REST, then supplies it in the STOMP `CONNECT` headers over WSS. A channel interceptor validates it and binds the authenticated principal. Do not place tokens in the WebSocket URL or broadcast payloads. Expired credentials cause a sanitized private error/disconnect; the client refreshes over REST and reconnects.

The browser WebSocket API cannot freely set HTTP handshake headers, so the final design must validate the chosen STOMP-header approach and CSRF/origin protections. Restrict allowed origins; do not rely on CORS alone for message authorization.

## Event envelope

All server events use a versioned envelope:

```json
{
  "protocolVersion": 1,
  "eventId": "uuid",
  "type": "GAME_STATE_CHANGED",
  "occurredAt": "2026-08-22T01:30:00Z",
  "correlationId": "uuid-or-null",
  "scope": { "roomId": "uuid", "gameId": "uuid" },
  "stateVersion": 42,
  "payload": {}
}
```

Fields not relevant to an event may be absent. `stateVersion` is authoritative for game-state events. Event schemas are additive within a protocol version; breaking changes require a new version. Public and private payload DTOs are distinct types, not one object serialized with ad-hoc field hiding.

Private command result/error example:

```json
{
  "protocolVersion": 1,
  "eventId": "uuid",
  "type": "COMMAND_REJECTED",
  "occurredAt": "2026-08-22T01:30:01Z",
  "correlationId": "client-action-uuid",
  "stateVersion": 43,
  "payload": {
    "code": "STALE_TURN",
    "message": "The turn has already advanced.",
    "resyncRequired": true
  }
}
```

## Client commands

Phase 4 implements STOMP at `/ws` without SockJS. A client supplies `Authorization: Bearer <access-token>` in the STOMP `CONNECT` headers. The server validates the Phase 3 JWT, reloads the active account, and binds the authenticated principal. `/topic/lobby` requires authentication; `/topic/room/{roomId}` additionally requires active membership on every `SUBSCRIBE`.

Implemented Phase 4 command:

```text
/app/room/{roomId}/ready
```

Payload:

```json
{ "clientCommandId": "uuid", "ready": true }
```

The handler ignores client identity/state fields and resolves membership from the authenticated principal. Only an active seated member in a `WAITING` room can ready or unready.

| Destination | Command | Required payload |
|---|---|---|
| `/app/room/{roomId}/ready` | `SET_READY` | `clientCommandId`, `ready` |
| `/app/room/{roomId}/chat` | `SEND_CHAT` (Phase 10B.3) | `clientMessageId`, `content` |
| `/app/game/{gameId}/action` | `PLAYER_ACTION` | `clientActionId`, `turnId`, `expectedStateVersion`, `action`, optional `amount` |

Poker `action` is one of `FOLD`, `CHECK`, `CALL`, `BET`, `RAISE`, or `ALL_IN`. Amount semantics must be fixed before implementation (recommended: total committed amount for bet/raise, with server-calculated call amounts). The server ignores all client-calculated pots, stacks, winners, cards, legal-action lists, actors, and deadlines.

Room creation/join/leave remain REST commands initially because they require durable request/response outcomes and may include a password. Passwords are never sent through public topics.

## Public events

Lobby topic examples:

- `ROOM_CREATED`, `ROOM_UPDATED`, and `ROOM_CLOSED` with sanitized lobby summaries;
- `PLAYER_COUNT_CHANGED` when seated or spectator membership changes.

Room topic examples:

- `PLAYER_JOINED` and `PLAYER_LEFT`;
- `PLAYER_READY` and `PLAYER_UNREADY`;
- `PLAYER_DISCONNECTED` and `PLAYER_RECONNECTED` are reserved until reconnect behavior is implemented.

The canonical Phase 4 vocabulary is `ROOM_CREATED`, `ROOM_UPDATED`, `ROOM_CLOSED`,
`PLAYER_COUNT_CHANGED`, `PLAYER_JOINED`, `PLAYER_LEFT`, `PLAYER_READY`, and
`PLAYER_UNREADY`. No `MEMBER_*`, `READY_CHANGED`, or `ROOM_REMOVED` aliases are used.
Events use protocol version 1, a unique event ID, UTC occurrence time, room scope,
and a sanitized summary/snapshot payload. Publication occurs only after the database transaction commits.
- `CHAT_MESSAGE` (Phase 10B.3, broadcast on `/topic/room/{roomId}` only after persistence commits);
- `GAME_STARTED`, `GAME_ENDED`.

Game topic examples:

- `HAND_STARTED`, `PHASE_CHANGED`, `COMMUNITY_CARDS_DEALT`;
- `TURN_STARTED` with `turnId`, deadline, and public legal-context summary;
- `PLAYER_ACTED` with sanitized action/amount;
- `POTS_UPDATED`, `SHOWDOWN_REVEALED`, `HAND_COMPLETED`;
- `GAME_STATE_CHANGED` or `GAME_SNAPSHOT_AVAILABLE` for reconciliation.

Public events never reveal unrevealed hole cards, deck order, private authentication data, room passwords/hashes, or hidden mucked cards. Showdown cards are public only when game rules mark them revealed.

## Frozen room-chat contract (Phase 10B.1)

Room chat is plain-text communication for current active room members only. Active membership means the existing `room_players` row has `left_at IS NULL`; both seated players and current spectators may send and read history. Non-members, departed or administratively removed members, inactive accounts, and all users after the room reaches `CLOSED` may not send. Chat remains allowed during an active hand and never enters Poker Engine or turn validation. Public/private room type makes no difference after legitimate admission.

Phase 10B.3 implements the existing `/app/room/{roomId}/chat` command; the server resolves sender identity solely from the authenticated principal. Its command contains required UUID `clientMessageId` and `content`, never `senderUserId`. The committed event type is `CHAT_MESSAGE`, broadcast on the existing `/topic/room/{roomId}` envelope with the authoritative safe message DTO. The payload contains `messageId`, `roomId`, `clientMessageId`, normalized `content`, `createdAt`, and safe `sender { userId, displayName, avatarUrl }`. There are no typing, receipt, edit, delete, attachment, private-message, or presence events.

Content is stripped at both edges, must remain nonblank, must contain no ISO control characters (therefore Phase 10B does not support multiline messages), and is limited to 500 Unicode code points. Internal ordinary Unicode whitespace is preserved. The database stores the normalized content as `utf8mb4` `VARCHAR(500)`.

The persistence sequence is authorization, normalization/idempotency validation, insert, transaction commit, `AFTER_COMMIT`, then best-effort broadcast. A rollback emits no event. An exact idempotent retry reuses the row and emits no duplicate event; a conflicting retry is rejected. Each realtime envelope gets a fresh event UUID independent of database `messageId` and client `clientMessageId`. `registry.setPreservePublishOrder(true)` preserves broker order for the same destination. STOMP is not durable; clients will recover missed messages through the separately planned REST history adapter.

## Private events

`/user/queue/private` carries the authenticated user's:

- `HOLE_CARDS_DEALT` and private snapshot fragment;
- detailed legal actions/limits if the product keeps them private;
- command acknowledgements/rejections;
- reconnect restoration data.

`/user/queue/notifications` carries friend requests/decisions, invitations if added, and relevant account notifications. Spectators never receive `HOLE_CARDS_DEALT`; a seated player receives only their own cards.

Phase 10A.3 uses `/user/queue/notifications` as an authenticated private queue. Its envelope is `{ protocolVersion: 1, eventId, type, occurredAt, payload }`; `payload` contains the friendship `requestId` when applicable and only the actor's safe player projection (`userId`, `displayName`, optional `avatarUrl`). Types are `FRIEND_REQUEST_RECEIVED`, `FRIEND_REQUEST_ACCEPTED`, `FRIEND_REQUEST_REJECTED`, and `FRIEND_REMOVED`. A new or reopened request notifies its recipient; accept/reject notifies the original requester; remove notifies the other participant. A crossed request sends only `FRIEND_REQUEST_ACCEPTED` to the original requester. Frames are emitted only after commit, never for conflicts, authorization failures, no-ops, or rolled-back work. Delivery is best effort rather than durable; clients reconcile with the friendship REST endpoints after reconnect.

Phase 10C adds `FRIEND_STATUS_CHANGED` to that same version-1 private notification protocol. Its payload is exactly `{ userId, presenceStatus }`, where status is `ONLINE` or `OFFLINE`; it excludes usernames, account fields, session IDs, connection counts, network metadata, room data, and cards. Only accepted friends are recipients. Pending, rejected, and unrelated users receive nothing. The state change is committed to the player profile before the `AFTER_COMMIT` listener attempts best-effort STOMP delivery.

Account presence is connection-derived: zero authenticated STOMP sessions means `OFFLINE`, while one or more means `ONLINE`. The first session produces the online transition, additional tabs/devices do not; only the final disconnect produces the offline transition. Duplicate connect/disconnect callbacks are idempotent, and reconnect after a true offline transition can produce one new online event. Login, token lifetime, and Poker-room `PLAYER_DISCONNECTED`/`PLAYER_RECONNECTED` state do not define account presence. The live-session registry is intentionally in-memory for the current single-server deployment, and startup resets stale persisted online values because a new JVM has no inherited live sessions. Online/offline friend status is the specification requirement; multi-session handling and startup reconciliation are correctness enhancements.

## Ordering, duplicates, and acknowledgements

The game serialization boundary defines event order for one game. `stateVersion`, `eventId`, and command correlation permit gap/duplicate detection. Transport delivery is treated as at-least-once or lossy across disconnects, not exactly-once. Correctness comes from idempotent commands and authoritative snapshots.

Broker acknowledgement mode and whether the simple in-memory broker is sufficient must be validated in Phase 1. The application must not claim durable delivery merely because a STOMP send returned.

## Reconnect and snapshot strategy

1. Client reconnects and authenticates after network loss/token refresh.
2. It resubscribes to destinations for which the server still authorizes it.
3. It requests `GET /api/v1/games/{gameId}/snapshot` (and room snapshot if needed), supplying/retaining its last observed version for diagnostics.
4. Server returns a role-filtered snapshot with `stateVersion`, `turnId`, `serverTime`, and deadline.
5. Client replaces local authoritative state, drops obsolete optimistic commands, then processes strictly newer events.

For active players, the initial reconnect window is 60 seconds. A disconnect produces authoritative `DISCONNECTED` state while retaining seat and Table Chips. The game continues; an expired turn produces `AUTO_CHECK` if legal or otherwise `AUTO_FOLD`. Reconnection authenticates and restores the session, locates the active room/game, returns a role-filtered snapshot and only that player's hole cards through the private queue, then resumes events.

A leave command during a Poker Hand is processed as a fold, keeps committed chips in the pot, publishes `LEAVING`, and defers removal until hand completion. Waiting-room departures are immediate; owner departure transfers ownership to an eligible player or closes an empty room. No owner event terminates an active hand.

If an event gap is detected during a live connection, the client pauses application of dependent events and repeats snapshot recovery. Full event replay is optional future work.

## Error and abuse handling

Malformed frames, oversized chat, rate violations, unauthorized destinations, and invalid commands produce stable error codes without internals. Repeated abuse may close the connection and is audited. Chat is length-limited and sanitized on rendering; server storage preserves safe text rules. Logs redact tokens, passwords, and private cards.
