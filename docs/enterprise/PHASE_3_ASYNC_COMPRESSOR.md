# Enterprise Phase 3 — Async, Secure Compressor

> Status: **implemented & verified**. Extends the pre-existing MVP compressor (`CompressionJob`,
> `CompressionService`, `CompressionController`, `document-compressor` page) — nothing was rebuilt
> from scratch. Work is uncommitted, awaiting review before Phase 4.

## Why

The MVP compressor ran the whole compression **on the HTTP request thread**, held a DB transaction
for its duration, read the entire file into memory, wrote output to an **absolute path** bypassing
`StorageService`, and emitted progress to a topic that (a) the WebSocket interceptor actually
**blocks** and (b) would have leaked every user's filenames had it been allowed. It had no archive
guardrails, no signature validation, no malware scan and no cancel/retry.

Phase 3 turns it into an asynchronous, bounded, guardrailed pipeline.

## Bugs fixed

| # | Issue | Fix |
| --- | --- | --- |
| 1 | Compression executed on the request thread, inside one long transaction | Submission persists a `QUEUED` job and hands the id to a **bounded worker pool** (`compressionExecutor`); the request returns `202` |
| 2 | `@Async` path received a `MultipartFile` whose temp file could already be gone | Input bytes are persisted to private storage at submission; the worker reads them back |
| 3 | Progress pushed to `/topic/compress/{userId}` | Switched to the **private** `/user/queue/compress` (the interceptor is deny-by-default; a shared topic was blocked *and* a cross-user leak) |
| 4 | Output stored via absolute path, bypassing `StorageService` | Output written with `StorageService.uploadRaw` under a generated key (`compression/{userId}/output/{uuid}_{name}`) |
| 5 | No archive-bomb / traversal protection | `SafeArchiveService` inspects archive inputs with hard caps (entries, uncompressed bytes, ratio, nesting) and rejects `..`/absolute entry names |
| 6 | Filename trusted for the entry name written into archives | `FileCompressionUtil.sanitizeEntryName` strips directories, `..`, drive letters, control chars (zip-slip defence) |
| 7 | Files never scanned for malware | Inputs are handed to the existing fail-closed `ClamAvScanner` before processing |
| 8 | Corrupt/truncated archives advertised as valid | `FileCompressionUtil.verifyReadable` drains the produced archive; `POST /{id}/verify` re-checks the stored checksum |
| 9 | No cancel/retry; jobs could hang forever | `cancel` (queued = immediate, running = cooperative), `retry` (capped at 3 attempts), and a startup sweep re-queues jobs interrupted by a restart |
| 10 | Module gated to admins only | Opened to any authenticated user; every lookup is scoped by `id + createdBy` |

## Design

### Asynchronous lifecycle

```
POST /files/compress ──► validate size ──► uploadRaw(input) ──► save job QUEUED ──► dispatch
                                                                          │
                                              compressionExecutor.execute(runJob)   (bounded pool)
                                                                          │
   runJob: QUEUED → IN_PROGRESS → [signature] → [AV scan] → [archive inspect]
           → compress/optimise → uploadRaw(output) → verifyReadable → COMPLETED | FAILED
                                                                          │
                       progress events ◄── convertAndSendToUser(userId, "/queue/compress")
```

- **Bounded pool** (`AsyncConfig.compressionExecutor`): core 2, max 4, queue 200, `AbortPolicy`.
  On saturation the job is marked `FAILED` and the request returns `503` — the request thread is
  never used to run compression and never blocks on the queue.
- **Transactions:** the service is no longer class-level `@Transactional`; only short `cancel` /
  `retry` / `verify` / `delete` operations are transactional. Workers never hold a transaction
  during the CPU work.
- **Cancellation** is cooperative: checked at each stage boundary (`cancelled(job)` re-reads the row).

### Guardrails (`nexmemo.compression.guardrails`)

| Setting | Default | Meaning |
| --- | --- | --- |
| `inspect-archives` | `true` | Walk archive inputs for bombs/traversal |
| `scan-with-clamav` | `true` | Send inputs to clamd (fail-closed; no-op when clamd disabled) |
| `max-entries` | `2000` | Entry-count cap |
| `max-uncompressed-bytes` | `524288000` | Cumulative expansion cap (checked while streaming) |
| `max-compression-ratio` | `200` | Expansion-ratio heuristic |
| `max-nesting-depth` | `1` | Nested-archive depth allowed |

`max-input-bytes` (default 100 MB) is enforced before any work is scheduled.

### Compression strategies (`CompressionPreset`)

- `FAST` / `BALANCED` / `MAXIMUM` → deflate level 1 / 6 / 9 (GZIP, ZIP, TAR_GZ).
- `IMAGE_OPTIMIZE` → re-encode an image in place via the JDK ImageIO stack
  (`ImageCompressionUtil`), with a ~40 MP decode-bomb guard and no-op when re-encoding would not
  shrink the file. Non-image uploads are rejected for this preset.
- Content is identified by **real magic bytes** (`FileSignatureValidator`), never by filename or
  `Content-Type`.

> **Deferred:** PDF *structural* optimisation is intentionally not included. Doing it safely needs a
> vetted library and a byte-level review; PDFBox is present but unused and unvalidated. This is a
> conscious Phase 3 scope boundary, not an oversight.

## Files

### New (core)
- `nexmemo-core/.../enums/CompressionPreset.java`
- `nexmemo-core/.../repository/CompressionJobRepository.java` — added `findByIdAndCreatedBy`,
  `findByCreatedByAndBatchIdOrderByCreatedDateAsc`, `findByStatus`; stats queries now count only
  `COMPLETED` jobs.

### New (web)
- `nexmemo-web/.../config/CompressionProperties.java` — `nexmemo.compression.*` (limits, executor,
  guardrails).
- `nexmemo-web/.../config/AsyncConfig.java` — bounded `compressionExecutor`.
- `nexmemo-web/.../utils/FileKind.java`, `FileSignatureValidator.java` — magic-byte detection.
- `nexmemo-web/.../utils/ImageCompressionUtil.java` — ImageIO optimisation.
- `nexmemo-web/.../service/SafeArchiveService.java`, `ArchiveSecurityException.java` — guardrails.

### Changed (core)
- `enums/CompressionStatus.java` — added `QUEUED`, `CANCELLED`.
- `entity/CompressionJob.java` — `progressPercent`, `cancelRequested`, `attempts`, `preset`,
  `batchId`, `inputStorageKey`, `storageKey`, `verified`, `archiveEntryCount`,
  `archiveUncompressedBytes`.
- `dto/CompressionResponse.java` — corresponding fields.
- `db/changelog/db.changelog-master.xml` — changesets **`20261015-1/2/3`**.

### Changed (web)
- `utils/FileCompressionUtil.java` — deflate levels, `sanitizeEntryName`, `verifyReadable`; ZIP now
  written with commons-compress so a level can be set; fixed the gzip trailer being dropped when
  bytes were captured before `close()`.
- `service/CompressionService.java` — full async rewrite.
- `Controller/CompressionController.java` — new/changed endpoints (below).
- `resources/application.yaml` — guardrail + executor settings.

### Frontend
- `admin-front/.../api-service/file.service.ts` — preset param; `cancelJob`/`retryJob`/`verifyJob`;
  extended `CompressionResponse`.
- `admin-front/.../pages/document-compressor/*` — preset selector, safe `/user/queue/compress`
  subscription, progress %, polling fallback, cancel/retry/verify actions, new filter values.

## Migration

`db.changelog-master.xml` (Liquibase, SQL Server, `ddl-auto: validate`):

- `20261015-1` — `progress_percent`, `cancel_requested`, `attempts`, `preset`, `batch_id` + index
  `idx_cj_batch`.
- `20261015-2` — `input_storage_key`, `storage_key`.
- `20261015-3` — `verified`, `archive_entry_count`, `archive_uncompressed_bytes`.

Legacy `storage_path` is retained as a read-only fallback for pre-Phase-3 rows.

## Endpoints (`/files/compress`)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/` | `file`, `type`, `preset` → `202` with a `QUEUED` job |
| POST | `/batch` | `files`, `type`, `preset` → `202` with a list of jobs sharing a `batchId` |
| GET | `/history` | paged, `status`/`search` filters |
| GET | `/batch/{batchId}` | jobs in a batch |
| GET | `/{id}` | job detail (includes `progressPercent`) |
| GET | `/{id}/download` | streams the artefact from private storage |
| POST | `/{id}/cancel` | queued → immediate; running → cooperative |
| POST | `/{id}/retry` | failed/cancelled, capped at 3 attempts |
| POST | `/{id}/verify` | re-checks SHA-256 + archive readability, persists `verified` |
| DELETE | `/{id}` | refused while queued/running; removes input and output blobs |

## Dependencies

**None added.** Uses existing `commons-compress` 1.26.0, the JDK ImageIO stack, the existing
`ClamAvScanner` and `StorageService`.

## Tests

New (all green): `FileCompressionUtilTest` (7), `FileSignatureValidatorTest` (4),
`SafeArchiveServiceTest` (9 — traversal, absolute path, entry flood, size bomb, ratio bomb, nesting),
`ImageCompressionUtilTest` (3), `CompressionServiceTest` (10 — queue-not-run, completion, malware,
guardrail, cancel, retry, delete-while-running, verify, image-optimise).

**Full suite:** `nexmemo-web -am test` → **305 tests, 0 failures, 0 errors, BUILD SUCCESS**
(`MAVEN_EXIT=0`).
Frontend: `ng build --configuration development` → exit 0.

## Verification

- Packaged jar booted with `--spring.profiles.active=dev`: Liquibase applied `20261015-1/2/3`
  (97 changesets total), Hibernate `ddl-auto=validate` passed, `Started NexMemoApplication`.

## Unresolved risks / follow-ups

- **PDF optimisation** deferred (see above).
- **AV in dev** is disabled (`nexmemo.security.clamav.enabled=false`); the scan is a no-op there and
  only engages when clamd is configured. Production must enable it.
- **Archive inspection cost** is bounded by the caps but a large-but-legitimate archive still streams
  up to `max-uncompressed-bytes`; tune per environment.
- **Restart recovery** re-queues `IN_PROGRESS` jobs on `ApplicationReadyEvent`; with a single node
  (agreed design) this is safe, but a multi-node deployment would need a claim/lease column.
