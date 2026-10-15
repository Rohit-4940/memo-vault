# Phase 5 — Workflow v2 (config-driven routing, assignee pools, revision freeze)

Status: **complete** · Tests: **229 green** (20 new) · Migrations: `20261012-4`, `20261012-5`, `20261012-6`

---

## 1. Problem

The stage engine (`WorkflowEngineService`) worked, but only for one shape of workflow and it had
three material gaps against the enterprise workflow requirements:

1. **Config was ignored.** Stage instances were always built from *all* global `workflow_stage`
   rows and the assignee came purely from the category rule matrix
   (`ApprovalRuleMatrix`). The per-memo-type `MemoWorkflowConfig` / `MemoWorkflowStage`
   (`assigneeType`, `assigneeId`, `stageOrder`, `stageName`) that administrators configure were
   never consulted.
2. **Only the "first" assignee could act.** `resolveAssignee` picked `roleUsers.get(0)` — a single
   user. Every other eligible approver of that stage was locked out: the memo decision endpoints
   required `instance.assignee == caller`. Middle-level approvers therefore could not use the
   actions their stage allowed; only the designated final approver in each level could.
3. **No versioning / freeze.** Nothing linked an in-flight instance to the config revision that
   launched it. Re-saving stages could, in principle, change the meaning of an already-running
   approval, and there was no way to tell which revision a memo was launched against.

## 2. What was built

### 2.1 Config revisioning

- `MemoWorkflowConfig.revision` (new `revision INT NOT NULL default 1` column — deliberately a
  separate column from `BaseEntity.version`, which stays reserved for JPA optimistic locking).
- `MemoWorkflowConfigService.saveConfig(...)` and `saveStages(...)` now bump the revision on every
  save (`prevRevision(child) + 1`), so an admin edit is always a new revision.
- `WorkflowEngineService.hasActiveConfigForMemoType(memoTypeId)` → true only when the memo type has
  an **active** config **with at least one stage**. `MemoService` uses it to route submissions and
  decisions down the config-driven path.

### 2.2 Frozen per-instance snapshot (protects in-flight instances)

`workflow_stage_instance` gains six snapshot columns (all nullable, so legacy rows are untouched):

| Column | Purpose |
|---|---|
| `config_id` | config the instance was launched from |
| `config_version` | the config **revision** at launch — never updated afterwards |
| `assignee_ids` | the frozen assignee pool (comma-separated user ids) |
| `stage_name` | frozen display label (config stages have no global `workflow_stage` row) |
| `require_comment` | frozen comment guard |
| `require_document` | frozen document guard |

`WORKFLOW_STAGE_INSTANCE.stage_id` is now **nullable** (`20261012-6`): config-driven instances carry
everything they need on themselves and do not reference a global `workflow_stage` row. The engine
never re-reads a config for an instance it already launched, so a later revision of the workflow
config can never rewrite an in-progress instance.

### 2.3 Config-driven stage creation + assignee pools

On `submitMemo`, when the memo type has an active staged config, instances are built from
`MemoWorkflowStage` (ordered by `stageOrder`). The first stage opens `IN_PROGRESS`; the rest wait as
`DRAFT`. For the opening stage the engine resolves and freezes an **assignee pool** from the stage's
`assigneeType`/`assigneeId`:

- `ROLE` → **every** member of the role is eligible (this is what lets all middle-level approvers
  act, not just one).
- `USER` (or any explicit `assigneeId`) → single eligible approver.
- otherwise → fallback pool from the category rule matrix (all direct users + all role members).

The first pool member is stored as the primary `assignee` (display + notification target); the pool
is what governs **who may act**. Legacy category-driven memos keep working exactly as before (the
global-stage + rule-matrix path is untouched and still the fallback when no config exists).

### 2.4 Every eligible approver can act — server-side

- `approveStage` / `rejectStage` / `returnToStage` / `delegateStage` now authorize against the
  frozen pool (`assignee` OR any id in `assignee_ids`), not the single primary assignee.
- Decision actions are keyed on the **stage-instance id** (unambiguous for config instances, where
  there is no stage id). `findActionableInstance` also accepts a legacy `stage_id` so existing
  clients keep working during the transition; `MemoService` now passes `current.getId()`.
- `MemoAuthorizationService.canViewMemo`/`canActOnMemo` also recognize pool membership (a pool
  member can see the memo and act on the in-progress stage), while keeping the P3 rule that
  **admins get no blanket approval bypass**.
- New endpoint-supporting helper `getEligibleAssigneesForStage(memoId, stageOrInstanceId)` returns
  the frozen pool for UI hints.

### 2.5 Per-stage guards + concurrency safety

- **Comment guard**: enforced when the effective `require_comment` flag (frozen snapshot, falling
  back to the global stage) is true and the decision comment is blank. Note the schema default for
  `workflow_stage.require_comment` is `1`, so stage decisions now genuinely require a remark.
- **Document guard**: enforced on **approve** when `require_document` is true and the memo has no
  attached document. Reject/return deliberately do **not** demand documents (you reject *because*
  documents are missing) but still require a comment.
- **Pessimistic lock**: `WorkflowStageInstanceRepository.findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`)
  is used for every decision, so two eligible pool members approving at the same instant cannot
  double-advance the memo.
- `delegateStage` narrows the frozen pool to the delegatee and rejects self-delegation.

## 3. Behavior / API

No new controller paths were required (existing stage-action endpoints keep their signatures and
now resolve the instance by id *or* legacy stage id). Notable behavior changes:

| Area | Before | After |
|---|---|---|
| Stage source | global `workflow_stage` only | memo type's active config stages when configured; legacy fallback otherwise |
| Who may act | single resolved assignee | any member of the frozen assignee pool |
| In-flight instances | no linkage to config | linked to `config_id` + `config_version`; never rewritten |
| Comment | not enforced | enforced when the stage requires it |
| Documents | not enforced | enforced on approve when the stage/config requires it |
| Concurrency | no lock on the stage row | `PESSIMISTIC_WRITE` lock per decision |

## 4. Files

**Added**
- `nexmemo-web/src/test/…/WorkflowEngineServiceTest.java` (12)
- `nexmemo-web/src/test/…/MemoWorkflowConfigServiceTest.java` (4)

**Changed**
- `nexmemo-core/…/entity/MemoWorkflowConfig.java` — added `revision`.
- `nexmemo-core/…/entity/WorkflowStageInstance.java` — added `configId`, `configVersion`,
  `assigneeIds`, `stageName`, `requireComment`, `requireDocument`; `stage` now nullable.
- `nexmemo-core/…/repository/WorkflowStageInstanceRepository.java` — `findByIdForUpdate`
  (`PESSIMISTIC_WRITE`).
- `nexmemo-web/…/service/WorkflowEngineService.java` — v2 submit path, pools, guards, locked
  decisions, `hasActiveConfigForMemoType`, `getEligibleAssigneesForStage`.
- `nexmemo-web/…/service/MemoWorkflowConfigService.java` — revision bump on config/stage save.
- `nexmemo-web/…/service/MemoService.java` — route on `hasActiveConfigForMemoType(...)`; pass the
  stage-instance id to the engine.
- `nexmemo-web/…/service/MemoAuthorizationService.java` — pool-aware view/act.
- `nexmemo-web/…/service/EscalationService.java` — null-safe for config-driven instances (no global
  stage → no escalation rule) and keeps the frozen `assignee_ids` pool in sync on reassignment.
- `nexmemo-core/…/db/changelog/db.changelog-master.xml` — `20261012-4` (config `revision`),
  `20261012-5` (instance snapshot columns), `20261012-6` (`stage_id` nullable).
- `nexmemo-web/src/test/…/MemoAuthorizationServiceTest.java` — 4 pool tests.

## 5. Verification

- `.\mvnw.cmd -pl nexmemo-web -am test` → **229 tests, 0 failures, 0 errors**.
- Dev-profile startup on SQL Server: changesets `20261012-4`, `20261012-5`, `20261012-6` applied
  (`revision(INT)` added to `memo_workflow_config`; snapshot columns added to
  `workflow_stage_instance`; null constraint dropped from `workflow_stage_instance.stage_id`);
  `ddl-auto: validate` passed; **`Started NexMemoApplication`**.

## 6. Notes / deferred

- Frontend affordances for config-driven stages (rendering `stage_name` when `stage.id` is null,
  showing "you are one of N eligible approvers", surfacing guard errors) belong to the memo-module
  UI work (P6 and frontend phases). The backend resolves either id form so current clients keep
  working in the meantime.
- `flowType` (`SEQUENTIAL`/parallel) is still treated as sequential; true parallel quorum
  (all-of / any-of) would need a stage completion counter and is deferred.
- Config stages default to `require_comment = true` (matching the global-stage schema default);
  a per-stage comment toggle for config stages can be added with the admin UI in P6.
- General memo **edit** endpoint still does not exist; when it lands it should call
  `MemoVersionService.snapshot` to append versions and re-run `validateFormValues`.
