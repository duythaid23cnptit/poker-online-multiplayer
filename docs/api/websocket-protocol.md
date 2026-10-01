# WebSocket/STOMP protocol

The implemented realtime transport is native WebSocket with STOMP at `/ws`. SockJS is not enabled. The broker prefixes are `/topic` and `/queue`, the application prefix is `/app`, and the user-destination prefix is `/user`. The simple broker preserves publication order for a destination.

The complete REST/realtime consumer map is in [contract-traceability.md](contract-traceability.md).

## Authentication and authorization

The client supplies `Authorization: Bearer <access-token>` in the STOMP `CONNECT` headers. Tokens never appear in the WebSocket URL, subscriptions, frames, logs, or query state. The allowed browser origin is currently `http://localhost:5173`.

On `CONNECT`, the server validates the access token, reloads the user, rejects inactive accounts, and binds an authenticated principal. Before every later `SEND` and `SUBSCRIBE`, it reloads the account again and requires both `ACTIVE` status and `ROLE_PLAYER`. Admin clients do not bootstrap this player realtime connection.

Subscription rules are:

| Destination | Rule |
|---|---|
| `/topic/lobby` | Authenticated active `PLAYER` account |
| `/topic/room/{roomId}` | Active room membership, including spectators |
| `/topic/game/{gameId}` | Authorized active-game observer |
| `/user/queue/private` | The broker-resolved authenticated player's own queue |
| `/user/queue/notifications` | The broker-resolved authenticated player's own queue |

Application services repeat object-level command checks. Destination knowledge or a visible frontend control never grants authority.

## Client commands

There are exactly three inbound destination patterns.

| Destination | Payload | Application authorization and semantics |
|---|---|---|
| `/app/room/{roomId}/ready` | `{ "clientCommandId": "uuid", "ready": true }` | Active seated member in a `WAITING` room. Changes readiness only and never starts a game. |
| `/app/room/{roomId}/chat` | `{ "clientMessageId": "uuid", "content": "text" }` | Active member of a non-closed room. Sender identity comes from the principal. |
| `/app/game/{gameId}/action` | `{ "actionType": "FOLD|CHECK|CALL|BET|RAISE|ALL_IN", "amount": number?, "turnId": "uuid", "clientActionId": "uuid" }` | Current participant and turn. The server validates membership, turn, legal action, target amount, and idempotency/staleness. |

For `BET` and `RAISE`, `amount` is required and means the requested total current bet, including chips already committed on that betting street. The client never sends cards, pots, winners, stacks, usernames, deadlines, legal actions, or readiness identity as authority.

Room creation, joining, spectator conversion, explicit host start, waiting-room leave, and active-game departure use REST because they need an immediate durable response and may involve password or chip-transfer errors.

## Room and lobby events

Room/lobby lifecycle events use:

```json
{
  "protocolVersion": 1,
  "eventId": "uuid",
  "type": "PLAYER_READY",
  "occurredAt": "2026-09-08T00:00:00Z",
  "scope": { "roomId": 12 },
  "payload": {}
}
```

`/topic/lobby` carries `ROOM_CREATED`, `ROOM_CLOSED`, and `PLAYER_COUNT_CHANGED` with a sanitized room summary. The frontend inserts, updates, or removes the corresponding waiting-room list item.

`/topic/room/{roomId}` carries:

- `PLAYER_JOINED`, `PLAYER_LEFT`, `PLAYER_READY`, and `PLAYER_UNREADY`, normally with an authoritative room detail snapshot;
- `PLAYER_DISCONNECTED` and `PLAYER_RECONNECTED` with the affected user and state;
- `GAME_STARTED` with `gameId`, `gameSessionId`, `handId`, and `handNumber`.

The `ROOM_UPDATED` vocabulary value is defined but has no current publisher path. Consumers accept it additively and reconcile through room detail rather than inventing state.

All room/lobby publication occurs after the room transaction commits. Passwords and password hashes never enter these events.

## Chat events

Persisted chat messages share `/topic/room/{roomId}` but have a separate version-1 envelope:

```json
{
  "protocolVersion": 1,
  "eventId": "uuid",
  "type": "CHAT_MESSAGE",
  "occurredAt": "2026-09-08T00:00:00Z",
  "scope": { "roomId": 12 },
  "payload": {
    "messageId": 91,
    "roomId": 12,
    "clientMessageId": "uuid",
    "content": "Good hand",
    "createdAt": "2026-09-08T00:00:00Z",
    "sender": { "userId": 7, "displayName": "River", "avatarUrl": null }
  }
}
```

Content is trimmed, nonblank, free of ISO control characters, and limited to 500 Unicode code points. `(roomId, senderUserId, clientMessageId)` is the persistence idempotency key. An exact retry reuses the row and does not rebroadcast; reuse with different content is rejected. The event is emitted only after commit.

STOMP delivery is best effort. `GET /api/v1/rooms/{roomId}/chat/messages` is the durable recovery contract. The client merges history and realtime messages by authoritative message ID and command identity.

## Game events

Public and private game frames use the same envelope. Unlike room/chat envelopes, this implemented game envelope has no `protocolVersion` field and uses `timestamp` plus top-level room/game/version fields:

```json
{
  "eventId": "uuid",
  "type": "GAME_STATE_UPDATE",
  "timestamp": "2026-09-08T00:00:00Z",
  "roomId": 12,
  "gameId": "uuid",
  "version": 42,
  "payload": {}
}
```

| Type | Payload summary | Visibility |
|---|---|---|
| `GAME_STARTED` | persistent session ID, hand ID/number | Public game topic |
| `HAND_STARTED` | hand ID/number, dealer/blind seats and amounts, public players | Public game topic |
| `HOLE_CARDS` | hand ID and exactly the recipient's cards | Private user queue only |
| `COMMUNITY_CARDS` | phase and public board | Public game topic |
| `YOUR_TURN` | hand/turn IDs, legal actions, call/min/max targets, own stack | Acting player's private queue only |
| `PLAYER_ACTION` | player/seat, action, committed amount, resulting bet/stack, command ID, automatic flag | Public game topic |
| `TIMER_UPDATE` | hand/turn IDs, acting seat, seconds, deadline | Public game topic; private replay on reconnect |
| `GAME_STATE_UPDATE` | full public state including players, commitments, completion flags | Public game topic; private recovery copy on reconnect |
| `SHOWDOWN` | hand ID and board; no hole cards | Public game topic |
| `GAME_RESULT` | pot awards, odd-chip data, returns, final public players, reason | Public game topic |
| `HAND_FINISHED` | hand ID/number and reason | Public game topic |
| `COMMAND_ERROR` | stable code and correlated client action ID | Requesting player's private queue only |

`GAME_RESULT` awards contain `potIndex`, `potType`, `potAmount`, `winnerUserIds`, `baseShare`, `oddChipUserIds`, and per-user `winnerPayouts`. Public player projections contain `userId`, seat, Table Chips, current bet, nullable total committed amount, participation, connection, and leaving flags.

The server never publishes hole cards to `/topic/game/{gameId}`. Spectators receive only public frames and never receive `HOLE_CARDS` or `YOUR_TURN`. Persisted historical snapshots return only the requesting participant's cards under the REST authorization policy.

## Friend and presence notifications

`/user/queue/notifications` carries two version-1 envelope families:

- social types `FRIEND_REQUEST_RECEIVED`, `FRIEND_REQUEST_ACCEPTED`, `FRIEND_REQUEST_REJECTED`, and `FRIEND_REMOVED`, with nullable request ID and the actor's safe player projection;
- `FRIEND_STATUS_CHANGED`, with only `userId` and `presenceStatus`.

Only the addressed user receives the frame. Presence changes are sent only to accepted friends. These best-effort notifications contain no email, role, account state, chip balance, room/session details, connection count, or network metadata. The client invalidates friendship state after reconnect to recover missed frames.

## Ordering, reconnect, and recovery

The game runtime serializes mutations per game. Events include unique IDs and state versions; the client rejects duplicate event IDs and older same-hand versions. It resets hand-transient state when a newer hand arrives.

On initial entry and every STOMP reconnect, the game client fetches `GET /api/v1/games/{gameId}/snapshot`. It buffers incoming game frames while that fetch is in flight, applies the authoritative snapshot, then applies newer buffered events. A direct URL and F5 use the same flow. Global reconnect handling also invalidates the active-game, room, and friendship queries.

Room `GAME_STARTED` navigation is backed by `GET /api/v1/games/active/room/{roomId}` so a missed discovery frame does not strand a member in the waiting room. Chat rehydrates from REST and de-duplicates the merged history.

The STOMP client requests 10-second incoming/outgoing heartbeats and reconnects after five seconds. Transport delivery itself is not durable or exactly once; snapshots, persisted chat history, idempotent command IDs, and authoritative REST state provide recovery.
