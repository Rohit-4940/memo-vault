# Phase 6 — Admin & Self-Service UI

**Status:** P6a ✅ complete · P6b ✅ complete · P6c ✅ complete
**Scope:** mostly frontend (Angular 18, `admin-front`); P6c adds one Liquibase changeset + entity/engine touch-ups.

This phase wires the enterprise backend delivered in P4b/P4c/P5/P5b into the Angular
application, and fixes two pre-existing frontend defects that had left dynamic forms
silently non-functional.

---

## P6a — Form-schema management + working dynamic forms

### Background / defects fixed

1. **Phantom API.** `memo.component` and the `memo-workflow-config` "form fields" tab both
   called `GET/POST/PUT /api/v1/memo-form-fields/**`. **That API does not exist** — there is no
   `MemoFormField` entity or controller. The tab and the memo create form were therefore dead
   (empty form / failed writes).
2. **Wrong payload key.** `memo.component` submitted dynamic values under `formData`, but
   `MemoService.CreateMemoRequest` (and the phase-4c validator) expect **`formValues`**. Dynamic
   values were never captured, and any memo type with a required schema field failed creation.

The authoritative form definition is `MemoType.formSchema` (a JSON array of
`com.nexmemo.core.dto.FormField` = `{ key, label, type, required, placeholder, defaultValue, options[] }`),
exposed by `GET /api/v1/memo-types/{id}/form-schema`.

### Changes

| File | Change |
| --- | --- |
| `shared-services/api-service/memo-type.service.ts` | Added `getFormSchema(id)` → `GET /memo-types/{id}/form-schema`. |
| `components/pages/memo-type/memo-type.component.{ts,html}` | New **dynamic form-field builder** in the create/edit modals: add/remove fields, key, label, widget type, required, placeholder, default, options. Serialises to `MemoType.formSchema`. Added a **Fields** count column. |
| `components/pages/memo/memo.component.ts` | Loads fields from `getFormSchema` and adapts them to the dynamic-form renderer; submits **`formValues`**; derives the payload from live form state (`getFormDataJson()`). |
| `components/pages/memo-workflow-config/memo-workflow-config.component.{ts,html}` | Repointed the form-fields tab from the phantom API to the real schema API: load maps schema → rows, save serialises rows → `formSchema` and PUTs the memo type. Options edited as a JSON array. Removed unsupported modal inputs (section/conditional/help/read-only). |

### Details worth noting

- **Clearing a schema.** `MemoTypeService.updateMemoType` only applies `form_schema` when the
  incoming value is **non-null**. The builder therefore sends `"[]"` (not `null`) when every
  field is removed, so clearing is persisted. `"[]"` passes `validateSchema` (parses to an empty list).
- **Widget type conventions.** The backend stores free-form, lowercase hints
  (`text`, `textarea`, `number`, `money`, `date`, `select`, `checkbox`, …). The reusable
  `DynamicFormComponent` expects UPPERCASE enum-like values (`TEXT`, `CURRENCY`, `DROPDOWN`, …).
  Two small adapters map lowercase → uppercase when rendering; storage stays lowercase.
- **Options encoding.** `DynamicFormComponent.getFieldOptions` calls `JSON.parse(field.options)`
  and falls back to `field.options.split(',')`. It requires a **string**, so options are passed as
  `JSON.stringify(list)` (an empty string yields no options).
- **Required-field validation** is enforced client-side (`DynamicFormComponent.isValid()`) and
  again server-side (`MemoFormConfigService.validateFormValues`).

---

## P6b — Self-service request UI

The P5b `/requests` backend is surfaced through the previously-placeholder
**`memo-catalogue`** page. Menu id 7 (`/layout/memo-catalogue`) is already granted to
SUPER_ADMIN, ADMIN **and USER** in `patch.sql`, so no menu/schema change was needed and the page
is reachable for every role (the sidebar's `menuGuard` enforces menu URLs after load).

### Changes

| File | Change |
| --- | --- |
| `shared-services/api-service/request.service.ts` **(new)** | Thin client over `/requests`: catalogue, my-requests, create, get, update, submit, cancel, tracking. |
| `components/pages/memo-catalogue/memo-catalogue.component.{ts,html,scss}` | Replaced the placeholder with the self-service UI. |

### Features

- **Raise a Request** — catalogue of request types the caller may raise (each card shows the
  type and its field count). Selecting a type opens a modal that renders the type's dynamic form
  from its schema, plus title, description, priority, amount, expected date and an urgent flag.
  The user can **Save Draft** (`submitNow=false`) or **Submit** (`submitNow=true`).
- **My Requests** — the caller's requests (newest first) with status/priority badges, and per-row
  actions: **Track**, **Edit** (DRAFT / REVISION_REQUESTED), **Submit** (DRAFT), **Cancel** (DRAFT).
- **Tracking** — one call (`/requests/{id}/tracking`) shows status, approval level progress,
  submitted/approved dates, workflow stage instances and approval history.

### Contract mapping

- `GET /requests/catalogue` → `ApiResponse<List<RequestTypeDto>>` (interceptor unwraps `data`).
- `GET /requests/my?page&size` → `PageResponse<Memo>` (read via `.content`).
- `POST /requests` body `{ request: CreateMemoRequest, submitNow }`.
- `PUT /requests/{id}` body `UpdateMemoRequest`.
- `POST /requests/{id}/submit`, `POST /requests/{id}/cancel`, `GET /requests/{id}/tracking`.

A request **is** a `Memo`, so approval, numbering, notification and audit are inherited
unchanged from the memo engine.

---

## Verification

- **Angular build:** `npm run build -- --configuration development` → exit code **0**
  (only the pre-existing `@angular/localize/init` polyfill warning).
- **Backend:** P6a/P6b made no backend changes. P6c adds Liquibase `20261013-1` and touches
  `MemoWorkflowStage` / `WorkflowEngineService`; the full suite is green
  (`nexmemo-web -am test` → **241 tests, 0 failures / 0 errors**). The P5b `/requests`
  contract remains covered by `RequestEngineServiceTest`.

## P6c — financial-period admin + per-stage decision guards (complete)

### Financial-period administration

The **Fiscal Years** page gained a per-row **Manage periods** action (`fiscal-year.component.*`),
opening a modal that lists a fiscal year's periods and drives the full open/close/lock lifecycle.

- New `shared-services/api-service/financial-period.service.ts` over `/financial-periods`
  (`getAll`, `getByFiscalYear`, `getCurrentOpen`, `create`, `update`, `delete`, `open`, `close`, `lock`).
- UI mirrors the backend lifecycle exactly:
  - **Open** only when `CLOSED` (opening auto-closes any other open period — the service does this).
  - **Close** only when `OPEN`.
  - **Lock** when not already `LOCKED`; locked periods are terminal (no edit/delete/close offered).
  - **Delete** only when not `OPEN` (the backend rejects deleting an open period).
- Create/edit validates name + dates and posts `fiscalYear: { id }`; dates are constrained by the
  backend to fall inside the fiscal year.

### Per-stage `requireComment` / `requireDocument`

Previously the config-driven engine path hard-coded `requireComment=true` and inherited a single
config-level `requireDocumentVerification` flag for every stage. The workflow-config **Stages** tab
now lets an administrator set both guards per stage.

- **Schema:** Liquibase `20261013-1` adds `require_comment BIT NOT NULL DEFAULT 1` and
  `require_document BIT NOT NULL DEFAULT 0` to `memo_workflow_stage`.
- **Entity:** `MemoWorkflowStage` gains the two fields (defaults `true` / `false`).
- **Engine:** `WorkflowEngineService.initStageInstancesFromConfig` now snapshots
  `cfgStage.getRequireComment()` / `cfgStage.getRequireDocument()`, falling back to
  `true` / the config-level `requireDocumentVerification` when a stage value is null — so existing
  configurations keep their previous behaviour.
- **UI:** the add-stage panel has *Require comment* / *Require document* toggles (applied to newly
  added stages), and each stage card exposes the same two toggles inline; both persist through the
  existing `PUT /memo-workflows/config/{configId}/stages`.

The snapshot semantics of Phase 5 are preserved: in-flight stage instances keep the guard flags they
were launched with and are never rewritten by later config revisions.

## Known follow-ups / notes

- `shared-services/api-service/memo-form-field.service.ts` is now unused (the phantom API it
  targeted never existed). Left in place; safe to delete.
- `patch_ms_sql/general_patch/patch-banking-expenses.sql` is not executed by
  `InitialPatch.runPatch()` (which only runs `location.sql`, `patch.sql`,
  `patch-workflow-entities.sql`) and is not referenced by Liquibase — it appears orphaned.
