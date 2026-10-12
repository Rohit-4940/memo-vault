# Phase 0 — Chat Security Hardening (Implemented)

Status: **Complete — backend compiles, 25 new unit tests pass.**
Scope: harden the *existing* chat module only. No new chat features, no schema changes,
no change to existing non-chat behaviour.

---

## 1. Purpose

The legacy chat module had multiple exploitable access-control flaws. Because chat will become
an enterprise banking collaboration surface (memo discussions, branch channels, DMs), it had to
be secured **before** any new feature is added. Phase 0 closes every server-side hole that was
identified in the Phase 1 analysis and is a prerequisite for Phases 1–4.

### Vulnerabilities fixed

| # | Issue | Before | After |
|---|-------|--------|-------|
| 1 | **BOLA/IDOR** on `GET /chat/memo/{memoId}` | Any authenticated user could read any memo's discussion | Caller must be admin, creator, current approver, CC recipient, or stage assignee |
| 2 | **BOLA/IDOR** on `GET /chat/branch/{id}` and `/paginated` | Any user could read any branch channel | Caller must belong to the branch or be admin |
| 3 | **BOLA/IDOR** on `GET /chat/conversation/{otherId}` | Target not validated | Peer must exist and differ from caller |
| 4 | **Trusting client-supplied targets** in `POST /chat/send` | Any `receiverId`/`memoId`/`branchId` accepted | Target authorized from the JWT principal + DB |
| 5 | **PII leak** `GET /chat/admin/users` | Returned the full `User` entity (email, username, account fields) to *any* authenticated caller | Admin-only; returns slim `{id,name}` DTO |
| 6 | **WebSocket origin wildcard** | `.setAllowedOriginPatterns("*")` | Restricted to `nexmemo.cors.allowed-origins` |
| 7 | **No SUBSCRIBE authorization** | Any client could subscribe to `/topic/branch/{anyId}`, `/topic/notifications/{anyUserId}`, `/queue/messages/{anyUserId}` | Every SUBSCRIBE is authorized against the same resource rules; unknown destinations denied by default |
| 8 | **Unauthorized mark-as-read** | Any caller could mark any message id read (existence oracle) | Only recipient, branch member, or admin |
| 9 | **No audit trail** | Denials were silent | Denials are recorded to `audit_log` (best-effort, own transaction) |

---

## 2. UI impact

- **No visual redesign.** The chat widget and branch-chat page behave the same for authorised users.
- The chat widget now shows a friendly error and returns to the list when a non-admin opens a
  branch they are not a member of (previously it silently failed).
- Participant names resolve via the new slim `/chat/users` endpoint.
- Non-admins see only branch conversations they are authorised to open; admins retain full access.
  (Branch-list filtering in the UI is deferred to Phase 1; the backend already enforces access.)

---

## 3. Backend changes (exact files)

### New files
| File | Purpose |
|------|---------|
| `nexmemo-core/.../dto/ChatUserDto.java` | Slim `{id,name}` projection; removes the PII leak. |
| `nexmemo-web/.../service/ChatAuthorizationService.java` | Central server-side authz: `isAdmin`, `canAccessBranch`, `canAccessMemo`, `canDirectMessage`, `assertCanAcknowledgeMessage`; throws `AccessDeniedException` + audits on denial. |
| `nexmemo-web/.../service/ChatAuditService.java` | Writes `audit_log` rows in `REQUIRES_NEW` (never rolled back by the caller); best-effort. |
| `nexmemo-web/.../test/.../ChatAuthorizationServiceTest.java` | 18 unit tests. |
| `nexmemo-web/.../test/.../WebSocketAuthInterceptorTest.java` | 7 unit tests. |

### Modified files
| File | Change |
|------|--------|
| `nexmemo-web/.../Controller/ChatController.java` | Every endpoint resolves the principal and calls `ChatAuthorizationService`; `@PreAuthorize("isAuthenticated()")` at class level; message length/blank validation; type inferred from supplied resource for backward compatibility; admin endpoints stay admin-only; `/chat/users` added. |
| `nexmemo-web/.../service/ChatService.java` | `markAsRead(id, userId)` now authorizes; branch mark-as-read uses a single bulk UPDATE; type normalized defensively. |
| `nexmemo-web/.../config/WebSocketConfig.java` | Origins taken from `NexmemoCorsProperties`; user destination prefix set. |
| `nexmemo-web/.../config/WebSocketAuthInterceptor.java` | CONNECT auth retained; new SUBSCRIBE authorization with deny-by-default. |
| `nexmemo-security/.../repository/UserRepository.java` | `existsByIdAndBranches_Id`, `findByStatus`. |
| `nexmemo-security/.../repository/ChatMessageRepository.java` | Bulk `markBranchMessagesRead`, `markMessageRead`. |
| `nexmemo-core/.../repository/WorkflowStageInstanceRepository.java` | `existsByMemoIdAndAssigneeId` (memo stage-assignee check). |
| `system/pom.xml` | Added `spring-boot-starter-test` (test scope) — the repo had **zero** test infrastructure. |

### Frontend changes
| File | Change |
|------|--------|
| `admin-front/.../api-service/chat.service.ts` | `getAllUsers()` now calls `/api/v1/chat/users`. |
| `admin-front/.../widgets/chat-widget/chat-widget.component.ts` | Graceful 403 handling when opening an unauthorised branch. |

---

## 4. Authorization rules

```
isAdmin(user)                 := role.roleType ∈ {ADMIN, SUPER_ADMIN}
canAccessBranch(user, branch) := isAdmin OR user ∈ branch.members
canAccessMemo(user, memo)     := memo not deleted AND ( isAdmin
                                 OR user == memo.createdBy
                                 OR user == memo.currentApprover
                                 OR user is a CC recipient
                                 OR user is assigned a workflow stage of memo )
canDirectMessage(user, other) := other exists AND other != user
canAcknowledge(user, msg)     := user == msg.receiverId
                                 OR (msg.branchId != null AND canAccessBranch)
                                 OR isAdmin
```

WebSocket SUBSCRIBE:
```
/user/**                      := allowed (session-private, set by principal)
/topic/branch/{id}            := canAccessBranch(principal, id)
/topic/notifications/{id}     := id == principal
/queue/messages/{id}          := id == principal
anything else                 := DENIED
```

---

## 5. REST endpoints (post-Phase 0)

| Method | Path | Authz |
|--------|------|-------|
| POST | `/chat/send` | resource-based per conversation type |
| GET | `/chat/memo/{memoId}` | `canAccessMemo` |
| GET | `/chat/branch/{branchId}` | `canAccessBranch` |
| GET | `/chat/branch/{branchId}/paginated` | `canAccessBranch` |
| GET | `/chat/conversation/{otherId}` | `canDirectMessage` |
| GET | `/chat/unread-count` | self |
| GET | `/chat/users` | authenticated; slim DTO, ACTIVE users |
| GET | `/chat/admin/all-branch-messages` | ADMIN/SUPER_ADMIN |
| GET | `/chat/admin/active-branches` | ADMIN/SUPER_ADMIN |
| GET | `/chat/admin/users` | ADMIN/SUPER_ADMIN; slim DTO |
| PUT | `/chat/branch/{branchId}/mark-read` | `canAccessBranch` |
| PUT | `/chat/{id}/read` | recipient / branch member / admin |

`AccessDeniedException` → HTTP 403 via `GlobalExceptionHandler` (message: "Access denied").

---

## 6. Tests

`mvn -pl nexmemo-web -am test` → **Tests run: 25, Failures: 0, Errors: 0**.

Covers: admin bypass, branch membership, memo creator/approver/CC/stage-assignee/unrelated/deleted,
self-DM prevention, denial auditing, message acknowledgement, and all SUBSCRIBE destinations
(allowed, cross-user, cross-branch, malformed, unauthenticated, unknown).

---

## 7. Backward compatibility & deployment

- **No DB migration** in Phase 0 (reuses existing `chat_message` and `audit_log`).
- Endpoints keep their paths and response envelope; only authorization and DTO shape changed.
- `GET /chat/admin/users` response shape changed from full `User` to `{id,name}`. Only the chat
  widget/branch-chat used it; both were migrated to `/chat/users`.
- **Operational requirement:** non-admin users must be assigned to at least one branch
  (`user_branches`) to use branch chat. Admin/super-admin accounts are unaffected.
- **Config requirement:** `nexmemo.cors.allowed-origins` must include every legitimate frontend
  origin; the WebSocket handshake now rejects anything else.
- No new runtime dependency; test scope only.

---

## 8. Manual test steps

1. Log in as a normal branch user; open the chat widget → only conversations are reachable;
   opening a foreign branch shows the access error and returns to the list.
2. As that user, `GET /api/v1/chat/branch/{foreignBranchId}` → **403**.
3. `GET /api/v1/chat/memo/{memoNotInvolvedIn}` → **403**.
4. `GET /api/v1/chat/admin/users` as a normal user → **403**; as admin → slim list.
5. Open two browser sessions (two users). In session A's devtools, attempt to STOMP-subscribe to
   `/topic/notifications/{B}` and `/queue/messages/{B}` → subscription is dropped (no frames).
6. Attempt a WebSocket handshake from a disallowed origin → rejected.
7. Send a branch message; verify the recipient/admin still receives it in real time.
8. Mark a message read as the recipient → succeeds; as an unrelated user → 403.
9. Check `audit_log` for `CHAT_ACCESS_DENIED` rows after the denied attempts.

---

## 9. Deferred to later phases (tracked)

- UI branch-list filtering for non-admins (Phase 1).
- Migrate DM/notification delivery to `/user/queue/**` and update the Angular WS client (Phase 1).
- Per-recipient read receipts / unread counts (current `isRead` is a single flag) (Phase 1).
- Attachments, reactions, threads, mentions, pins, edit history, moderation, retention (Phases 1–3).
