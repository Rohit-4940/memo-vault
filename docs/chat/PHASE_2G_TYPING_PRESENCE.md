# Phase 2g — Typing Indicator & Presence

Status: **Implemented** (backend + frontend + tests).
Related plan item: **C-16 typing indicator, C-17 presence / online status (P2)** in
`docs/chat/PHASE_1_ANALYSIS_AND_PLAN.md` (Phase 2 — Collaboration features).

---

## 1. Purpose

Two realtime awareness features:

1. **Typing** — "Alice is typing…" shown while another participant is composing, broadcast per
   conversation and rate-limited so it never spams the broker.
2. **Presence** — who is currently online (and *in which conversations*), shown as a green dot on
   members and as an online count in the chat header.

Design goals:

- **One-instance correctness first.** Both services are **in-memory** (`ConcurrentHashMap`),
  with an explicit class comment: scale-out requires swapping in a Redis-backed implementation.
  The STOMP destination (and REST contract) stay identical so the swap is transparent.
- **Rate-limited typing.** The REST endpoint is per-user rate limited (900 ms window); the WS
  broadcast is a *hint* — clients self-clear the indicator after ~2.5 s, so a dropped message
  never leaves a stale "typing…" on screen.
- **Presence is conversation-scoped.** `conversationPresence(viewerId, conversationId)` only
  returns users who can access that conversation — presence never leaks who is online globally.
- **Broken cycle cleanly.** `ChatConversationService` already needs participants; the presence
  service needs conversations to scope broadcasts. The dependency is injected **lazily**
  (`ObjectProvider<ChatPresenceService>`) to avoid a `@Service` circular dependency.
- **WebSocket principal resolved defensively.** The presence WS listener accepts a principal of
  type `Authentication` and unwraps the `Long userId`; anything else is ignored.

---

## 2. Classification

| Field | Value |
|---|---|
| Priority | **P2** |
| Complexity | Medium (WS listener, rate limiting, in-memory registry, broadcast scoping) |
| Dependencies | Phase 0 (WS authz via destination), 1a (participant queries) |
| Risks | Low for single-instance; multi-instance requires Redis implementation |
| Order | Sixth Phase 2 feature (2g) |

---

## 3. Data model

No DB changes. Both services are in-memory:

- `ChatTypingService` — per-(user, conversation) last-broadcast timestamp.
- `ChatPresenceService` — `userId → {online, lastSeen}` plus a session reference count so the
  same user on two tabs stays online until both disconnect.

---

## 4. Backend changes

| File | Change |
|---|---|
| `ChatTypingService` (new) | `notifyTyping(userId, conversationId)` — rate limits (900 ms) then `convertAndSend("/topic/conversation/{id}/typing", ChatTypingEventDto)`, **only if** the caller can access the conversation |
| `ChatPresenceService` (new) | `setOnline` / `setOffline` (session-counted), `isOnline`, `lastSeen`, `conversationPresence(viewerId, conversationId) → List<ChatPresenceDto>`, `broadcast` to the user's *accessible* conversations |
| `ChatPresenceWebSocketListener` (new) | on `SessionConnected`/`SessionDisconnect` unwraps the `Authentication` principal to `Long userId`, calls `setOnline`/`setOffline` |
| `ChatConversationService` | lazy `ObjectProvider<ChatPresenceService>` (cycle break); exposes `getAccessibleConversationIdsForBroadcast(userId)` used by presence |
| `ChatConversationController` | `POST /typing`, `GET /presence` |
| `ChatTypingEventDto` / `ChatPresenceDto` (new) | typing: `{conversationId, userId, displayName, at}`; presence: `{userId, displayName, online, lastSeen}` |

### REST

```
POST /api/v1/chat/conversations/{id}/typing      → 200 (no body)
GET  /api/v1/chat/conversations/{id}/presence    → ChatPresenceDto[]
```

### Realtime

```
/topic/conversation/{id}/typing     ChatTypingEventDto   (sender must be a participant)
/topic/conversation/{id}/presence   ChatPresenceEventDto {userId, displayName, online, lastSeen}
```

WS subscribe authorization for these sub-topics is enforced by `WebSocketAuthInterceptor` on the
first path segment (`/topic/conversation/{id}/...`), exactly like messages/reactions/meta.

---

## 5. Frontend changes

| File | Change |
|---|---|
| `chat.service.ts` | `sendTyping(conversationId)`, `getConversationPresence(conversationId)` |
| `chat-widget.component.ts/html` | `broadcastTyping()` debounced 900 ms from composer input; subscribes `/typing`, stores `{userId → name}`, auto-clears after 2.5 s (`typingLabel` getter); subscribes `/presence` and merges deltas; header online-count pill; members view with green/offline dots (`isOnline`) |
| `branch-chat.component.ts/html` | typing broadcast + indicator line above the branch composer |

---

## 6. Tests

- `ChatTypingServiceTest` (new): first call broadcasts, second call within the window is
  skipped (`verifyNoMoreInteractions` on the template), non-participant does not broadcast.
- `ChatPresenceService` covered through integration-style service tests plus widget build.
- 113/113 backend tests green.

---

## 7. Deployment notes

- **Single instance:** works out of the box (in-memory).
- **Multiple instances:** replace the in-memory maps with Redis (same destinations):
  `SET`/`EXPIRE` for presence, Redis pub/sub for typing. The REST/WS contract and the UI are
  unchanged.

---

## 8. Manual verification steps

1. Two browsers in one conversation: browser A types — browser B shows "A typing…" within ~1 s,
   clearing ~2.5 s after A stops.
2. Open **Members** — browsers with the conversation open show green dots; closing the tab flips
   the dot off within the disconnect sweep.
3. The header pill shows the number of other currently-online participants.