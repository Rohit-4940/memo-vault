import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class ChatService {
  constructor(private http: HttpClient) {}

  sendMessage(payload: {
    receiverId?: number; memoId?: number; branchId?: number;
    conversationType?: string; message: string
  }): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/chat/send`, payload);
  }

  getMemoMessages(memoId: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/memo/${memoId}`);
  }

  getBranchMessages(branchId: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/branch/${branchId}`);
  }

  getBranchMessagesPaginated(branchId: number, page: number = 0, size: number = 50): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/branch/${branchId}/paginated`, { params: { page, size } });
  }

  getConversation(otherId: number, page: number = 0, size: number = 50): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/conversation/${otherId}`, { params: { page, size } });
  }

  getUnreadCount(): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/unread-count`);
  }

  getAllBranchMessages(page: number = 0, size: number = 50): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/admin/all-branch-messages`, { params: { page, size } });
  }

  getActiveBranchIds(): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/admin/active-branches`);
  }

  getAllUsers(): Observable<any> {
    // Slim participant directory (id + name only). Available to every authenticated user so
    // conversations can resolve display names without exposing account/PII fields.
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/users`);
  }

  markBranchRead(branchId: number): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/chat/branch/${branchId}/mark-read`, {});
  }

  markAsRead(id: number): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/chat/${id}/read`, {});
  }

  // ── Phase 1: conversation-centric API ──────────────────────────────────────

  /** My conversations (DIRECT/GROUP/BRANCH/DEPARTMENT/MEMO) with unread + last preview. */
  getConversations(): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/conversations`);
  }

  getConversationById(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/conversations/${id}`);
  }

  /** Idempotent get-or-create. Only fields relevant to conversationType are used. */
  createConversation(payload: {
    conversationType: string;
    otherUserId?: number;
    branchId?: number;
    departmentId?: number;
    memoId?: number;
    title?: string;
    memberIds?: number[];
  }): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/chat/conversations`, payload);
  }

  getOrCreateMemoConversation(memoId: number): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/chat/conversations/memo/${memoId}`, {});
  }

  /** Cursor-paginated history (newest first). Pass beforeId to page backwards. */
  getConversationMessages(conversationId: number, beforeId?: number, size: number = 50): Observable<any> {
    const params: any = { size };
    if (beforeId != null) {
      params.beforeId = beforeId;
    }
    return this.http.get<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/messages`, { params });
  }

  sendConversationMessage(conversationId: number, payload: {
    clientMessageId: string;
    message: string;
    parentMessageId?: number;
  }): Observable<any> {
    return this.http.post<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/messages`, payload);
  }

  markConversationRead(conversationId: number, lastReadMessageId?: number): Observable<any> {
    return this.http.post<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/read`, { lastReadMessageId });
  }

  /** Records that the client received the conversation feed up to the current tail. */
  markConversationDelivered(conversationId: number): Observable<any> {
    return this.http.post<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/delivered`, {});
  }

  /** Author-only, within the 15-minute edit window. */
  editConversationMessage(conversationId: number, messageId: number, message: string): Observable<any> {
    return this.http.put<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/messages/${messageId}`, { message });
  }

  /** Author or admin. Soft-deletes and returns the tombstone. */
  deleteConversationMessage(conversationId: number, messageId: number, reason?: string): Observable<any> {
    const params: any = {};
    if (reason) { params.reason = reason; }
    return this.http.delete<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/messages/${messageId}`, { params });
  }

  /** Delivery/read status for one of the caller's own messages. */
  getMessageStatus(conversationId: number, messageId: number): Observable<any> {
    return this.http.get<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/messages/${messageId}/status`);
  }

  // ── Phase 2a: emoji reactions ─────────────────────────────────────────────

  /** Adds the caller's emoji reaction (idempotent). Returns the message's reaction summaries. */
  addReaction(messageId: number, emoji: string): Observable<any> {
    return this.http.post<any>(
      `${ApiConfig.URL}/api/v1/chat/messages/${messageId}/reactions`, { emoji });
  }

  /** Removes the caller's emoji reaction (idempotent). Returns the reaction summaries. */
  removeReaction(messageId: number, emoji: string): Observable<any> {
    return this.http.delete<any>(
      `${ApiConfig.URL}/api/v1/chat/messages/${messageId}/reactions`, { params: { emoji } });
  }

  getReactions(messageId: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/messages/${messageId}/reactions`);
  }

  // ── Phase 2b: @mentions ───────────────────────────────────────────────────

  /** Messages where the caller was tagged (newest first). */
  getMyMentions(size: number = 30): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/mentions`, { params: { size } });
  }

  // ── Phase 2c: threads ─────────────────────────────────────────────────────

  /** Replies to a parent message, oldest-first. Pass afterId to page forward. */
  getReplies(conversationId: number, messageId: number, afterId?: number, size: number = 50): Observable<any> {
    const params: any = { size };
    if (afterId != null) { params.afterId = afterId; }
    return this.http.get<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/messages/${messageId}/replies`, { params });
  }

  // ── Phase 2d: pin / bookmark / mark-unread ────────────────────────────────

  /** Messages pinned in a conversation (author/admin pins), newest first. */
  getPinned(conversationId: number, size: number = 50): Observable<any> {
    return this.http.get<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/pinned`, { params: { size } });
  }

  /** Toggles pin state — author or admin only. Returns the updated message. */
  togglePin(conversationId: number, messageId: number): Observable<any> {
    return this.http.post<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/messages/${messageId}/pin`, {});
  }

  /** Saves a message to the caller's private bookmark list. */
  bookmarkMessage(messageId: number): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/chat/messages/${messageId}/bookmark`, {});
  }

  /** Removes the caller's bookmark from a message. */
  unbookmarkMessage(messageId: number): Observable<any> {
    return this.http.delete<any>(`${ApiConfig.URL}/api/v1/chat/messages/${messageId}/bookmark`);
  }

  /** The caller's saved messages, newest first. */
  getMyBookmarks(size: number = 50): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/bookmarks`, { params: { size } });
  }

  /** Marks the conversation unread again, rewinding to just before messageId. */
  markConversationUnread(conversationId: number, messageId?: number): Observable<any> {
    return this.http.post<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/unread`, { messageId });
  }

  // ── Phase 2e: forwarding ──────────────────────────────────────────────────

  /** Copies a message into another conversation (source read + target write authz). */
  forwardMessage(messageId: number, targetConversationId: number, clientMessageId?: string): Observable<any> {
    return this.http.post<any>(
      `${ApiConfig.URL}/api/v1/chat/messages/${messageId}/forward`,
      { targetConversationId, clientMessageId });
  }

  // ── Phase 2f: secure attachments ──────────────────────────────────────────

  /** Two-step upload: message first, then files. Returns the attachment DTO. */
  uploadAttachment(conversationId: number, messageId: number, file: File): Observable<any> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/messages/${messageId}/attachments`, form);
  }

  /** Attachments already attached to a message. */
  getMessageAttachments(conversationId: number, messageId: number): Observable<any> {
    return this.http.get<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/messages/${messageId}/attachments`);
  }

  /**
   * Download URL for a scanned-clean attachment. The endpoint streams with a
   * Content-Disposition attachment header (bytes are served from SQL Server).
   */
  getAttachmentDownloadUrl(attachmentId: number): string {
    return `${ApiConfig.URL}/api/v1/chat/attachments/${attachmentId}/download`;
  }

  /**
   * Streams an attachment as a blob, keeping the JWT header (a plain <a href> cannot).
   * The response carries Content-Disposition so the caller can save with the real name.
   */
  downloadAttachment(attachmentId: number): Observable<any> {
    return this.http.get(
      `${ApiConfig.URL}/api/v1/chat/attachments/${attachmentId}/download`,
      { responseType: 'blob', observe: 'response' });
  }

  // ── Phase 2g: typing / presence ───────────────────────────────────────────

  /** Rate-limited typing indicator for a conversation. */
  sendTyping(conversationId: number): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/typing`, {});
  }

  /** Who is currently in the conversation (id + name + online + lastSeen). */
  getConversationPresence(conversationId: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/presence`);
  }

  // ── Phase 2h: notification settings ───────────────────────────────────────

  /** Current notification level (ALL | MENTIONS | MUTE) for the caller. */
  getNotificationSetting(conversationId: number): Observable<any> {
    return this.http.get<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/notification-setting`);
  }

  /** Updates the caller's per-conversation notification level. */
  updateNotificationSetting(conversationId: number, level: 'ALL' | 'MENTIONS' | 'MUTE'): Observable<any> {
    return this.http.put<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/notification-setting`, { level });
  }

  // ── Phase 2i: scoped search ───────────────────────────────────────────────

  /** Full-text search across the caller's accessible conversations only. */
  searchMessages(q: string, page: number = 0, size: number = 50): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/search`, { params: { q, page, size } });
  }

  // ── Phase 2j: admin moderation / members ──────────────────────────────────

  /** Locks (or unlocks) a conversation — locked = read-only for everyone. */
  setConversationLocked(conversationId: number, locked: boolean): Observable<any> {
    return this.http.post<any>(
      `${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/lock`, { locked });
  }

  /** Active members of a conversation with live presence overlay. */
  getConversationMembers(conversationId: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/conversations/${conversationId}/members`);
  }

  /** Admin-only moderation queue (report-style problem messages). */
  getAdminReports(page: number = 0, size: number = 50): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/admin/reports`, { params: { page, size } });
  }
}
