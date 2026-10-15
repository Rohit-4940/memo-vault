# Phase 2 — Document Storage, Metadata & Access-Control Foundation

**Status:** implemented and verified (backend). Frontend catalogue UI is intentionally deferred to
Phase 4 (Secure Document Vault) so the foundation can be reviewed first.

This phase delivers the canonical document core and closes the pre-existing broken-object-authorization
(BOLA/IDOR) defects that the Phase 0/1 assessment flagged in production code.

---

## 1. What was delivered

### 1.1 Canonical document core (new)
- Entities: `DocumentRecord`, `DocumentVersion`, `DocumentPermission`, `DocumentShareLink`,
  `DocumentAuditEvent`, `DocumentTag`.
- Enums: `DocumentStatus`, `DocumentClassification`, `DocumentSourceType`, `PermissionSubjectType`.
- Repositories for all of the above; `DocumentRecordRepository.findReadable(...)` enforces the
  read scope in a single query (owner + USER/ROLE/BRANCH/DEPARTMENT grants + optional search).
- DTOs: `DocumentDto`, `DocumentVersionDto`, `DocumentPermissionDto`, `DocumentShareLinkDto`,
  `DocumentAuditEventDto`, `GrantPermissionRequest`, `CreateShareLinkRequest`.

### 1.2 Access-control foundation (new)
- `DocumentAuthorizationService` — server-side capability resolution (read/write/delete/share) from
  ownership, administrator status and explicit grants. Denials are logged **and** audited, then thrown
  as `AccessDeniedException`. A soft-deleted document is treated as non-existent.
- `DocumentService` — upload, versioning, authorized download, metadata update, soft delete,
  permissions, share links and per-document audit.
- `DocumentController` — REST surface under `/documents` (see §3).

### 1.3 Security fixes to existing code (BOLA/IDOR)
| Location | Before | After |
| --- | --- | --- |
| `GET /files/download?path=` | `isAuthenticated()` only → any user could read **any** file by path | `SUPER_ADMIN`/`ADMIN` only; logged as an administrative action |
| `DELETE /files?path=` | `isAuthenticated()` only → any user could delete **any** file by path | `SUPER_ADMIN`/`ADMIN` only; logged |
| `/memo-documents/memo/{memoId}` | `isAuthenticated()` only | `assertCanViewMemo` |
| `POST /memo-documents/memo/{memoId}/upload` | `isAuthenticated()` only | `assertCanAttachDocument` (editor, current approver or stage assignee) |
| `DELETE /memo-documents/{id}` | `isAuthenticated()` only | `assertCanEditMemo` (resolved via the owning memo) |

New authorized, id-based endpoint `GET /memo-documents/{id}/download` replaces reliance on the generic
path download for memo attachments.

### 1.4 Storage correctness fix
`StorageService` gained `uploadRaw(path, bytes, fileName)`. The pre-existing
`upload(byte[], …)` transparently gzip-compresses files above `nexmemo.compression.threshold-bytes`
(10 KB), which would corrupt a document vault and break the recorded SHA-256. The document core and new
memo attachments now store bytes **exactly**; local and MinIO implementations both provide the new method.

---

## 2. Database migrations (Liquibase, SQL Server)

Added to `nexmemo-core/…/db/changelog/db.changelog-master.xml`:

| Changeset | Table | Notes |
| --- | --- | --- |
| `20261014-1` | `document_record` | metadata only; bytes live in storage under a generated `storage_key` |
| `20261014-2` | `document_version` | per-revision snapshots; FK → `document_record`; unique `(document_id, version_no)` |
| `20261014-3` | `document_permission` | additive grants; unique `(document_id, subject_type, subject_id)` |
| `20261014-4` | `document_share_link` | opaque token (unique), expiry, revocation, cap, optional BCrypt password |
| `20261014-5` | `document_audit_event` | per-document audit trail |
| `20261014-6` | `document_tag` | free-text tags; unique `(document_id, tag)` |

All changesets are guarded with `preConditions onFail="MARK_RAN"` and use the project's existing
conventions (`BIGINT` identity, `version` optimistic-lock column, `created_by_id/modified_by_id`,
`created_date/modified_date`).

---

## 3. API endpoints (context path `/api/v1`)

### Document vault — `/documents` (authenticated unless noted)
| Method | Path | Capability required |
| --- | --- | --- |
| POST | `/documents` | authenticated (caller becomes owner) |
| GET | `/documents?page&size&search` | readable scope (server-filtered) |
| GET | `/documents/{id}` | read |
| PATCH | `/documents/{id}` | write |
| DELETE | `/documents/{id}` | delete (soft delete + revoke share links) |
| GET | `/documents/{id}/versions` | read |
| POST | `/documents/{id}/versions` | write |
| GET | `/documents/{id}/download?version=` | read |
| GET | `/documents/{id}/permissions` | read |
| POST | `/documents/{id}/permissions` | share |
| DELETE | `/documents/{id}/permissions/{permissionId}` | share |
| GET | `/documents/{id}/share-links` | share |
| POST | `/documents/{id}/share-links` | share |
| DELETE | `/documents/{id}/share-links/{shareLinkId}` | share |
| GET | `/documents/{id}/audit` | read |
| GET | `/documents/shared/{token}/download?password=` | **public** — the token is the credential; expiry/revocation/cap/password re-checked every call |

### Memo attachments — `/memo-documents`
| Method | Path | Capability required |
| --- | --- | --- |
| GET | `/memo-documents/memo/{memoId}` | memo view |
| GET | `/memo-documents/{id}/download` | memo view (new) |
| POST | `/memo-documents/memo/{memoId}/upload` | memo attach (editor/approver/assignee) |
| DELETE | `/memo-documents/{id}` | memo edit |

---

## 4. Changed / added files

**New — `nexmemo-core`**
`enums/{DocumentStatus,DocumentClassification,DocumentSourceType,PermissionSubjectType}.java`,
`entity/{DocumentRecord,DocumentVersion,DocumentPermission,DocumentShareLink,DocumentAuditEvent,DocumentTag}.java`,
`repository/{DocumentRecord,DocumentVersion,DocumentPermission,DocumentShareLink,DocumentAuditEvent,DocumentTag}Repository.java`,
`dto/{DocumentDto,DocumentVersionDto,DocumentPermissionDto,DocumentShareLinkDto,DocumentAuditEventDto,GrantPermissionRequest,CreateShareLinkRequest}.java`

**New — `nexmemo-web`**
`service/DocumentAuthorizationService.java`, `service/DocumentService.java`, `Controller/DocumentController.java`

**New — tests**
`service/DocumentAuthorizationServiceTest.java` (13 tests), `service/DocumentServiceTest.java` (17 tests)

**Modified**
`storage/StorageService.java`, `storage/LocalStorageService.java`, `storage/MinioStorageService.java`,
`Controller/MemoDocumentController.java`, `Controller/FileController.java`, `service/MemoService.java`,
`service/MemoAuthorizationService.java`, `nexmemo-security/…/SecurityConfig.java`,
`nexmemo-core/…/db/changelog/db.changelog-master.xml`

---

## 5. Configuration

| Key | Default | Purpose |
| --- | --- | --- |
| `nexmemo.documents.share-base-url` | `http://localhost:4202` | Base URL used to compose share links |

`/documents/shared/**` was added to the security allow-list (public endpoints) because the opaque token
is the credential. It is a `GET`, so CSRF is not applicable.

---

## 6. Dependencies

**None added.** The phase uses existing `StorageService`, JPA, Spring Security and BCrypt only.

---

## 7. Verification

- `mvn -pl nexmemo-web -am compile` → clean.
- `mvn -pl nexmemo-web -am test` → **272 tests, 0 failures, 0 errors, BUILD SUCCESS** (was 242; +30 new).
- Liquibase `20261014-1..6` applied against the dev SQL Server database and Hibernate
  `ddl-auto=validate` passed on boot.

---

## 8. Unresolved risks / deferred to later phases

- **Antivirus scanning** and **real file-signature (magic-byte) validation** are not yet applied to
  uploads — scheduled for Phase 3 (compressor) and Phase 9 (hardening). Files are currently stored
  byte-exact but not scanned.
- **No frontend** for the vault yet (Phase 4). Existing `document-compressor`/`document-list` pages are
  untouched.
- **Legacy memo documents** uploaded through the old gzip-compressing path remain stored compressed;
  new uploads are byte-exact. A back-fill/repair task is noted for Phase 4.
- The public share endpoint relies on the existing rate-limit filter; a dedicated per-token throttle and
  download-attempt lockout are deferred to hardening.
- `document_record.status`/`retention_until` are modelled but retention enforcement/legal hold is Phase 4.
