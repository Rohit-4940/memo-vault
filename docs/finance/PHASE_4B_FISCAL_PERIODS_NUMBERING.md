# Phase 4b — Fiscal Periods (open / close / lock) + Memo Numbering

Status: **complete** · Tests: **197 green** (12 new) · Migration: `20261012-1`

---

## 1. Problem

- No financial-period concept: a fiscal year could only be toggled active/inactive — there was no
  open / close / lock lifecycle controlling when new memos may be created.
- Memo numbering was **inconsistent across three implementations**:
  - `MemoService.generateMemoNo(...)` — fragile `NEXM-0001` from "last memo + 1" parsing (no lock, can
    clash under concurrency, ignores fiscal year). **This was the live path used by `createMemo`.**
  - `MemoNumberGeneratorService.generateMemoNumber(fyId)` — correct row-locked per-FY `NEXM-{fyId}-{seq}`,
    but **dead code (no callers)**.
  - `ReferenceNumberService.generateMemoRefNo(...)` — configured-format generator, also **dead (no callers)**.
- `createMemo` never assigned the memo's `fiscal_year_id`, although the column already existed.

## 2. What was built

### 2.1 Financial period lifecycle

- New `FinancialPeriod` entity (`financial_period` table), `FinancialPeriodStatus` enum
  (`OPEN` / `CLOSED` / `LOCKED`), `FinancialPeriodRepository`.
- `FinancialPeriodService` enforces:
  - **Dates** — `end >= start`, and the period must fall inside its fiscal year.
  - **Single OPEN invariant** — opening (or creating/updating to OPEN) auto-closes any other OPEN period.
  - **Transitions** — `OPEN → CLOSED`, `CLOSED → OPEN`, `CLOSED/OPEN → LOCKED`; LOCKED is terminal and
    cannot be reopened; delete is only allowed for non-OPEN periods.
- `FinancialPeriodController` at `/financial-periods` — CRUD (ADMIN), `GET /current`, per-fiscal-year
  listing, and `PUT /{id}/open|close|lock`.

### 2.2 Memo-creation gate (fail open when unconfigured)

`FinancialPeriodService.resolveCreationFiscalYear()` decides the memo's fiscal year:

| State | Result |
|---|---|
| OPEN period covers today | its fiscal year (strict) |
| OPEN periods exist but none covers today | reject (`IllegalStateException`) |
| No periods configured → active fiscal year exists | fall back to active FY |
| Nothing configured at all | `null` → legacy `NEXM-{sequence}` number |

This matches the codebase's established "enforce once configured, fail open when unconfigured"
philosophy (same as the P3 permission matrix).

### 2.3 Unified numbering

- `createMemo` now calls the single canonical path:
  `MemoNumberGeneratorService.generateMemoNumber(fyId)` → **`NEXM-{fiscalYearId}-{sequence}`** using a
  `PESSIMISTIC_WRITE`-locked `reference_sequence` row per fiscal year (concurrency-safe).
- The memo's `fiscal_year_id` is now assigned at creation.
- The fragile `MemoService.generateMemoNo(...)` (last-memo parsing) was **removed**; the legacy global
  format survives only as a documented fallback when no fiscal year / period exists
  (`NEXM-%04d` from `count()+1`), so a fresh database never bricks memo creation.

## 3. API

| Method | Path | Auth |
|---|---|---|
| GET | `/financial-periods` | authenticated |
| GET | `/financial-periods/{id}` | authenticated |
| GET | `/financial-periods/current` | authenticated (404 when none open) |
| GET | `/financial-periods/fiscal-year/{fyId}` | authenticated |
| POST | `/financial-periods` | SUPER_ADMIN / ADMIN |
| PUT | `/financial-periods/{id}` | SUPER_ADMIN / ADMIN |
| DELETE | `/financial-periods/{id}` | SUPER_ADMIN / ADMIN |
| PUT | `/financial-periods/{id}/open` | SUPER_ADMIN / ADMIN |
| PUT | `/financial-periods/{id}/close` | SUPER_ADMIN / ADMIN |
| PUT | `/financial-periods/{id}/lock` | SUPER_ADMIN / ADMIN |

## 4. Files

**Added**
- `nexmemo-core/…/enums/FinancialPeriodStatus.java`
- `nexmemo-core/…/entity/FinancialPeriod.java`
- `nexmemo-core/…/repository/FinancialPeriodRepository.java`
- `nexmemo-web/…/service/FinancialPeriodService.java`
- `nexmemo-web/…/Controller/FinancialPeriodController.java`
- `nexmemo-web/src/test/…/FinancialPeriodServiceTest.java` (10)
- `nexmemo-web/src/test/…/MemoNumberGeneratorServiceTest.java` (2)

**Changed**
- `nexmemo-web/…/service/MemoService.java` — creation now resolves fiscal year + canonical number;
  fragile `generateMemoNo` removed; legacy fallback added.
- `nexmemo-web/…/service/FiscalYearService.java` — added `getActiveFiscalYearSafely()`.
- `nexmemo-core/…/db/changelog/db.changelog-master.xml` — changeset `20261012-1` (financial_period).
- `nexmemo-web/src/test/…/MemoServiceTest.java` — new mocked `FinancialPeriodService`.

## 5. Verification

- `.\mvnw.cmd -pl nexmemo-web -am test` → **197 tests, 0 failures, 0 errors**.
- Dev-profile startup on SQL Server: Liquibase applied `20261012-1`
  ("Table financial_period created", FK + indexes), `ddl-auto: validate` passed,
  `Started NexMemoApplication` (no `UnsatisfiedDependency`, no schema errors).
- Dev DB now holds active fiscal year `FY 2082/83` (id 1) → new memos will be numbered `NEXM-1-0001`,
  `NEXM-1-0002`, ….

## 6. Notes / deferred

- `ReferenceNumberService` (config-format ref numbers) remains dead code; it may be used in P4c if
  memo "reference numbers" are needed alongside the canonical `memo_no`. Not removed to avoid churn.
- Period auto-creation / seed data is intentionally omitted — admins define periods; until then the
  system fails open to the active fiscal year.
- No frontend page was added for period administration (tracked for P6 admin UI).