# Concurrency and State Synchronization

## Active game state

Each running game has one authoritative mutable aggregate containing phase, players, stacks, commitments, pots, board/deck, dealer/blinds, acting seat, legal-action context, deadlines, `turnId`, and monotonically increasing `stateVersion`. Only the game application/runtime layer can mutate it; clients and other modules receive immutable projections.

Persisted checkpoints/action history make state auditable and define recovery. The exact checkpoint frequency is deferred, but an accepted action must not be acknowledged as durable before its required transaction commits.

## Per-game serialization boundary

All mutations for one `gameId`—player commands, timeout callbacks, disconnect policies, and administrative termination—run sequentially through that game's processor. Different games run concurrently. An implementation may use a per-game lock or a single-consumer command queue per game; a serialized queue is preferred because ordering and timer handling are explicit.

The registry must create/remove processors atomically and avoid two processors owning the same active game. Locks are never held while performing slow network broadcasts. Database work and state publication follow a consistent order, and failures trigger snapshot recovery rather than speculative client state.

Initial deployment assumes one application instance. Horizontal scaling requires explicit game ownership/routing or distributed coordination and is out of Phase 0 scope; deploying multiple uncoordinated instances is unsafe.

## Command identity and validation

Every game command carries:

- `clientActionId`: client-generated UUID, unique for a player/game command;
- `turnId`: opaque server-issued identifier for the current decision window;
- `expectedStateVersion`: last authoritative version the client acted upon.

Inside the game boundary, validation order is: authenticated identity, membership/seat, command shape, duplicate ID, game status, expected turn, expected version policy, then poker legality/amount. A stale command is rejected with the current version and a resynchronization hint. The server may accept a version older only for explicitly version-insensitive commands; poker actions are version-sensitive.

## Duplicate handling

Accepted and terminally rejected `clientActionId` results are retained for a bounded game/session window, keyed with player identity and game. A retry returns the original outcome without applying it again. A reused ID with a different payload is rejected as a conflict and audited.

The database should enforce a uniqueness constraint on the durable action identity (conceptually game, actor, client action ID) as a second defense. Internal event consumers also deduplicate by event ID.

## Version and event ordering

`stateVersion` increments exactly once for each externally observable authoritative transition, including timer-driven transitions. Public/private events include the resulting version. Clients:

1. apply an event only when it is the next expected version;
2. ignore already-applied versions (while still allowing multiple event fragments tagged with the same transition/event ID);
3. fetch a snapshot on a gap, incompatible schema, or reconnect;
4. replace local projections with the snapshot before consuming newer events.

An event envelope includes a unique `eventId` so multiple public/private representations of one transition are not confused with separate state mutations.

## Timer safety

The server records an absolute UTC `turnDeadline` and publishes it with `turnId`; clients display a countdown but do not decide expiry. A scheduled callback captures `gameId`, `turnId`, expected version, and deadline. When it runs inside the game processor, it verifies that the game, turn, and deadline are still current. Old callbacks become no-ops.

If a player action and timeout arrive together, serialization establishes one winner. The second command observes a changed `turnId`/version and cannot apply. Timer cancellation is an optimization, never the correctness mechanism. The approved timeout rule is `AUTO_CHECK` when `CHECK` is legal and otherwise `AUTO_FOLD`.

## Disconnect and presence

A user may have multiple browser connections. Presence is tracked per connection and aggregated per user. When the last connection closes, an active player is marked `DISCONNECTED` without losing their seat, Table Chips, or game state. The initial `RECONNECT_TIMEOUT` is 60 seconds and must be configurable later. The game processor receives disconnect, reconnect, turn-expiry, and grace-expiry commands through the same serialization boundary.

Disconnect does not freeze a game or grant extra client authority. The current server timer continues; if it expires, the server applies `AUTO_CHECK` when checking is legal and otherwise `AUTO_FOLD`. Reconnection inside the grace window restores the player to the appropriate authoritative room-player state. The post-60-second seat/removal policy beyond already-expired-turn behavior remains to be specified.

If a player intentionally leaves while the room is `WAITING`, removal is immediate and remaining Table Chips return atomically to Account Chips. If a Poker Hand is active, the leave command is serialized as a fold, already committed chips remain in the pot, state becomes `LEAVING`, and removal/cash-out occurs after hand completion. An owner's disconnect/leave never terminates an active hand; in `WAITING`, ownership transfers to an eligible player or the empty room closes.

## Reconnect and restoration

After authentication and session restoration, the server locates the reconnecting user's active room/game (the client may also supply its last observed version). It rechecks membership/spectator authorization and returns a role-specific snapshot:

- public table/board/pot/turn state for authorized members/spectators;
- the reconnecting seated player's own hole cards via private response only;
- no other private cards.

The snapshot includes `stateVersion`, current `turnId` when applicable, and server deadline/time. The client discards conflicting optimistic state. Event replay may be added later; snapshot-first recovery is the baseline.

## Failure and cleanup risks

- Processor creation/removal races require atomic registry operations and lifecycle tests.
- A process crash between commit and publish requires a recoverable event/publication strategy.
- Unbounded idempotency caches and abandoned games require retention/cleanup policies.
- Clock changes require UTC instants and a monotonic scheduler for elapsed time where possible.
- Database deadlocks/retries must not rerun non-idempotent mutations outside the command identity guard.
