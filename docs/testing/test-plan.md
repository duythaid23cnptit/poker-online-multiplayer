# Test Plan

## Strategy

Testing follows the architecture: pure domain rules are fast and exhaustive; adapters and module contracts are integration-tested; a small number of end-to-end scenarios prove the networked system. Tests must be deterministic, independent, and safe to run against the dedicated local `poker_online_test` database using environment-provided credentials.

No Testcontainers or Docker is used. Main database data must never be used by automated tests. A test must fail safely if required test credentials/database identification is absent rather than silently falling back to `poker_online`.

## Unit tests

- Value objects, validation, state transitions, permission policies, DTO mapping, and error mapping.
- Application use cases with mocked/fake ports: registration, friends, room lifecycle/readiness, history, ranking, analytics, and admin orchestration.
- Account Chip/Table Chip buy-in and legal cash-out policies, including insufficient funds, non-negative balances, atomic rollback, and server-only mutation.
- Room and room-player state transitions using only the approved state vocabularies.
- Idempotent event handlers and statistics/ranking calculations.
- Frontend Zod schemas, formatting/selectors, reducers/stores, retry/reconciliation logic, and timer display calculations.

JUnit 5 and Mockito are used for Java; Vitest for TypeScript. Avoid mocking domain objects when real deterministic values are clearer.

## Poker Engine tests

The pure Java engine receives the largest unit-test matrix. Inject deterministic decks/randomness and clocks. Cover:

- card/deck uniqueness, parsing, dealing, and deterministic shuffle seam;
- all hand categories, ace-low straight, board-play, kickers, comparisons, and multiway ties;
- dealer/blind movement including heads-up behavior and eliminated/sitting-out seats;
- pre-flop/flop/turn/river/showdown transitions and early win when all opponents fold;
- fold/check/call/bet/raise/all-in legality, min-raise rules, incomplete all-in raises, action reopening, and invalid amounts;
- round completion with checks, calls, folds, and multiple all-ins;
- main pot, multiple side pots, eligibility, split pots, odd-chip allocation, and chip conservation;
- turn ordering and legal-action calculation for 6 through 9 seats and reduced active-player counts;
- invariants/property-style cases: no duplicate cards, no negative stacks/pots, total chips conserved, one acting player, deterministic result for the same inputs;
- malformed/stale commands do not mutate state.

Poker rule variants (especially incomplete raises and odd chips) must be approved and encoded as named tests before engine implementation.

## Backend integration tests

Using Spring Boot Test and the local MySQL test database:

- Flyway migrates an empty test schema and Hibernate validation succeeds;
- repository constraints, mappings, transactions, pagination, and rollback behavior;
- REST serialization/validation/status/error envelope and module use-case wiring;
- duplicate/idempotency constraints and event/outbox behavior if adopted;
- atomic Account Chip to Table Chip buy-in and reverse cash-out against concurrent join/leave requests;
- Game Session persistence containing multiple distinct Poker Hands and history queries that preserve that hierarchy;
- profile isolation ensures production credentials/database are never selected.

Tests should reset only the explicitly verified test schema using controlled fixtures/migrations. They must not use `ddl-auto=create`, `create-drop`, or `update`.

## Security tests

- Registration/login validation, password hashing, rate-limit behavior, and generic authentication failures.
- JWT valid/expired/tampered/wrong-signature cases; refresh rotation, logout/revocation, reuse, and account lock.
- REST role/object authorization, including one user accessing another's private data.
- WebSocket `CONNECT`, `SUBSCRIBE`, and `SEND` authorization independently.
- Attempts to subscribe to rooms/games without membership, spoof player IDs, or target another user queue.
- Public payload inspection confirms absence of hole cards, passwords/hashes, tokens, and private state.
- Origin/CSRF policy, payload limits, injection-safe chat rendering, and sanitized errors/logs.

## WebSocket tests

- Multiple concurrent authenticated clients connect, heartbeat, subscribe, receive broadcast, and receive private unicast only when addressed.
- Lobby room create/update/remove propagation and room membership/ready/chat ordering.
- Game command correlation and authoritative event versions.
- Private hole cards reach exactly the seated owner; opponents and spectators cannot receive them.
- Disconnect detection with multiple connections, the 60-second grace window, `DISCONNECTED` state, reconnect/resubscribe, and snapshot restoration containing only the player's own hole cards.
- Turn expiry while disconnected produces `AUTO_CHECK` when legal and otherwise `AUTO_FOLD`.
- Mid-hand leave produces fold then `LEAVING`; removal waits for hand completion. Waiting-room leave is immediate.
- Waiting-room owner leave transfers ownership to an eligible player or closes an empty room; owner departure never terminates an active hand.
- Duplicate/out-of-order events are ignored or trigger snapshot recovery; event gaps do not leave silent divergence.
- Token expiry/reconnect and unauthorized destination failures are well-defined.

## Concurrency tests

Use barriers/latches and repeated stress runs rather than timing sleeps where possible:

- two actions for the same turn: exactly one valid transition;
- duplicate `clientActionId`: one deduction/action, stable replay response;
- same ID with a changed payload: conflict, no second mutation;
- action versus timer at the deadline: one outcome, no double advance;
- stale timer after turn/game/hand changes: no-op;
- concurrent commands in different games: progress independently (no global lock);
- simultaneous joins for the final seat: capacity and unique-seat constraints hold;
- simultaneous buy-ins/cash-outs cannot overspend Account Chips, duplicate transfers, or lose chips;
- disconnect/reconnect racing with a turn action;
- transaction failure/retry does not duplicate chips, pots, actions, statistics, or events;
- registry create/remove races never produce two owners for one active game.

Assert invariants after every run, including state version monotonicity and chip/pot conservation.

## Frontend tests

With Vitest and React Testing Library:

- authentication forms, protected/admin routes, profile/friends/room forms, Zod errors, and accessible interactions;
- TanStack Query loading/error/cache invalidation for REST resources;
- Zustand realtime store applies sequential versions, ignores duplicates, detects gaps, and replaces state from snapshots;
- STOMP lifecycle, subscription cleanup, reconnect/backoff, and private/public event routing with mocked transport;
- room/lobby views for owner/player/spectator roles;
- poker action controls render only server-provided legal choices and remain pending until authoritative response;
- countdown uses server deadline and resynchronizes without declaring a timeout locally;
- hidden cards never render for spectators/opponents, including after reconnect.

## Critical end-to-end scenarios

1. Register two or more users, login, establish STOMP connections, and observe correct presence.
2. Send/accept/reject friend requests and receive private notifications.
3. Create public/private rooms, reject a wrong password, buy in from Account Chips, join up to capacity, ready, and observe broadcasts.
4. Connect 6–9 players plus spectators; verify role-filtered snapshots and private card isolation.
5. Complete a deterministic hand through every street with valid betting and verify history/statistics/ranking.
6. Complete a multi-all-in hand with multiple side pots and a split result; verify exact chip conservation.
7. Win early after folds; verify no unnecessary card reveal.
8. Send out-of-turn, duplicate, stale-version, and illegal-amount actions; verify no authoritative mutation.
9. Disconnect the acting player, retain seat/state for the 60-second window, verify authoritative auto-check/fold on turn expiry, reconnect, and restore state plus only that player's hole cards.
10. Leave during a hand, verify fold and retained pot contribution, remove after hand completion, and return remaining Table Chips to Account Chips.
11. Leave a waiting room as owner, verify deterministic transfer or room closure; repeat during active play and verify the hand continues.
12. Lock a user as admin and verify new authentication/access behavior plus audit record.

## Quality gates

Before claiming a phase/change complete:

- run the relevant backend unit and integration tests;
- run frontend unit/component tests;
- run Maven and frontend production builds when those projects exist;
- run architecture/boundary checks and Flyway validation when introduced;
- record commands, results, skipped checks, and environmental limitations in the handoff.

Coverage percentages may support review but do not replace scenario and invariant coverage. Phase 0 has no executable code, so verification consists of repository/file checks and documentation consistency review.
