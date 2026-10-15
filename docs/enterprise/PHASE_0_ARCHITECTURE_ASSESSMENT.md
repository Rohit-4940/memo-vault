# Phase 0/1 — Repository Discovery & Architecture Assessment

**Status:** discovery complete, proposal ready, **awaiting approval before any large-scale implementation.**
**Scope of this document:** findings from the actual repository + a proposed target architecture, UI navigation,
data model, API contracts, security model and a phased implementation plan.

> This document deliberately contains **no implementation**. Per the master brief ("Do not begin large-scale
> implementation until the plan is approved"), the next step is a decision, not code.

---

## 0. Executive summary

The repository is a working **banking Memo Management System** that has already been extended (Phases P3–P7)
with enterprise workflow, security, notifications, fiscal periods, dynamic forms and a self-service request engine.

Crucially, **several modules the brief asks for already exist in a first-generation form** and must be *extended*,
not rebuilt:

| Brief module | Already in repo? | Verdict |
| --- | --- | --- |
| Compressor | ✅ exists but **synchronous, in-memory, archive-only** | **Extend** (async + PDF/image + presets + guardrails) |
| Secure document vault / sharing | ⚠️ partially — `DocumentTransfer` + `MemoDocument` | **Extend + add vault layer** |
| Internal messaging | ✅ a full **chat module** already exists (conversations/messages/reactions/mentions/attachments/WebSocket) | **Reuse as the internal-messaging core** |
| Email | ✅ SMTP config + `NotificationDispatchService` | **Extend** (mail center, templates, delivery log) |
| Notifications | ✅ entity + dispatch + config + logs | **Reuse** |
| Workflow / approvals | ✅ Phase-5 workflow v2 engine | **Reuse / integrate** |
| Auth, RBAC, audit | ✅ JWT, `@PreAuthorize`, DB-driven menus, audit module | **Reuse; must fix BOLA gaps (see §8)** |
| AI assistant | ❌ none | **New (optional, provider-gated)** |
| Preview / OCR / text index | ⚠️ `MemoDocument.extractedText` exists, OCR config exists but **OCR service is commented out** | **Extend** |

**Critical pre-existing security findings** (must be fixed as part of the programme, not deferred):

- `GET /files/download?path=…` and `DELETE /files?path=…` are guarded only by `isAuthenticated()` — **any
  authenticated user can download or delete any file by guessing its storage path (broken object-level
  authorization / IDOR).**
- `MemoDocumentController` (`/memo-documents/**`) is guarded only by `isAuthenticated()` — **no per-memo
  authorization**, so a user can read/add/delete documents on a memo they cannot access.
- `CompressionController` has **no file-type/signature validation and no malware scan**, and runs the work
  **synchronously inside the HTTP thread** reading the whole file into memory.

---

## 1. Confirmed technology stack (as inspected — not assumed)

**Backend** — `system/system/` (Maven multi-module)
- Spring Boot **3.2.4** (parent POM), **Java 17**.
- Modules: `nexmemo-core`, `nexmemo-web`, `nexmemo-security`, `nexmemo-service`, `nexmemo-audit`.
- Persistence: JPA/Hibernate, **Microsoft SQL Server**, schema owned by **Liquibase** (`ddl-auto: validate`).
- Also declared: `spring-boot-starter-data-elasticsearch` **8.13.4**, `spring-boot-starter-cache` + **Caffeine**,
  **Apache Commons Compress** (used by the compressor), **Jasypt** (config encryption), ClamAV config keys.
- Web: Spring MVC, **STOMP/SockJS WebSocket** (chat), context path **`/api/v1`**, port **8086**.

**Frontend** — `admin-front/` (Angular, standalone components, lazy routes)
- **Angular 18.2**, TypeScript 5.5, **Bootstrap 5.3.3**, **ng-bootstrap 17**, **ngx-toastr 19**,
  **bootstrap-icons 1.11**, **CKEditor 5** (rich text), **ng-select 13**, **ngx-translate 17**,
  **STOMP 7 + SockJS** (websockets), **chart.js / ng2-charts**, Angular SSR (`@angular/ssr`).
- API base via `ApiConfig.URL`; `ApiResponseInterceptor` unwraps `{success,data}`.

**Storage** — `StorageService` interface with two implementations: `LocalStorageService`
(`C:/nexmemo/uploads`, configurable) and `MinioStorageService`. Config prefix `nexmemo.storage`.

> **Version-confirmation note:** no framework upgrade is proposed. Any new library (§10) is additive and
> compatible with the versions above; it will not be added until approved.

---

## 2. What already exists (inventory with reuse verdicts)

### 2.1 File & document handling
- `StorageService` / `LocalStorageService` / `MinioStorageService` — upload (multipart or byte[]),
  download, delete. **Reuse as the storage abstraction.**
- `FileController` (`/files/upload`, `/files/download`, `/files/signature`, `DELETE /files`) — extension +
  MIME whitelist, 10 MB cap, path traversal sanitising. **Reuse the validation idea; fix authorization (§8).**
- `MemoDocument` entity (`fileName, filePath, fileType, fileSize, extractedText`) bound to `Memo`;
  `MemoDocumentController` (`/memo-documents/**`). **Reuse + secure.**
- `DocumentTransfer` + `DocumentTransferFile` + `DocumentTransferService` + `DocumentTransferController`
  (`/files/transfer/**`: create/send/receive/deliver/archive/cancel, per-file & zip download, stats) and the
  `transfer-document` frontend page. **This is the closest existing thing to a "secure document vault" — reuse.**

### 2.2 Compression (existing first-generation)
- `CompressionJob` entity (`compression_job`: original/compressed name+size, type, status, storagePath,
  sha-256 checksums both sides, timing), `CompressionType` (GZIP/ZIP/TAR/TAR_GZ), `CompressionStatus`.
- `CompressionService` + `CompressionController` (`/files/compress`, `/batch`, `/history`, `/{id}`,
  `/{id}/download`, DELETE), `FileCompressionUtil` (Commons Compress), `CompressionJobRepository`.
- Frontend: `file.service.ts` + `document-compressor` page.
- **Gaps vs brief:** synchronous in the request thread; whole file read into memory (`file.getBytes()`);
  archive creation only (**no extraction, no PDF/image optimisation, no presets, no metadata strip, no
  integrity confirm UI, no queue/worker, no cancel/retry, no per-item batch model, no AV scan, no signature
  check, no zip-bomb limits**).

### 2.3 Messaging / chat (already substantial)
- Backend entities/services/controllers for conversations, messages, reactions, mentions, bookmarks,
  unread, threads, forwards, **`ChatAttachment`** (+ scan-status enum), typing/presence, notification
  settings, scoped search, admin moderation; **STOMP WebSocket**; module-specific authorization tests exist.
- Frontend: `chat.service.ts`, `branch-chat` page, `chat-widget`, `notification.service.ts`.
- **Implication:** the brief's *internal* messaging/labels/folders/threads/search should be built as an
  **extension of this existing chat domain**, not a parallel "mailbox" stack.

### 2.4 Email & notifications
- SMTP config `nexmemo.email.fallback` (host/port/username/password/from/TLS) + Jasypt for secrets.
- `NotificationDispatchService`, `MemoNotificationEvent` (AFTER_COMMIT, async), `NotificationController`,
  `NotificationConfigController`, `NotificationLogController`, notification entity/log tables.
- **Gaps vs brief:** no mailbox/thread model for *external* email, no delivery-attempt/retry ledger,
  no scheduled send, no undo-send, no templates/signatures store, no provider-event tracking.

### 2.5 Workflow / approvals
- Phase-5 workflow v2 engine (`WorkflowEngineService`, config-driven stages, assignee pools, frozen
  snapshots, per-stage guards), request engine (`/requests`), approval rules/kanban. **Reuse — the brief's
  collaboration must *integrate* with this, never fork a second approval engine.**

### 2.6 Security, RBAC, audit
- Spring Security + JWT, roles SUPER_ADMIN/ADMIN/USER, `@PreAuthorize` method security, URL-level
  permission matrix (`url_api` / `role_permission_api`, fail-open until configured), **DB-driven menus**
  (sidebar built from `getMenusByRole`), audit module (`nexmemo-audit`, `AuditEventPublisher`,
  `AuditLogController`), AES-GCM at-rest key, brute-force/OTP/rate-limit config, ClamAV config keys.
- **See §8 for the BOLA gaps that must be closed.**

### 2.7 Frontend
- Standalone Angular, lazy routes, DB-driven sidebar + `menuGuard`, layout shell, shared `api-service/*`
  (one service per resource), `DynamicFormComponent` (schema-driven), advanced table, toastr, spinners,
  ng-bootstrap modals. Existing pages include `document-compressor`, `transfer-document`, `document-list`,
  `task-manager` (placeholder), `branch-chat`, `system-config`, `audit-logs`, etc.

---

## 3. Gap analysis — brief modules mapped to the codebase

| # | Brief module | Existing basis | Net new work |
| --- | --- | --- | --- |
| 1 | File compressor | `CompressionService` MVP | async job model, extraction, PDF/image strategies, presets, progress, cancel/retry, integrity, guardrails, AV |
| 2 | Mail & messaging centre | chat module + email config | mailbox/labels/folders UI over chat; external delivery, drafts/autosave, scheduled send, undo, templates, delivery ledger |
| 3 | Secure document vault | `DocumentTransfer`, `MemoDocument` | canonical `DocumentRecord` + versions + permissions + share links + retention + quota |
| 4 | Preview & intelligence | `extractedText`, OCR config | preview pipeline, text extraction/index, compare, duplicates (hash), classification |
| 5 | Collaboration & workflow | Phase-5 engine + chat | discussions/tasks linked to memo/workflow ids; reuse engine |
| 6 | AI assistant | none | provider-gated, permission-aware, prompt-injection-hardened layer |
| 7 | Next-gen UI | existing design system | compressor workspace UI, three-pane mail UI, global search, skeletons/empty states |
| 8 | Administration & governance | `system-config`, RBAC, menus | policy keys for formats/sizes/quotas/retention/scanning/AI, enforced server-side |
| 9 | Data model & backend | BaseEntity, DTOs, Liquibase | new entities (below), all Liquibase-owned |
| 10 | Performance/observability | Caffeine, async events | bounded worker pool, retry/backoff, health/metrics, cleanup |
| 11 | Testing | JUnit suite (242 green) | archive-safety, bomb, IDOR, share-link, delivery-retry, concurrency, injection tests |

---

## 4. Proposed target architecture

### 4.1 Principle: reuse-first
Extend the existing chat / transfer / memo / workflow / audit domains. No parallel approval engine, no second
notification system, no duplicate storage abstraction.

### 4.2 Module layout (backend)
Keep the existing 5 modules. New capabilities live in:
- **`nexmemo-core`** — new entities, enums, repositories, DTOs.
- **`nexmemo-service`** — document-processing, storage-catalogue, mail services (fits the existing split).
- **`nexmemo-web`** — controllers, security wiring, job runner.
- **`nexmemo-security`** — reusable authorization checks (document permission evaluator).
- **`nexmemo-audit`** — document/mail audit event types.

### 4.3 Storage strategy
- Keep `StorageService` (local + MinIO). Add a **canonical `DocumentRecord`** that owns a generated
  storage id (never a user filename). All new downloads go through an **authorized, short-lived,
  id-based** endpoint — the raw path-based `/files/download` is retired for vault/memo content.
- SQL Server stores **metadata only**; bytes live in private local/MinIO storage.

### 4.4 Async processing (justified by constraints)
The brief forbids long work in HTTP threads. Proposed minimal, deploy-friendly design:
- DB-backed job rows (`CompressionJob` extended, plus `CompressionJobItem` for batch) + a **bounded
  `ThreadPoolTaskExecutor`** worker (`@Async` with a dedicated executor) — **no external broker required**.
- A `ProcessingJobService` handles status transitions, cancel flags, retry with backoff, idempotency keys.
- If/when the deployment needs horizontal scale, a queue/object-store provider can be added behind the same
  service interface. **Decision needed from you** (see §11).
- Streaming upload/download for large files; resumable uploads deferred unless required.

### 4.5 Antivirus / scanning
ClamAV config keys already exist. Proposed: a `MalwareScanService` (ClamAV daemon) invoked on upload and
before serving, with a configurable **fail-open in dev / fail-closed in prod** switch, plus scan-status on
attachments and documents (the chat module already has a scan-status enum to mirror).

### 4.6 Email
Reuse the existing `JavaMailSender`/dispatch path. Add: `Mailbox`/`MessageThread`/`Message` for *external*
mail, `DeliveryAttempt` ledger, retry/backoff, scheduled send + undo-send (delayed dispatch), templates &
signatures, and an admin health/test-connection panel. **Internal messages stay in the chat domain and are
reported separately from SMTP status.**

### 4.7 AI layer (optional)
Provider-abstracted (`AiProvider` SPI). Enforced server-side: the same authorization checks as document
access, document text treated as untrusted, prompt-injection containment, no confidential doc to unapproved
providers, all outputs staged for human review. **Off by default; enabled per policy.**

---

## 5. UI / navigation proposal (reuse the DB-driven menu)

Add (as new menu rows granting to appropriate roles) under a new **"Workspace"** group, keeping existing items:

```
Workspace
├── Document Compressor   (existing page, upgraded to async workspace)
├── Document Vault        (new — catalogue, folders, versions, sharing, retention)
├── Mail Centre           (new — three-pane; internal = chat domain, external = mail domain)
├── Collaboration         (new — discussions + tasks linked to memos/workflow)
└── AI Assistant          (optional, policy-gated)
```
Plus: **global search** in the header, a real **notification centre** (reuse notifications), quick actions.
Reuse existing layout, `api-service` pattern, toasts, modals, `DynamicFormComponent`, advanced table.

---

## 6. Data model proposal (reuse-first; all Liquibase-owned)

**Reuse unchanged:** `Memo`, `MemoDocument`, `MemoVersion`, `DocumentTransfer`/`DocumentTransferFile`,
chat entities, notification entities, workflow entities, audit entities, `BaseEntity`.

**New candidates** (names indicative; normalise during Phase 2):
- Document core: `DocumentRecord`, `DocumentVersion`, `DocumentPermission`, `DocumentShareLink`,
  `DocumentAuditEvent`, `DocumentIndexRecord`, `DocumentTag` (+ join).
- Processing: `CompressionJob` *(extend)*, `CompressionJobItem`, `ProcessingArtifact`, `ProcessingPreset`.
- Mail: `Mailbox`, `MessageThread`, `Message`, `MessageRecipient`, `MessageAttachment`, `MessageLabel`,
  `UserMessageState`, `ScheduledMessage`, `DeliveryAttempt`.
- Collaboration: `CollaborationComment`, `CollaborationTask`.
- Governance: `StorageQuota` / policy rows (or reuse `system_config`).

**Rules:** business approval actions, document comments and ordinary messages remain **distinct** in the data
model and authorization policy; canonical document records are **referenced** (not copied) across memo/mail/vault.

---

## 7. API contract conventions
- REST under `/api/v1`, `ApiResponse<T>` envelope (existing), `PageResponse` for lists, consistent error
  shape, `@PreAuthorize` on every endpoint, DTOs (never raw entities for new endpoints), streaming for
  large transfer, id-based authorized download URLs.

---

## 8. Security model

**Must fix first (pre-existing BOLA):**
1. `GET /files/download` / `DELETE /files` — replace path-trust with authorization (owner/permission or
   signed, short-lived token bound to the requesting user).
2. `/memo-documents/**` — enforce per-memo authorization on read/add/delete.
3. Compressor — add signature/extension validation, size/entry/nesting/time limits, AV scan; move off the
   request thread.

**New-feature controls:**
- Server-side authorization on every document/job/preview/download; ownership + branch/department isolation;
  share links with expiry + revocation; permission re-checked on each access.
- Upload hardening: signature sniffing (not browser MIME), generated ids, private storage, AV, isolated
  parsers/converters, restricted extraction paths, archive-traversal / zip-bomb / decompression-bomb /
  oversized-image limits.
- Secrets (SMTP, AI keys) never reach Angular/logs/audit; **backend-enforced** policies (UI is never a control).
- Audit events with before/after + secret redaction.
- AI: permission-aware retrieval, untrusted-input handling, human-in-the-loop, no autonomous banking actions.

---

## 9. Phased implementation plan (each phase: files, migrations, endpoints, deps, tests, risks)

| Phase | Theme | Key deliverables |
| --- | --- | --- |
| **P2** | Document storage, metadata & access-control foundation | `DocumentRecord`/`Version`/`Permission`; fix the 3 BOLA issues; authorized id-based download; Liquibase migration; tests |
| **P3** | Compressor MVP (async + secure) | job runner + worker; extraction + PDF/image strategies; presets; progress via STOPS; cancel/retry; integrity; guardrails; AV; workspace UI; tests (archive safety, bombs, size accounting) |
| **P4** | Secure vault, preview, sharing | catalogue/folders/tags; preview pipeline; versions + hash dedupe; share links (expiry/revoke); retention/quota; tests (IDOR, link expiry/revocation) |
| **P5** | Internal messaging & attachment integration | upgrade existing chat into the mail-centre UI (labels/folders/search); attach vault documents; tests (draft autosave, threads) |
| **P6** | External email, delivery tracking, advanced mail UI | mailbox/thread/message, delivery ledger + retry, scheduled/undo, templates/signatures, admin SMTP panel; tests (delivery failure/retry) |
| **P7** | Collaboration, comments & tasks → workflow | discussions/tasks linked to memo/workflow ids; reuse engine; tests (integration with memos) |
| **P8** | Search, OCR & optional AI | text extraction/index (evaluate the declared Elasticsearch), OCR, compare, AI SPI + safeguards; tests (permission-aware search, prompt-injection) |
| **P9** | Administration, analytics, hardening | policy keys, monitoring/metrics/health, quota reporting, cleanup, regression + E2E; full security review |

Each phase ends with: **changed files, Liquibase changesets, endpoints, dependencies, tests, config
requirements, unresolved risks** — then a stop for your review before the next phase.

---

## 10. Proposed new dependencies (all pending approval — none added yet)

| Need | Candidate | Note |
| --- | --- | --- |
| Archives | **Apache Commons Compress** | *already present* |
| PDF optimise/render | Apache **PDFBox** | license Apache-2.0; verify version present in `nexmemo-service` |
| Images | **TwelveMonkeys ImageIO** / `ImageIO` + `java.awt` | JDK-native first; TwelveMonkeys only for exotic formats |
| Thumbnails | **Thumbnailator** | optional; Apache-2.0 |
| OCR | **Tesseract (tess4j)** | already configured but currently commented out |
| Malware | **ClamAV** (clamd client) | config keys already exist |
| AI | provider HTTP client | only if AI phase approved |

No upgrade of Spring Boot / Angular is proposed. Every addition will be checked for license + CVE history
before use, as the brief requires.

---

## 11. Risks & open decisions

**Risks**
- Storage is **local/on-disk** by default → multi-instance needs MinIO or shared storage. Security of local
  path downloads is the biggest current gap.
- Async jobs currently have **no broker**; DB-backed worker is simpler but not horizontally scalable.
- `soft`/in-memory compression will not reduce already-compressed formats (pdf/jpg/zip) — UI must say so.
- OCR/AV add **native dependencies** and operational overhead; both default **off**.
- Elasticsearch is declared but its actual usage is unconfirmed → confirm before designing search on it.

**Open decisions (need your input)**
1. Approve the reuse-first architecture and the P2→P9 ordering?
2. Async processing: **DB-backed worker (simple, single-node)** vs **queue + object store (scalable)**?
3. Vault: extend `DocumentTransfer` into the vault, or introduce a separate `DocumentRecord` catalogue
   *alongside* it (recommended: separate canonical record, keep transfer as a workflow on top)?
4. Internal messaging: confirm we **extend the existing chat module** rather than build new mailbox tables.
5. AI: include in scope now (P8, off by default) or defer entirely?
6. Confirm **fixing the 3 pre-existing BOLA issues in P2** is acceptable (it touches auth on existing APIs).

---

## 12. Recommendation

Proceed with **Phase 2 (document storage, metadata & access-control foundation)** after approval, because
every later module depends on a correct, authorized document core — and because the three BOLA issues found
above are security defects in production code that should be closed before adding surface area on top.

**Awaiting your approval of the plan (and answers to §11).**
