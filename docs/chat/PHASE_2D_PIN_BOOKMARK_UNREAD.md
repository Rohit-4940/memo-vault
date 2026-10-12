# Phase 2d — Pin, Bookmark & Mark-as-Unread

Status: **Implemented** (backend + frontend + tests).
Related plan item: **C-12 pinning / starring, C-13 save-for-later (bookmarks), rewinding unread** (P1/P2) in
`docs/chat/PHASE_1_ANALYSIS_AND_PLAN.md` (Phase 2 — Collaboration features).

---

## 1. Purpose

Three related "attention" primitives:

1. **Pin** (`is_pinned`) — a message the author (or an admin) flags as important for the whole
   conversation. Pinned messages are surfaced via a dedicated pinned filter in the UI.
2. **Bookmark** — a *private, per-user* "save for later" list, invisible to everyone else. No
   notification, no broadcast — purely personal.
3. **Mark unread** — rewinds the participant's read watermark so the conversation re-appears as
   unread even though the client already received the messages.

Design goals:

- **Pin is public intent, bookmark is private.** Pin toggles a column on the message, the change
  is broadcast so every participant sees it. Bookmark lives in `chat_bookmark` keyed by
  `(user_id, message_id)` and is only ever queried with the principal's own id.
- **Pin is restricted.** Only the message author or an admin can toggle pin — a user cannot pin
  someone else's message. Enforced server-side (`author or admin` in `ChatMessageService`).
- **Authoritative flags in the feed.** `ChatMessageDto` carries `isPinned` and `isBookmarked`
  so the UI never guesses or keeps its own state.
- **Unread rewind is allowed but idempotent.** The watermark rewinds to just before the given
  message id (or to the conversation start when none is given).

---

## 2. Classification

| Field | Value |
|---|---|
| Priority | P1 (pin), P2 (bookmark, mark-unread) |
| Complexity | Low |
| Dependencies | Phase 1a (conversation/participant model), 1c (realtime), Phase 0 (authz) |
| Risks | Low |
| Order | Second Phase 2 feature (2d) |

---

## 3. Data model / DB migration

Liquibase changesets in `db.changelog-master.xml`:

**`20260616-1`** — `chat_message.is_pinned BIT NOT NULL DEFAULT 0`
(precondition: skip when column exists).

**`20260616-2`** — table `chat_bookmark`:

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK identity | |
| `user_id` | BIGINT NOT NULL | owner (principal) |
| `message_id` | BIGINT NOT NULL | FK → `chat_message` |
| `conversation_id` | BIGINT | denormalized for the saved-list query |
| `created_by_id` / `modified_by_id` / `created_date` / `modified_date` | | audit (BaseEntity) |

Unique `(user_id, message_id)`; index on `user_id`.

---

## 4. Backend changes

| File | Change |
|---|---|
| `ChatMessageService` | `getPinned(conversationId, userId, size)` (page of pinned, newest first, null-safe `bookmarkMessageIds` fillers); `pinMessage` toggle — author-or-admin check inside the transaction, broadcasts the updated DTO; `toDto` now sets `isPinned` + `isBookmarked` |
| `ChatBookmarkService` (new) | `bookmark`, `unbookmark`, `list(userId, size)` returning `List<ChatBookmarkDto>` |
| `ChatConversationService` | `markUnread(participant, userId)` rewinds `lastReadMessageId` to `< messageId` (or null) and bumps the summary counter |
| `ChatConversationController` | pinned list, pin toggle, bookmark/unbookmark, my-bookmarks, unread endpoints |
| `ChatMessageDto` | `isPinned`, `isBookmarked` |
| Repos | `ChatMessageRepository.findPinnedByConversationId(...)`, `findActiveConversationIdsByUserId(...)` (used by bookmark fetch), `ChatBookmarkRepository` |

### REST

```
GET    /api/v1/chat/conversations/{id}/pinned?size=50          → Page<ChatMessageDto>
POST   /api/v1/chat/conversations/{id}/messages/{messageId}/pin → ChatMessageDto (403 if not author/admin)
POST   /api/v1/chat/messages/{messageId}/bookmark              → 200
DELETE /api/v1/chat/messages/{messageId}/bookmark              → 200
GET    /api/v1/chat/bookmarks?size=50                          → ChatBookmarkDto[]
POST   /api/v1/chat/conversations/{id}/unread   body {messageId?} → 200
```

Authorization: all endpoints require conversation access (`assertCanAccessConversation`), the
principal is `@AuthenticationPrincipal Long userId` (never a client-supplied id).

### Realtime

- **Pin toggle** broadcasts the updated `ChatMessageDto` on `/topic/conversation/{id}` — every
  participant's pinned indicator flips live. Bookmark changes are intentionally **not**
  broadcast (private).
- Mark-unread is a local watermark change; no topic.

---

## 5. Frontend changes

| File | Change |
|---|---|
| `chat.service.ts` | `getPinned`, `togglePin`, `bookmarkMessage`, `unbookmarkMessage`, `getMyBookmarks`, `markConversationUnread` |
| `chat-widget.component.ts/html` | 📌 pin button (author/admin only, `.active` when pinned); toolbar pin-filter toggling `visibleMessages` between the feed and the pinned list; bookmark button + fill indicator; "Mark unread" toolbar action; **Saved** view in the list footer showing `getMyBookmarks` (click → opens the source conversation) |
| `branch-chat.component.ts/html` | pin + bookmark buttons on each bubble; mark-unread + saved access via same service |

In both UIs messages render `isPinned`/`isBookmarked` state straight from the DTO so the widget
never drifts after a re-broadcast.

---

## 6. Tests

- `ChatMessageServiceTest`: `pin_author_only`, `pin_admin_can_toggle`, `pin_otherUser_denied`,
  `getPinned_returnsOnlyPinned`, `bookmarkMessageIds_nullSafe`.
- `ChatBookmarkServiceTest` (new): bookmark once / duplicate idempotent, unbookmark missing is
  silent, list is scoped to the principal and DTO-mapped.
- `ChatConversationServiceTest`: `markUnread` rewinds watermark and bumps unread count.

---

## 7. Deployment notes

- Run backend — `20260616-1` and `20260616-2` apply automatically (Liquibase, `ddl-auto:
  validate` satisfied once applied).
- No env changes.

---

## 8. Manual verification steps

1. As message author: pin a message → 📌 appears for every other participant live.
2. As a different user: the pin button is hidden/absent (403 if called directly).
3. Bookmark a message → fill icon; open **Saved** in the widget → it is listed; unbookmark → gone.
4. Mark the active conversation as unread → unread badge comes back in the list view.