# Poker Online Frontend

This React application is the Phase 11A foundation for Poker Online Multiplayer. It implements the authentication and application-shell foundation only: register, login, session restoration, logout, protected routing, a current-profile foundation, reusable UI primitives, and a low-level STOMP transport client.

It deliberately does **not** implement Lobby, Room, Poker Table, gameplay actions, chat, friends, presence UI, ranking, history, statistics, analytics, or admin screens.

## Requirements

- Node.js 22
- npm 10

Use npm consistently with the committed `package-lock.json`.

## Commands

Run these commands from this directory:

```powershell
npm install
npm run dev
npm test
npm run build
npm run lint
```

`npm test` is a non-watch Vitest run. `npm run build` type-checks the application before creating the Vite production build.

## Environment and local backend integration

Copy the safe example before changing local values:

```powershell
Copy-Item .env.example .env
```

| Variable | Default example | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `/api/v1` | REST API base path |
| `VITE_WS_URL` | `/ws` | Native WebSocket/STOMP endpoint |

These Vite values are public client configuration, not secrets. Never put database passwords, JWT signing keys, access tokens, refresh tokens, or private keys in `.env` files.

During local development, Vite proxies `/api` and `/ws` to the local Spring Boot server. Keeping the browser on the Vite origin avoids the backend's intentionally narrow browser-origin policy. For a deployed frontend, use a same-origin or reverse-proxy arrangement that the backend explicitly permits; do not work around STOMP authentication with a token query parameter.

## Stack

- React and TypeScript, built with Vite
- React Router for routing and route guards
- TanStack Query for server state
- Zustand for small client-owned session metadata
- React Hook Form and Zod for form state and validation
- Tailwind CSS for the UI foundation
- `@stomp/stompjs` for the low-level authenticated STOMP client
- Vitest and React Testing Library for frontend tests

No Axios, UI-framework, Redux, or animation-library replacement is used for this phase. The shared HTTP client is based on native `fetch`.

## Architecture and state ownership

The source is organized by application, shared technical foundation, and feature:

```text
src/
  app/          providers, router, layouts
  shared/       typed API client, config, realtime transport, UI primitives
  features/
    auth/       API, session, pages, forms, schemas
    profile/    API, hooks, page, schema, types
```

TanStack Query owns server-derived data such as the current profile. Zustand does not duplicate API responses; it holds only client session metadata and the in-memory access credential needed to authorize requests.

## Authentication and session model

The frozen backend returns an access token and opaque refresh token in the login JSON response. It does not use an HTTP-only refresh-token cookie.

- The access token stays in memory only.
- The refresh token is isolated in `sessionStorage`, never `localStorage`, query data, route state, or a persisted Zustand store.
- On startup, the app enters a checking state, performs one refresh when a refresh token exists, then loads `GET /api/v1/me` before rendering protected content.
- Protected request `401` responses share one refresh operation and retry the original request at most once. Failed refresh clears local session state and sensitive query data.
- Logout calls the backend when possible, disconnects any active STOMP client, clears the token vault and query cache, then routes to Login even if the remote logout request fails.

The refresh token must be JavaScript-readable because that is the frozen backend contract. `sessionStorage` is the least-persistent compatible option; it is not equivalent to an HTTP-only cookie and must never be logged or rendered.

## REST and STOMP contracts

REST authentication uses the existing `Authorization: Bearer <access-token>` contract. The frontend does not invent `/api/login`, `/api/auth/me`, or `/api/profile`; current profile access is `GET`/`PATCH /api/v1/me`.

The STOMP endpoint is `/ws` without SockJS. When a future feature activates the transport, it supplies the access token in the native STOMP `CONNECT` header named `Authorization`. The Phase 11A transport foundation does not subscribe to lobby, room, game, chat, friendship, or presence destinations, and it disables STOMP frame debug logging to avoid token exposure.

## Scope

The frontend renders server-authoritative data only. It does not reproduce poker rules, calculate chips, infer presence, or manufacture dashboard/business data. Future feature screens will be added in their own feature modules after their corresponding phases are approved.
