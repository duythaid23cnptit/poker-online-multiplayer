# Concurrency and State Synchronization

## Active game state

Each running game has one authoritative mutable aggregate containing phase, players, stacks, commitments, pots, board/deck, dealer/blinds, acting seat, legal-action context, deadlines, `turnId`, and monotonically increasing `stateVersion`. Only the game application/runtime layer can mutate it; clients and other modules receive immutable projections.

Persisted hand/action/settlement history makes completed outcomes auditable. Active recovery uses the in-process aggregate plus authoritative snapshots; process-crash restoration of an active hand is not implemented. Before the web server accepts normal traffic on a new process, startup reconciliation locks every inherited `ACTIVE` session, refunds each active membership's persisted Table Chips exactly once, finalizes those memberships, marks the session `ABORTED`, and moves a `PLAYING` room to `FINISHED`.

## Per-game serialization boundary

All mutations for one `gameId`—player commands, timeout callbacks, disconnect policies, and administrative termination—run sequentially through that game's processor. Different games run concurrently. An implementation may use a per-game lock or a single-consumer command queue per game; a serialized queue is preferred because ordering and timer handling are explicit.

The registry must create/remove processors atomically and avoid two processors owning the same active game. Locks are never held while performing slow network broadcasts. Database work and state publication follow a consistent order, and failures trigger snapshot recovery rather than speculative client state.

Deployment assumes one application instance. Horizontal scaling requires explicit game ownership/routing or distributed coordination; deploying multiple uncoordinated instances is unsafe.

## Command identity and validation

Every game command carries:

- `clientActionId`: client-generated UUID, unique for a player/game command;
- `turnId`: opaque server-issued identifier for the current decision window;

Inside the game boundary, validation covers authenticated identity, membership/seat, command shape, mutable game state, the exact `turnId`, then poker legality and amount. A stale command cannot match the current turn and receives a private `COMMAND_ERROR`; the client rehydrates from an authoritative snapshot when transport continuity is lost.

## Duplicate handling

Accepted player actions persist their `clientActionId`. The turn changes after an accepted action, so a duplicate command becomes stale and cannot apply a second mutation. Automatic actions carry no client ID. The client also keeps one command pending until the authoritative action/error correlation arrives.

## Version and event ordering

`stateVersion` increments exactly once for each externally observable authoritative transition, including timer-driven transitions. Public/private events include the resulting version. Clients:

1. ignore duplicate `eventId` values and older same-hand versions;
2. reset hand-transient data when a newer hand arrives;
3. fetch a snapshot on initial entry and reconnect;
4. buffer frames during hydration, apply the snapshot, then consume newer events.

An event envelope includes a unique `eventId` so multiple public/private representations of one transition are not confused with separate state mutations.

## Timer safety

The server records an absolute UTC turn deadline and publishes it with `turnId`; clients display a countdown but do not decide expiry. A scheduled callback captures `gameId`, `handId`, `turnId`, and deadline. When it runs inside the game processor, it verifies that the game, hand, turn, and deadline are still current. Old callbacks become no-ops.

If a player action and timeout arrive together, serialization establishes one winner. The second command observes a changed `turnId`/version and cannot apply. Timer cancellation is an optimization, never the correctness mechanism. The approved timeout rule is `AUTO_CHECK` when `CHECK` is legal and otherwise `AUTO_FOLD`.

## Disconnect and presence

A user may have multiple browser connections. Presence is tracked per connection and aggregated per user. When the last connection closes, an active player is marked `DISCONNECTED` without losing their seat, Table Chips, or game state. The reconnect grace is configurable and defaults to 60 seconds. The game processor receives disconnect, reconnect, turn-expiry, and grace-expiry commands through the same serialization boundary.

Disconnect does not freeze a game or grant extra client authority. The current server timer continues; if it expires, the server applies `AUTO_CHECK` when checking is legal and otherwise `AUTO_FOLD`. Reconnection inside the grace window restores the player to the appropriate authoritative room-player state. Grace expiry excludes the player from later hands and finalizes departure at a safe hand boundary.

If a player intentionally leaves while the room is `WAITING`, removal is immediate and remaining Table Chips return atomically to Account Chips. If a Poker Hand is active, the leave command is serialized as a fold, already committed chips remain in the pot, state becomes `LEAVING`, and removal/cash-out occurs after hand completion. An owner's disconnect/leave never terminates an active hand; in `WAITING`, ownership transfers to an eligible player or the empty room closes.

## Reconnect and restoration

After authentication and session restoration, the server locates the reconnecting user's active room/game (the client may also supply its last observed version). It rechecks membership/spectator authorization and returns a role-specific snapshot:

- public table/board/pot/turn state for authorized members/spectators;
- the reconnecting seated player's own hole cards via private response only;
- no other private cards.

The snapshot includes `version`, current `turnId` when applicable, and the active timer deadline. The client discards conflicting transient state. Event replay is not implemented; snapshot-first recovery is the current behavior.

## Failure and cleanup risks

- Processor creation/removal races require atomic registry operations and lifecycle tests.
- A process crash between commit and publish still requires event replay for seamless hand restoration. The current startup policy safely aborts the unrecoverable session and conserves chips instead of resuming that hand.
- Unbounded idempotency caches and abandoned games require retention/cleanup policies.
- Clock changes require UTC instants and a monotonic scheduler for elapsed time where possible.
- Database deadlocks/retries must not rerun non-idempotent mutations outside the command identity guard.
