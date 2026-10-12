# Phase 2b — @Mentions & Employee Tagging

Status: **Implemented** (backend + frontend + tests).
Related plan item: **C-10 @mentions + employee tagging (P1)** in
`docs/chat/PHASE_1_ANALYSIS_AND_PLAN.md` (Phase 2 — Collaboration features).

---

## 1. Purpose

Let a sender tag colleagues with `@name`, turning the tag into a clickable chip and creating an
in-app notification plus a realtime event for every newly tagged user. Tagged users also get a
personal "tagged inbox" (`GET /chat/mentions`).

Design goals:

- **Never trust the client about ids.** The composer encodes a mention as a `@[Display Name](userId)`
  token; the server re-parses the raw body with a regex and resolves the target ids itself.
- **A notification can never leak.** Mentions are persisted and notified **only** for users who pass
  `chatAuthorizationService.canAccessConversation(...)` — i.e. people who could read the message
  anyway. Self-mentions and unknown users are dropped silently.
- **No spoofable behavior.** A user typing a token manually is just deliberately tagging that user;
  there is no way to forge a mention from someone else.
- Mentions follow **edits** (re-parsed, stale tags removed, only newly added users notified) and
  **deletes** (rows cleaned up so deleted content is not referenceable).

---

## 2. Classification

| Field | Value |
|---|---|
| Priority | **P1** |
| Complexity | Medium (parsing, authorization filter, sync-on-edit, notifications, composer autocomplete in two UIs) |
| Dependencies | Phase 0 (authz), Phase 1a (conversation core), Phase 1b (UI), Phase 1c (realtime) |
| Risks | Low–Medium — token syntax is new (affects rendering only), editing now re-parses content |
| Order | Second Phase 2 feature |

---

## 3. Token format

```
@[Display Name](123)
```

- Regex (`ChatMentionService.MENTION`): `@\[([^\]\r\n]{1,80})\]\((\d{1,19})\)`.
- The label is display-only (used for the chip and the un-tagged preview); the numeric id is what
  matters and is validated server-side.
- Display names are stored in the message body as an immutable snapshot — renaming later does not
  rewrite history.
- Preview rendering (`ChatMentionService.preview`) replaces tokens with `@Name`, collapses
  whitespace and truncates to 120 chars.

---

## 4. Data model / DB migration

Liquibase changeset **`20260615-1`** in
`system/system/nexmemo-core/src/main/resources/db/changelog/db.changelog-master.xml`
(precondition-guarded like every other changeset):

Table `chat_message_mention`:

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT identity | PK |
| `version` | INT | optimistic lock |
| `message_id` | BIGINT | FK → `chat_message.id` (`fk_cmment_message`) |
| `conversation_id` | BIGINT | denormalized for scoped queries |
| `mentioned_user_id` | BIGINT | the tagged user |
| audit columns | — | `created_by_id`, `modified_by_id`, `created_date`, `modified_date` |

Constraints / indexes:

- `uq_cmment_msg_user` — UNIQUE `(message_id, mentioned_user_id)` → no duplicate tags.
- `idx_cmment_message` — `(message_id)`.
- `idx_cmment_user` — `(mentioned_user_id, id)` → powers the tagged inbox.

Entity: `nexmemo-core/.../entity/ChatMessageMention.java` (extends `BaseEntity<Long>`).
Repository: `nexmemo-security/.../repository/ChatMessageMentionRepository.java`.

> Purely additive — no existing table/column changes; old messages render as plain text.

---

## 5. REST endpoints

| Method | Path | Params | Returns | Notes |
|---|---|---|---|---|
| `GET` | `/chat/mentions` | `size` (default 30, max 50) | `List<ChatMentionDto>` | Caller's own tagged inbox, newest first |

`ChatMentionDto`: `{ messageId, conversationId, mentionedUserId, mentionedByUserId, mentionedByName,
preview, createdDate }`.

- Scoped by `mentioned_user_id = principal` — no way to enumerate another user's mentions.
- Mentions of **deleted** messages are filtered out before being returned.

Messages themselves carry `ChatMessageDto.mentionedUserIds` (`List<Long>`), populated on send,
history, and single-message reads (bulk-loaded with reactions — no N+1).

---

## 6. Realtime events

Topic: **`/topic/notifications/{mentionedUserId}`** — same topic already authorized by the
`WebSocketAuthInterceptor` (caller must equal the user in the path segment).

Payload `ChatMentionEventDto`:

```json
{ "conversationId": 1, "messageId": 50, "mentionedUserId": 20, "mentionedByUserId": 10,
  "mentionedByName": "Asha", "preview": "@Ravi please review the T+1 memo" }
```

Additionally a persisted `Notification` (type `CHAT_MENTION`, referenceType `CONVERSATION`,
referenceId = conversation id) is created per newly tagged user, so the existing notification bell
(badge polls every 30 s; `fa-at` icon added for the type) surfaces it even after reload/disconnect.

Emitted **only** for newly added mentions — editing a message to re-tag the same people does not
re-notify.

---

## 7. Server-side validation / authorization / errors

`ChatMentionService.syncMentions(conversation, message, senderId)` — called from
`ChatMessageService.send` and `editMessage` (transactional):

1. Parse tokens → distinct ids (order preserved), max **25** per message (cap logged).
2. Drop the sender (`self-match`), unknown users, and users failing
   `canAccessConversation(userId, conversation)` (logged at DEBUG).
3. Diff against the persisted rows: delete stale, insert new (unique constraint makes it
   idempotent even if two sends race), notify only newly added.
4. `removeForMessage(messageId)` is invoked on soft-delete.

Errors: parsing/cap issues never fail the message send — they degrade by dropping tags. The only
failure modes are the pre-existing conversation authorization (403) and send restrictions.

---

## 8. Frontend changes

### `chat.service.ts`
- `getMyMentions(size)` → `/api/v1/chat/mentions`.

### `chat-widget.component.{ts,html,scss}`
- Mention **autocomplete**: typing `@` in the composer opens a picker of matching users (from the
  slim directory, caller excluded, max 6). **Enter** selects the top candidate, **Escape** closes,
  clicking inserts `@[Name](id) ` at the caret.
- Mention **rendering**: `messageSegments(text)` splits a body into text/mention segments (regex,
  no `innerHTML` → XSS-safe); mention chips are styled (`@Name`), highlighted when the tag is the
  caller.
- Messages where the caller was tagged get a subtle left-accent (`isMentionedIn`).

### `branch-chat.component.{ts,html,scss}`
- Same composer autocomplete, segment rendering and highlight, for the full-page branch hub.

### `notification-bell.component.ts`
- `CHAT_MENTION` → `fa-at` icon.

---

## 9. Tests

Backend `ChatMentionServiceTest` (8 tests):

- token parsing extracts/dedupes/ignores malformed tokens;
- `preview` resolves tokens and truncates;
- `syncMentions` persists only authorized targets and notifies them (notification + realtime);
- self/unknown/unauthorized targets are dropped — nothing persisted, nothing notified;
- edit-sync removes stale rows and notifies **only** newly tagged users;
- `removeForMessage` cleans up on delete;
- `getMyMentions` maps sender names/previews and skips deleted messages.

`ChatMessageServiceTest` updated with `ChatMessageMentionRepository` + `ChatMentionService` mocks;
all existing assertions still green (reactions/mentions are additive to the DTO).

Result: **77 tests, 0 failures, BUILD SUCCESS**. Frontend `ng build` succeeds; Liquibase changelog
parses (78 changesets, last `20260615-1`).

### Manual test steps

1. Open any conversation in the widget, type `@` → picker appears; pick a colleague → token
   inserted; send the message.
2. The message renders with a styled `@Name` chip; the tagged user's notification bell shows a
   `CHAT_MENTION` (fa-at) unread within ~30 s (on next poll), and their `/topic/notifications/{id}`
   socket receives the event.
3. Tag yourself → the message gets the "mentioned" accent and the chip highlights.
4. Edit the message to remove the tag → the tagged user's notification is not duplicated (no re-send
   for unchanged tags), and a tag added in the edit IS notified.
5. Delete the message → `GET /chat/mentions` no longer shows it.
6. Craft `@[x](999)` for a nonexistent/unauthorized user → send succeeds, no notification created
   (DEBUG log shows the drop).
7. `GET /api/v1/chat/mentions` with another bearer token returns only that user's own tags.

---

## 10. Deployment notes

- Run Liquibase (adds `chat_message_mention`) before deploying the new backend.
- Backward compatible: the old backend ignores the table; the new backend treats legacy messages
  (with no mention rows) as having no mentions.
- Rolling upgrade safe (additive schema, additive endpoints, additive topic).

---

## 11. Files touched

**New**
- `nexmemo-core/.../entity/ChatMessageMention.java`
- `nexmemo-security/.../repository/ChatMessageMentionRepository.java`
- `nexmemo-core/.../dto/ChatMentionEventDto.java`
- `nexmemo-core/.../dto/ChatMentionDto.java`
- `nexmemo-web/.../service/ChatMentionService.java`
- `nexmemo-web/src/test/.../service/ChatMentionServiceTest.java`

**Modified**
- `nexmemo-core/.../db/changelog/db.changelog-master.xml` (changeset `20260615-1`)
- `nexmemo-core/.../dto/ChatMessageDto.java` (`mentionedUserIds`)
- `nexmemo-web/.../service/ChatMessageService.java` (inject mention repo/service; sync on
  send/edit/delete; bulk-load + map `mentionedUserIds`)
- `nexmemo-web/.../Controller/ChatConversationController.java` (`GET /chat/mentions`)
- `nexmemo-web/src/test/.../service/ChatMessageServiceTest.java` (new mocks)
- `admin-front/.../api-service/chat.service.ts` (`getMyMentions`)
- `admin-front/.../widgets/chat-widget/chat-widget.component.{ts,html,scss}`
- `admin-front/.../pages/branch-chat/branch-chat.component.{ts,html,scss}`
- `admin-front/.../shared-components/notification-bell/notification-bell.component.ts` (icon)