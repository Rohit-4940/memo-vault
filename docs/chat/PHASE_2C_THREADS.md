# Phase 2c — Message Threads / Replies

Status: **Implemented** (backend + frontend + tests).
Related plan item: **C-11 threaded replies (P1)** in
`docs/chat/PHASE_1_ANALYSIS_AND_PLAN.md` (Phase 2 — Collaboration features).

---

## 1. Purpose

Every message can become a thread. Users click the reply icon (or the `💬 n` counter) on any
message, and a thread panel opens showing all replies to that message plus a dedicated reply
composer. Replies are **not** first-class top-level messages — they are children of the parent.

Design goals:

- **Simple, correct model.** A reply is a normal `chat_message` row with `parent_message_id`
  set. The top-level feed stays clean: threads appear as a counter on the parent, never inline.
- **Authorized on both sides.** Sending a reply runs the exact same conversation-write
  authorization (`assertCanWriteConversation`) as a normal message, and the parent must be in
  the same conversation the caller can read.
- **No reply-to-reply nesting.** Replies are always one level deep (parent = top-level message).
- **Consistent realtime.** Replies are broadcast on the same `/topic/conversation/{id}` topic,
  so every client updates the parent's counter without a poll.

---

## 2. Classification

| Field | Value |
|---|---|
| Priority | **P1** |
| Complexity | Low (column already exists from Phase 1c; paging + counter sync on client) |
| Dependencies | Phase 1a (conversation core), 1c (realtime), Phase 0 (authz) |
| Risks | Low — no schema changes; existing messages are just parents of zero replies |
| Order | First Phase 2 feature after @mentions |

---

## 3. Data model

No new table. Reuses columns introduced by changeset **`20260612-6`**:

- `chat_message.parent_message_id BIGINT NULL` — parent id (indexed `idx_cm_parent`).
- `chat_message.reply_count INT NOT NULL DEFAULT 0` — denormalized counter, maintained
  transactionally by the service so the feed never has to `COUNT(*)`.

Validation rules (in `ChatMessageService`):

- A reply can only be sent to a **non-deleted** parent message.
- A reply's own `parent_message_id` sets the "size" of the thread. Replying to a reply is a
  404-equivalent (`IllegalArgumentException: "Nested replies are not supported"`).
- Replies inherit the conversation of the parent; the caller must be able to write that
  conversation.

---

## 4. Backend changes

| File | Change |
|---|---|
| `ChatMessageService` | `sendMessage` accepts `parentMessageId` (validated, `replyCount++`, broadcast); `getReplies(conversationId, parentMessageId, userId, afterId, size)`; private `incrementReplyCount` |
| `ChatConversationController` | `GET /chat/conversations/{id}/messages/{messageId}/replies?afterId=&size=` |
| `ChatMessageValidator` | rejects nested replies & deleted parents |

`getReplies` returns a `Page<ChatMessageDto>` (`content` = DTOs, oldest-first within the page)
scoped to the conversation; `afterId` pages forward on the cursor (`id > afterId` order by id).

### REST

```
GET /api/v1/chat/conversations/{conversationId}/messages/{messageId}/replies?size=50&afterId=
Authorization: Bearer <JWT>
```

Response uses the standard envelope: `{ "data": { "content": [ChatMessageDto...], ... } }`.

### Realtime

Replies do **not** get their own topic. The server broadcasts the new reply DTO on
`/topic/conversation/{id}` exactly like a normal message. Clients already subscribed to that
topic upsert the reply and increment the parent's `replyCount` from the DTO (`replyCount` is
authoritative on the parent).

---

## 5. Frontend changes

| File | Change |
|---|---|
| `chat.service.ts` | `getReplies(conversationId, messageId, afterId?, size?)` |
| `chat-widget.component.ts` | `threadId` / `threadReplies` / `threadReplyText` state; `openThread`, `closeThread`, `threadParent`, `loadThreadReplies`, `sendReply` (uses `parentMessageId`), `replyTo`; `scrollThreadBottom` |
| `chat-widget.component.html` | reply icon + `💬 n` counter on messages; thread panel (replies + composer) replacing the main composer while open |
| `branch-chat.component.ts/html` | same minimal set (open thread from any message, reply in a panel) |

Composer contract: the reply composer calls `sendConversationMessage(convId, { clientMessageId,
message, parentMessageId })`. On success the server response is appended to `threadReplies` and
the parent's counter is bumped; the WS echo keeps every other client in sync.

---

## 6. Tests

- `ChatMessageServiceTest`:
  - `sendMessage_replyToParent_AppendsToThreadAndBroadcasts` — parent `replyCount` becomes 1,
    message has `parentMessageId`, broadcast carries updated parent counter.
  - `sendMessage_replyToDeletedParent_Rejected`
  - `sendMessage_nestedReply_Rejected`
  - `getReplies_returnsRepliesFromConversation_Only`
- Frontend builds clean (Angular 18 template compile is part of `npm run build`).

---

## 7. Deployment notes

- Run the backend normally — Liquibase already owns the schema; `ddl-auto: validate` requires
  the pre-existing columns (present since `20260612-6`, no action needed).
- No environment variable changes.

---

## 8. Manual verification steps

1. Open the chat widget, pick a conversation, send "hello".
2. Click the reply arrow on it → thread panel opens with "No replies yet".
3. Type a reply → it appears in the panel; parent now shows `💬 1`.
4. In a second browser session, the same conversation updates live (counter + reply visible).
5. Try replying to a reply → rejected with "Nested replies are not supported".