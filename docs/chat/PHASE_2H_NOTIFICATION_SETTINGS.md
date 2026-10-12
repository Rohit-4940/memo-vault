# Phase 2h — Per-Conversation Notification Settings

Status: **Implemented** (backend + frontend + tests).
Related plan item: **C-18 notification preferences (P2)** in
`docs/chat/PHASE_1_ANALYSIS_AND_PLAN.md` (Phase 2 — Collaboration features).

---

## 1. Purpose

Let each participant control how noisy a conversation is for them, server-side, by setting one
of three levels:

| Level | Behaviour |
|---|---|
| `ALL` (default) | every new message notifies the participant |
| `MENTIONS` | only messages that `@mention` the participant notify (and are persisted as mentions) |
| `MUTE` | **nothing** — not even notifications; @mentions are not even persisted for this user |

Design goals:

- **The gate is server-side.** The choose is stored on `chat_participant.notification_level`
  (existing column, no migration) and enforced in `ChatMentionService` and the message
  notification path — never by hiding toasts in the client. A MUTEd user's mention rows are
  dropped before persistence, so even the mention inbox / receipt paths are silent.
- **One setting per (user, conversation).** Update replaces the participant's row value;
  involvement itself is unchanged (the user still sees the conversation).
- **Null-safe accessor.** `notificationLevelOf(participant, default)` tolerates a missing
  participant row (legacy/invite-less participants) and defaults to `ALL`.

---

## 2. Classification

| Field | Value |
|---|---|
| Priority | **P2** |
| Complexity | Low |
| Dependencies | Phase 0 (authz), 1a (participant model), 2b (mention persistence) |
| Risks | Low |
| Order | Seventh Phase 2 feature (2h) |

---

## 3. Data model

No migration. Reuses `chat_participant.notification_level VARCHAR(20)` (values `ALL`,
`MENTIONS`, `MUTE`; default `ALL`) from Phase 1a.

---

## 4. Backend changes

| File | Change |
|---|---|
| `ChatConversationService` | `getNotificationSetting(userId, conversationId)` (participant access-checked), `updateNotificationSetting(userId, conversationId, level)` (validated enum, denied for non-participants) |
| `ChatMentionService` | mention persistence + notification now gated on `notificationLevelOf(participant)` — `MUTE` skips row insert entirely; `MENTIONS` behaves like `ALL` for mentions but suppresses the plain-message notification path |
| `ChatNotificationLevel` (new enum, or reuse) | `ALL`, `MENTIONS`, `MUTE` |
| `ChatConversationController` | `GET`/`PUT /chat/conversations/{id}/notification-setting` |

### REST

```
GET /api/v1/chat/conversations/{id}/notification-setting → "ALL" | "MENTIONS" | "MUTE"
PUT /api/v1/chat/conversations/{id}/notification-setting
{ "level": "MENTIONS" }
```

The PUT body follows the project's standard DTO validation (invalid level → 400; non-member →
403).

---

## 5. Frontend changes

| File | Change |
|---|---|
| `chat.service.ts` | `getNotificationSetting(conversationId)`, `updateNotificationSetting(conversationId, level)` |
| `chat-widget.component.ts/html` | bell button in the chat toolbar cycles `ALL → MENTIONS → MUTE → ALL`, state loaded when a conversation opens (`loadNotificationSetting`), icon switches `fa-bell` ↔ `fa-bell-slash` |
| `branch-chat.component.ts/html` | same bell in the branch conversation header |

---

## 6. Tests

- `ChatConversationServiceTest`: `getNotificationSetting` returns the participant level;
  `updateNotificationSetting` persists and rejects invalid levels; non-participant denied.
- `ChatMentionServiceTest`: MUTEd participant → no mention row persisted and no notification;
  `MENTIONS` → mention persisted + notified; missing participant row → defaults to `ALL`
  (null-safe).
- 113/113 backend tests green.

---

## 7. Deployment notes

- No migration, no env changes.

---

## 8. Manual verification steps

1. Open a conversation, click the bell until it shows MUTE (`fa-bell-slash` + toast).
2. From another session, `@`-mention the muted user → they get **no** in-app notification and
   the message does not appear in their @mention inbox (`GET /chat/mentions` excludes it).
3. Cycle back to `ALL` — a fresh message produces a notification again.
4. Put `MENTIONS` — only tagged messages notify.