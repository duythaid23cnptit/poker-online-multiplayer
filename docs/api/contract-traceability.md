# Frontend/backend contract traceability

This matrix audits the current source contracts for the v1.0.0-rc2 alignment sprint. The status column classifies each human-facing contract as `PLAYER_ONLY`, `ADMIN_ONLY`, or `SHARED_AUTH`; deployment-only contracts use `INTERNAL_NO_UI_REQUIRED`.

REST paths are relative to `/api/v1`. Every application endpoint uses JSON and the bearer access token unless the authentication column says otherwise. `ApiError { code, message, fieldErrors }` is the shared error body.

## REST capabilities

| Capability | Backend endpoint | Authentication | Request and response | Frontend API client | Query or mutation | Page or component | Test coverage | Status |
|---|---|---|---|---|---|---|---|---|
| Register player account | `POST /auth/register` | Public; always creates `PLAYER` | `RegisterRequest` -> `CurrentUserResponse`, 201 | `authApi.register` | `useRegister` | `RegisterPage` | `AuthRoutes`, `RegisterPage` | PLAYER_ONLY |
| Login | `POST /auth/login` | Public | `LoginRequest` -> `AuthResponse`, 200 | `authApi.login` | `useLogin` | `LoginPage` | `AuthRoutes`, `LoginPage` | SHARED_AUTH |
| Refresh access token | `POST /auth/refresh` | Public, refresh token in body | `RefreshTokenRequest` -> `AccessTokenResponse`, 200 | `authApi.refresh` | `sessionCoordinator` | `SessionBootstrap` | `sessionCoordinator`, `httpClient` | SHARED_AUTH |
| Logout | `POST /auth/logout` | Any authenticated role | `LogoutRequest` -> 204 | `authApi.logout` | `useLogout` | `AppShell`, `AdminShell` | `useLogout` | SHARED_AUTH |
| Read current account | `GET /me` | Any authenticated role | none -> `CurrentUserResponse`, 200 | `profileApi.current` | `useCurrentProfile` | role routing and both shells | route guard, shell, profile, and session tests | SHARED_AUTH |
| Update current profile | `PATCH /me` | `ROLE_PLAYER` | `UpdateProfileRequest` -> `CurrentUserResponse`, 200 | `profileApi.update` | `useUpdateProfile` | `ProfilePage` | `ProfilePage` | PLAYER_ONLY |
| Create room | `POST /rooms` | `ROLE_PLAYER` | `CreateRoomRequest` -> `RoomDetailResponse`, 201 | `roomApi.create` | `useCreateRoom` | `CreateRoomPanel` | rooms page/schema/query tests | PLAYER_ONLY |
| List waiting rooms | `GET /rooms` | `ROLE_PLAYER` | none -> `RoomSummaryResponse[]`, 200 | `roomApi.list` | `useRooms` | `LobbyPage`, `RoomsPage` | rooms page/query tests | PLAYER_ONLY |
| Read room detail | `GET /rooms/{roomId}` | `ROLE_PLAYER`; non-waiting rooms require active membership | path ID -> `RoomDetailResponse`, 200 | `roomApi.detail` | `useRoomDetail`, membership lookup | waiting room and game pages | room/game page and query tests | PLAYER_ONLY |
| Join room or convert spectator | `POST /rooms/{roomId}/join` | `ROLE_PLAYER` | `JoinRoomRequest` -> `RoomDetailResponse`, 200 | `roomApi.join` | `useJoinRoom` | `JoinRoomPanel`, `JoinedRoomPanel` | rooms and seat tests | PLAYER_ONLY |
| Leave waiting room | `POST /rooms/{roomId}/leave` | `ROLE_PLAYER` active member | path ID -> `RoomDetailResponse`, 200 | `roomApi.leave` | `useLeaveRoom` | `JoinedRoomPanel` | rooms page/query tests | PLAYER_ONLY |
| Explicit host game start | `POST /games/rooms/{roomId}/start` | `ROLE_PLAYER` room host | path ID -> `ActiveGameResponse`, 200 | `gameApi.start` | local mutation | `JoinedRoomPanel` | waiting room and backend start tests | PLAYER_ONLY |
| Leave active game | `POST /games/{gameId}/leave` | `ROLE_PLAYER` `PLAYER` participant | UUID -> `GameDepartureResponse`, 200 | `gameApi.leave` | `useGameDeparture` | `LeaveGameControl` | departure hook/control and backend tests | PLAYER_ONLY |
| Game snapshot | `GET /games/{gameId}/snapshot` | `ROLE_PLAYER` authorized observer or historical participant | UUID -> `GameSnapshotResponse`, 200 | `gameApi.snapshot` | initial/reconnect realtime hydration | `GameTablePage` | game query/page/reducer/reconnect tests | PLAYER_ONLY |
| Active game by room | `GET /games/active/room/{roomId}` | `ROLE_PLAYER` authorized observer | room ID -> `ActiveGameResponse`, 200 | `gameApi.activeByRoom` | waiting-room recovery | `JoinedRoomPanel` | waiting room tests | PLAYER_ONLY |
| Current player's active game | `GET /games/active/me` | `ROLE_PLAYER` | none -> `ActiveGameResponse`, 200 | `gameApi.activeMine` | `useActiveGame` | `ActiveGameRecovery` | recovery/query tests | PLAYER_ONLY |
| Send friend request | `POST /friend-requests` | `ROLE_PLAYER` | `SendFriendRequest` -> `FriendshipView`, 201 or 200 | `friendApi.send` | `useSendFriendRequest` | `FriendsPage` | frontend friend hooks/backend lifecycle | PLAYER_ONLY |
| List friend requests | `GET /friend-requests?direction=...` | `ROLE_PLAYER` | `incoming` or `outgoing` -> `FriendshipView[]`, 200 | `friendApi.requests` | `useFriendRequests` | `FriendsPage` | frontend friend hooks/backend lifecycle | PLAYER_ONLY |
| Accept friend request | `POST /friend-requests/{requestId}/accept` | `ROLE_PLAYER` recipient | path ID -> `FriendshipView`, 200 | `friendApi.accept` | `useAcceptFriendRequest` | `FriendsPage` | frontend friend hooks/backend lifecycle | PLAYER_ONLY |
| Reject friend request | `POST /friend-requests/{requestId}/reject` | `ROLE_PLAYER` recipient | path ID -> `FriendshipView`, 200 | `friendApi.reject` | `useRejectFriendRequest` | `FriendsPage` | frontend friend hooks/backend lifecycle | PLAYER_ONLY |
| List friends | `GET /friends` | `ROLE_PLAYER` | none -> `FriendshipView[]`, 200 | `friendApi.list` | `useFriends` | lobby and friends pages | frontend friend hooks/backend lifecycle | PLAYER_ONLY |
| Remove friend | `DELETE /friends/{friendId}` | `ROLE_PLAYER` participant | other user ID -> 204 | `friendApi.remove` | `useRemoveFriend` | `FriendsPage` | frontend friend hooks/backend lifecycle | PLAYER_ONLY |
| Read chat history | `GET /rooms/{roomId}/chat/messages?limit=...` | `ROLE_PLAYER` active room member | limit 1-100 -> `ChatMessageView[]`, 200 | `chatApi.history` | `useRoomChat` | `GameSidePanel` | chat query/hook and backend tests | PLAYER_ONLY |
| Current ranking | `GET /rankings/me` | `ROLE_PLAYER` | none -> `CurrentRanking`, 200 | `rankingApi.current` | `useCurrentRanking` | `RankingsPage` | frontend contract/backend API tests | PLAYER_ONLY |
| Ranking leaderboard | `GET /rankings/leaderboard?page=&size=` | `ROLE_PLAYER` | page >= 0, size 1-100 -> page DTO with authoritative nullable `username` and `displayName` identity fields per item | `rankingApi.leaderboard` | `useLeaderboard` | lobby and ranking pages | frontend contract/backend API tests | PLAYER_ONLY |
| Ranking history | `GET /rankings/me/history?page=&size=` | `ROLE_PLAYER` | page >= 0, size 1-100 -> `RankingHistory[]` | `rankingApi.history` | `useRankingHistory` | `RankingsPage` | frontend contract/backend API tests | PLAYER_ONLY |
| Player statistics | `GET /players/me/statistics` | `ROLE_PLAYER` | none -> `PlayerStatisticsResponse`, 200 | `statisticsApi.current` | `useCurrentStatistics` | `StatisticsPage` | frontend states/contracts and backend integration | PLAYER_ONLY |
| Daily player analytics | `GET /analytics/me/daily?from=&to=` | `ROLE_PLAYER` | ISO dates, max 366-day span -> `Daily[]`, 200 | `statisticsApi.daily` | `useDailyAnalytics` | `StatisticsPage` | frontend states/contracts and backend integration | PLAYER_ONLY |
| Weekly player analytics | `GET /analytics/me/weekly?from=&to=` | `ROLE_PLAYER` | ISO dates normalized to Monday, max 104 weeks -> `Weekly[]`, 200 | `statisticsApi.weekly` | `useWeeklyAnalytics` | `StatisticsPage` | frontend states/contracts and backend integration | PLAYER_ONLY |
| Current analytics summary | `GET /analytics/me/summary` | `ROLE_PLAYER` | none -> `Summary`, 200 | `statisticsApi.summary` | `useAnalyticsSummary` | `StatisticsPage` | frontend states/contracts and backend integration | PLAYER_ONLY |
| Admin overview | `GET /admin/overview` | `ROLE_ADMIN` | none -> `Overview`, 200 | `adminApi.overview` | `useAdminOverview` | `AdminPage` | frontend admin/API and backend integration | ADMIN_ONLY |
| Admin user list | `GET /admin/users` | `ROLE_ADMIN` | page, size, search, status, role -> `Page<UserItem>` | `adminApi.users` | `useAdminUsers` | `AdminUsersPanel` | frontend admin/API and backend integration | ADMIN_ONLY |
| Admin user detail | `GET /admin/users/{id}` | `ROLE_ADMIN` | user ID -> `UserDetail`, 200 | `adminApi.user` | `useAdminUser` | `AdminUsersPanel` | frontend admin/API and backend integration | ADMIN_ONLY |
| Suspend user | `POST /admin/users/{id}/suspend` | `ROLE_ADMIN` | optional reason -> `MutationResponse`, 200 | `adminApi.suspendUser` | `useAdminMutation` | `AdminUsersPanel` | frontend moderation/backend integration | ADMIN_ONLY |
| Reactivate user | `POST /admin/users/{id}/reactivate` | `ROLE_ADMIN` | optional reason -> `MutationResponse`, 200 | `adminApi.reactivateUser` | `useAdminMutation` | `AdminUsersPanel` | frontend moderation/backend integration | ADMIN_ONLY |
| Admin room list | `GET /admin/rooms` | `ROLE_ADMIN` | page, size, search, status, roomType -> `Page<RoomItem>` | `adminApi.rooms` | `useAdminRooms` | `AdminRoomsPanel` | frontend admin/API and backend integration | ADMIN_ONLY |
| Admin room detail | `GET /admin/rooms/{id}` | `ROLE_ADMIN` | room ID -> `RoomDetail`, 200 | `adminApi.room` | `useAdminRoom` | `AdminRoomsPanel` | frontend admin/API and backend integration | ADMIN_ONLY |
| Remove room member | `POST /admin/rooms/{roomId}/players/{userId}/remove` | `ROLE_ADMIN` | optional reason -> `MutationResponse`, 200 | `adminApi.removePlayer` | `useAdminMutation` | `AdminRoomsPanel` | frontend moderation/backend integration | ADMIN_ONLY |
| Close room | `POST /admin/rooms/{id}/close` | `ROLE_ADMIN` | optional reason -> `MutationResponse`, 200 | `adminApi.closeRoom` | `useAdminMutation` | `AdminRoomsPanel` | frontend moderation/backend integration | ADMIN_ONLY |
| Admin game list | `GET /admin/games` | `ROLE_ADMIN` | page, size, status, roomId, userId, from, to -> `Page<GameItem>` | `adminApi.games` | `useAdminGames` | `AdminGamesPanel` | frontend admin/API and backend integration | ADMIN_ONLY |
| Admin game detail | `GET /admin/games/{id}` | `ROLE_ADMIN` | session ID -> `GameDetail`, 200 | `adminApi.game` | `useAdminGame` | `AdminGamesPanel` | frontend admin/API and backend integration | ADMIN_ONLY |
| Admin game hands | `GET /admin/games/{id}/hands` | `ROLE_ADMIN` | session ID, page, size -> `Page<HandItem>` | `adminApi.hands` | `useAdminHands` | `AdminGamesPanel` | frontend admin/API and backend integration | ADMIN_ONLY |
| Terminate game | `POST /admin/games/{id}/terminate` | `ROLE_ADMIN` | optional reason -> `MutationResponse`, 200 | `adminApi.terminateGame` | `useAdminMutation` | `AdminGamesPanel` | frontend moderation/backend integration | ADMIN_ONLY |
| Admin audit log | `GET /admin/audit-log` | `ROLE_ADMIN` | pagination, actor/action/target/date filters -> `Page<AuditItem>` | `adminApi.audit` | `useAdminAudit` | `AdminAuditPanel` | frontend admin/API and backend integration | ADMIN_ONLY |
| Health probe | `GET /actuator/health` | Public | Spring Boot health response, 200/503 | none | none | deployment tooling only | Spring context/package gate | INTERNAL_NO_UI_REQUIRED |

The initial game snapshot mismatch is type coverage: the backend award payload also supplies `baseShare` and `oddChipUserIds`, but the TypeScript `GameAward` did not declare them. The UI did not fabricate them and did not use them, but the type was incomplete.

## Realtime capabilities

### Inbound commands

| Capability | Destination | Payload | Authorization | Frontend sender | Consumer | Tests | Status |
|---|---|---|---|---|---|---|---|
| Set ready state | `/app/room/{roomId}/ready` | `{ clientCommandId: UUID, ready: boolean }` | `ROLE_PLAYER`, active account; application requires active seated member in a waiting room | `JoinedRoomPanel` | `RoomMessagingController` | room STOMP and waiting-room frontend tests | PLAYER_ONLY |
| Send room chat | `/app/room/{roomId}/chat` | `{ clientMessageId: UUID, content: string }` | `ROLE_PLAYER`, active account; application requires active member and open room | `useRoomChat` | `ChatMessagingController` | chat STOMP/hook tests | PLAYER_ONLY |
| Submit game action | `/app/game/{gameId}/action` | `{ actionType, amount?, turnId, clientActionId }` | `ROLE_PLAYER`, active account; game service validates participant, turn and action | `useGameActions` | `GameMessagingController` | game STOMP/action tests | PLAYER_ONLY |

### Outbound subscriptions

| Destination | Event family and types | Visibility | Frontend consumer | Reconciliation | Tests | Status |
|---|---|---|---|---|---|---|
| `/topic/lobby` | `RealtimeEvent`: `ROOM_CREATED`, `ROOM_CLOSED`, `PLAYER_COUNT_CHANGED` | Active `ROLE_PLAYER` account | `useLobbyRealtime` | update waiting-room list | lobby realtime tests | PLAYER_ONLY |
| `/topic/room/{roomId}` | `RealtimeEvent`: player/room lifecycle and `GAME_STARTED`; `ChatRealtimeEvent`: `CHAT_MESSAGE` | Active `ROLE_PLAYER` room members, including spectators | `JoinedRoomPanel`, `useRoomChat` | cache snapshot or invalidate; navigate on start; merge chat by authoritative IDs | room/chat tests | PLAYER_ONLY |
| `/topic/game/{gameId}` | Public `GameRealtimeEvent`: hand, action, timer, board, result and public state types | Authorized active `ROLE_PLAYER` observers; never hole cards or private turn options | `useGameRealtime` | snapshot first, buffer frames, version/event de-duplication | game reducer/page/STOMP tests | PLAYER_ONLY |
| `/user/queue/private` | Private `GameRealtimeEvent`: own hole cards, own turn, command errors and private recovery events | Broker-resolved authenticated `ROLE_PLAYER` user only | `RealtimeBootstrap`, `useGameRealtime` | discovery plus same snapshot/event reducer | STOMP privacy and reducer tests | PLAYER_ONLY |
| `/user/queue/notifications` | `SocialNotificationEvent` and `FriendPresenceNotificationEvent` | Broker-resolved authenticated `ROLE_PLAYER` user and accepted-friend policy | `RealtimeBootstrap` | notification store plus friend cache update/invalidation | notification/STOMP tests | PLAYER_ONLY |

Connection authentication uses the bearer access token only in the STOMP `CONNECT` header. The access token remains in memory, the opaque refresh token uses tab-scoped `sessionStorage`, and neither token appears in a URL. The server re-loads the active account for every `SUBSCRIBE` and `SEND`, requires `ROLE_PLAYER`, authorizes each topic, and resolves all sender/player identities from the authenticated principal. Admin sessions do not start the player realtime bootstrap.

## Initial gaps

- Four user-facing read capabilities had no frontend: current statistics, daily analytics, weekly analytics, and current summary.
- All fourteen human administration capabilities had no frontend API client, route, role guard, navigation, or screen.
- The game result award TypeScript type omitted two authoritative response fields.
- Ranking REST consumers had backend integration coverage but no focused frontend API contract tests.
- The root API and WebSocket documents and `frontend/README.md` still mixed old proposals with current routes and event names.

## Final status

All 44 application REST endpoints and all realtime destinations have a matched frontend flow or an explicit UI classification. The public Actuator health probe is `INTERNAL_NO_UI_REQUIRED` because it exists for deployment and process monitoring rather than an authenticated human workflow. There are no remaining frontend implementation or contract gaps in this matrix.
