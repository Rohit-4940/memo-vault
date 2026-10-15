# NexMemo — Phase Documentation Index

This folder holds the design + verification notes for each phase of the enterprise build-out of
the Memo Management System. Work proceeded in the agreed order **P3 → P4 → P5 → P6 → P7** and was
committed phase-by-phase (each phase reported before the next began).

## Enterprise phases

| Phase | Theme | Document |
| --- | --- | --- |
| **P3** | Foundation & security (audit, authz, scoping, secrets, attachments) | [security/PHASE_3_FOUNDATION_SECURITY.md](security/PHASE_3_FOUNDATION_SECURITY.md) |
| **P4a** | Email / notification activation | [notifications/PHASE_4A_EMAIL_NOTIFICATION.md](notifications/PHASE_4A_EMAIL_NOTIFICATION.md) |
| **P4b** | Fiscal periods & memo numbering | [finance/PHASE_4B_FISCAL_PERIODS_NUMBERING.md](finance/PHASE_4B_FISCAL_PERIODS_NUMBERING.md) |
| **P4c** | Dynamic forms & memo configuration | [memo/PHASE_4C_DYNAMIC_FORMS_MEMO_CONFIG.md](memo/PHASE_4C_DYNAMIC_FORMS_MEMO_CONFIG.md) |
| **P5** | Workflow v2 (config-driven stages, assignee pools, frozen snapshots) | [workflow/PHASE_5_WORKFLOW_V2.md](workflow/PHASE_5_WORKFLOW_V2.md) |
| **P5b** | Self-service / common request engine (`/requests`) | [requests/PHASE_5B_SELF_SERVICE_REQUEST_ENGINE.md](requests/PHASE_5B_SELF_SERVICE_REQUEST_ENGINE.md) |
| **P6** | Admin & self-service UI (form-schema editor, dynamic memo forms, self-service page, financial-period admin, per-stage guards) | [ui/PHASE_6_UI.md](ui/PHASE_6_UI.md) |

## Enterprise platform programme (master brief)

A separate, larger programme to turn the system into a next-generation document, compression, mail
and collaboration platform. It proceeds strictly phase-by-phase, reusing the existing modules and
stopping for review after each phase.

| Phase | Theme | Document |
| --- | --- | --- |
| **0/1** | Repository discovery & architecture assessment (proposed architecture, UI nav, data model, API contracts, security model, phased plan) | [enterprise/PHASE_0_ARCHITECTURE_ASSESSMENT.md](enterprise/PHASE_0_ARCHITECTURE_ASSESSMENT.md) |
| **2** | Document storage, metadata & access-control foundation (canonical document core; fixes pre-existing BOLA on `/files/download`, `/files` delete and `/memo-documents`) | [enterprise/PHASE_2_DOCUMENT_FOUNDATION.md](enterprise/PHASE_2_DOCUMENT_FOUNDATION.md) |
| **3** | Async, secure compressor (bounded worker pool, private storage keys, presets + image optimisation, archive bomb/traversal guardrails, AV scan, cancel/retry/verify; fixes broken/leaky compression progress topic) | [enterprise/PHASE_3_ASYNC_COMPRESSOR.md](enterprise/PHASE_3_ASYNC_COMPRESSOR.md) |
| **4** | Secure document vault (folders/tags, magic-byte preview, version restore & hash de-duplication, expiring/revocable share links, retention & legal hold, per-user quota; replaces the `document-list` placeholder with a real vault UI) | [enterprise/PHASE_4_SECURE_VAULT.md](enterprise/PHASE_4_SECURE_VAULT.md) |

The agreed roadmap continues with P5 internal messaging (over the
existing chat module) → P6 external email/delivery → P7 collaboration→workflow → P8 search/OCR/optional
AI → P9 administration/observability/hardening.

## Chat module phases (earlier work)

| Phase | Theme | Document |
| --- | --- | --- |
| P0 | Chat security hardening | [chat/PHASE_0_SECURITY_HARDENING.md](chat/PHASE_0_SECURITY_HARDENING.md) |
| P1 | Analysis & plan | [chat/PHASE_1_ANALYSIS_AND_PLAN.md](chat/PHASE_1_ANALYSIS_AND_PLAN.md) |
| P1a | Conversation & message core | [chat/PHASE_1A_CONVERSATION_MESSAGE_CORE.md](chat/PHASE_1A_CONVERSATION_MESSAGE_CORE.md) |
| P1b | Frontend migration | [chat/PHASE_1B_FRONTEND_MIGRATION.md](chat/PHASE_1B_FRONTEND_MIGRATION.md) |
| P1c | Receipts, edit, delete | [chat/PHASE_1C_RECEIPTS_EDIT_DELETE.md](chat/PHASE_1C_RECEIPTS_EDIT_DELETE.md) |
| P2a | Reactions | [chat/PHASE_2A_REACTIONS.md](chat/PHASE_2A_REACTIONS.md) |
| P2b | Mentions | [chat/PHASE_2B_MENTIONS.md](chat/PHASE_2B_MENTIONS.md) |
| P2c | Threads | [chat/PHASE_2C_THREADS.md](chat/PHASE_2C_THREADS.md) |
| P2d | Pin / bookmark / unread | [chat/PHASE_2D_PIN_BOOKMARK_UNREAD.md](chat/PHASE_2D_PIN_BOOKMARK_UNREAD.md) |
| P2e | Forward | [chat/PHASE_2E_FORWARD.md](chat/PHASE_2E_FORWARD.md) |
| P2f | Attachments | [chat/PHASE_2F_ATTACHMENTS.md](chat/PHASE_2F_ATTACHMENTS.md) |
| P2g | Typing presence | [chat/PHASE_2G_TYPING_PRESENCE.md](chat/PHASE_2G_TYPING_PRESENCE.md) |
| P2h | Notification settings | [chat/PHASE_2H_NOTIFICATION_SETTINGS.md](chat/PHASE_2H_NOTIFICATION_SETTINGS.md) |
| P2i | Scoped search | [chat/PHASE_2I_SCOPED_SEARCH.md](chat/PHASE_2I_SCOPED_SEARCH.md) |
| P2j | Admin moderation | [chat/PHASE_2J_ADMIN_MODERATION.md](chat/PHASE_2J_ADMIN_MODERATION.md) |

## P7 — Tests & documentation sweep

The final phase tightened regression coverage and documentation for the enterprise work:

- **New regression test**
  `WorkflowEngineServiceTest.submitMemo_configDriven_snapshotsPerStageGuardsWithConfigLevelFallback`
  asserts the Phase 6c per-stage `requireComment` / `requireDocument` values are frozen onto stage
  instances at submission, and that unset values fall back to the previous defaults (comment on;
  document inherits the config-level `requireDocumentVerification` flag).
- **Documentation:** this index links every phase document in order, and the Phase 6 document was
  updated with the P6c section.
- **Final verification:** `nexmemo-web -am test` → **242 tests, 0 failures / 0 errors**; Angular
  development build → exit 0; a packaged-jar boot against the dev SQL Server confirms Liquibase
  `20261013-1` applies and Hibernate `ddl-auto=validate` passes.

## Conventions used in these documents

- **One document per phase**, describing the design, the files touched, the gotchas discovered and
  the verification performed.
- **Schema changes are Liquibase-owned** (`db/changelog/db.changelog-master.xml`, `ddl-auto: validate`);
  the newest changeset for each enterprise phase is noted in its document.
- **Verification** for backend phases is the Maven suite (`nexmemo-web -am test`); for frontend
  phases it is the Angular development build plus, where a schema change is involved, a boot of
  the packaged jar against the dev SQL Server confirming the changeset applies.
