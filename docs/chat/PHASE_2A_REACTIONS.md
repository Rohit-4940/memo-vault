# Phase 2a — Emoji Reactions

Status: **Implemented** (backend + frontend + tests).
Related plan item: **C-11 Emoji reactions (P1)** in
`docs/chat/PHASE_1_ANALYSIS_AND_PLAN.md` (Phase 2 — Collaboration features).

---

## 1. Purpose

Let any participant of a conversation attach lightweight emoji reactions to a message, anywhere a
message is rendered (the floating `chat-widget` inbox and the full-page `branch-chat` hub).

Design goals:

- One reaction row per `(message, user, emoji)` — a user cannot stack duplicates, and concurrent
  adds are naturally idempotent (DB unique constraint).
- Reactions are **never** trusted from the client; the server derives the caller from the JWT
  principal and re-checks conversation access on every mutation.
- Realtime updates must not disturb message reconciliation — reaction deltas travel on a dedicated
  sub-topic, so a reaction event can never be mistaken for a new/edit/delete message event.
- Counts are **authoritative from the server** (`count` in every event and in every summary) so a
  client that missed an event self-heals on the next event / reload instead of drifting.

---

## 2. Classification

| Field | Value |
|---|---|
| Priority | **P1** (collaboration, non-blocking) |
| Complexity | Medium (new table, service, endpoints, realtime sub-topic, two UI surfaces) |
| Dependencies | Phase 0 (authz), Phase 1a (conversation/message core), Phase 1b (UI), Phase 1c (realtime sub-topic pattern) |
| Risks | Low — additive schema, additive endpoints, additive topic; no change to existing message payload semantics |
| Order | First Phase 2 feature (self-contained; establishes the "typed sub-event on a dedicated sub-topic" pattern reused by mentions/typing/presence) |

---

## 3. Data model / DB migration

Liquibase changeset **`20260614-1`** in
`system/system/nexmemo-core/src/main/resources/db/changelog/db.changelog-master.xml`
(`mssql`-safe, guarded by a `tableExists` precondition → idempotent re-run):

Table `chat_message_reaction`:

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT identity | PK |
| `version` | INT | optimistic-locking column (matches every other entity) |
| `message_id` | BIGINT | FK → `chat_message.id` (`fk_cmreact_message`) |
| `user_id` | BIGINT | reactor |
| `emoji` | NVARCHAR(32) | the reaction glyph/sequence |
| `created_by_id`,`modified_by_id` | BIGINT | audit fields (`BaseEntity`) |
| `created_date`,`modified_date` | DATETIME | audit fields (`BaseEntity`) |

Constraints / indexes:

- `uq_cmreact_msg_user_emoji` — UNIQUE `(message_id, user_id, emoji)` → duplicate-proof.
- `idx_cmreact_message` — index on `message_id` (history bulk-load).

Entity: `nexmemo-core/.../entity/ChatMessageReaction.java` (extends `BaseEntity<Long>`).
Repository: `nexmemo-security/.../repository/ChatMessageReactionRepository.java`.

> Backward compatibility: purely additive. No existing column/table is altered. Existing rows are
> untouched and messages simply render with an empty reaction list.

---

## 4. REST endpoints

All under the existing `/api/v1/chat` base, mapped in `ChatConversationController`.

| Method | Path | Body / Params | Returns | Notes |
|---|---|---|---|---|
| `POST` | `/chat/messages/{messageId}/reactions` | `{ "emoji": "👍" }` | `List<ChatReactionSummaryDto>` | idempotent add |
| `DELETE` | `/chat/messages/{messageId}/reactions?emoji=...` | query param `emoji` | `List<ChatReactionSummaryDto>` | idempotent remove |
| `GET` | `/chat/messages/{messageId}/reactions` | — | `List<ChatReactionSummaryDto>` | read-only |

`ChatReactionSummaryDto`: `{ emoji, count, reactedByMe, userIds[] }`
(`reactedByMe` is viewer-specific; `userIds` is capped at 50 and powers the chip tooltip).

`DELETE` uses a **query parameter** rather than the `/{emoji}` path segment planned in
`PHASE_1_ANALYSIS_AND_PLAN.md` (C-11). Rationale: emoji are multi-codepoint sequences (ZWJ,
variation selectors, skin-tone modifiers) that are fragile URL path segments across proxies; a
query parameter is percent-encoded reliably by `HttpClient`.

All responses are wrapped in the project's `ApiResponse<T>` envelope.

---

## 5. Realtime event

Topic: **`/topic/conversation/{conversationId}/reactions`** (dedicated sub-topic — mirrors the
Phase 1c `/receipts` sub-topic).

Payload `ChatReactionEventDto`:

```json
{ "conversationId": 1, "messageId": 50, "emoji": "👍", "userId": 10, "action": "ADDED", "count": 3 }
```

- `action` ∈ `ADDED | REMOVED`.
- `count` is the **total after** the change → clients assign it directly (drift-free).
- Emitted only on an actual state change; idempotent retries do not re-broadcast.

Subscription authorization is handled by the existing `WebSocketAuthInterceptor`: any destination
starting with `/topic/conversation/` is authorized by resolving the **first** segment as the
conversation id and calling `chatAuthorizationService.canAccessConversation(...)`. This already
covered `/receipts`; the new `/reactions` sub-topic is authorized by the same code path (covered by
a new unit test).

> New/edit/delete messages continue to broadcast on `/topic/conversation/{id}` (unchanged). The
> separate topic is what prevents a reaction event from being misread by the message reconciler.

---

## 6. Server-side validation / authorization / errors

`ChatReactionService` (transactional):

1. `sanitizeEmoji` — trims; rejects `null`/blank, `> 32` chars, and newline/tab characters
   (`IllegalArgumentException` → HTTP 400). Rendering is text-bound in Angular, so no HTML is
   interpreted; storage is length-bounded to the column.
2. `requireReactableMessage`:
   - message must exist (`IllegalArgumentException` → 404/400);
   - message must belong to a conversation (`chat_message.conversation_id` non-null);
   - `chatAuthorizationService.assertCanAccessConversation(userId, conversation)` →
     `AccessDeniedException` → HTTP 403 for non-participants / unauthorized branch or department;
   - reacting to a **soft-deleted** message is rejected (`IllegalArgumentException`).
3. Idempotency: an existing `(message,user,emoji)` add is a no-op; a missing row on remove is a
   no-op. Neither broadcasts.
4. The caller id is **always** `@AuthenticationPrincipal Long userId` — the client can never spoof a
   reactor id (BOLA/IDOR protection).

Logging: `ChatReactionService` logs add/remove at DEBUG with `messageId`/`userId`; denials are
logged by `ChatAuthorizationService` (WARN) as in Phase 0.

---

## 7. Frontend changes

### `chat.service.ts`
- `addReaction(messageId, emoji)`, `removeReaction(messageId, emoji)`, `getReactions(messageId)`.

### `chat-widget.component.{ts,html,scss}`
- `ChatMessageDto.reactions` is rendered as chips (`emoji + count`) under each message; the chip the
  viewer reacted with is highlighted (`reactedByMe`).
- A quick-reactor (`fa-smile`) button on each message toggles a 6-emoji palette
  (`👍 ❤️ 😂 🎉 👀 ✅`). Clicking a chip toggles the viewer's reaction.
- Subscribes to `/topic/conversation/{id}/reactions`; `applyReactionEvent` patches the in-memory
  message using the authoritative `count` (never increments blindly). Unsubscribes on
  conversation switch/close.
- Tooltip lists reactor names resolved via the slim user directory.

### `branch-chat.component.{ts,html,scss}`
- Same chips, quick-reactor and sub-topic subscription for the full-page branch hub.

> Both surfaces only render reactions for non-deleted messages. Deleted tombstones show no chips
> (the server would reject a reaction anyway).

---

## 8. Tests

Backend `ChatReactionServiceTest` (10 tests):

- add creates a row + broadcasts `ADDED` with correct count and `reactedByMe`;
- add is idempotent (no save, no broadcast when the row exists);
- remove deletes + broadcasts `REMOVED` with the post-change count;
- remove is idempotent when absent;
- reacting to a deleted message is rejected;
- reacting to a legacy message (no conversation) is rejected;
- conversation access is enforced (`AccessDeniedException`);
- blank emoji rejected; over-long emoji rejected;
- `summarize` groups by emoji, sets `count`, `userIds` and `reactedByMe`.

`WebSocketAuthInterceptorTest` (+1 test, 8 total):

- `/topic/conversation/{id}`, `/receipts` and `/reactions` allowed for members; denied for
  non-members (first-segment resolution).

`ChatMessageServiceTest` updated to inject the new `ChatMessageReactionRepository` mock; all
existing assertions still pass, confirming reactions are additive to the message DTO.

Result: **69 tests, 0 failures, BUILD SUCCESS** (`mvnw -pl nexmemo-web -am test`).
Frontend `ng build` succeeds; Liquibase changelog parses (77 changesets, last `20260614-1`).

### Manual test steps

1. Start backend + frontend; open the chat widget, open any conversation.
2. Hover a message → click the smiley → pick `👍`. The chip appears with count 1 (highlighted).
3. In a second browser (another participant), open the same conversation → the `👍` chip appears
   live; click it → count increments to 2 and the first browser updates live.
4. Click again to remove → count decrements; at 0 the chip disappears.
5. Try to react to a **deleted** message (delete it first) → no chip / server rejects.
6. Try `POST /api/v1/chat/messages/{id}/reactions` for a message in a conversation the token cannot
   access → HTTP 403.
7. Try `SUBSCRIBE /topic/conversation/{unauthorizedId}/reactions` → connection refuses (WARN log).
8. Reload → reaction chips persist (loaded with message history).

---

## 9. Deployment notes

- Run the Liquibase migration (creates `chat_message_reaction`) before deploying the new backend.
- No config, env var, or data backfill required; fully backward compatible.
- Rolling upgrade is safe: the old backend ignores the new table; the new frontend degrades
  gracefully if the reaction endpoints are not yet live (chip simply does not appear).

---

## 10. Files touched

**New**
- `nexmemo-core/.../entity/ChatMessageReaction.java`
- `nexmemo-security/.../repository/ChatMessageReactionRepository.java`
- `nexmemo-core/.../dto/ChatReactionSummaryDto.java`
- `nexmemo-core/.../dto/ReactionRequest.java`
- `nexmemo-core/.../dto/ChatReactionEventDto.java`
- `nexmemo-web/.../service/ChatReactionService.java`
- `nexmemo-web/src/test/.../service/ChatReactionServiceTest.java`

**Modified**
- `nexmemo-core/.../db/changelog/db.changelog-master.xml` (changeset `20260614-1`)
- `nexmemo-core/.../dto/ChatMessageDto.java` (`reactions` field)
- `nexmemo-web/.../service/ChatMessageService.java` (bulk-load reactions; map into DTO; broadcast signature)
- `nexmemo-web/.../Controller/ChatConversationController.java` (reaction endpoints)
- `nexmemo-web/src/test/.../service/ChatMessageServiceTest.java` (new mock)
- `nexmemo-web/src/test/.../config/WebSocketAuthInterceptorTest.java` (sub-topic test)
- `admin-front/.../api-service/chat.service.ts` (reaction API)
- `admin-front/.../widgets/chat-widget/chat-widget.component.{ts,html,scss}`
- `admin-front/.../pages/branch-chat/branch-chat.component.{ts,html,scss}`
