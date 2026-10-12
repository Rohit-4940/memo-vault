# Phase 2f — Secure Attachments

Status: **Implemented** (backend + frontend + tests).
Related plan item: **C-15 file attachments (P1)** in
`docs/chat/PHASE_1_ANALYSIS_AND_PLAN.md` (Phase 2 — Collaboration features).

---

## 1. Purpose

Let users attach files to chat messages. Files are stored **in SQL Server** (`VARBINARY(MAX)`),
scanned for malware with a self-hosted **ClamAV** daemon (clamd, direct `INSTREAM` socket
protocol — no client library), and served back only after a clean scan, to authorized readers.

Design goals (security-first):

- **Scan on upload, fail closed.** If ClamAV is enabled but unreachable, uploads are **rejected**
  (no bytes are ever stored unscanned in production). When disabled (dev), scan is skipped and
  the file is stored as clean — the flag is explicit in `application.yaml`.
- **Whitelist, not blacklist.** Only
  `pdf, doc, docx, xls, xlsx, ppt, pptx, txt, csv, jpg, jpeg, png, gif, zip` are accepted.
- **Size cap 25 MB** (`MAX_SIZE_BYTES`), enforced server-side before any bytes are stored.
- **Integrity + audit.** Every stored file has a `sha256`, `uploaded_by_id`, size and scan
  result. The **original** bytes are kept for re-scanning; downloads stream from the DB.
- **Authorized downloads.** The download stream re-checks `assertCanAccessConversation` on the
  attachment's conversation and refuses anything whose scan status is not `CLEAN`.
- **XSS-safe filenames.** The stored `file_name` is sanitized: path separators and control
  characters stripped.

---

## 2. Classification

| Field | Value |
|---|---|
| Priority | **P1** |
| Complexity | High (clamd wire protocol, DB BLOB storage, fail-closed policy, streaming downloads) |
| Dependencies | Phase 0 (authz), 1a; external: a clamd host in production |
| Risks | Medium (deployment must run clamd; fail-closed by design) |
| Order | Fifth Phase 2 feature (2f) |

---

## 3. Data model / DB migration

Liquibase **`20260616-3`** — table `chat_attachment`:

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK identity | |
| `message_id` | BIGINT NOT NULL | FK → `chat_message` (`fk_cattach_message`) |
| `conversation_id` | BIGINT NOT NULL | denormalized for download authz + index |
| `file_name` | VARCHAR(255) NOT NULL | sanitized |
| `content_type` | VARCHAR(120) | as reported by the client (display only) |
| `size_bytes` | BIGINT NOT NULL | |
| `sha256` | CHAR(64) NOT NULL | hex digest |
| `scan_status` | VARCHAR(20) NOT NULL DEFAULT 'PENDING' | PENDING → CLEAN / INFECTED / ERROR |
| `file_data` | VARBINARY(MAX) | original bytes |

`chat_message` gains **no** column — attachment ids are rendered via a per-message grouping
query at DTO build time.

---

## 4. Backend changes

| File | Change |
|---|---|
| `ClamAvScanner` (new) | opens a raw `Socket` to `clamav.host:port`, sends `zINSTREAM\0` then 4-byte big-endian length-prefixed chunks, terminates with `\0\0\0\0`; parses the reply; `"stream: ok"` → clean. Enabled-but-unreachable → throws (fail closed) |
| `ChatAttachmentService` (new) | `upload` (whitelist + size + sanitize + scan + persist), `loadAuthorized` (authz + CLEAN gate), `forMessage`, `groupByMessage` |
| `ChatMessageService` | `broadcastMessageAfterAttachment` re-broadcasts the message DTO (now showing attachments) on `/topic/conversation/{id}`; `renderBulk` attaches `attachments` maps |
| `ChatConversationController` | 3 endpoints below |
| `application.yaml` | `nexmemo.security.clamav.{enabled,host,port,timeout-ms}` (default `enabled: false`) |

### REST

```
POST   /api/v1/chat/conversations/{conversationId}/messages/{messageId}/attachments   (multipart, field "file")
GET    /api/v1/chat/conversations/{conversationId}/messages/{messageId}/attachments   → ChatAttachmentDto[]
GET    /api/v1/chat/attachments/{attachmentId}/download                                → application/octet-stream + Content-Disposition: attachment
```

Upload flow (two-step, by design): the client sends the text message first, then uploads files
to that message id. A file can also be attached to any existing own message. Upload validates
**message access first**, then extension/size, then scans **before** persisting.

Download: `loadAuthorized` loads the row, checks the caller can access `conversation_id`, and
throws unless `scan_status == CLEAN`; bytes stream as a `ByteArrayResource` with
`ContentDisposition.attachment().filename(...)`.

---

## 5. Frontend changes

| File | Change |
|---|---|
| `chat.service.ts` | `uploadAttachment(convId, msgId, File)` (FormData), `getMessageAttachments`, `downloadAttachment(id)` (blob + `observe: 'response'` so the JWT header is sent — a plain `<a href>` cannot) |
| `chat-widget.component.ts/html` | paperclip button in the composer → hidden file input; two-step send-then-upload (or attach to the caller's latest own message when the composer is empty); attachment chips under messages with download; file-size formatter |
| `branch-chat.component.ts/html` | same attach/download on the branch page |

Client-side errors are mapped from the server message: malware/scanner failures → "Attachment
rejected (malware scan)"; size/extension → the server's own message.

---

## 6. Tests

- `ChatAttachmentServiceTest` (new): clean upload (scan disabled) stores with correct hash/size;
  infected scan rejects and stores nothing; scanner down → fail-closed; disallowed extension
  rejected; oversized file rejected; filename sanitization strips paths/controls;
  `loadAuthorized` denies non-members and non-clean files.
- `ClamAvScanner` protocol behavior covered through the service tests (mock scanner).
- 113/113 backend tests green; frontend `npm run build` green.

---

## 7. Deployment notes

- **Production:** set `CLAMAV_ENABLED=true`, `CLAMAV_HOST`, `CLAMAV_PORT` (3310). Reference:
  `docker run -d -p 3310:3310 clamav/clamav:stable`. Verify with
  `echo -e 'zINSTREAM\0...'` / netcat or just attempt an upload of a harmless file.
- If clamd is down in prod, **upload fails closed** by design — monitor alerts accordingly.
- `VARBINARY(MAX)` stores bytes in SQL Server; no filesystem path config needed.

---

## 8. Manual verification steps

1. Send a message, then attach a small `.txt` → chip appears for every participant.
2. Download it → saves locally with the original filename.
3. Try `evil.png` (renamed `.exe` content bypass attempt) → rejection by extension list.
4. With ClamAV enabled: upload `eicar.com` content in a `.txt` → "malware scan" rejection.
5. A non-member calling the download URL directly → 403.