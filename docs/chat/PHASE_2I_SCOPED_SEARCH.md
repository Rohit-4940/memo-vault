# Phase 2i — Scoped Full-Text Search

Status: **Implemented** (backend + frontend + tests).
Related plan item: **C-19 scoped search (P2)** in
`docs/chat/PHASE_1_ANALYSIS_AND_PLAN.md` (Phase 2 — Collaboration features).

---

## 1. Purpose

Search messages **only within what the caller can already read** — the search can never widen a
user's access. For an admin, search spans everything.

Scope rules (union, evaluated per request against the principal):

1. Conversations the caller **participates in** (`chat_participant`, active).
2. `BRANCH`/`DEPARTMENT` conversations of the groups the caller belongs to
   (`User.branches` / `User.departments` — the same groups used by Phase 0 access checks).
   `MEMO` conversations are only reachable via participation, so they cannot leak through the
   group broadcast.
3. **Admins** (`ADMIN` / `SUPER_ADMIN`) search across all active conversations.

Design goals:

- **Server-computed scope, every request.** The caller's id resolves the conversation id set in
  SQL; the query is then issued with `WHERE conversation_id IN (:ids)`, so a manual request with
  an out-of-scope conversation id simply finds nothing.
- **Mention previews stay safe.** Result DTOs are rendered with the same `ChatMessageService`
  DTO pipeline (mention chips, deleted-marker, `preview`) used by the feed.
- **Bonus: admin moderation queue.** The same service exposes `adminReports` — messages flagged
  as problem reports (`⚠ PROBLEM REPORT`) — for the admin console (feeds Phase 2j).

---

## 2. Classification

| Field | Value |
|---|---|
| Priority | **P2** |
| Complexity | Medium (scope union queries, page mapping) |
| Dependencies | Phase 0 (group access), 1a (participants, DTOs) |
| Risks | Low — scope is recomputed server-side per request |
| Order | Eighth Phase 2 feature (2i) |

---

## 3. Data model

No migration. Reuses `chat_conversation.conversation_type`, `chat_participant`,
`user_branch`/`user_department` join tables (existing Phase 0/1a model).

---

## 4. Backend changes

| File | Change |
|---|---|
| `ChatSearchService` (new) | `search(userId, query, page, size) → Page<ChatMessageDto>`; `adminReports(adminId, page, size) → Page<ChatReportDto>` (delegates role check to authorization service) |
| `ChatMessageRepository` | `searchInConversations(ids, q, pageable)` (LIKE on body, ordered by id desc), `findProblemReports(pageable)`, plus the scope helpers below |
| `UserRepository` / access helpers | `findActiveConversationIdsByUserId`, `findBranchIdsByUserId`, `findDepartmentIdsByUserId` (group-of-group resolution) |
| `ChatConversationController` | `GET /chat/search`, `GET /chat/admin/reports` |

### REST

```
GET /api/v1/chat/search?q=invoice&page=0&size=50   → Page<ChatMessageDto>
GET /api/v1/chat/admin/reports?page=0&size=50      → Page<ChatReportDto>   (admin only)
```

Query is trimmed and length-gated (2+ chars) on the server; empty/minimal queries return an
empty page rather than a full-table scan.

---

## 5. Frontend changes

| File | Change |
|---|---|
| `chat.service.ts` | `searchMessages(q, page, size)`, `getAdminReports(page, size)` |
| `chat-widget.component.ts/html` | **Search** action in the widget footer opens a dedicated search view (debounced 400 ms, 2-char minimum tip); results list shows sender + preview, clicking a result opens the source conversation (`openSearchResult`); **Moderate** action (admin only) opens the reports queue view (`openReports`), each entry jumps into its conversation |

The search box is strictly client-side convenience — the scope is enforced by the server.

---

## 6. Tests

- `ChatSearchServiceTest` (new): search only returns rows from the caller's scope union;
  admin search is unfiltered; a conversation outside the caller's scope is excluded even when
  the text matches; empty/2-char gating; reports paging.
- 113/113 backend tests green.

---

## 7. Deployment notes

- No migration, no env changes.

---

## 8. Manual verification steps

1. Search for a word that exists in a conversation you participate in → the message appears.
2. Search for a word that only exists in a conversation you cannot access → no results.
3. As an admin, open **Moderate** → the problem-report queue lists `⚠ PROBLEM REPORT` messages.