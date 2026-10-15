# Phase 4a — Email / Notification Activation

Status: **complete** · Tests: **185 green** (13 new) · No schema migration required

This phase turns the previously dormant notification infrastructure into a working, production-safe
delivery pipeline for memo lifecycle events, and closes a secret-exposure gap in the admin config API.

---

## 1. Problem

Before this phase:

- `NotificationConfig` and `NotificationLog` entities existed but **were never read or written** at
  runtime — `notification_config` had no consumer and `notification_log` had no producer.
- Memo lifecycle actions (`submit`, `approve`, `reject`, `return`, `forward`, `backward`) only created
  **in-app** notifications via `NotificationService`. No email was ever sent.
- `EmailService` only knew how to send a handful of hard-coded auth templates (account created,
  welcome, OTP, …). It had no generic "send this rendered HTML" entry point.
- `SystemConfigService.getEmailConfig()` returned **`SMTP_PASSWORD` in plaintext** through
  `GET /system-config/email` — a secret exposed to the frontend.

## 2. What was built

### 2.1 Transaction-safe event pipeline

```
MemoService / WorkflowEngineService
        │  publishEvent(MemoNotificationEvent)          ← inside @Transactional method
        ▼
(Spring tx commits — approval is now durable)
        │
        ▼  @TransactionalEventListener(phase = AFTER_COMMIT)  +  @Async
NotificationDispatchService
        │  resolve notification_config (event × channel)
        │  render subject/body (configured template or built-in default)
        │  EmailService.sendHtml(...)
        ▼
notification_log  (SENT / FAILED / SKIPPED)
```

**Key guarantee:** delivery runs *after* commit and on a *separate thread*, and every layer swallows
its own exceptions. A slow or failing SMTP server can never block, throw into, or roll back a
committed approval — exactly the requirement stated in the spec.

- `MemoNotificationEvent` (`nexmemo-core/…/event/`) is a plain immutable payload carrying event type,
  memo id/no/title, recipient id/email/name, actor, and remarks. Recipient email is resolved by the
  publisher while the entity is still in scope, so the async listener never touches lazy associations.
- `fallbackExecution = true` means events published outside a transaction still deliver (used by any
  future non-transactional callers).

### 2.2 Activated configuration + auditing

- `NotificationConfigRepository.findByEventTypeAndChannel(eventType, EMAIL)` is now consumed.
  - **Active config** → its `template_subject` / `template_body` are used.
  - **Inactive config** → the event is `SKIPPED` and logged.
  - **No config** → a built-in default template is used, so notifications work out of the box.
- Every attempt (including skips) is persisted by `NotificationLogService.logNotification(...)` into
  `notification_log` with status `SENT` / `FAILED` / `SKIPPED`.

### 2.3 Generic, failure-propagating email send

New `EmailService.sendHtml(String to, String subject, String htmlBody)`:
- resolves the admin-configured `EmailConfig` (falling back to `application.yaml` SMTP), and
- **throws** on transport failure (wrapping in `MailSendException`) so the dispatcher can record
  `FAILED`. The existing auth helpers keep their old swallow-and-log behaviour.

Logs only ever contain `to` + `subject`; never a password or body secret.

### 2.4 Event wiring

`MemoService` (global/level-based path) now emails the relevant party on:

| Event | Recipient |
|---|---|
| `MEMO_SUBMITTED` | next approver (submit + partial-approval-to-next-level + backward) |
| `MEMO_APPROVED` | creator (full approval, and partial approval) |
| `MEMO_REJECTED` | creator |
| `MEMO_RETURNED` | creator |
| `MEMO_FORWARDED` | forwarded-to user |

`WorkflowEngineService` (category / stage-based path) now emails on:

| Event | Recipient |
|---|---|
| `MEMO_SUBMITTED` | first **and** each newly-activated stage's assignee (so middle-level approvers are notified) |
| `MEMO_APPROVED` | creator (final stage completion) |
| `MEMO_REJECTED` | creator |
| `MEMO_RETURNED` | creator |
| `MEMO_DELEGATED` | delegatee |

Existing in-app notifications are unchanged — email is additive.

### 2.5 Secret safety

`SystemConfigService.getEmailConfig()` now returns **detached copies** with secret-named keys
(`*PASSWORD*`, `*SECRET*`, `*TOKEN*`, `*CREDENTIAL*`) masked as `********`. The managed entity is not
mutated. Plaintext SMTP passwords are no longer returned by `GET /system-config/email`.

## 3. Configuration

```yaml
nexmemo:
  notifications:
    # Base URL used to build "Open memo" links in emails. Empty = omit link.
    memo-link-base: ${MEMO_LINK_BASE:http://localhost:4202}
```

Template placeholders (usable in `notification_config.template_subject` / `template_body`):
`${recipientName}`, `${recipientEmail}`, `${memoId}`, `${memoNo}`, `${memoTitle}`, `${actorName}`,
`${remarks}`, `${event}`, `${link}`. Unknown placeholders render as empty.

## 4. Files

**Added**
- `nexmemo-core/…/event/MemoNotificationEvent.java`
- `nexmemo-web/…/service/NotificationTemplateRenderer.java`
- `nexmemo-web/…/service/NotificationDispatchService.java`
- `nexmemo-web/src/test/…/NotificationTemplateRendererTest.java` (6)
- `nexmemo-web/src/test/…/NotificationDispatchServiceTest.java` (5)
- `nexmemo-web/src/test/…/SystemConfigServiceTest.java` (2)

**Changed**
- `nexmemo-security/…/service/EmailService.java` — added `sendHtml`.
- `nexmemo-web/…/service/MemoService.java` — publisher + 6 lifecycle hooks + helpers.
- `nexmemo-web/…/service/WorkflowEngineService.java` — publisher + 5 hooks + helpers.
- `nexmemo-web/…/service/SystemConfigService.java` — secret masking.
- `nexmemo-web/src/main/resources/application.yaml` — `nexmemo.notifications.memo-link-base`.
- `nexmemo-web/src/test/…/MemoServiceTest.java` — new mocked `ApplicationEventPublisher`.

## 5. Tests

All DB-free (Mockito / plain unit):

- Dispatcher: default-template send + `SENT`; configured-template send; inactive → `SKIPPED` with no
  email; transport failure → `FAILED`, exception swallowed; missing recipient email → `SKIPPED`.
- Renderer: placeholder substitution, unknown → empty, null-safe, default vs configured, blank body
  fallback.
- SystemConfig: secret masked, non-secret untouched, non-SMTP filtered.

## 6. Deferred / notes

- Admin CRUD for `EmailConfig` (the SMTP entity) is still absent; SMTP is configured via
  `application.yaml` fallback or direct DB rows. `SystemConfig` SMTP keys remain a parallel mechanism.
- SMS / in-app / push channels are modelled by the enum but only `EMAIL` is dispatched here.
- A user-facing "notification preferences" screen and per-user opt-out are not part of this phase.
- Full end-to-end delivery requires a reachable SMTP server; unit tests mock `EmailService`.

## 7. Verification

- `.\mvnw.cmd -pl nexmemo-web -am test` → **185 tests, 0 failures, 0 errors**.
- No entity/schema changes → Liquibase `ddl-auto: validate` startup is unaffected.
