# Phase 5b — Self-Service Request Catalogue & Common Request Engine

Status: **complete** · Tests: **242 green** (12 new) · Migrations: none (no schema change)

---

## 1. Problem

After P4c (memo types + dynamic forms) and P5 (config-driven workflow v2), the system could route a
memo through a configured approval workflow, but there was **no self-service surface**:

- A user had to know a memo type id and shape a full `POST /memos` payload — there was **no
  catalogue** telling them which request types they are allowed to raise, nor the form fields of
  each type.
- A request, once created, **could not be edited**. `memo_version` existed and `MemoVersionService`
  could snapshot, but no endpoint wrote a second version — the P4c note explicitly deferred a
  draft-edit flow.
- There was **no "my requests" / "where is my request?"** view scoped to the requester.
- Each domain (leave, budget, expense, document transfer, vendor) had its own ad-hoc endpoint with
  no shared approval engine, so behaviour diverged.

The enterprise requirement is a **common request engine**: one configuration-driven way for any
employee to raise, edit, submit, cancel and track a request, routed through the same approval truth
as memos.

## 2. Design decision

A request **is** a `Memo`; a request *type* **is** an active `MemoType` (with its P4c
`form_schema`); approval runs through the existing `WorkflowEngineService`. The engine is therefore a
**thin orchestration + catalogue + tracking facade over the memo pipeline**, not a parallel domain.

This keeps a single source of truth for:

- numbering (`NEXM-{fy}-{seq}` via the fiscal-year sequence),
- approval (P5 config routing, assignee pools, revision freeze, per-stage guards),
- notification (P4a events) and audit,
- authorization (`MemoAuthorizationService`).

No new tables, entities or workflows were introduced.

## 3. What was built

### 3.1 `RequestEngineService` (`nexmemo-web`)

| Method | Behaviour |
| --- | --- |
| `getCatalogue(userId)` | Active `MemoType`s the caller may raise, each with its parsed form schema. |
| `createRequest(command, submitNow, userId)` | Authorizes the type, creates the draft via `MemoService.createMemo`, optionally submits. |
| `updateRequest(id, command, userId)` | Authorizes edit, delegates to `MemoService.updateDraftMemo`. |
| `submitRequest(id, userId)` | Authorizes edit, delegates to `MemoService.submitMemo`. |
| `cancelRequest(id, userId)` | Authorizes edit, delegates to `MemoService.cancelMemo`. |
| `getRequest(id, userId)` | Authorizes view, returns the request. |
| `getMyRequests(userId, pageable)` | The caller's own requests, newest first. |
| `trackRequest(id, userId)` | Status + approval history + workflow stage instances. |

Every read/write is authorized through `MemoAuthorizationService`; the caller identity always comes
from the JWT principal, never from the payload.

### 3.2 Draft edit — the deferred P4c/P5 gap

`MemoService.updateDraftMemo(id, request, userId)`:

- locked read (`findByIdForUpdate`),
- only `DRAFT` / `REVISION_REQUESTED` are editable,
- only the creator (or an admin via `isAdmin`) may edit,
- resolves the effective memo type and (optional) template, then re-validates the **effective
  dynamic values** and the amount ceiling **before** mutating anything,
- applies only non-null fields (delta update),
- writes a `memo_version` snapshot and publishes a `MEMO_UPDATED` audit event with before/after.

`MemoFormConfigService.parseFormValues(memo)` was added so an edit that does not resend the form
data still re-validates the stored values.

### 3.3 `RequestController` — `/requests` (authenticated)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/requests/catalogue` | Request types you may raise (+ form schemas). |
| `GET` | `/requests/my` | Your requests, paginated, newest first. |
| `POST` | `/requests` | Raise a request (`{ request: <memo payload>, submitNow: bool }`). |
| `GET` | `/requests/{id}` | Read one of your requests. |
| `PUT` | `/requests/{id}` | Edit a draft / returned request. |
| `POST` | `/requests/{id}/submit` | Submit for approval. |
| `POST` | `/requests/{id}/cancel` | Cancel a draft. |
| `GET` | `/requests/{id}/tracking` | Status + history + stages. |

### 3.4 DTOs (`nexmemo-core`)

- `RequestTypeDto` — catalogue entry: `id`, `name`, `description`, parsed `List<FormField> fields`.
- `RequestTrackingDto` — `memoId`, `memoNo`, `title`, `status`, `approvalLevel`,
  `totalApprovalLevels`, `currentApproverId`, `submittedDate`, `approvedDate`, `history`, `stages`.

## 4. Authorization

- Catalogue filters by `canCreateMemoOfType`; the type must also be `ACTIVE`.
- Create asserts `assertCanCreateMemoOfType`.
- Edit / submit / cancel assert `assertCanEditMemo`.
- Read / track assert `assertCanViewMemo`.
- Draft-edit additionally enforces creator-or-admin and the editable-status guard inside the
  service (defense in depth).
- Admins keep configuration authority but gain **no** approval bypass.

## 5. Verification

- `RequestEngineServiceTest` (8): catalogue filtering, create (with/without `submitNow`),
  update/submit/cancel delegation + authz, view authz, tracking assembly.
- `MemoServiceTest` (+4): draft edit applies delta + validates + snapshots + audits; rejects
  non-editable status; rejects non-creator non-admin; admin may edit another user's draft.
- Full suite **242 green** (was 230), 0 failures / 0 errors / 0 skipped.
- App boots on the `dev` profile: `Started NexMemoApplication in 83.5s`; Liquibase up to date (87
  changesets); initial patch completed successfully; `/api/v1/requests/**` is behind authentication
  (401 without a token).

## 6. Notes / deferred

- Frontend self-service UI (catalogue, raise form from schema, my-requests, tracking) is UI work
  tracked with the P6 admin/module UI.
- `getMyRequests` uses the existing created-by scoping; no cross-user search here.
- Parallel/quorum stages remain sequential (carried over from P5).
