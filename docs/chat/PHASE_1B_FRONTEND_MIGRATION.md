# Phase 1b — Angular UI Migration to the Conversation API (Implemented)

Status: **Complete — `ng build` succeeds (EXIT=0); backend 47/47 tests still pass.**

---

## 1. Purpose

Phase 1a added the conversation/message backend but the Angular UI still spoke only to the
legacy endpoints. Phase 1b moves the UI onto `/chat/conversations`, `/topic/conversation/{id}`
and the idempotent send/read endpoints, so the front end actually uses the new model.
Existing features were preserved; nothing was removed.

---

## 2. UI changes

### Floating `chat-widget` — now a conversation **inbox**
| View | Content |
|---|---|
| `list` | my conversations (DIRECT / GROUP / BRANCH / DEPARTMENT / MEMO) with type icon, title, last-message preview and per-conversation unread badge |
| `people` | direct-message picker (active users from `/chat/users`), excluding self |
| `branches` | branch picker → get-or-create a BRANCH conversation |
| `chat` | message history + composer for the selected conversation |

- Footer actions: **New Chat**, **Branches**, **Report** (all preserved).
- Total unread badge = sum of per-conversation `unreadCount` (was a global `isRead` scan).
- "Report Problem" now posts a problem message into the branch conversation.

### `branch-chat` page — branch-centric layout preserved, transport migrated
- Sidebar and admin activity feed are unchanged in look.
- Opening a branch now calls get-or-create `BRANCH` conversation, loads cursor history,
  subscribes to `/topic/conversation/{id}` and sends via the idempotent message endpoint.
- Problem-report form posts through the conversation API.
- Admin feed (`/chat/admin/all-branch-messages`) is untouched.

### `ChatService` (Angular) — new conversation methods
`getConversations`, `getConversationById`, `createConversation`, `getOrCreateMemoConversation`,
`getConversationMessages` (cursor), `sendConversationMessage` (idempotent), `markConversationRead`.
Legacy methods retained for compatibility.

### Realtime
Widget and page subscribe to `/topic/conversation/{conversationId}`. The message is appended only
if its `id` is not already present (de-dupe against the sender's own echo), and inbound messages
from others auto-advance the read marker.

---

## 3. Files changed (frontend)

| File | Change |
|---|---|
| `admin-front/.../api-service/chat.service.ts` | added conversation-centric methods |
| `admin-front/.../widgets/chat-widget/chat-widget.component.ts` | rewritten as conversation inbox + pickers |
| `admin-front/.../widgets/chat-widget/chat-widget.component.html` | view switch (list/people/branches/chat) |
| `admin-front/.../widgets/chat-widget/chat-widget.component.scss` | `.cw-action-btn` + footer flex layout |
| `admin-front/.../pages/branch-chat/branch-chat.component.ts` | transport migrated to conversation API |

Backend supporting change:
| File | Change |
|---|---|
| `ChatConversationSummaryDto.java` | added `otherUserId` |
| `ChatConversationService.java` | DIRECT summaries now resolve the peer's display name |

---

## 4. Backward compatibility

- Legacy REST endpoints and `/topic/branch/{id}` are still published by the backend, so any
  un-migrated screen keeps working. The migrated screens no longer depend on them.
- No routing, styling framework or business rule was changed.
- `clientMessageId` is generated with `crypto.randomUUID()` (with a fallback) so retries are safe.

---

## 5. Manual test steps

1. `ng build` / `ng serve` — no compile errors.
2. Open the floating widget → conversation list loads (empty first run).
3. **New Chat** → pick a user → a DIRECT conversation opens; send a message.
4. From another session (same user or the peer), send a message → it appears in real time and the
   unread badge clears while the conversation is open.
5. **Branches** → open an authorised branch → history loads; sending works.
6. Open a branch you are **not** a member of → access-error toast; list preserved.
7. `branch-chat` page → select a branch → send/receive works; admin sees the activity feed.
8. Retry a send (network throttle) → only one message row is created (idempotency).

---

## 6. Deferred (tracked)

- **Phase 1c:** receipts endpoints (delivered/read fan-out) + message edit (≤15 min) / soft-delete
  (author+admin) with edit-history table.
- Typing indicators & presence (Redis).
- Group creation UI (backend already admin-only) and memo-chat entry button.
- Search/bookmarks, threads UI, reactions, mentions (Phase 2).
