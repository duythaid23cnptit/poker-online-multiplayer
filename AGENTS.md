# Poker Online Development Rules

These rules apply to the entire repository and are durable constraints for future work.

## Scope and stack

- Build a real-time multiplayer Texas Hold'em system as a client-server modular monolith.
- Backend: Java 17, Spring Boot, Maven, Spring MVC, Spring Security, JWT, Spring Data JPA/Hibernate, Flyway, MySQL 8.x, Jakarta Validation, WebSocket, and STOMP.
- Frontend: React, TypeScript, Vite, React Router, TanStack Query, Zustand, React Hook Form, Zod, Tailwind CSS, and `@stomp/stompjs`.
- Tests: JUnit 5, Mockito, Spring Boot Test, Vitest, and React Testing Library.
- Local databases are `poker_online` and `poker_online_test`. Supply credentials only through environment variables. Never commit, log, invent, or hardcode passwords or tokens.
- Do not introduce Docker, Docker Compose, PostgreSQL, Testcontainers, Redis, Kafka, RabbitMQ, or microservices unless a later requirement explicitly changes this rule.

## Architecture

- Organize backend code by business feature: `common`, `auth`, `player`, `social`, `room`, `game`, `ranking`, `analytics`, and `admin`.
- Within a feature, use only useful `api`, `application`, `domain`, and `infrastructure` packages. Do not create empty ceremonial layers.
- Dependencies point inward: adapters depend on application/domain contracts; domain code does not depend on Spring, persistence, HTTP, or WebSocket.
- Controllers translate and validate transport input, call application use cases, and translate output. They contain no business decisions.
- Repositories persist/query state; they contain no game or policy decisions.
- Never expose JPA entities through REST or STOMP. Use explicit request, response, command, event, and snapshot DTOs.
- Avoid circular module dependencies. Cross-module access occurs through explicit application interfaces or published internal events, not another module's repositories.
- Keep `common` small and technical. It must not become a dumping ground or own feature-specific domain concepts.
- Follow the detailed dependency policy in `docs/architecture/module-boundaries.md`.

## Server authority and game engine

- The server is the sole source of truth for cards, chips, pots, winners, legal actions, turn order, timers, and game state. Clients send intentions only.
- Treat every client value as untrusted. Validate identity, membership, game/turn/version, action legality, and amounts on the server.
- Isolate the Poker Engine as pure Java where practical. It must not depend on Spring MVC, WebSocket, Spring Data, JPA sessions, or infrastructure concerns.
- Serialize mutations per active game (or use an equivalent per-game lock). Never use a single global game lock.
- Carry `clientActionId`, `turnId`, and `stateVersion` where specified. Make retries idempotent and stale commands/timers harmless.
- Account Chips are the player's persistent balance; Table Chips are the chips available at a table. Joining performs a server-authoritative buy-in from Account Chips to Table Chips, and a legal departure returns remaining Table Chips to Account Chips.
- A Game Session is one continuous period of play in a room and contains multiple Poker Hands. Preserve this distinction in APIs, persistence, history, and statistics.
- Room states are exactly `WAITING`, `PLAYING`, `FINISHED`, and `CLOSED`. Room player states are exactly `NOT_READY`, `READY`, `PLAYING`, `SPECTATING`, `DISCONNECTED`, and `LEAVING` unless a later approved specification changes them.
- The reconnect grace baseline is 60 seconds. A disconnected active player retains their seat while the game continues; an expired turn causes `AUTO_CHECK` when checking is legal and otherwise `AUTO_FOLD`.
- Leaving during an active hand folds the player, leaves committed chips in the pot, marks them `LEAVING`, and removes them after the hand. In `WAITING`, departure is immediate.
- If an owner leaves a `WAITING` room, transfer ownership to another eligible player or close the empty room. Owner departure/disconnection never terminates an active hand.
- Ranking results, like game results, are server-authoritative.

## Database and migrations

- Flyway is the sole schema authority. Add versioned, reviewable migrations; never edit an applied migration.
- Main profiles must use `spring.jpa.hibernate.ddl-auto=validate`, never `create`, `create-drop`, or `update`.
- Keep database compatibility with MySQL 8.x. Tests that require the database use the local `poker_online_test` database and environment-supplied credentials.
- Use transactions at application use-case boundaries. Define ownership before one module reads or writes another module's data.
- Do not add migrations during Phase 0.
- Preserve approved logical table names: `users`, `player_profiles`, `refresh_tokens`, `friendships`, `chat_messages`, `rooms`, `room_players`, `game_sessions`, `poker_hands`, `hand_players`, `player_actions`, `pots`, `player_statistics`, `player_rankings`, `ranking_history`, `daily_statistics`, and `weekly_statistics`.

## WebSocket and security

- Authenticate the STOMP connection and authorize every subscription and command; connection authentication alone is insufficient.
- Public topics must never contain hole cards, secrets, password material, JWTs, or another player's private state.
- Send hole cards only to the authenticated player's user queue. Spectators never receive hole cards.
- Room/game membership and role checks are server-side. Do not trust destination path IDs without checking authorization.
- Do not put access tokens in URLs. Prefer an authenticated STOMP `CONNECT` header over TLS, with a documented refresh/reconnect flow.
- Use typed, versioned event envelopes and sanitized errors. Do not leak stack traces or sensitive internal state.

## Testing and delivery

- Unit-test domain rules and application policies. The Poker Engine requires extensive deterministic tests, including side pots, ties, all-ins, early wins, and invalid actions.
- Add integration tests for persistence, Flyway, REST/security, and WebSocket behavior; add concurrency tests for duplicates, races, stale timers, and reconnect.
- Frontend tests cover schemas, state stores, hooks/components, authorization-aware routing, and realtime reconciliation.
- Never weaken or delete tests merely to make a build pass.
- Run the relevant tests and build before claiming success. Report exactly what ran and any checks that could not run.
- Keep changes inside the requested phase. Do not start later phases, install dependencies, or add infrastructure speculatively.
