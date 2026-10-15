# Enterprise Phase 4 — Secure Document Vault (catalogue, preview, versions, sharing, retention & quota)

Implements P4 of the enterprise roadmap: turn the Phase 2 document foundation into a usable **secure
document vault** — folders/tags, a safe preview pipeline, version history with hash de-duplication,
expiring/revocable share links, retention/legal-hold controls and a per-user quota — all enforced
server-side and surfaced through the existing frontend (replacing the `document-list` placeholder).

---

## 1. Scope delivered

| Capability | What shipped |
| --- | --- |
| **Catalogue** | Owner-scoped folder tree (`document_folder`), `folder_id` on documents, tag filtering, combined list filters (search / folder / classification / status / tag). |
| **Preview pipeline** | Magic-byte classification (`PreviewKind`), inline only for verified images/PDF/small-UTF-8-text, hardened response headers; everything else is download-only. |
| **Versions** | Existing version history + **restore** (appends a new revision that reuses the old bytes); pre-upload **duplicate check** by SHA-256 and optional **reuse-existing** upload. |
| **Sharing** | Single validator (`ShareLinkGuard`); token-gated landing + download + **inline preview**; password moved to the POST body; classification policy blocks RESTRICTED/CONFIDENTIAL sharing unless explicitly enabled. |
| **Retention / lifecycle** | `status` transitions incl. `LEGAL_HOLD`; `retention_until`; delete blocked under legal hold or unexpired retention (admin-only `force`); deleting a document revokes all its share links. |
| **Quota** | Per-user byte quota measured across **all revisions**; fail-closed enforcement on upload and on adding a version. |
| **Security** | Every endpoint re-authorizes through `DocumentAuthorizationService`; public routes are token-gated; new exception type maps to real HTTP statuses. |
| **Tests** | +57 tests (305 → **362**): folder tree, preview types, quota, share-link guard, lifecycle/retention, dedupe, restore, share-policy, plus the refactor of the existing service tests. |
| **Frontend** | Real vault UI at the existing `/layout/document-list` route (no menu migration needed). |

**Deferred / out of scope (recorded as risks):** PDF *structural* optimisation (needs a vetted library);
repair of legacy gzip-stored `MemoDocument` rows (bundled with the P5 attachment work); restart recovery
stays single-node.

---

## 2. Design decisions

1. **Folders are an organisational overlay, never a permission boundary.** A folder is owned by exactly
   one user (`owner_id`); only the owner (or an admin) can see or use it. A document's access is still
   decided solely by `DocumentAuthorizationService` — moving a document into a folder changes nothing
   about who can read it. A folder id belonging to another user is treated as **not found**.
2. **Previewability is decided from bytes, not the declared content type.** `FileSignatureValidator`
   (Phase 3) classifies the head of the file. Only `PNG/JPEG/GIF/BMP/WEBP → IMAGE`, `PDF → PDF`, and a
   small, valid UTF-8 sample with no NUL/control bytes `→ TEXT` are renderable. HTML/SVG are treated as
   *text* (served `text/plain`) and can never execute; anything unknown is `NONE` (download-only).
   This removes the stored-XSS risk entirely rather than relying on a content-type allow-list.
3. **One place validates a share link.** `ShareLinkGuard` owns existence, revocation, expiry, download
   cap and password checks and is used by both the download and the preview paths, so the rules cannot
   drift apart.
4. **The share password travels in a POST body.** The legacy GET form is retained (deprecated) so
   existing links keep working, but new callers use the POST so the password never lands in access logs,
   browser history or a `Referer` header.
5. **Quota counts every revision.** A superseded revision still occupies disk, so the quota sums
   `document_version.size_bytes` for the user's live documents — not just the current revision.
6. **Reuse the existing route.** The vault replaces the `document-list` placeholder component, keeping
   route `/layout/document-list` and the seeded menu id 9; only the menu label was updated
   (`20261016-3`), so no menu/permission migration is required.
7. **Explicit exception → status mapping.** `DocumentPolicyException` carries an `HttpStatus` and has a
   dedicated handler, because `GlobalExceptionHandler` has a catch-all `Exception` handler that would
   otherwise turn policy denials into `500`s.

---

## 3. Data model & migrations (Liquibase-owned)

`db/changelog/db.changelog-master.xml`:

| Changeset | Change |
| --- | --- |
| `20261016-1` | New table `document_folder` (`owner_id`, `parent_id`, `name`, `path`, soft-delete + audit columns) with `idx_docfolder_owner`, `idx_docfolder_parent`. |
| `20261016-2` | `document_record` + `folder_id (BIGINT)`, `hold_reason (VARCHAR(500))`, index `idx_docrec_folder`. |
| `20261016-3` | Menu label for `/layout/document-list` → **Document Vault**. |

`path` stores the materialised ancestry (`/5/42/`) so a tree/breadcrumb can be rendered without
recursive queries; re-parenting rewrites descendant paths in one pass. `ddl-auto: validate` is kept, so
the entities and the schema must agree.

New/changed entities: `DocumentFolder` (new); `DocumentRecord` (+`folderId`, +`holdReason`, +index),
`DocumentDto` (+`folderId`, +`holdReason`).

---

## 4. Backend files

**Core (`nexmemo-core`)**
- `enums/PreviewKind.java` — `IMAGE | PDF | TEXT | NONE`.
- `entity/DocumentFolder.java` — owner-scoped node.
- `dto/` — `DocumentFolderDto`, `SaveFolderRequest`, `MoveDocumentRequest`, `DocumentStatusRequest`,
  `DocumentQuotaDto`, `PreviewMetadataDto`, `ShareLinkLandingDto`, `RedeemShareLinkRequest`,
  `DuplicateCheckResult`.
- `repository/DocumentFolderRepository.java` — added.
- `repository/DocumentRecordRepository.java` — filtered `findReadable(...)`, `findBySha256AndIsDeletedFalse`,
  `countByOwnerIdAndIsDeletedFalse`, `countByFolderIdAndIsDeletedFalse`, `sumLiveSizeByOwner`.
- `repository/DocumentVersionRepository.java` — `sumLiveVersionBytesByOwner` (**native SQL**, see §7).

**Web (`nexmemo-web`)**
- `config/DocumentProperties.java` — `nexmemo.documents.*` (`share-base-url`, `quota.*`, `share.*`, `preview.*`).
- `utils/PreviewSupport.java` — `previewKindFor` / `mimeFor` / `isText`.
- `service/ShareLinkGuard.java` — the single share-link validator.
- `service/DocumentQuotaService.java` — usage + fail-closed `assertWithinQuota`.
- `service/DocumentFolderService.java` — tree CRUD, cycle-safe re-parenting, breadcrumb.
- `service/DocumentPreviewService.java` — preview metadata/stream for authenticated users and share links.
- `exception/DocumentPolicyException.java` — status-carrying exception.
- `exception/GlobalExceptionHandler.java` — handler for `DocumentPolicyException`.
- `service/DocumentService.java` — extended upload (folder + reuse), filtered list, move, status,
  restore, duplicates, retention metadata, delete guards, share classification policy.
- `Controller/DocumentController.java` — full vault REST surface (see §5).
- `Controller/DocumentFolderController.java` — `/document-folders`.
- `resources/application.yaml` — `nexmemo.documents` block.

---

## 5. API surface

All routes require authentication unless marked **public**; every call is re-authorized server-side.

| Method & path | Purpose |
| --- | --- |
| `POST /documents` | Upload (`folderId`, `reuseExisting`, `classification`, `tags`…). |
| `GET /documents` | Filtered, paged list (`search`, `folderId`, `classification`, `status`, `tag`). |
| `GET /documents/{id}` | Metadata. |
| `PATCH /documents/{id}` | Update metadata / classification / **retention** / tags. |
| `DELETE /documents/{id}?force=` | Soft-delete (revokes share links; retention/legal-hold guarded). |
| `PATCH /documents/{id}/folder` | Move to a folder (or uncategorised). |
| `PATCH /documents/{id}/status` | Lifecycle change (legal hold = admin). |
| `GET/POST /documents/{id}/versions` | List / add a version. |
| `POST /documents/{id}/versions/{no}/restore` | Restore a prior revision. |
| `GET /documents/{id}/preview-metadata` | Preview kind / type / size. |
| `GET /documents/{id}/preview` | Inline stream (verified types only). |
| `GET /documents/{id}/download` | Download (attachment). |
| `GET /documents/duplicates?sha256=` · `POST /documents/duplicates/check` | De-duplication lookup / pre-upload check. |
| `GET /documents/quota` | Usage vs limit. |
| `GET/POST/DELETE /documents/{id}/permissions…` | Permission grants. |
| `GET/POST/DELETE /documents/{id}/share-links…` | Share-link management. |
| `GET /documents/{id}/audit` | Audit trail. |
| **public** `GET /documents/shared/{token}` | Landing metadata (no password check). |
| **public** `POST /documents/shared/{token}/download` | Redeem (password in body). |
| **public** `GET /documents/shared/{token}/preview` | Inline preview under the same link policy. |
| `GET/POST/PATCH/DELETE /document-folders…` | Folder tree CRUD + breadcrumb. |

---

## 6. Security notes

- **No ID-based access.** Knowing a document id is never sufficient — `DocumentAuthorizationService`
  decides and BOLA attempts return `403` (or `404` for foreign folders).
- **Preview hardening.** `nosniff`, `Content-Disposition: inline`, a locked-down CSP
  (`default-src 'none'; img-src 'self' data: blob:; style-src 'unsafe-inline'; object-src 'none'`) and
  `Cross-Origin-Resource-Policy: same-origin`. `sandbox` is intentionally omitted so the browser's PDF
  viewer works; HTML/SVG are served as `text/plain` and are not treated as renderable kinds.
- **The Angular app renders previews from blobs** fetched with the JWT, so it never navigates to an
  authenticated URL (no token in history) and the server's frame/CSP headers don't interfere with the app.
- **Share links** are re-validated per request; deleting a document revokes all of its links; the share
  classification policy denies RESTRICTED/CONFIDENTIAL by default.
- **Quota is fail-closed** (`413`), measured across all revisions.
- **Legal hold / retention** block deletion; only an admin may `force` a retained delete, and legal hold
  can never be bypassed.

---

## 7. Gotchas discovered (worth remembering)

- **Generic `BaseEntity<PK> id` breaks HQL `IN (subquery)`.** Hibernate statically types `d.id` as the
  erased `Serializable`, so `where v.documentId in (select d.id …)` fails validation with
  *"Cannot compare left expression of type 'java.lang.Long' with right expression of type
  'java.io.Serializable'"*. Equality (`p.documentId = d.id`) happens to be lenient, so this only bit the
  version-sum query. Fixed with a **native query** (`sumLiveVersionBytesByOwner`) that joins
  `document_version`↔`document_record`; `ddl-auto: validate` + a real boot caught it.
- **`GlobalExceptionHandler` has a catch-all**, so any new status exception needs its own handler.
- **Replacing the placeholder component** (not the route) kept the DB-driven menu working without a
  migration.
- **`NgbPagination` page vs index** and the `blob:` URL cleanup (`URL.revokeObjectURL`) needed care in
  the modal preview to avoid leaks.

---

## 8. Verification

- **Backend:** `mvnw -pl nexmemo-web -am test` → **362 tests, 0 failures / 0 errors**, BUILD SUCCESS.
  New suites: `DocumentFolderServiceTest` (13), `DocumentPreviewServiceTest` (11), `DocumentQuotaServiceTest` (5),
  `ShareLinkGuardTest` (10), `PreviewSupportTest` (7); `DocumentServiceTest` rewritten for the refactor (28).
- **Boot:** packaged jar booted against the dev SQL Server on `dev`; Liquibase applied `20261016-1/2/3`
  successfully (100 changesets total) and `Started NexMemoApplication` was reached with Hibernate
  `ddl-auto=validate` passing (i.e. every entity/repository query is valid).
- **Frontend:** `ng build --configuration development` → exit 0, `document-list-component` bundle built.

---

## 9. Next phase

P5 — **internal messaging** on the existing chat module (share documents into conversations, previews,
permission-aware). The deferred legacy-`MemoDocument` gzip repair should be bundled there. P6 → external
email/delivery; P7 → collaboration/workflow; P8 → search/OCR/optional AI; P9 → admin/observability/hardening.
