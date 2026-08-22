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

| Destination | Command | Required payload |
|---|---|---|
| `/app/room/{roomId}/ready` | `SET_READY` | `clientCommandId`, `ready` |
| `/app/room/{roomId}/chat` | `SEND_CHAT` | `clientCommandId`, `text` |
| `/app/game/{gameId}/action` | `PLAYER_ACTION` | `clientActionId`, `turnId`, `expectedStateVersion`, `action`, optional `amount` |

Poker `action` is one of `FOLD`, `CHECK`, `CALL`, `BET`, `RAISE`, or `ALL_IN`. Amount semantics must be fixed before implementation (recommended: total committed amount for bet/raise, with server-calculated call amounts). The server ignores all client-calculated pots, stacks, winners, cards, legal-action lists, actors, and deadlines.

Room creation/join/leave remain REST commands initially because they require durable request/response outcomes and may include a password. Passwords are never sent through public topics.

## Public events

Lobby topic examples:

- `ROOM_CREATED`, `ROOM_UPDATED`, `ROOM_REMOVED` with sanitized lobby summaries;
- aggregate presence/count updates if approved.

Room topic examples:

- `MEMBER_JOINED`, `MEMBER_LEFT`, `OWNER_CHANGED`;
- `PLAYER_STATE_CHANGED`, `READY_CHANGED`, `ROOM_STATE_CHANGED`, `ROOM_SETTINGS_CHANGED`;
- `CHAT_MESSAGE_CREATED`;
- `GAME_STARTED`, `GAME_ENDED`.

Game topic examples:

- `HAND_STARTED`, `PHASE_CHANGED`, `COMMUNITY_CARDS_DEALT`;
- `TURN_STARTED` with `turnId`, deadline, and public legal-context summary;
- `PLAYER_ACTED` with sanitized action/amount;
- `POTS_UPDATED`, `SHOWDOWN_REVEALED`, `HAND_COMPLETED`;
- `GAME_STATE_CHANGED` or `GAME_SNAPSHOT_AVAILABLE` for reconciliation.

Public events never reveal unrevealed hole cards, deck order, private authentication data, room passwords/hashes, or hidden mucked cards. Showdown cards are public only when game rules mark them revealed.

## Private events

`/user/queue/private` carries the authenticated user's:

- `HOLE_CARDS_DEALT` and private snapshot fragment;
- detailed legal actions/limits if the product keeps them private;
- command acknowledgements/rejections;
- reconnect restoration data.

`/user/queue/notifications` carries friend requests/decisions, invitations if added, and relevant account notifications. Spectators never receive `HOLE_CARDS_DEALT`; a seated player receives only their own cards.

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
