# Phase 4c — Dynamic Memo Forms & Memo Type/Template Configuration

Status: **complete** · Tests: **210 green** (13 new) · Migrations: `20261012-2`, `20261012-3`

---

## 1. Problem

- `memo_type` was only `name + description + status` — no form definition, so every memo type
  rendered the same static form.
- `createMemo` ignored most of the memo's own configured surface plus the template's configuration:
  it never honored template defaults (`defaultAmount`, `defaultPriority`, `defaultPaymentMethod`,
  `requiredApprovalLevels`) nor the template's `required_fields`, and it never captured **dynamic
  field values** at all.
- `memo_version` was dead infrastructure: the table + read-only controller existed, but **nothing
  ever wrote a version row**.
- Memo fields that already existed in the schema (`payment_method`, `budget_code`, `vendor_name`,
  `vendor_account`, `is_urgent`, `expected_date`) were never populated by `createMemo`.

## 2. What was built

### 2.1 Dynamic form schemas on memo types

- `MemoType.formSchema` (new `form_schema TEXT` column) stores a JSON list of `FormField`s —
  `key`, `label`, `type` (text / textarea / number / date / select / checkbox / money ...),
  `required`, `placeholder`, `defaultValue`, `options`.
- New `FormField` DTO, `MemoFormConfigService.parseSchema(...)` (JSON → `List<FormField>`; throws
  `IllegalArgumentException` on malformed schema), and `validateSchema(...)`.
- `MemoTypeService` now validates the schema on create/update and **preserves an existing schema
  when an update omits it**. Writes evict the `referenceData` cache so fresh schemas are served
  immediately.
- New endpoint `GET /memo-types/{id}/form-schema` → parsed fields for dynamic form rendering.

### 2.2 Capturing dynamic values + validation at create

- `CreateMemoRequest` gains `formValues` (`Map<String,Object>`) plus the previously unused fields
  (`paymentMethod`, `budgetCode`, `vendorName`, `vendorAccount`, `isUrgent`, `expectedDate`);
  `priority` is no longer `@NotNull` so template defaults may apply.
- `Memo.formValues` (new `form_values TEXT` column) stores the captured values as JSON.
- `MemoFormConfigService.validateFormValues(...)` enforces requiredness from **both** the memo
  type's schema (`required: true` fields) **and** the template's `required_fields` list (blank
  values count as missing). Unknown keys are tolerated (permissive).
- `MemoFormConfigService.validateAmount(...)` enforces the template's `max_amount` ceiling.

### 2.3 Template defaults actually applied

When a memo is created from a template, `createMemo` now applies: `defaultAmount`, `defaultPriority`,
`defaultPaymentMethod`, and `requiredApprovalLevels` → memo `totalApprovalLevels`; template `content`
already fed the body. Explicit request values always win.

### 2.4 Memo versioning becomes real

- New `MemoVersionService.snapshot(memo, actorId)` writes `memo_version` rows (v1 at creation,
  later versions appended) with a JSON snapshot of memoNo / title / description / content / amount /
  formValues / memoTypeId.
- `createMemo` calls it after save, so `GET /memo-versions/memo/{id}` now returns real history.
- Future edit flows (P5) simply call `snapshot` again to append versions.

## 3. API

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/memo-types/{id}/form-schema` | authenticated | parsed `FormField[]` for dynamic form rendering |
| POST | `/memos` | creator | body now accepts `formValues`, `paymentMethod`, `budgetCode`, `vendorName`, `vendorAccount`, `isUrgent`, `expectedDate`; `priority` optional |
| GET | `/memo-versions/memo/{memoId}` | authenticated | now returns persisted snapshots (was empty) |

## 4. Files

**Added**
- `nexmemo-core/…/dto/FormField.java`
- `nexmemo-web/…/service/MemoFormConfigService.java`
- `nexmemo-web/…/service/MemoVersionService.java`
- `nexmemo-web/src/test/…/MemoFormConfigServiceTest.java` (8)
- `nexmemo-web/src/test/…/MemoVersionServiceTest.java` (2)
- `nexmemo-web/src/test/…/MemoTypeServiceTest.java` (3)

**Changed**
- `nexmemo-core/…/entity/MemoType.java` — added `formSchema`.
- `nexmemo-core/…/entity/Memo.java` — added `formValues`.
- `nexmemo-web/…/service/MemoService.java` — DTO extension; validate + defaults + snapshot wiring in `createMemo`.
- `nexmemo-web/…/service/MemoTypeService.java` — schema validate/copy/preserve + cache eviction + `getFormSchema`.
- `nexmemo-web/…/Controller/MemoTypeManageController.java` — `GET /{id}/form-schema`.
- `nexmemo-core/…/db/changelog/db.changelog-master.xml` — `20261012-2` (form_schema), `20261012-3` (form_values).
- `nexmemo-web/src/test/…/MemoServiceTest.java` — two new mocked collaborators.

## 5. Verification

- `.\\mvnw.cmd -pl nexmemo-web -am test` → **210 tests, 0 failures, 0 errors**.
- Dev-profile startup on SQL Server: `20261012-2` (`Columns form_schema(TEXT) added to memo_type`)
  and `20261012-3` (`Columns form_values(TEXT) added to memo`) ran successfully; `ddl-auto: validate`
  passed; **`Started NexMemoApplication`**.

## 6. Notes / deferred

- Frontend dynamic-form rendering of `form_schema` is part of P6 (admin UI) / memo-module UI work.
- A general draft-edit endpoint does not exist yet; when one lands (P5), it should call
  `MemoVersionService.snapshot` to append versions and re-run `validateFormValues`.
- `ReferenceNumberService` (config-format ref numbers) remains dead code — still candidates for
  cleanup if unneeded by P5's workflow v2.