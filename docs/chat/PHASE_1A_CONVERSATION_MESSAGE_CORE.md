# Phase 1a — Conversation & Message Core (Implemented)

Status: **Complete (backend) — compiles, 47 unit tests pass.**
Scope: introduce the enterprise conversation/message model **behind** the existing chat UI.
Legacy endpoints remain functional, so nothing breaks before the Angular migration (Phase 1b).

---

## 1. Purpose

The legacy chat was message-only: a flat `chat_message` row keyed by `memoId`/`branchId`/
`receiverId`, with a single global `is_read` flag. That model cannot support groups, per-user
unread counts, threads, mentions, or receipts. Phase 1a replaces it with a first-class
**conversation + participant** model while preserving every legacy endpoint.

Decisions applied (from the approved Q&A):
- **Department channels are real now** — `user_departments` link added, `DEPARTMENT` type supported.
- **Group creation is admin-only** (`assertCanCreateGroup`).
- (Edit/delete policy + Redis are exercised in later sub-phases.)

---

## 2. Data model (new)

```
chat_conversation ──1:N── chat_participant ──N:1── users
        │                        │
        │                        └── last_read_message_id  → unread
        └──1:N── chat_message ──1:N── chat_message_receipt

users ──N:M── department        (via user_departments)
```

### `chat_conversation`
| column | notes |
|---|---|
| conversation_type | DIRECT, GROUP, BRANCH, DEPARTMENT, MEMO |
| title | group name |
| branch_id / department_id / memo_id | resource pointer for typed conversations |
| dedupe_key | `DIRECT:min:max`, `BRANCH:{id}`, `DEPT:{id}`, `MEMO:{id}` → idempotent get-or-create |
| is_archived / is_locked | lifecycle flags |
| last_message_id / last_message_at | list ordering + preview |

Unique filtered index `uq_chat_conv_dedupe (dedupe_key) WHERE dedupe_key IS NOT NULL` (SQL Server)
gives race-safe get-or-create without breaking GROUP rows (NULL dedupe_key).

### `chat_participant`
Membership for DIRECT/GROUP; **lazily created on first join** for BRANCH/DEPARTMENT/MEMO so
unread tracking is uniform. Holds `participant_role` (OWNER/MODERATOR/MEMBER),
`notification_level` (ALL/MENTIONS/MUTE) and `last_read_message_id`.

### `chat_message` (extended)
Added: `conversation_id`, `client_message_id`, `message_type`, `parent_message_id`, `reply_count`,
`is_edited`, `edited_at`, `is_deleted`, `deleted_by_id`, `deleted_reason`.
Legacy columns (`receiver_id`, `memo_id`, `branch_id`, `is_read`) are retained for backward
compatibility. Idempotency index: `(sender_id, client_message_id) WHERE client_message_id IS NOT NULL`.

### `chat_message_receipt`
Per-recipient `delivered_at` / `read_at` (replaces the global flag for group correctness).

### `user_departments`
`(user_id, department_id)` join table enabling DEPARTMENT channels.

---

## 3. Authorization matrix (server-side)

| Conversation | Read / send | Manage (rename/add/remove) |
|---|---|---|
| DIRECT | active participant only (admins **not** auto-granted) | OWNER/MODERATOR or admin |
| GROUP | active participant only | OWNER/MODERATOR or admin |
| BRANCH | member of branch or admin | admin |
| DEPARTMENT | member of department or admin | admin |
| MEMO | memo creator/approver/CC/stage-assignee or admin | admin |

Every denial is audited (`CHAT_ACCESS_DENIED`) and returns HTTP 403.

---

## 4. REST endpoints (new)

| Method | Path | Purpose |
|---|---|---|
| POST | `/chat/conversations` | create/get-or-create (type in body) |
| POST | `/chat/conversations/memo/{memoId}` | get-or-create memo discussion |
| GET | `/chat/conversations` | my conversations w/ unread + last preview |
| GET | `/chat/conversations/{id}` | conversation detail |
| POST | `/chat/conversations/{id}/participants` | add member (owner/mod/admin) |
| DELETE | `/chat/conversations/{id}/participants/{userId}` | remove/leave |
| POST | `/chat/conversations/{id}/messages` | send (idempotent via `clientMessageId`) |
| GET | `/chat/conversations/{id}/messages?beforeId=&size=` | cursor pagination (newest first) |
| POST | `/chat/conversations/{id}/read` | mark read up to `lastReadMessageId` |
| GET | `/chat/messages/{messageId}` | single message (access-checked) |

The legacy endpoints from Phase 0 (`/chat/send`, `/chat/branch/{id}`, `/chat/memo/{id}`, …) are
unchanged and still authoritative for the current UI.

## 5. Realtime events (new)

| Destination | Payload | Authz |
|---|---|---|
| `/topic/conversation/{conversationId}` | saved `ChatMessage` | conversation membership |
| `/user/{userId}/queue/messages` | saved `ChatMessage` | self only |
| `/topic/branch/{branchId}` | saved `ChatMessage` (BRANCH only) | branch membership (legacy compat) |

`WebSocketAuthInterceptor` now authorizes `/topic/conversation/**` in addition to Phase 0 rules;
unknown destinations stay deny-by-default.

---

## 6. Files

### New — core
- `nexmemo-core/.../enums/ChatConversationType.java`
- `nexmemo-core/.../enums/ChatParticipantRole.java`
- `nexmemo-core/.../enums/ChatNotificationLevel.java`
- `nexmemo-core/.../enums/ChatMessageType.java`
- `nexmemo-core/.../entity/ChatConversation.java`
- `nexmemo-core/.../entity/ChatParticipant.java`
- `nexmemo-core/.../entity/ChatMessageReceipt.java`
- `nexmemo-core/.../dto/CreateConversationRequest.java`
- `nexmemo-core/.../dto/SendMessageRequest.java`
- `nexmemo-core/.../dto/ChatConversationSummaryDto.java`

### New — security/web
- `nexmemo-security/.../repository/ChatConversationRepository.java`
- `nexmemo-security/.../repository/ChatParticipantRepository.java`
- `nexmemo-security/.../repository/ChatMessageReceiptRepository.java`
- `nexmemo-web/.../service/ChatConversationService.java`
- `nexmemo-web/.../service/ChatMessageService.java`
- `nexmemo-web/.../Controller/ChatConversationController.java`

### Modified
- `nexmemo-core/.../entity/ChatMessage.java` (new columns)
- `nexmemo-core/.../entity/User.java` (`departments` ManyToMany)
- `nexmemo-core/.../resources/db/changelog/db.changelog-master.xml` (changesets `20260612-1..7`)
- `nexmemo-security/.../repository/ChatMessageRepository.java` (conversation queries)
- `nexmemo-security/.../repository/UserRepository.java` (`existsByIdAndDepartments_Id`)
- `nexmemo-web/.../service/ChatAuthorizationService.java` (department + conversation checks)
- `nexmemo-web/.../config/WebSocketAuthInterceptor.java` (`/topic/conversation/**`)

### Tests (new)
- `ChatConversationServiceTest` (8), `ChatMessageServiceTest` (6), extended
  `ChatAuthorizationServiceTest` (26 total), `WebSocketAuthInterceptorTest` (7).
- Command: `mvn -pl nexmemo-web -am test` → **Tests run: 47, Failures: 0, Errors: 0**.

---

## 7. Backward compatibility & deployment

- **DB migration required**: 7 new Liquibase changesets (guarded with preconditions; safe to re-run).
  `ddl-auto: validate` — entities were written to match the migration exactly.
- No legacy endpoint removed or changed in shape; existing widget keeps working.
- New tables are additive; existing `chat_message` rows get `conversation_id = NULL` and are
  untouched (they remain readable via the legacy endpoints).
- Filtered unique indexes (`uq_chat_conv_dedupe`, `uq_chat_msg_client_id`) are **SQL Server**
  (`dbms="mssql"`); on other dialects the app falls back to application-level checks.

### Manual test steps
1. Start backend → Liquibase applies `20260612-*`; app starts (validate passes).
2. Admin creates a GROUP via `POST /chat/conversations` `{conversationType:"GROUP",title:"Ops",memberIds:[9]}` → 200.
3. Non-admin same call → **403**.
4. `POST /chat/conversations` `{conversationType:"DIRECT",otherUserId:9}` twice → same conversation id (idempotent).
5. `POST /chat/conversations/{id}/messages` with a `clientMessageId`, repeat → same message id, one row.
6. `GET /chat/conversations` → conversation with `unreadCount` and `lastMessagePreview`.
7. STOMP subscribe to `/topic/conversation/{id}` as a non-member → dropped; as member → receives sends.
8. `GET /chat/conversations/{foreignId}` for a DIRECT you're not in → **403** + `audit_log` row.

---

## 8. Deferred to next sub-phases (tracked)

- **Phase 1b** — wire the Angular widget/branch-chat onto `/chat/conversations` + `/topic/conversation/**`
  and `/user/queue/messages`; conversation list UI; per-conversation unread badges.
- **Phase 1c** — receipts endpoints (`delivered`/`read` fan-out), message edit/delete with edit-history
  table (policy: edit ≤15 min, soft-delete author+admin).
- **Phase 2** — threads UI, reactions, @mentions, forwarding/pinning, search/bookmarks.
- Redis-backed presence/typing and multi-instance fan-out (decision: Redis available everywhere).
