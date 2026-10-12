# Phase 2j — Admin Moderation: Lock, Members & Report Queue

Status: **Implemented** (backend + frontend + tests).
Related plan item: **C-20 moderation / conversation lock, C-21 member roster (P2)** in
`docs/chat/PHASE_1_ANALYSIS_AND_PLAN.md` (Phase 2 — Collaboration features).

---

## 1. Purpose

Give moderators the tools to keep conversations healthy:

1. **Lock** — an admin/manager can freeze a conversation. Locked conversations are **read-only
   for everyone** (send, edit, delete all rejected with 403) until unlocked. The lock state is
   broadcast in realtime so every open client flips to read-only immediately.
2. **Members** — a roster of who is in a conversation (name + participant role + live presence),
   powering the member panel with online dots.
3. **Report queue** — the admin-visible list of `⚠ PROBLEM REPORT` messages (from the legacy
   problem-report flow) so moderators can jump into the offending conversation.

Design goals:

- **Lock is an authoritative conversation property.** Stored on
  `chat_conversation.is_locked` and enforced in the message service on every write path
  (`assertNotLocked`), not just hidden in the UI. The summary DTO carries `isLocked` so lists
  don't mislead; a `/meta` broadcast keeps open sessions in lockstep.
- **Lock requires manage permission.** `assertCanManageConversation` (admin/manager of the
  conversation) gates the toggle — a regular participant calling the endpoint gets 403.
- **Members never leak.** The roster is participant-scoped, returns only identity fields
  (name, role) plus presence, and is access-checked per request.
- **A problem report is just a message.** The queue reuses the existing `⚠` text convention and
  the Phase 2i report query — no new storage.

---

## 2. Classification

| Field | Value |
|---|---|
| Priority | P2 (lock), P2 (members), P1 (moderation queue for admin UX) |
| Complexity | Low–Medium |
| Dependencies | Phase 0 (roles/`assertCanManageConversation`), 1a, 2g (presence), 2i (report query) |
| Risks | Low |
| Order | Ninth Phase 2 feature (2j) |

---

## 3. Data model

Uses existing `chat_conversation.is_locked BIT` (Phase 1a). No new migration.

---

## 4. Backend changes

| File | Change |
|---|---|
| `ChatConversationService` | `setLocked(userId, conversationId, locked)` — `assertCanManageConversation` then persist + broadcast; `listMembers(userId, conversationId) → List<ChatMemberDto>` |
| `ChatMessageService` | writes (send/edit/delete/forward/attach) already call `assertNotLocked(conversation)` — locked conversations reject every write |
| `ChatConversationSummaryDto` | new `isLocked` field so conversation lists show lock state |
| `ChatConversationLockEventDto` (new) | `{conversationId, locked, changedById}` broadcast on `/topic/conversation/{id}/meta` |
| `ChatConversationController` | `POST /lock`, `GET /members` |
| `ChatSearchService.adminReports` / `ChatMessageRepository.findProblemReports` | moderation queue (Phase 2i) |

### REST

```
POST /api/v1/chat/conversations/{id}/lock       body { "locked": true }   → 200 (manage role only)
GET  /api/v1/chat/conversations/{id}/members                              → ChatMemberDto[]
```

`ChatMemberDto` = `{userId, name, participantRole, online, lastSeen}` (online/lastSeen from
`ChatPresenceService` — Phase 2g).

### Realtime

```
/topic/conversation/{id}/meta   ChatConversationLockEventDto {conversationId, locked, changedById}
```

Subscribed clients flip `isLocked` on their local conversation object and (on lock) show a
toast, so a locked conversation becomes read-only everywhere within one broker hop.

---

## 5. Frontend changes

| File | Change |
|---|---|
| `chat.service.ts` | `setConversationLocked(conversationId, locked)`, `getConversationMembers(conversationId)` |
| `chat-widget.component.ts/html` | admin-only 🔒 toolbar toggle (`toggleLock`); `/meta` subscription updating `activeConv.isLocked`; `convLocked` getter drives the read-only banner + disabled composer; **Members** view (`openMembers`) with presence dots and online count; **Moderate** view (`openReports`) for the queue |
| `branch-chat.component.ts/html` | same lock toggle/banner + member presence overlays on the branch page |

---

## 6. Tests

- `ChatConversationServiceTest`: `setLocked` requires manage role (non-manager → 403, nothing
  persisted); lock persists + broadcasts the lock event DTO on `/topic/conversation/{id}/meta`;
  `listMembers` is access-checked and DTO-mapped with presence.
- Existing write tests assert `assertNotLocked` blocks send/edit/delete on locked conversations.
- `ChatSearchService.adminReports` covered under Phase 2i tests.
- 113/113 backend tests green.

---

## 7. Deployment notes

- No migration, no env changes.

---

## 8. Manual verification steps

1. As admin, open a conversation and click 🔒 → a read-only banner appears for **every**
   participant immediately; the composer disables.
2. A participant tries to send/edit/delete directly via the API → 403.
3. Click 🔒 again → conversation is writable again.
4. Open **Members** → roster shows names/roles and green dots for online teammates.
5. As admin, open **Moderate** → reported `⚠ PROBLEM REPORT` messages are listed; clicking one
   jumps into its conversation.