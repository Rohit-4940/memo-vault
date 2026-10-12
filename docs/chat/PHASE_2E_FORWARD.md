# Phase 2e — Forward a Message

Status: **Implemented** (backend + frontend + tests).
Related plan item: **C-14 forwarding (P2)** in
`docs/chat/PHASE_1_ANALYSIS_AND_PLAN.md` (Phase 2 — Collaboration features).

---

## 1. Purpose

Let a user copy any message they can **read** into another conversation they can **write**.
The copy is a brand-new message with `messageType = FORWARD`, its body prefixed with
`↪ Forwarded from <sender>`, and its `@mentions` re-parsed against the *target* conversation.

Design goals:

- **Double authorization, both sides.** The source must be readable (`assertCanAccessConversation`
  on source) *and* the target must be writable (`assertCanAccessConversation` + not-locked on
  target). Forwarding can never leak a message into a conversation the caller could not already
  read.
- **No stale content.** Deleted sources are rejected. Edited/deleted *copies* behave exactly like
  normal messages afterwards (soft-delete, edit, reply, pin/forward again).
- **Idempotent fast-retry.** A `clientMessageId` on the request de-duplicates double clicks; the
  second call returns the already-created message instead of duplicating the copy.
- **Mentions follow the target.** The raw body is re-scanned with `ChatMentionService` so a
  forwarded message notifies the *target* conversation's participants, never the original ones.

---

## 2. Classification

| Field | Value |
|---|---|
| Priority | **P2** |
| Complexity | Medium (source+target authz checks, mention re-parse, idempotency) |
| Dependencies | Phase 0 (authz), 1a, 2b (mentions) |
| Risks | Low |
| Order | Fourth Phase 2 feature (2e) |

---

## 3. Data model

No new migration. A forwarded copy is a normal `chat_message` row:

- `message_type = 'FORWARD'` (new enum value added to `ChatMessageType`; column already exists,
  `VARCHAR(20)`).
- `client_message_id` = the caller-supplied id (idempotency key, unique per conversation scope).
- The body is `↪ Forwarded from {senderName}\n\n{original body}`.

The source message id is intentionally **not** stored (no reference chain, no amplification of
soft-deletes).

---

## 4. Backend changes

| File | Change |
|---|---|
| `ChatMessageType` | add `FORWARD` |
| `ChatMessageService.forwardMessage` | source read authz → target write authz + locked-check → idempotency check → mention re-parse → copy insert → broadcast |
| `ForwardMessageRequest` (new DTO) | `targetConversationId`, `clientMessageId` |
| `ChatConversationController` | `POST /chat/messages/{messageId}/forward` |

### REST

```
POST /api/v1/chat/messages/{sourceMessageId}/forward
Authorization: Bearer <JWT>
{ "targetConversationId": 2, "clientMessageId": "cmid-..." }
```

Errors:

| Case | Result |
|---|---|
| Source not found / already deleted | `400 "Message not found"` / `400 "A deleted message cannot be forwarded"` |
| No read on source or no write on target | `403 AccessDeniedException` |
| Target locked | `403` |
| Duplicate `clientMessageId` | returns the **existing** copy (idempotent), no duplicate row |

### Realtime

The new copy is broadcast on `/topic/conversation/{targetId}` like any other message, so target
participants receive the forwarded content immediately.

---

## 5. Frontend changes

| File | Change |
|---|---|
| `chat.service.ts` | `forwardMessage(messageId, targetConversationId, clientMessageId?)` |
| `chat-widget.component.ts/html` | share icon on messages opens a forward picker (overlay listing the caller's other conversations, limited to 20); `pickForwardTarget` calls the API with a fresh `clientMessageId`; success toast shows the target name |

The picker only lists conversations the caller can already see (the widget's own conversation
list), and the server still re-checks both sides — the picker is convenience, not security.

---

## 6. Tests

- `ChatMessageServiceTest`:
  - `forwardMessage_copiesIntoTarget_withBothSidesAuthorized` — new FORWARD row, `↪ Forwarded
    from` prefix (name resolved via `userRepository`), mentions re-parsed, broadcast on the
    target topic.
  - `forwardMessage_deniedWithoutTargetAccess` — target write denial throws 403, nothing saved.
  - `forwardMessage_deniedWithoutSourceAccess`, `forwardMessage_deletedSource_rejected`,
    `forwardMessage_duplicateClientMessageId_isIdempotent`.

---

## 7. Deployment notes

- No migration. `ChatMessageType.FORWARD` is a Java enum value; existing rows keep `TEXT`.
- No env changes.

---

## 8. Manual verification steps

1. Open conversation A, send "hello world".
2. Forward it into conversation B via the share icon.
3. In B the copy shows `↪ Forwarded from <you>`; participants of B (not A) see it live.
4. Double-click forward → only one copy is created.
5. Forward a deleted message → rejected with "A deleted message cannot be forwarded".