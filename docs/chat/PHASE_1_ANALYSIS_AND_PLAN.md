# Enterprise Chat Module — Phase 1 Analysis & Implementation Plan
NexMemo Memo Management System — Banking Edition

> Status: AWAITING APPROVAL. No production code written yet.
> This document is based only on verified files in the repository.

---

## 1. Architecture Findings (verified)

### 1.1 Repository layout
```
memo-vault/
├── admin-front/                     Angular 18.2 standalone SPA (SSR-capable)
└── system/system/                   Maven multi-module Spring Boot 3.2.4 (Java 17)
    ├── nexmemo-core                 Entities, DTOs, Enums, Repositories, Liquibase changelog
    ├── nexmemo-security             SecurityConfig, JWT, filters, auth services + repos
    ├── nexmemo-service              OCR (Tesseract), VirusScan (ClamAV)
    ├── nexmemo-web                  Controllers, Services, WebSocket, Storage, patch runner
    └── nexmemo-audit                (module present, no source files found)
```

### 1.2 Runtime configuration (verified)
- Server port `8086`, servlet context path **`/api/v1`**.
- DB: **Microsoft SQL Server**; `spring.jpa.hibernate.ddl-auto: validate` — **Liquibase owns the schema**.
- Master changelog: `nexmemo-core/src/main/resources/db/changelog/db.changelog-master.xml`.
- Legacy seed/patch runner: `nexmemo-web/.../InitialPatch.java` runs `patch_ms_sql/general_patch/*.sql` on startup (seed data only, no `CREATE TABLE` found there).
- Profiles: `dev` and `prod` (`application.yaml` + `application.dev.yaml`/`.prod.yaml`).
- Redis is configured/optional; used for token blacklist + distributed rate limiting. Prod `rate-limit.redis-enabled: true`.
- WebSocket: `spring-boot-starter-websocket` present; STOMP + SockJS at `/api/v1/ws`, simple broker (`/topic`, `/queue`), app prefix `/app`.
- CORS allowed origins from config (dev: localhost:3000/4200/4202/8080).
- Elasticsearch: `MemoSearchRepository` exists (memo full-text search).

### 1.3 Authentication & authorization (verified)
- **JWT bearer only** (`Authorization: Bearer`). `JwtAuthenticationFilter` parses the token and sets `Authentication.principal = userId (Long)`, authority `ROLE_<roleName.upper()>`.
- Token claims: `sub`=userId, `username`, `role`, `type` (`ACCESS`/`REFRESH`), `jti`, `token_family`. Access 15 min / refresh 7 days (prod).
- `@EnableMethodSecurity(prePostEnabled = true)` → `@PreAuthorize` works.
- HTTP rules in `SecurityConfig`: `/admin/**` and `/users/**` require `SUPER_ADMIN|ADMIN`; everything else `authenticated()`.
- **Resource-level (row-level) authorization is largely absent.** e.g. `MemoService.getMemoById(id)` returns any memo with no ownership/branch/CC check. Only two methods compare `memo.getCreatedBy().equals(userId)`.
- Roles are **dynamic DB rows** (`Role.roleName`), not a fixed enum. `RoleType` = MAKER/APPROVAL/COMMITTEE/ADMIN/USER/SUPER_ADMIN. `RoleAccess` = SPECIFIC/OWN/ALL.

### 1.4 Users / employees / org model (verified)
- The "employee" is the **`User`** entity (`users` table). No separate `Employee` entity exists on the backend (frontend has an `employee.ts` model only).
- `User.role` (ManyToOne, EAGER), `User.branches` (ManyToMany via `user_branches`).
- **No department membership on `User`.** `department` exists only as a field on `Memo`.
- BM / RM / PM are not hard-coded; they are role names in the `role` table. This matters for memo-chat participant resolution.

### 1.5 Memo & workflow (verified)
- `Memo` entity: `memoNo`, `title`, `status` (`MemoStatus` DRAFT→PENDING_APPROVAL→LEVEL_x_APPROVED→APPROVED/REJECTED/CANCELLED/REVISION_REQUESTED), `priority`, `memoType`, `department`, `branch`, `amount`, `createdBy` (audit `createdBy` + `createdByUser`), `currentApprover`, `approvalLevel`, `totalApprovalLevels`, `ccRecipients` (`MemoCc`), `documents` (`MemoDocument`), `approvalHistory`, routing (`MemoRouting`), workflow stages (`WorkflowStageInstance`), `isDeleted`.
- `MemoController` is `@PreAuthorize("isAuthenticated()")` at class level; per-operation checks are thin.

### 1.6 Notifications (verified)
- `notification` table + `Notification` entity + `NotificationService.createNotification(userId, title, message, type, referenceId, referenceType)`.
- Also `notification_config` and `notification_log` + `NotificationEventType`, `NotificationChannel` enums (richer event model exists but is used by memo workflow, not chat).
- Real-time notification pushes already go to `/topic/notifications/{userId}`.

### 1.7 Document storage & scanning (verified)
- `StorageService` abstraction: `LocalStorageService` and `MinioStorageService`; config `nexmemo.storage.*`.
- `VirusScanService` (ClamAV) exists — reusable for chat attachments.
- `FileController` present for file operations.

### 1.8 Auditing (verified)
- `AuditLog` entity + `AuditLogService` + `AuditLogController`; `createdBy`/`modifiedBy` autofilled via `AuditingEntityListener`.
- This is the natural sink for chat administrative/security audit events.

### 1.9 Existing chat implementation (partial — verified)
Backend:
- `ChatMessage` entity: `id, senderId, receiverId, memoId, branchId, conversationType, message, isRead, createdDate`.
- `ChatMessageRepository` (findConversation, countUnread, branch queries, `JpaSpecificationExecutor`).
- `ChatService`, `ChatController` (`/chat/...`).
- `WebSocketConfig` + `WebSocketAuthInterceptor` (CONNECT-time JWT auth only).

Frontend:
- `shared-services/api-service/chat.service.ts`, `shared-services/websocket.service.ts`.
- `components/pages/branch-chat/branch-chat.component.ts` (full-page branch chat).
- `components/widgets/chat-widget/chat-widget.component.ts` (floating widget, mounted in `layout.component.html`).
- Route `branch-chat` with `menuGuard`.

### 1.10 Critical gaps / risks in the current chat (must fix before extending)
| # | Risk | Evidence |
|---|------|----------|
| 1 | **IDOR/BOLA on reads** — `GET /chat/memo/{id}`, `/chat/branch/{id}`, `/chat/conversation/{otherId}` return any data to any authenticated user | `ChatController` methods have no ownership check |
| 2 | **Spoofable arbiter fields** — `POST /chat/send` trusts client `receiverId/memoId/branchId` with no validation | `ChatService.sendMessage` |
| 3 | **WebSocket eavesdropping** — server publishes DMs to `/queue/messages/{receiverId}`; with the simple broker any authenticated client can `SUBSCRIBE` to any `/queue/...` or `/topic/...` | `ChatService` lines 43–51, `WebSocketConfig` |
| 4 | **`GET /chat/admin/users` returns all users to any authenticated caller** (PII/email leakage) | `ChatController` line 94 (no `@PreAuthorize`) |
| 5 | **No CONNECT-origin restriction** — `.setAllowedOriginPatterns("*")` | `WebSocketConfig` |
| 6 | **Global `isRead` flag** — not per-recipient; unread counts are wrong for group/branch chats | `ChatMessage.isRead`, `countUnreadBranchMessages` |
| 7 | **No idempotency / no client message id** → duplicates on retry | `ChatMessage` has no client id |
| 8 | **No edit/delete/react/mention/thread/attachment/pin/bookmark/typing/presence** | entity has only `message` |
| 9 | **No authorization on WS SUBSCRIBE** (only CONNECT) | `WebSocketAuthInterceptor` |
| 10 | **No chat audit, no retention/legal hold, no moderation** | none found |
| 11 | **No tests** — zero `*Test.java`, only 3 trivial Angular specs | repo scan |

### 1.11 How chat should integrate (design principle)
Chat must be **another domain on the existing platform**, not a parallel app:
- Reuse `User`/`Role`/`Branch`, `Notification`/`NotificationService`, `AuditLog`, `StorageService`/`VirusScanService`, the existing JWT + `@PreAuthorize` mechanism, and the existing STOMP endpoint.
- Keep **official memo approval state in `Memo`/workflow**; chat only *references* memos read-only and reacts to status changes.
- Enforce **resource-based authorization on the backend for every chat operation** (read, write, subscribe, download).

---

## 2. Prioritized Functionality Checklist

Legend: P0 = secure working MVP, P1 = enterprise, P2 = advanced, P3 = optional AI.

| ID | Feature | Prio | Depends on | Risk | Complexity |
|----|---------|------|-----------|------|-----------|
| C-01 | Fix BOLA/IDOR + server-side participant checks on all chat reads/writes | P0 | — | High (security) | M |
| C-02 | Conversation model (DIRECT/GROUP/BRANCH/DEPARTMENT/MEMO) + participants | P0 | C-01 | M | L |
| C-03 | Message model extension: conversation_id, client_message_id (idempotency), type | P0 | C-02 | M | M |
| C-04 | Per-recipient read/delivery receipts + accurate unread counts | P0 | C-03 | M | M |
| C-05 | Authenticated STOMP SUBSCRIBE authorization (user-destination queues) | P0 | C-01 | High | M |
| C-06 | Pagination + lazy history (`before id` cursor) for all conversations | P0 | C-03 | L | S |
| C-07 | Memo-linked discussion with memo access control | P0 | C-02,C-03 | High | M |
| C-08 | In-app notifications + realtime badge for new chat/mention | P0 | C-04 | L | S |
| C-09 | Attachment upload/share with type+size validation, virus scan, authorized download | P1 | C-03, storage | High | L |
| C-10 | @mentions + employee tagging | P1 | C-03 | M | M |
| C-11 | Emoji reactions | P1 | C-03 | L | S |
| C-12 | Message editing + edit history | P1 | C-03 | M | S |
| C-13 | Message deletion policy (soft delete / retention) | P1 | C-03 | M | S |
| C-14 | Threads / replies (`parent_message_id`) | P1 | C-03 | M | M |
| C-15 | Pin / bookmark / mark-unread | P1 | C-03 | L | S |
| C-16 | Typing indicators + presence | P1 | C-05 | M | M |
| C-17 | Forwarding | P1 | C-02,C-03 | L | S |
| C-18 | Search messages/conversations with authorization scoping | P1 | C-03 | High | L |
| C-19 | Per-conversation notification settings (all/mentions/mute) | P1 | C-04 | L | S |
| C-20 | Email/push channel fan-out via `notification_log` | P2 | C-08 | M | M |
| C-21 | Admin: membership & moderation/reporting | P2 | C-02 | M | M |
| C-22 | Retention + legal hold | P2 | C-13 | High | M |
| C-23 | Transactional outbox for reliable external delivery | P2 | C-20 | M | M |
| C-24 | Activity timeline / conversation analytics | P2 | C-03 | L | M |
| C-25 | Message delivery-failure monitoring + health metrics | P2 | C-23 | M | M |
| C-26 | AI: conversation/memo-thread summaries, action items, reply suggestions | P3 | C-03, config | High (data-leak) | L |

---

## 3. Proposed Database Design (SQL Server / Liquibase)

New changeSets appended to `db.changelog-master.xml` (all guarded with `preConditions onFail="MARK_RAN"`). Existing `chat_message` is **extended, not dropped**, for backward compatibility.

### 3.1 `chat_conversation`
| column | type | notes |
|---|---|---|
| id | BIGINT PK identity | |
| version | INT | optimistic lock |
| conversation_type | VARCHAR(30) NOT NULL | DIRECT, GROUP, DEPARTMENT, BRANCH, MEMO |
| title | VARCHAR(255) | group name; null for direct |
| branch_id | BIGINT | FK branch (BRANCH type) |
| department_id | BIGINT | FK department (DEPARTMENT type) |
| memo_id | BIGINT | FK memo (MEMO type) |
| created_by_id | BIGINT | |
| is_archived | BIT default 0 | |
| is_locked | BIT default 0 | moderation |
| last_message_id | BIGINT | denormalized for list ordering |
| last_message_at | DATETIME | |
| retention_policy_id | BIGINT | optional FK |
| created_by_id / modified_by_id / created_date / modified_date | audit | |

Indexes: `(conversation_type, memo_id)`, `(branch_id)`, `(last_message_at DESC)`.

### 3.2 `chat_participant`
| column | type | notes |
|---|---|---|
| id | BIGINT PK | |
| conversation_id | BIGINT NOT NULL | FK |
| user_id | BIGINT NOT NULL | FK users |
| participant_role | VARCHAR(20) | OWNER, MODERATOR, MEMBER |
| notification_level | VARCHAR(20) default ALL | ALL, MENTIONS, MUTE |
| last_read_message_id | BIGINT | drives unread counts |
| last_read_at | DATETIME | |
| is_active | BIT default 1 | |
| joined_at / left_at | DATETIME | |
Constraints: `UNIQUE(conversation_id, user_id)`. Index `(user_id, is_active)`.

### 3.3 `chat_message` (ALTER existing + new columns)
Add: `conversation_id BIGINT` (nullable initially for legacy rows), `parent_message_id BIGINT` (threads), `client_message_id VARCHAR(64)`, `message_type VARCHAR(20)` default TEXT (TEXT, SYSTEM, MEMO_REF, FILE), `is_edited BIT`, `edited_at DATETIME`, `is_deleted BIT`, `deleted_by_id BIGINT`, `deleted_reason VARCHAR(255)`, `forwarded_from_message_id BIGINT`, `reply_count INT`, `sentiment VARCHAR(20)` (future AI).
- `UNIQUE(client_message_id, sender_id)` filtered/where-not-null to enforce **idempotency**.
- Index `(conversation_id, id DESC)`, `(parent_message_id)`, `(sender_id)`.

### 3.4 `chat_message_edit_history`
`id, message_id FK, previous_content TEXT, edited_by_id, edited_at`.

### 3.5 `chat_message_reaction`
`id, message_id FK, user_id FK, emoji VARCHAR(32), created_date`. `UNIQUE(message_id, user_id, emoji)`.

### 3.6 `chat_message_mention`
`id, message_id FK, mentioned_user_id FK, is_notified BIT, created_date`. Index `(mentioned_user_id, is_notified)`.

### 3.7 `chat_message_receipt` (per-recipient delivery/read)
`id, message_id FK, user_id FK, delivered_at, read_at`. `UNIQUE(message_id, user_id)`. Index `(user_id, read_at)`.
This replaces the single global `isRead` for correctness in group/branch conversations.

### 3.8 `chat_attachment`
`id, message_id FK, file_name, storage_key VARCHAR(500), file_type VARCHAR(100), file_size BIGINT, checksum VARCHAR(128), scan_status VARCHAR(20)` (PENDING/CLEAN/INFECTED/FAILED), `uploaded_by_id, created_date`.
Downloads are always streamed through an **authorized controller**, never a public URL.

### 3.9 `chat_pinned_message` / `chat_bookmark`
- `chat_pinned_message(id, conversation_id, message_id, pinned_by_id, pinned_at)` UNIQUE(conversation_id, message_id).
- `chat_bookmark(id, user_id, message_id, conversation_id, created_date)` UNIQUE(user_id, message_id).

### 3.10 `chat_report` (moderation)
`id, message_id, conversation_id, reported_by_id, reason VARCHAR(500), status` (OPEN/REVIEWED/ACTIONED/DISMISSED), `reviewed_by_id, action_taken VARCHAR(255), created_date, reviewed_date`.

### 3.11 `chat_retention_policy` + legal hold
`chat_retention_policy(id, scope` (GLOBAL/TYPE/CONVERSATION)`, scope_ref_id, retain_days INT, legal_hold BIT, applied_by_id, created_date)`.
Deletion job soft-deletes then hard-deletes only rows not under legal hold.

### 3.12 `chat_outbox` (transactional outbox)
`id, aggregate_type, aggregate_id, event_type, payload TEXT, status` (NEW/SENT/FAILED)`, retry_count INT, last_error TEXT, created_date, processed_at`. Polled by a scheduled sender → guarantee of at-least-once external delivery.

### 3.13 `chat_presence`
`user_id PK, last_seen_at DATETIME, connection_count INT`. (Or pure-Redis; DB fallback recommended.)

---

## 4. REST API & Real-time Event Specification

All REST paths are under context `/api/v1`, follow the existing `ApiResponse<T>` envelope, use `@AuthenticationPrincipal Long userId`, and are **authorized server-side** per §5.

### 4.1 Conversations
| Method | Path | Purpose | Prio |
|---|---|---|---|
| POST | `/chat/conversations` | Create DIRECT/GROUP/MEMO/DEPARTMENT conv (participants validated) | P0 |
| GET | `/chat/conversations` | List my conversations (cursor paginated, unread count, last msg) | P0 |
| GET | `/chat/conversations/{id}` | Get conversation detail (participant only) | P0 |
| PUT | `/chat/conversations/{id}` | Rename/archive group (owner/mod) | P1 |
| POST | `/chat/conversations/{id}/participants` | Add participants (authz) | P0 |
| DELETE | `/chat/conversations/{id}/participants/{userId}` | Remove/leave | P1 |
| POST | `/chat/conversations/{id}/read` | Mark read up to `lastReadMessageId` | P0 |
| PUT | `/chat/conversations/{id}/settings` | notification level / mute | P1 |
| POST | `/chat/conversations/with/{userId}` | Idempotent get-or-create DIRECT | P0 |

### 4.2 Messages
| Method | Path | Purpose | Prio |
|---|---|---|---|
| POST | `/chat/conversations/{id}/messages` | Send (body: `clientMessageId, content, messageType, parentMessageId?, mentions[], attachmentIds[]`) — **idempotent** | P0 |
| GET | `/chat/conversations/{id}/messages?beforeId=&size=` | Cursor history (participant only) | P0 |
| GET | `/chat/messages/{id}` | Single message (participant only) | P0 |
| PUT | `/chat/messages/{id}` | Edit (author only, within policy) | P1 |
| GET | `/chat/messages/{id}/history` | Edit history | P1 |
| DELETE | `/chat/messages/{id}` | Soft delete (author/mod per policy) | P1 |
| POST | `/chat/messages/{id}/reactions` | Add reaction | P1 |
| DELETE | `/chat/messages/{id}/reactions/{emoji}` | Remove reaction | P1 |
| GET | `/chat/messages/{id}/replies` | Thread replies (paginated) | P1 |
| POST | `/chat/messages/{id}/forward` | Forward to conversation(s) | P1 |
| POST | `/chat/messages/{id}/pin` / DELETE | Pin/unpin (mod) | P1 |
| POST | `/chat/messages/{id}/bookmark` / DELETE | Bookmark | P1 |
| POST | `/chat/messages/{id}/unread` | Mark conversation unread from here | P1 |

### 4.3 Memo-integrated
| Method | Path | Purpose | Prio |
|---|---|---|---|
| POST | `/chat/conversations/memo/{memoId}` | Get-or-create memo discussion (requires memo access) | P0 |
| GET | `/chat/conversations/memo/{memoId}` | Existing discussion summary | P0 |
| POST | `/chat/messages/{id}/link-memo-section` | Link message to memo/document | P2 |
| (event) | `MEMO_STATUS_CHANGED` system message | Posted by workflow on status change | P1 |

### 4.4 Attachments
| Method | Path | Purpose | Prio |
|---|---|---|---|
| POST | `/chat/attachments` | Upload (multipart) → scans → returns id (not yet attached) | P1 |
| GET | `/chat/attachments/{id}/download` | **Authorized** stream (validates conversation membership + memo permission) | P1 |
| GET | `/chat/attachments/{id}/meta` | metadata (no content) | P1 |

### 4.5 Search / presence / admin
| Method | Path | Purpose | Prio |
|---|---|---|---|
| GET | `/chat/search?q=&from=&to=&conversationType=&memoNo=&userId=` | Scoped search (only my conversations) | P1 |
| GET | `/chat/presence?userIds=` | Presence snapshot | P1 |
| GET | `/chat/admin/conversations` | Admin list (paginated) | P2 |
| GET | `/chat/admin/reports` / PUT `/chat/admin/reports/{id}` | Moderation queue + action | P2 |
| PUT | `/chat/admin/conversations/{id}/legal-hold` | Legal hold toggle | P2 |
| GET | `/chat/admin/metrics` | Delivery failures, volume, active users | P2 |

> **Backward compatibility:** keep legacy `/chat/send`, `/chat/branch/{id}`, `/chat/conversation/{otherId}` as thin adapters that (a) apply the new authorization, and (b) map to the new conversation model — then deprecate. Remove `GET /chat/admin/users` (PII leak) or restrict to ADMIN and paginate.

### 4.6 WebSocket / STOMP events
Endpoint unchanged: `/api/v1/ws` (SockJS). **Server-side authorization required on SUBSCRIBE.**

Client → server (`/app/...`):
- `/app/chat.send` (envelope with clientMessageId)
- `/app/chat.typing` `{conversationId, isTyping}`
- `/app/chat.delivered` `{messageId}`
- `/app/chat.read` `{conversationId, lastReadMessageId}`
- `/app/presence.heartbeat`

Server → client (use **user destinations**, not raw id-based topics):
- `/user/queue/messages` — new message for me
- `/user/queue/receipts` — delivery/read receipts + typing + presence of my peers
- `/user/queue/notifications` — unread/badge/mention
- `/topic/conversation/{conversationId}` — only if membership enforced at SUBSCRIBE (prefer user queue + fan-out)

Envelope: `{ eventType, conversationId, messageId, payload, serverTimestamp }`.

---

## 5. Security & Authorization Matrix

### 5.1 Role matrix (roles are dynamic; matrix expressed by capability)
| Capability | USER/BM/RM/PM | ADMIN | SUPER_ADMIN | Notes |
|---|---|---|---|---|
| Read own DIRECT/GROUP conversations | ✔ (participant) | ✔ (participant) | ✔ | participant check |
| Read BRANCH conversation | ✔ if member of that branch | ✔ all | ✔ all | `user_branches` |
| Read DEPARTMENT conversation | ✖ until user↔dept model exists | ✔ | ✔ | **gap: no user→dept link** |
| Read MEMO discussion | ✔ if creator / current approver / CC / stage assignee | ✔ if memo visible | ✔ | reuse memo visibility rule |
| Send message | participant only | participant | participant | |
| Edit/delete own message | ✔ author | ✔ | ✔ | policy window |
| Delete any message | ✖ | ✔ | ✔ | moderation |
| Pin / lock conversation | ✖ | ✔ | ✔ | |
| Create group / add members | ✔ (own groups) | ✔ | ✔ | |
| Admin reports / legal hold | ✖ | ✔ (hold: SUPER) | ✔ | audit |
| Attachment download | conversation + memo auth | ✔ | ✔ | |
| Download infected file | ✖ | ✖ | ✖ | scan_status must be CLEAN |

### 5.2 Enforcement rules (each operation)
1. **Authentication**: JWT required (HTTP filter / STOMP CONNECT).
2. **Conversation membership**: `chat_participant(userId, conversationId, is_active=1)` check in a single reusable `ChatAuthorizationService`.
3. **Conversation-typed rules**: BRANCH → `user.branches` contains branch; MEMO → memo visibility (creator ∨ currentApprover ∨ CC ∨ stage assignee ∨ admin); DEPARTMENT → dept membership (needs model — see risks).
4. **Participant management**: only OWNER/MODERATOR/ADMIN.
5. **SUBSCRIBE authorization**: `ChannelInterceptor` validates the destination against membership before allowing subscription; reject with STOMP ERROR otherwise.
6. **Object-level**: never trust `senderId` from body — always derive from principal.
7. **Download**: re-check authorization on every request; never expose storage keys.
8. **Audit**: every admin/moderation/legal-hold/delete action → `AuditLog` (or `chat_report`).

### 5.3 Threat coverage
| Threat | Control |
|---|---|
| IDOR/BOLA | `ChatAuthorizationService` on every endpoint + SUBSCRIBE |
| WebSocket eavesdrop | user-destination queues + SUBSCRIBE authz (never raw `/queue/{userId}`) |
| XSS | message stored as text; render via Angular interpolation (never `innerHTML`); sanitize any memo-ref HTML |
| Injection | JPA parameter binding; validate enums/ids |
| Unauthorized download | authorized streaming endpoint + scan status |
| Info leakage | remove/restrict `/chat/admin/users`; return only participants of common conversations |
| CSRF | existing Origin/Referer matcher already covers state-changing methods |
| Rate limiting | reuse `RateLimitFilter`; add per-user send throttle |
| At-rest/TLS | rely on DB encryption + `nexmemo.security.encryption.key`; TLS at deployment |
| Retention/legal hold | `chat_retention_policy` + scheduled job honoring `legal_hold` |

### 5.4 Known structural gap to decide
There is **no user↔department link** in the schema. DEPARTMENT channels therefore cannot be authorized reliably today. Options: (A) derive department from the user's branch, (B) add `user_departments` table, (C) defer DEPARTMENT channels to P2. Recommend **(C) defer** unless you confirm (B).

---

## 6. Phased Implementation Plan

Each phase ends with unit + integration tests and a manual test script. No phase starts before the previous is reviewed.

### Phase 0 — Security hardening of existing chat (P0) ← **first**
- Introduce `ChatAuthorizationService` + `ChatAccessDeniedException`.
- Fix BOLA on all legacy `/chat/**` reads/writes; validate memo/branch/receiver access.
- Restrict `GET /chat/admin/users`; add pagination.
- WebSocket: enforce CONNECT origin allow-list + **SUBSCRIBE authorization**; switch DMs to `/user/queue/messages`.
- Add chat audit logging for reads of restricted data and all failures.
- **Deliverable:** existing branch chat + widget are safe; no behavior regression for admins.

### Phase 1 — Conversation & message core (P0)
- `chat_conversation`, `chat_participant`, extend `chat_message`, `chat_message_receipt`.
- New conversation + message endpoints (cursor pagination, idempotency via `client_message_id`).
- Accurate unread counts; realtime via `/user/queue/*`.
- Memo discussion get-or-create with memo authorization.
- Frontend: new `chat` feature module (standalone components) reusing existing styling; migrate `branch-chat` page + widget onto it.
- Tests: authz unit tests, repository integration tests, WS SUBSCRIBE tests.

### Phase 2 — Collaboration features (P1)
- Mentions, reactions, edit + history, soft delete, threads, pin/bookmark, mark-unread, forwarding.
- Attachments with validation + ClamAV scan + authorized download.
- Typing/presence, per-conversation notification settings, scoped search.
- Admin: membership management, moderation/report queue.

### Phase 3 — Enterprise governance (P2)
- Retention policies + legal hold job; notification fan-out via `notification_log`; transactional outbox; metrics/monitoring (delivery failures, health).

### Phase 4 — AI (P3, opt-in)
- Summaries / action items / reply suggestions, gated behind an org config flag; **never** send data to an external provider without explicit approval; prefer a self-hosted/on-prem model. All AI reads go through the same `ChatAuthorizationService`.

### Sequencing & estimates
| Phase | Scope | Est. effort | Key risk |
|---|---|---|---|
| 0 | Security fix | 3–5 days | regressions in legacy consumers |
| 1 | Core conv/message | 2–3 weeks | schema migration + WS rework |
| 2 | Collaboration | 3–4 weeks | attachment security |
| 3 | Governance | 2–3 weeks | retention correctness |
| 4 | AI | 1–2 weeks | data leakage |

### Deployment / backward-compat concerns
- Liquibase changes are additive and guarded; existing `chat_message` rows keep working (legacy columns retained).
- Legacy endpoints kept as adapters then deprecated with a documented sunset.
- New WS destinations introduced alongside old ones during transition.
- Requires `client_message_id` on new clients; old clients continue via adapters.

---

## Open Decisions Needed Before Phase 0 Code
1. **Department channels**: defer (recommended) or add `user_departments`?
2. **Redis**: is it guaranteed in all environments (for presence/rate-limit/fan-out), or must we have a DB-only fallback?
3. **Group chat membership**: can any employee create groups, or only admins/BM?
4. **Message edit window** (e.g. 15 min) and **delete policy** (soft vs hard) expected by the bank?
5. **AI provider**: on-prem only, or is an approved external provider available?
6. **Retention default** in days, and who may place legal hold?
