# Phase 3 — Foundation & Security

**Status:** complete · **Date:** 2026-10-12 · **Scope:** backend (no schema migration required)

This phase closes the resource-authorization and audit gaps that existed across the memo module and
activates the previously dead role → URL permission tables. It is the first workstream of the
enterprise hardening programme and is a prerequisite for every later phase.

---

## 1. Problem statement

| # | Gap | Impact |
|---|-----|--------|
| 1 | `MemoController` was protected only by class-level `isAuthenticated()`. Every `{id}` endpoint trusted the client-supplied id with no ownership/assignment check. | **BOLA / IDOR** — any signed-in user could read any memo and, worse, drive `submit / approve / reject / return / forward / backward` on any memo id. |
| 2 | Administrative privileges implicitly granted approval authority. | Segregation-of-duties violation: a config admin could approve financial transactions. |
| 3 | `role_permission_rights` → `role_permission_api` → `url_api` were persisted but never consulted at request time. | Dead RBAC configuration; API-level authorization was effectively role-name-only. |
| 4 | Only `AuthService` and `ChatAuditService` wrote audit rows. Memo life-cycle decisions left no audit trail, and there was no before/after capture or secret redaction. | Non-repudiation gap; secrets risk leaking into audit detail. |
| 5 | Workflow decisions did a read-then-write with no lock. | Concurrent approve/reject could produce inconsistent workflow state. |
| 6 | `memo_workflow_permission` (per-memo-type can_create/can_edit/…) was stored but never enforced. | Any user could create any memo type. |

## 2. What was implemented

### 2.1 `MemoAuthorizationService` (new) — resource authorization core
`system/system/nexmemo-web/src/main/java/com/nexmemo/web/service/MemoAuthorizationService.java`

Central, server-side authorization mirroring the existing `ChatAuthorizationService` pattern.
Resolves the caller from the JWT principal and the resource from the database — the client is never
trusted to declare what it may access.

| Decision | Rule |
|----------|------|
| **View** | admin **or** creator **or** current approver **or** CC recipient **or** assigned to any workflow stage of the memo. Soft-deleted memos are treated as non-existent, even for admins. |
| **Edit** (submit / cancel) | admin **or** creator while the memo is `DRAFT` / `REVISION_REQUESTED`. |
| **Act** (approve / reject / return / forward / backward / stage actions) | **only** the current approver **or** the assignee of the in-progress stage. **Admins get no blanket bypass** — configuration authority is deliberately separated from financial approval authority. |
| **Acknowledge** | CC recipient (or admin). |
| **Branch access** | branch member or admin. |
| **Create of a memo type** | governed by the `MemoWorkflowPermission` matrix when configured; **allowed when the matrix is empty** (backward compatible until an admin opts in). |

Every denial writes an audit event and throws `AccessDeniedException` (mapped to HTTP 403).

### 2.2 Endpoint enforcement
`system/system/nexmemo-web/src/main/java/com/nexmemo/web/Controller/MemoController.java`

Every per-resource endpoint now asserts before touching the service:

- reads (`/{id}`, `/{id}/routing`, `/{id}/history`, `/{id}/documents`, `/{id}/cc`, `/{id}/stages`) → `assertCanViewMemo`
- `/{id}/submit`, `/{id}/cancel` → `assertCanEditMemo`
- `/{id}/approve`, `/{id}/reject`, `/{id}/return`, `/{id}/forward`, `/{id}/backward` → `assertCanActOnMemo`
- `/{id}/approve-stage`, `/{id}/reject-stage`, `/{id}/return-stage`, `/{id}/delegate-stage` → `assertCanActOnMemo`
- `/{id}/acknowledge` → `assertCanAcknowledgeMemo`
- `/by-branch/{branchId}` → `assertCanAccessBranch`
- `POST /memos` → `assertCanCreateMemoOfType(userId, request.getMemoTypeId())`

`/my`, `/pending-approvals` and `/my-cc` are already self-scoped and were left unchanged.

### 2.3 Concurrency-safe decisions
- `MemoRepository.findByIdForUpdate(Long)` — `@Lock(PESSIMISTIC_WRITE)` on the memo row.
- All decision paths (`submit`, `approve`, `reject`, `return`, `forward`, `backward`, `cancel`) load the
  memo through it. The second of two concurrent decisions blocks until the first commits, then
  observes the new status and is rejected by the existing status guard — no inconsistent state.
- No new idempotency table was required: a duplicate click re-enters the same status guard and is
  rejected. (A first-class `clientDecisionId` receipt is planned with Workflow v2.)

### 2.4 `AuditEventPublisher` (new)
`system/system/nexmemo-web/src/main/java/com/nexmemo/web/service/AuditEventPublisher.java`

General audit publisher for business-sensitive operations:

- `REQUIRES_NEW` — the audit row commits on its own connection and survives a business rollback.
- **Best-effort** — a failed audit write is logged and swallowed; it can never break or mask the
  request outcome.
- **Before/after snapshots** serialized to JSON.
- **Secret redaction** — values for keys such as `password`, `token`, `access_token`, `refresh_token`,
  `reset_password_token`, `otp`, `pin`, `cvv`, `card_number`, `account_number`, `api_key`,
  `client_secret`, `private_key` are replaced with `***` before persistence.

Wired into every memo transition (`MEMO_SUBMITTED`, `MEMO_APPROVED`, `MEMO_REJECTED`,
`MEMO_RETURNED`, `MEMO_FORWARDED`, `MEMO_BACKWARDED`, `MEMO_CANCELLED`) with before/after
`status`, `approvalLevel`, `currentApproverId` and `remarks`.

### 2.5 Role → URL permission enforcement
`system/system/nexmemo-web/src/main/java/com/nexmemo/web/service/ApiPermissionService.java`
`system/system/nexmemo-web/src/main/java/com/nexmemo/web/config/ApiPermissionEnforcementFilter.java`

Activates the previously unused `role_permission_rights` → `role_permission_api` → `url_api` chain.

- Semantics — **"enforce once configured, fail open when unconfigured"**:
  - admins always pass;
  - a role with **no** URL rights configured is allowed (so enabling the filter can never lock out an
    unconfigured deployment);
  - a role **with** URL rights is limited to the configured method/URL combinations.
- URL patterns matched with `AntPathMatcher` (`/memos/**`); the configured method may be `*`/blank to
  mean "any".
- Resolved role rights are cached for 60s (`evictCache()` after role changes).
- Runs as a servlet filter inside the Spring Security chain's scope (default order, after the security
  chain), so the `SecurityContext` is populated. Unauthenticated requests (login, refresh, WebSocket
  handshake) and `OPTIONS` are skipped. Denials write an `ACCESS_DENIED` audit event and return 403.
- Config: `nexmemo.security.permission-enforcement.enabled` (env `PERMISSION_ENFORCEMENT_ENABLED`),
  default `true`.

> Because no `url_api`/`role_permission_api` rows exist yet, this is a **no-op** in the current
> database until an administrator maps URLs to roles. It becomes live the moment that is done.

### 2.6 List/search scoping (P3.1)
`system/system/nexmemo-core/src/main/java/com/nexmemo/core/repository/MemoSpecifications.java`

The global read endpoints now return only memos the caller may open, removing the previous
list/detail inconsistency (a row you can see but then get 403 on).

- `MemoSpecifications.accessibleBy(userId)` is the query-side twin of `canViewMemo`
  (creator **or** current approver **or** CC recipient **or** stage assignee), with `LEFT` joins so
  memos without a current approver are not dropped by the `OR`.
- Applied to `GET /memos`, `/memos/paginated`, `/memos/by-status/{status}` and `/memos/search` for
  **non-admins only**. Admins keep the existing (unscoped) fast queries, so dashboard/kanban
  performance is unchanged.
- `/memos/by-branch/{branchId}` remains governed by `assertCanAccessBranch`; `/my`,
  `/pending-approvals` and `/my-cc` were already self-scoped.

## 3. Files changed

**New**
- `nexmemo-web/.../service/MemoAuthorizationService.java`
- `nexmemo-web/.../service/AuditEventPublisher.java`
- `nexmemo-web/.../service/ApiPermissionService.java`
- `nexmemo-web/.../config/ApiPermissionEnforcementFilter.java`
- `nexmemo-web/src/test/.../service/MemoAuthorizationServiceTest.java` (30 tests)
- `nexmemo-web/src/test/.../service/AuditEventPublisherTest.java` (3 tests)
- `nexmemo-web/src/test/.../service/ApiPermissionServiceTest.java` (13 tests)
- `nexmemo-core/.../repository/MemoSpecifications.java` (accessible-memo specification)
- `nexmemo-web/src/test/.../service/MemoServiceTest.java` (6 tests)

**Modified**
- `nexmemo-core/.../repository/MemoRepository.java` (add `findByIdForUpdate`)
- `nexmemo-core/.../repository/WorkflowStageInstanceRepository.java` (add `existsByMemoIdAndAssigneeIdAndStatus`)
- `nexmemo-web/.../Controller/MemoController.java` (authorization assertions on every endpoint; list endpoints pass the caller)
- `nexmemo-web/.../service/MemoService.java` (pessimistic lock + audit on all decisions; accessible-scoped list/search)
- `nexmemo-web/src/main/resources/application.yaml` (permission-enforcement flag)

## 4. Migrations

**None.** Phase 3 reuses existing tables; the locking change is query-level only.

## 5. Tests

`$env:JAVA_HOME='C:\Users\Asus\.jdks\ms-17.0.20'; .\mvnw.cmd -pl nexmemo-web -am test` → **165 tests, 0 failures**
(113 pre-existing + 52 new).

## 6. Behaviour change to note

The global list/search endpoints (`GET /memos`, `/memos/paginated`, `/memos/by-status/{status}`,
`/memos/search`) now return only memos the caller can open (see §2.6), so the list and detail views
agree. Admins continue to see everything. `MemoServiceTest` covers the admin/non-admin branching; the
accessibility specification itself is exercised at runtime (there is no DB-backed test harness yet —
see deferred items).

## 7. Deferred (tracked)

- Add a DB-backed (H2/SQL Server container) integration test for `MemoSpecifications.accessibleBy`
  and other `@Query`/specification paths so query regressions fail the build.
- First-class decision receipt (`clientDecisionId` unique constraint) with Workflow v2.
- `ApiPermissionService` currently duplicates the small `isAdmin` role lookup also present in
  `ChatAuthorizationService` / `MemoAuthorizationService`; extract a shared `CurrentUserRoleService`
  when those modules are next touched.
