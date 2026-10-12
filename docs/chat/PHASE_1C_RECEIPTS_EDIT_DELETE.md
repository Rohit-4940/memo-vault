# Phase 1c — Receipts (delivered/read) + Message Edit / Soft-Delete

**Status:** Implemented, backend `BUILD SUCCESS` (58/58 tests), frontend `ng build` EXIT=0.
**Depends on:** Phase 0 (security hardening), Phase 1a (conversation & message core), Phase 1b (Angular UI migration).

---

## 1. Purpose

Give the messaging core a trustworthy, **server-enforced** lifecycle for messages:

- **Delivery / read receipts** — a sender can see whether their message reached and was read by
  the recipients, with correct behaviour for 1:1, group and large (branch/department/memo) fan-out.
- **Message editing** — the author may correct a message **within 15 minutes** of sending; every
  prior body is retained in an immutable audit table.
- **Policy-based deletion** — the author or an administrator may **soft-delete** a message. The
  body is hidden from everyone except administrators; the row (and any edit trail) is preserved
  for moderation / legal hold.

All authorization is resolved from the JWT principal and the database. The UI only *reflects*
these rules; it is never the enforcement point.

---

## 2. Data model

Migration `db.changelog-master.xml` (append-only):

| Changeset | Change |
|---|---|
| `20260613-1` | `chat_participant.last_delivered_message_id BIGINT` — per-member delivery watermark. |
| `20260613-2` | New table `chat_message_edit_history` (id, version, message_id FK→chat_message, previous_content TEXT, edited_by_id, edited_at, audit columns) + indexes `idx_cmeh_message(message_id, edited_at)`, `idx_cmeh_edited_by(edited_by_id)`. |

Entities:

- `ChatParticipant.lastDeliveredMessageId` (read implies delivered, so `lastDeliveredMessageId >= lastReadMessageId`).
- `ChatMessageEditHistory` extends `BaseEntity<Long>`.

`chat_message_receipt` (created in Phase 1a) is now **actually written** — but only for
`DIRECT` and `GROUP` conversations (bounded member counts). Large channels use watermarks only,
which avoids one receipt row per member per message.

### Why watermarks + selective receipts

- **Unread / status** is computed from participant watermarks — O(participants), no row growth.
- **Exact read timestamps** are materialized as `chat_message_receipt` rows for DIRECT/GROUP so a
  1:1 sender sees "read at 14:32".
- The watermark is **seeded at join time** (`joinIfNeeded`) to the conversation tail, so a member
  who opens a busy branch is not shown the whole history as unread, and no receipt backfill occurs.

---

## 3. Backend changes

### 3.1 New / changed classes

| Layer | File | Change |
|---|---|---|
| Entity | `core/entity/ChatParticipant.java` | `+ lastDeliveredMessageId` |
| Entity | `core/entity/ChatMessageEditHistory.java` | **new** immutable edit-audit row |
| Repo | `security/repository/ChatMessageRepository.java` | `+ findByConversationIdAndIsDeletedFalseOrderByIdDesc`, `+ …IsDeletedFalseAndIdLessThan…`, `+ …IdGreaterThan…IdLessThanEqual…OrderByIdAsc` |
| Repo | `security/repository/ChatMessageEditHistoryRepository.java` | **new** |
| Repo | `security/repository/ChatMessageReceiptRepository.java` | `+ findByMessageIdInAndUserId` |
| DTO | `core/dto/ChatMessageDto.java` | **new** message read-model (status + tombstone-safe) |
| DTO | `core/dto/MessageStatusDto.java` | **new** single-message status |
| DTO | `core/dto/ChatReceiptEventDto.java` | **new** realtime watermark event |
| DTO | `core/dto/EditMessageRequest.java` | **new** edit payload |
| Service | `web/service/ChatMessageService.java` | DTO mapping, `editMessage`, `deleteMessage`, `getMessageStatus`, status in history/send |
| Service | `web/service/ChatConversationService.java` | `markDelivered`, watermark seeding, receipt materialization, receipt broadcast |
| Service | `web/service/ChatService.java` | legacy list endpoints filter soft-deleted rows |
| Controller | `web/Controller/ChatConversationController.java` | edit / delete / delivered / status endpoints; DTO responses |

### 3.2 Message read-model (`ChatMessageDto`)

`id, conversationId, conversationType, senderId, receiverId, memoId, branchId, messageType,
message, parentMessageId, replyCount, isEdited, editedAt, isDeleted, deletedReason, createdDate,
clientMessageId, deliveryState, deliveredCount, readCount, totalRecipients, readAt`.

- `message` is **null when the message is deleted and the viewer is not an admin**.
- `deletedReason` is only included for admins.
- `deliveryState` / counts are only populated when the viewer authored the message (or is an admin).

### 3.3 REST endpoints (all under `/chat`, caller = JWT principal)

| Method | Path | Authorization |
|---|---|---|
| `POST` | `/conversations/{id}/messages` | conversation access (existing) |
| `GET` | `/conversations/{id}/messages` | conversation access (existing) |
| `PUT` | `/conversations/{id}/messages/{messageId}` | **author only**, within 15 min, not deleted, not locked |
| `DELETE` | `/conversations/{id}/messages/{messageId}?reason=` | **author or admin**, not locked |
| `GET` | `/conversations/{id}/messages/{messageId}/status` | **sender or admin** |
| `POST` | `/conversations/{id}/read` | conversation access (advances read+delivered) |
| `POST` | `/conversations/{id}/delivered` | conversation access (advances delivered) |

### 3.4 Realtime events

| Topic | Payload | When |
|---|---|---|
| `/topic/conversation/{id}` | `ChatMessageDto` (new / edited / tombstoned) | send, edit, delete |
| `/topic/conversation/{id}/receipts` | `ChatReceiptEventDto` | any member's watermark advances |
| `/queue/messages/{userId}`, `/topic/branch/{branchId}` | `ChatMessageDto` | unchanged legacy fan-out |

`WebSocketAuthInterceptor` already authorizes `/topic/conversation/**` by parsing the **first**
path segment as the conversation id, so the `/receipts` sub-topic is covered by the same
resource check — no interceptor change was required.

---

## 4. Security

- **Author-only edit** and **author-or-admin delete** are enforced in `ChatMessageService`; the
  client cannot spoof a sender (principal-derived) nor an author id.
- **No content leak:** deleted bodies are nulled in the DTO for non-admins; history queries use
  `…IsDeletedFalse…`; legacy `getBranchMessages`/`getMemoMessages` filter tombstones; conversation
  previews skip deleted messages.
- **Status is metadata:** only the sender (or an admin) may query per-message status, so read
  activity of private conversations is not exposed to other participants.
- **Immutable edit trail** in `chat_message_edit_history` for moderation / audit.
- **Locked conversations** reject new sends, edits and deletes.
- **SPA** reconciles events by id (append vs. replace) — an edited/deleted broadcast updates the
  same row, never duplicates it.

---

## 5. Tests

`nexmemo-web` unit tests — **58 run, 0 failures** (11 new):

- `ChatMessageServiceTest` (14): idempotent send, lock enforcement, access enforcement, cursor
  history, edit within window appends history + broadcasts, edit denied after window / for
  non-author, author delete hides content, admin delete retains content + reason, delete denied
  for non-author non-admin, status for sender, status denied for non-sender.
- `ChatConversationServiceTest` (11): + `markRead` advances delivered watermark, `markDelivered`
  sets tail watermark + broadcasts, `markDelivered` no-op when already delivered.

---

## 6. Frontend changes

- `shared-services/api-service/chat.service.ts` — `markConversationDelivered`,
  `editConversationMessage`, `deleteConversationMessage`, `getMessageStatus`.
- `chat-widget` — reconcile realtime events by id; edit (inline textarea, ≤15 min) / delete actions
  on own messages; "edited" marker; tombstone rendering; delivery ticks (single = sent,
  double = delivered, double in accent = read); subscribes to `/receipts` and upgrades ticks live;
  marks delivery on open.
- `branch-chat` page — reconciles edit/delete broadcasts instead of skipping existing ids; marks
  delivery on open.

---

## 7. Deployment / backward-compatibility notes

- Liquibase changesets are additive and guarded with preconditions; existing data is untouched.
  New participant rows get a seeded watermark; pre-existing rows have a null watermark (treated as
  "nothing delivered yet", which is correct for fresh deployments).
- API responses for send/history/getMessage are now `ChatMessageDto` instead of the raw entity.
  Field names used by the Phase 1b UI (`id`, `senderId`, `message`, `createdDate`) are unchanged.
- Legacy endpoints (`/chat/branch/{id}`, `/chat/memo/{id}`) still return entities but now omit
  soft-deleted rows.

---

## 8. Manual test steps

1. **Delivered/read (1:1):** user A opens a DM with B and sends a message → A sees a single check.
   B opens the widget → A's check becomes double (delivered) then accent double (read) without reload.
2. **Group fan-out:** admin creates a group with B and C; A sends → tick advances as each member
   opens; state reaches READ only when all recipients have read.
3. **Edit:** A sends, edits within 15 min → text updates everywhere, "edited" appears; the edit
   trail row exists in `chat_message_edit_history`. After 15 min the edit action disappears and the
   API returns 403.
4. **Delete:** A deletes own message → all clients show "This message was deleted". B (non-admin)
   receives a null body; an admin querying the message sees the original body + reason.
5. **Authorization:** B tries `PUT` on A's message → 403; B tries `DELETE` on A's message → 403;
   B tries `GET …/status` on A's message → 403.
6. **Branch channel:** open a branch conversation with history → unread is 0 (no backfill); new
   messages increment unread; deleted branch messages vanish from the legacy feed.
