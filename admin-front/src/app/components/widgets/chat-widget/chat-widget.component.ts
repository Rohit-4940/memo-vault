import { Component, OnInit, OnDestroy, inject, HostListener } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../../shared-services/api-service/chat.service';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { WebSocketService } from '../../../shared-services/websocket.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { Subject, takeUntil } from 'rxjs';

type WidgetView = 'list' | 'chat' | 'people' | 'branches' | 'saved' | 'search' | 'members' | 'reports';

@Component({
  selector: 'app-chat-widget',
  standalone: true,
  imports: [DatePipe, NgClass, FormsModule],
  templateUrl: './chat-widget.component.html',
  styleUrl: './chat-widget.component.scss'
})
export class ChatWidgetComponent implements OnInit, OnDestroy {
  isOpen = false;
  view: WidgetView = 'list';
  conversations: any[] = [];
  activeConv: any = null;
  messages: any[] = [];
  newMessage = '';
  loading = false;
  search = '';
  currentUserId = 0;
  currentRole = '';
  users: any[] = [];
  branches: any[] = [];

  // Message editing state.
  editingId: number | null = null;
  editText = '';

  /** Emoji palette offered by the quick reactor. */
  readonly reactionPalette = ['👍', '❤️', '😂', '🎉', '👀', '✅'];
  /** Message whose emoji picker is currently open (null = none). */
  pickerMessageId: number | null = null;

  // ── Phase 2c: threads ──────────────────────────────────────────────────────
  /** Parent message whose thread panel is open (null = thread closed). */
  threadId: number | null = null;
  threadReplies: any[] = [];
  threadReplyText = '';
  threadLoading = false;

  // ── Phase 2d: pin / bookmark ───────────────────────────────────────────────
  /** When true the message list is replaced by the pinned-messages view. */
  showPinnedOnly = false;
  pinnedMessages: any[] = [];
  savedMessages: any[] = [];

  // ── Phase 2e: forwarding ───────────────────────────────────────────────────
  showForwardPicker = false;
  forwardSourceId: number | null = null;
  forwardTargets: any[] = [];

  // ── Phase 2f: attachments ──────────────────────────────────────────────────
  /** File picked from the composer's paperclip, uploaded to the just-sent message. */
  pendingFile: File | null = null;
  attaching = false;

  // ── Phase 2g: typing / presence ────────────────────────────────────────────
  typingUsers = new Map<number, string>();
  private typingTimer: any = null;
  onlineCount = 0;
  members: any[] = [];

  // ── Phase 2h: notification settings ────────────────────────────────────────
  notificationLevel: string = 'ALL';

  // ── Phase 2i: scoped search ────────────────────────────────────────────────
  searchResults: any[] = [];
  searchQuery = '';
  searchLoading = false;
  private searchTimer: any = null;

  // ── Phase 2j: admin moderation ─────────────────────────────────────────────
  reports: any[] = [];

  // @mention autocomplete state.
  mentionCandidates: any[] = [];
  private mentionStart = -1;

  /** Author edit window (mirror of the server-side policy). */
  private readonly editWindowMs = 15 * 60 * 1000;

  private chatService = inject(ChatService);
  private branchService = inject(BranchService);
  private wsService = inject(WebSocketService);
  private toastr = inject(ToastrService);
  private destroy$ = new Subject<void>();
  private subscribedTopic = '';
  private receiptsTopic = '';
  private reactionsTopic = '';
  private typingTopic = '';
  private presenceTopic = '';
  private metaTopic = '';

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.currentUserId = storage.userId || 0;
    this.currentRole = storage.roleName || '';
    this.loadUsers();
    this.loadBranches();
    this.loadConversations();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    this.unsubscribeFromTopic();
  }

  get isAdmin(): boolean {
    return this.currentRole === 'ADMIN' || this.currentRole === 'SUPER_ADMIN';
  }

  get totalUnread(): number {
    return this.conversations.reduce((sum, c) => sum + (c.unreadCount || 0), 0);
  }

  get filteredConversations(): any[] {
    if (!this.search) return this.conversations;
    const q = this.search.toLowerCase();
    return this.conversations.filter(c =>
      (c.title || '').toLowerCase().includes(q) ||
      (c.lastMessagePreview || '').toLowerCase().includes(q));
  }

  get filteredUsers(): any[] {
    const q = this.search.toLowerCase();
    return this.users
      .filter(u => u.id !== this.currentUserId)
      .filter(u => !q || (u.name || '').toLowerCase().includes(q));
  }

  get filteredBranches(): any[] {
    const q = this.search.toLowerCase();
    return this.branches.filter(b =>
      !q || (b.name || '').toLowerCase().includes(q) || (b.branchCode || '').toLowerCase().includes(q));
  }

  // ── data ────────────────────────────────────────────────────────────────────

  loadConversations() {
    this.chatService.getConversations().subscribe({
      next: (res: any) => { this.conversations = res?.data || res || []; },
      error: () => { /* non-fatal: list stays as-is */ }
    });
  }

  loadUsers() {
    this.chatService.getAllUsers().subscribe({
      next: (res: any) => { this.users = res?.data || res || []; }
    });
  }

  loadBranches() {
    this.branchService.getAllBranches().subscribe({
      next: (data: any) => { this.branches = Array.isArray(data) ? data : data?.data || []; }
    });
  }

  // ── navigation ──────────────────────────────────────────────────────────────

  toggleOpen() {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.loadConversations();
    } else {
      this.resetToInbox();
      this.isOpen = false;
    }
  }

  showPeople() {
    this.view = 'people';
    this.search = '';
  }

  showBranches() {
    this.view = 'branches';
    this.search = '';
  }

  back() {
    this.resetToInbox();
  }

  private resetToInbox() {
    this.view = 'list';
    this.activeConv = null;
    this.messages = [];
    this.search = '';
    this.cancelEdit();
    this.pickerMessageId = null;
    this.closeMentions();
    this.closeThread();
    this.showPinnedOnly = false;
    this.pinnedMessages = [];
    this.showForwardPicker = false;
    this.forwardSourceId = null;
    this.typingUsers.clear();
    this.members = [];
    this.onlineCount = 0;
    this.searchResults = [];
    this.searchQuery = '';
    this.unsubscribeFromTopic();
  }

  // ── conversations ───────────────────────────────────────────────────────────

  openConversation(conv: any) {
    if (!conv?.id) return;
    this.activeConv = conv;
    this.view = 'chat';
    this.messages = [];
    this.loading = true;
    this.cancelEdit();
    this.pickerMessageId = null;
    this.closeThread();
    this.showForwardPicker = false;
    this.showPinnedOnly = false;
    this.unsubscribeFromTopic();
    this.subscribeToConversation(conv.id);
    this.subscribeToReceipts(conv.id);
    this.subscribeToReactions(conv.id);
    this.subscribeToTyping(conv.id);
    this.subscribeToPresence(conv.id);
    this.subscribeToMeta(conv.id);
    this.loadNotificationSetting(conv.id);
    this.loadPresence(conv.id);
    this.chatService.getConversationMessages(conv.id).subscribe({
      next: (res: any) => {
        const page = res?.data || res || {};
        const content: any[] = page.content || [];
        // API returns newest-first; display oldest-first.
        this.messages = [...content].reverse();
        this.loading = false;
        this.markRead(conv);
        this.chatService.markConversationDelivered(conv.id).subscribe({ next: () => {}, error: () => {} });
        setTimeout(() => this.scrollBottom(), 100);
      },
      error: (err: any) => {
        this.loading = false;
        this.toastr.error(err?.status === 403
          ? 'You do not have access to this conversation'
          : 'Failed to load messages');
        this.resetToInbox();
      }
    });
  }

  startDirect(user: any) {
    this.chatService.createConversation({ conversationType: 'DIRECT', otherUserId: user.id }).subscribe({
      next: (res: any) => this.openConversation(res?.data || res),
      error: (err: any) => this.toastr.error(err?.status === 403
        ? 'You cannot message this user'
        : 'Failed to start conversation')
    });
  }

  openBranch(branch: any) {
    this.chatService.createConversation({ conversationType: 'BRANCH', branchId: branch.id }).subscribe({
      next: (res: any) => this.openConversation(res?.data || res),
      error: (err: any) => this.toastr.error(err?.status === 403
        ? 'You do not have access to this branch conversation'
        : 'Failed to open branch conversation')
    });
  }

  // ── messaging ───────────────────────────────────────────────────────────────

  sendMessage() {
    const text = this.newMessage.trim();
    if (!text || !this.activeConv?.id || this.convLocked) return;
    const payload = { clientMessageId: this.uuid(), message: text };
    this.chatService.sendConversationMessage(this.activeConv.id, payload).subscribe({
      next: () => { this.newMessage = ''; this.typingUsers.delete(this.currentUserId); },
      error: (err: any) => this.toastr.error(err?.status === 403
        ? 'You cannot post in this conversation'
        : 'Failed to send')
    });
  }

  // ── @mentions ──────────────────────────────────────────────────────────────

  /**
   * Fires on composer input: when the text before the caret ends with {@code @query}, opens the
   * user picker; otherwise closes it. Mentions are inserted as {@code @[Name](id)} tokens which
   * the server re-parses — the client never decides the target id.
   */
  onComposerInput(composer: HTMLInputElement) {
    const value = composer.value;
    const caret = composer.selectionStart ?? value.length;
    const upto = value.slice(0, caret);
    const at = upto.lastIndexOf('@');
    if (at < 0 || (at > 0 && !/\s/.test(upto[at - 1]))) { this.closeMentions(); return; }
    const query = upto.slice(at + 1);
    if (!/^[\w\s.'-]{0,40}$/.test(query)) { this.closeMentions(); return; }
    this.mentionStart = at;
    const q = query.trim().toLowerCase();
    this.mentionCandidates = this.users
      .filter(u => u.id !== this.currentUserId)
      .filter(u => !q || (u.name || '').toLowerCase().includes(q))
      .slice(0, 6);
    this.broadcastTyping();
  }

  selectMention(user: any, composer: HTMLInputElement) {
    if (this.mentionStart < 0) return;
    const caret = composer.selectionStart ?? this.newMessage.length;
    const before = this.newMessage.slice(0, this.mentionStart);
    const after = this.newMessage.slice(caret);
    const token = '@[' + user.name + '](' + user.id + ') ';
    this.newMessage = before + token + after;
    this.closeMentions();
    const pos = (before + token).length;
    setTimeout(() => {
      composer.setSelectionRange(pos, pos);
      composer.focus();
    });
  }

  closeMentions() {
    this.mentionCandidates = [];
    this.mentionStart = -1;
  }

  /** Enter picks the top mention candidate (else sends); Escape closes the picker. */
  onComposerKeydown(event: KeyboardEvent, composer: HTMLInputElement) {
    if (event.key === 'Enter' && this.mentionCandidates.length > 0) {
      event.preventDefault();
      this.selectMention(this.mentionCandidates[0], composer);
      return;
    }
    if (event.key === 'Escape' && this.mentionCandidates.length > 0) {
      event.preventDefault();
      this.closeMentions();
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      this.sendMessage();
    }
  }

  reportProblem() {
    let branchId = this.activeConv?.branchId;
    if (!branchId && this.branches.length > 0) branchId = this.branches[0].id;
    if (!branchId) {
      this.toastr.warning('No branch available to report to');
      return;
    }
    const description = prompt('Describe your problem:');
    if (!description || !description.trim()) return;
    const text = '\u26A0 PROBLEM REPORT\n---\n' + description.trim();

    this.chatService.createConversation({ conversationType: 'BRANCH', branchId }).subscribe({
      next: (res: any) => {
        const conv = res?.data || res;
        this.chatService.sendConversationMessage(conv.id, { clientMessageId: this.uuid(), message: text })
          .subscribe({
            next: () => this.toastr.success('Problem reported to admins'),
            error: () => this.toastr.error('Failed to report problem')
          });
      },
      error: () => this.toastr.error('Failed to report problem')
    });
  }

  markRead(conv: any) {
    const last = this.messages.length > 0 ? this.messages[this.messages.length - 1] : null;
    this.chatService.markConversationRead(conv.id, last?.id).subscribe({
      next: () => { conv.unreadCount = 0; }
    });
  }

  // ── edit / delete ─────────────────────────────────────────────────────────

  canEdit(msg: any): boolean {
    if (!msg || msg.isDeleted || !this.isCurrentUser(msg.senderId)) return false;
    if (!msg.createdDate) return false;
    return (Date.now() - new Date(msg.createdDate).getTime()) < this.editWindowMs;
  }

  canDelete(msg: any): boolean {
    return !!msg && !msg.isDeleted && (this.isCurrentUser(msg.senderId) || this.isAdmin);
  }

  startEdit(msg: any) {
    this.editingId = msg.id;
    this.editText = msg.message;
  }

  cancelEdit() {
    this.editingId = null;
    this.editText = '';
  }

  saveEdit(msg: any) {
    const text = (this.editText || '').trim();
    if (!text || !this.activeConv?.id) return;
    this.chatService.editConversationMessage(this.activeConv.id, msg.id, text).subscribe({
      next: (res: any) => {
        this.upsertMessage(res?.data || res);
        this.cancelEdit();
      },
      error: (err: any) => this.toastr.error(err?.status === 403
        ? 'You can no longer edit this message'
        : 'Failed to update message')
    });
  }

  deleteMsg(msg: any) {
    if (!this.activeConv?.id) return;
    if (!confirm('Delete this message?')) return;
    this.chatService.deleteConversationMessage(this.activeConv.id, msg.id).subscribe({
      next: (res: any) => this.upsertMessage(res?.data || res),
      error: (err: any) => this.toastr.error(err?.status === 403
        ? 'You cannot delete this message'
        : 'Failed to delete message')
    });
  }

  deliveryIcon(msg: any): string {
    if (!this.isCurrentUser(msg?.senderId) || msg?.isDeleted) return '';
    switch (msg?.deliveryState) {
      case 'READ':
      case 'DELIVERED': return 'fa-check-double';
      case 'SENT': return 'fa-check';
      default: return '';
    }
  }

  deliveryStateClass(msg: any): string {
    return msg?.deliveryState === 'READ' ? 'read' : 'delivered';
  }

  // ── reactions ─────────────────────────────────────────────────────────────

  toggleReactionPicker(msg: any, event?: Event) {
    if (event) event.stopPropagation();
    if (!msg || msg.isDeleted) return;
    this.pickerMessageId = this.pickerMessageId === msg.id ? null : msg.id;
  }

  hasReacted(msg: any, emoji: string): boolean {
    return !!msg?.reactions?.some((r: any) => r.emoji === emoji && r.reactedByMe);
  }

  /** Adds or removes the caller's reaction; the server response is the authoritative summary. */
  toggleReaction(msg: any, emoji: string) {
    if (!msg || msg.isDeleted) return;
    this.pickerMessageId = null;
    const call = this.hasReacted(msg, emoji)
      ? this.chatService.removeReaction(msg.id, emoji)
      : this.chatService.addReaction(msg.id, emoji);
    call.subscribe({
      next: (res: any) => this.setMessageReactions(msg.id, res?.data || res || []),
      error: () => this.toastr.error('Failed to update reaction')
    });
  }

  private setMessageReactions(messageId: number, reactions: any[]) {
    this.messages = this.messages.map(m =>
      m.id === messageId ? { ...m, reactions: reactions || [] } : m);
  }

  reactionTooltip(r: any): string {
    if (!r?.userIds?.length) return '';
    return r.userIds.map((id: number) => this.getSenderName(id)).join(', ');
  }

  /**
   * Splits a message body into plain-text and mention segments so mentions can be rendered as
   * styled chips without innerHTML (XSS-safe).
   */
  messageSegments(text: string | null): any[] {
    if (!text) return [{ type: 'text', text: '' }];
    const segments: any[] = [];
    const re = /@\[([^\]\n]+)\]\((\d+)\)/g;
    let last = 0;
    let m: RegExpExecArray | null;
    while ((m = re.exec(text)) !== null) {
      if (m.index > last) segments.push({ type: 'text', text: text.slice(last, m.index) });
      segments.push({ type: 'mention', text: m[1], userId: Number(m[2]) });
      last = m.index + m[0].length;
    }
    if (last < text.length) segments.push({ type: 'text', text: text.slice(last) });
    return segments.length ? segments : [{ type: 'text', text }];
  }

  /** Highlights messages where the caller was tagged. */
  isMentionedIn(msg: any): boolean {
    return !!msg?.mentionedUserIds?.includes(this.currentUserId);
  }

  // ── realtime ────────────────────────────────────────────────────────────────

  private subscribeToConversation(conversationId: number) {
    const topic = '/topic/conversation/' + conversationId;
    this.subscribedTopic = topic;
    this.wsService.subscribe(topic).pipe(takeUntil(this.destroy$)).subscribe((msg: any) => {
      if (!msg || msg.id == null) return;
      const existed = this.messages.some(m => m.id === msg.id);
      this.upsertMessage(msg);
      if (!existed) {
        setTimeout(() => this.scrollBottom(), 100);
        // Viewing the conversation implies read.
        if (!this.isCurrentUser(msg.senderId) && this.activeConv) {
          this.chatService.markConversationRead(this.activeConv.id, msg.id).subscribe({
            next: () => { this.activeConv.unreadCount = 0; }
          });
        }
      }
    });
  }

  private subscribeToReceipts(conversationId: number) {
    const topic = '/topic/conversation/' + conversationId + '/receipts';
    this.receiptsTopic = topic;
    this.wsService.subscribe(topic).pipe(takeUntil(this.destroy$)).subscribe((evt: any) => {
      if (!evt) return;
      // Only other members' watermarks can change the state of my own messages; my own
      // read event must not mark my just-sent message as READ.
      if (evt.userId === this.currentUserId) return;
      this.applyReceipt(evt.deliveredUpTo, evt.readUpTo);
    });
  }

  private subscribeToReactions(conversationId: number) {
    const topic = '/topic/conversation/' + conversationId + '/reactions';
    this.reactionsTopic = topic;
    this.wsService.subscribe(topic).pipe(takeUntil(this.destroy$)).subscribe((evt: any) => {
      if (!evt || evt.messageId == null) return;
      this.applyReactionEvent(evt);
    });
  }

  /**
   * Applies a reaction delta to the in-memory message. {@code count} from the server is
   * authoritative, so the chip never drifts even if a previous event was missed.
   */
  private applyReactionEvent(evt: any) {
    const idx = this.messages.findIndex(m => m.id === evt.messageId);
    if (idx < 0) return;
    const msg = this.messages[idx];
    const reactions: any[] = [...(msg.reactions || [])];
    const i = reactions.findIndex(r => r.emoji === evt.emoji);
    const mine = evt.userId === this.currentUserId;
    if (evt.action === 'ADDED') {
      if (i >= 0) {
        reactions[i] = { ...reactions[i], count: evt.count, reactedByMe: reactions[i].reactedByMe || mine };
      } else {
        reactions.push({ emoji: evt.emoji, count: evt.count, reactedByMe: mine, userIds: [evt.userId] });
      }
    } else if (i >= 0) {
      if (evt.count <= 0) {
        reactions.splice(i, 1);
      } else {
        reactions[i] = { ...reactions[i], count: evt.count,
          reactedByMe: mine ? false : reactions[i].reactedByMe };
      }
    }
    const copy = [...this.messages];
    copy[idx] = { ...msg, reactions };
    this.messages = copy;
  }

  /**
   * Applies another participant's watermark to the caller's own messages. Never downgrades.
   *
   * A single group member reading a message does NOT imply all members read it, so live READ
   * upgrades are limited to DIRECT conversations. For GROUP/BRANCH the receipt advances the
   * message to DELIVERED; the aggregated READ state is resolved by the history/status endpoints.
   */
  private applyReceipt(deliveredUpTo: number, readUpTo: number) {
    if (deliveredUpTo == null && readUpTo == null) return;
    const isDirect = this.activeConv?.conversationType === 'DIRECT';
    const deliveredCeiling = Math.max(deliveredUpTo ?? 0, readUpTo ?? 0);
    this.messages = this.messages.map(m => {
      if (!this.isCurrentUser(m.senderId) || m.isDeleted) return m;
      if (isDirect && readUpTo != null && m.id <= readUpTo && m.deliveryState !== 'READ') {
        return { ...m, deliveryState: 'READ' };
      }
      if (deliveredCeiling > 0 && m.id <= deliveredCeiling
          && m.deliveryState !== 'READ' && m.deliveryState !== 'DELIVERED') {
        return { ...m, deliveryState: 'DELIVERED' };
      }
      return m;
    });
  }

  private upsertMessage(msg: any) {
    const idx = this.messages.findIndex(m => m.id === msg.id);
    if (idx >= 0) {
      const copy = [...this.messages];
      copy[idx] = { ...copy[idx], ...msg };
      this.messages = copy;
    } else {
      this.messages = [...this.messages, msg];
    }
  }

  private unsubscribeFromTopic() {
    if (this.subscribedTopic) {
      this.wsService.unsubscribe(this.subscribedTopic);
      this.subscribedTopic = '';
    }
    if (this.receiptsTopic) {
      this.wsService.unsubscribe(this.receiptsTopic);
      this.receiptsTopic = '';
    }
    if (this.reactionsTopic) {
      this.wsService.unsubscribe(this.reactionsTopic);
      this.reactionsTopic = '';
    }
    if (this.typingTopic) {
      this.wsService.unsubscribe(this.typingTopic);
      this.typingTopic = '';
    }
    if (this.presenceTopic) {
      this.wsService.unsubscribe(this.presenceTopic);
      this.presenceTopic = '';
    }
    if (this.metaTopic) {
      this.wsService.unsubscribe(this.metaTopic);
      this.metaTopic = '';
    }
  }

  // ── Phase 2c: threads ──────────────────────────────────────────────────────

  /** Opens the reply thread panel for a parent message and loads its replies. */
  openThread(msg: any) {
    if (!msg?.id) return;
    this.threadId = msg.id;
    this.threadReplies = [];
    this.threadReplyText = '';
    this.threadLoading = true;
    this.loadThreadReplies();
  }

  /** The parent message the open thread belongs to (null when no thread is open). */
  threadParent(): any {
    return this.threadId != null ? this.messages.find(m => m.id === this.threadId) || null : null;
  }

  closeThread() {
    this.threadId = null;
    this.threadReplies = [];
    this.threadReplyText = '';
    this.threadLoading = false;
  }

  private loadThreadReplies() {
    if (!this.activeConv?.id || this.threadId == null) return;
    this.chatService.getReplies(this.activeConv.id, this.threadId).subscribe({
      next: (res: any) => {
        this.threadReplies = res?.data || res || [];
        this.threadLoading = false;
      },
      error: () => {
        this.threadLoading = false;
        this.toastr.error('Failed to load thread');
      }
    });
  }

  sendReply() {
    const text = this.threadReplyText.trim();
    if (!text || this.threadId == null || !this.activeConv?.id) return;
    this.chatService.sendConversationMessage(this.activeConv.id, {
      clientMessageId: this.uuid(),
      message: text,
      parentMessageId: this.threadId
    }).subscribe({
      next: (res: any) => {
        this.threadReplyText = '';
        const reply = res?.data || res;
        if (reply?.id) {
          this.threadReplies = [...this.threadReplies, reply];
          // Keep the parent's reply counter in sync.
          const parent = this.messages.find(m => m.id === this.threadId);
          if (parent) parent.replyCount = (parent.replyCount || 0) + 1;
        }
        setTimeout(() => this.scrollThreadBottom(), 80);
      },
      error: (err: any) => this.toastr.error(err?.status === 403
        ? 'You cannot post in this conversation'
        : 'Failed to reply')
    });
  }

  /** Reply to a message from the quick-action row (same as opening its thread). */
  replyTo(msg: any) {
    this.openThread(msg);
  }

  // ── Phase 2d: pin / bookmark / mark-unread ─────────────────────────────────

  canPin(msg: any): boolean {
    return !!msg && !msg.isDeleted && (this.isCurrentUser(msg.senderId) || this.isAdmin);
  }

  togglePinMsg(msg: any) {
    if (!this.activeConv?.id) return;
    this.chatService.togglePin(this.activeConv.id, msg.id).subscribe({
      next: (res: any) => {
        const dto = res?.data || res;
        this.upsertMessage(dto);
        this.pinnedMessages = this.pinnedMessages.map(p => p.id === dto.id ? dto : p);
      },
      error: (err: any) => this.toastr.error(err?.status === 403
        ? 'Only the author or an admin can pin'
        : 'Failed to update pin')
    });
  }

  /** Switches the message list to pinned-only (fetch authoritative list). */
  togglePinnedOnly() {
    if (!this.activeConv?.id) return;
    this.showPinnedOnly = !this.showPinnedOnly;
    if (this.showPinnedOnly) {
      this.chatService.getPinned(this.activeConv.id).subscribe({
        next: (res: any) => {
          const page = res?.data || res || {};
          this.pinnedMessages = [...(page.content || [])].reverse();
        },
        error: () => this.toastr.error('Failed to load pinned messages')
      });
    } else {
      this.pinnedMessages = [];
    }
  }

  /** Messages displayed in chat view — pinned filter swaps the list. */
  get visibleMessages(): any[] {
    return this.showPinnedOnly ? this.pinnedMessages : this.messages;
  }

  toggleBookmark(msg: any) {
    const call = msg.isBookmarked
      ? this.chatService.unbookmarkMessage(msg.id)
      : this.chatService.bookmarkMessage(msg.id);
    call.subscribe({
      next: () => this.upsertMessage({ id: msg.id, isBookmarked: !msg.isBookmarked }),
      error: () => this.toastr.error('Failed to update bookmark')
    });
  }

  loadSaved() {
    this.view = 'saved';
    this.search = '';
    this.chatService.getMyBookmarks(50).subscribe({
      next: (res: any) => { this.savedMessages = res?.data || res || []; },
      error: () => this.toastr.error('Failed to load saved messages')
    });
  }

  openBookmark(bm: any) {
    if (!bm?.conversationId) return;
    this.openConversation({ id: bm.conversationId, conversationType: bm.conversationType });
  }

  /** Rewinds the read watermark so the newest message counts as unread again. */
  markUnread() {
    if (!this.activeConv?.id) return;
    const latest = this.messages[this.messages.length - 1];
    this.chatService.markConversationUnread(this.activeConv.id, latest?.id).subscribe({
      next: () => {
        this.toastr.success('Marked as unread');
        this.activeConv.unreadCount = (this.activeConv.unreadCount || 0) + 1;
      },
      error: () => this.toastr.error('Failed to mark unread')
    });
  }

  // ── Phase 2e: forwarding ────────────────────────────────────────────────────

  startForward(msg: any) {
    if (!msg?.id) return;
    this.forwardSourceId = msg.id;
    this.forwardTargets = this.conversations
      .filter(c => c.id !== this.activeConv?.id)
      .slice(0, 20);
    this.showForwardPicker = true;
  }

  cancelForward() {
    this.showForwardPicker = false;
    this.forwardSourceId = null;
    this.forwardTargets = [];
  }

  pickForwardTarget(conv: any) {
    if (this.forwardSourceId == null || !conv?.id) return;
    this.chatService.forwardMessage(this.forwardSourceId, conv.id, this.uuid()).subscribe({
      next: () => {
        this.toastr.success('Message forwarded to ' + this.displayTitle(conv));
        this.cancelForward();
      },
      error: () => this.toastr.error('Failed to forward message')
    });
  }

  // ── Phase 2f: attachments ───────────────────────────────────────────────────

  /** Picks a file from the composer; the message goes first, then the file. */
  onFilePicked(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0] || null;
    input.value = '';
    if (!file) return;
    this.pendingFile = file;
    this.attachPendingFile();
  }

  /**
   * Two-step upload: posts the typed message (if any) to get its id, otherwise attaches
   * to the caller's latest message in the conversation. The re-broadcast keeps peers — and
   * this client — in sync.
   */
  private attachPendingFile() {
    if (!this.pendingFile || !this.activeConv?.id) return;
    if (this.attaching) return;
    this.attaching = true;
    const text = this.newMessage.trim();
    if (text) {
      this.chatService.sendConversationMessage(this.activeConv.id, {
        clientMessageId: this.uuid(), message: text
      }).subscribe({
        next: (res: any) => {
          this.newMessage = '';
          this.doUpload(res?.data?.id);
        },
        error: () => { this.attaching = false; this.toastr.error('Failed to send message'); }
      });
    } else {
      const mine = [...this.messages].reverse().find(m => this.isCurrentUser(m.senderId) && !m.isDeleted);
      if (!mine) {
        this.attaching = false;
        this.toastr.warning('Type a message first, then attach the file');
        return;
      }
      this.doUpload(mine.id);
    }
  }

  private doUpload(messageId: number) {
    const file = this.pendingFile;
    this.pendingFile = null;
    if (!file) { this.attaching = false; return; }
    this.chatService.uploadAttachment(this.activeConv!.id, messageId, file).subscribe({
      next: () => { this.attaching = false; this.toastr.success('File attached'); },
      error: (err: any) => {
        this.attaching = false;
        const msg = err?.error?.message || '';
        this.toastr.error(msg.includes('malware') || msg.includes('scanner')
          ? 'Attachment rejected (malware scan)'
          : (msg.includes('25 MB') || msg.includes('not allowed')
            ? msg : 'Failed to upload file'));
      }
    });
  }

  fileName(a: any): string {
    return a?.fileName || 'file';
  }

  fileSize(a: any): string {
    const b = Number(a?.fileSizeBytes || 0);
    if (b < 1024) return b + ' B';
    if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
    return (b / (1024 * 1024)).toFixed(1) + ' MB';
  }

  /** Streams the file through the API (keeps the JWT header) and saves it locally. */
  downloadAttachment(a: any) {
    this.chatService.downloadAttachment(a.id).subscribe({
      next: (resp: any) => {
        const blob = resp?.body as Blob;
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = a?.fileName || 'attachment';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
      },
      error: () => this.toastr.error('Failed to download attachment')
    });
  }

  // ── Phase 2g: typing / presence ────────────────────────────────────────────

  /** Rate-limited typing broadcast, fired from the composer input. */
  broadcastTyping() {
    if (!this.activeConv?.id || this.typingTimer) return;
    this.chatService.sendTyping(this.activeConv.id).subscribe({ next: () => {}, error: () => {} });
    this.typingTimer = setTimeout(() => { this.typingTimer = null; }, 900);
  }

  get typingLabel(): string {
    if (this.typingUsers.size === 0) return '';
    const names = [...this.typingUsers.values()].slice(0, 2);
    return names.join(', ') + (this.typingUsers.size > 2 ? '…' : '') + ' typing…';
  }

  private subscribeToTyping(conversationId: number) {
    const topic = '/topic/conversation/' + conversationId + '/typing';
    this.typingTopic = topic;
    this.wsService.subscribe(topic).pipe(takeUntil(this.destroy$)).subscribe((evt: any) => {
      if (!evt || evt.userId === this.currentUserId) return;
      this.typingUsers.set(evt.userId, evt.displayName || 'Someone');
      setTimeout(() => {
        if (this.typingUsers.has(evt.userId)) this.typingUsers.delete(evt.userId);
      }, 2500);
    });
  }

  private subscribeToPresence(conversationId: number) {
    const topic = '/topic/conversation/' + conversationId + '/presence';
    this.presenceTopic = topic;
    this.wsService.subscribe(topic).pipe(takeUntil(this.destroy$)).subscribe((evt: any) => {
      if (!evt) return;
      this.applyPresenceEvent(evt);
    });
  }

  private applyPresenceEvent(evt: any) {
    if (evt.userId === this.currentUserId) return;
    this.members = this.members.map(m =>
      m.userId === evt.userId ? { ...m, online: !!evt.online, lastSeen: evt.lastSeen || m.lastSeen } : m);
    this.refreshOnlineCount();
  }

  private refreshOnlineCount() {
    this.onlineCount = this.members.filter(m => m.online && m.userId !== this.currentUserId).length;
  }

  /** Snapshot presence from REST (authoritative), then live deltas arrive on WS. */
  loadPresence(conversationId: number) {
    this.chatService.getConversationPresence(conversationId).subscribe({
      next: (res: any) => {
        const list: any[] = res?.data || res || [];
        this.onlineCount = list.filter(p => p.userId !== this.currentUserId && p.online).length;
        // Overlay online state onto the members panel when it is already loaded.
        this.members = this.members.map(m => {
          const p = list.find(x => x.userId === m.userId);
          return p ? { ...m, online: p.online, lastSeen: p.lastSeen } : m;
        });
      },
      error: () => {}
    });
  }

  openMembers() {
    if (!this.activeConv?.id) return;
    this.view = 'members';
    this.chatService.getConversationMembers(this.activeConv.id).subscribe({
      next: (res: any) => {
        this.members = res?.data || res || [];
        this.refreshOnlineCount();
      },
      error: () => this.toastr.error('Failed to load members')
    });
  }

  backToChat() {
    this.view = 'chat';
  }

  isOnline(userId: number): boolean {
    return !!this.members.find(m => m.userId === userId)?.online;
  }

  isCurrentMember(userId: number): boolean {
    return userId === this.currentUserId;
  }

  // ── Phase 2h: notification settings ────────────────────────────────────────

  loadNotificationSetting(conversationId: number) {
    this.chatService.getNotificationSetting(conversationId).subscribe({
      next: (res: any) => {
        const v = res?.data || res;
        if (v) this.notificationLevel = v;
      },
      error: () => {}
    });
  }

  /** ALL → MENTIONS → MUTE → ALL. MUTE suppresses even @mentions server-side. */
  cycleNotification() {
    if (!this.activeConv?.id) return;
    const next = this.notificationLevel === 'ALL' ? 'MENTIONS'
      : this.notificationLevel === 'MENTIONS' ? 'MUTE' : 'ALL';
    this.chatService.updateNotificationSetting(this.activeConv.id, next).subscribe({
      next: (res: any) => {
        this.notificationLevel = res?.data || next;
        this.toastr.success('Notifications: ' + this.notificationLevel);
      },
      error: () => this.toastr.error('Failed to update notification setting')
    });
  }

  // ── Phase 2i: scoped search ────────────────────────────────────────────────

  openSearchView() {
    this.view = 'search';
    this.searchResults = [];
    this.searchQuery = '';
    this.searchLoading = false;
  }

  onSearchInput() {
    clearTimeout(this.searchTimer);
    const q = this.searchQuery.trim();
    if (q.length < 2) {
      this.searchResults = [];
      this.searchLoading = false;
      return;
    }
    this.searchLoading = true;
    this.searchTimer = setTimeout(() => {
      this.chatService.searchMessages(q).subscribe({
        next: (res: any) => {
          const page = res?.data || res || {};
          this.searchResults = [...(page.content || [])].reverse();
          this.searchLoading = false;
        },
        error: () => {
          this.searchLoading = false;
          this.toastr.error('Search failed');
        }
      });
    }, 400);
  }

  openSearchResult(msg: any) {
    if (!msg?.conversationId) return;
    this.openConversation({ id: msg.conversationId, conversationType: msg.conversationType });
  }

  // ── Phase 2j: moderation (lock / reports) ──────────────────────────────────

  /** Locks/unlocks the conversation (manager/admin only). Broadcast keeps everyone read-only. */
  toggleLock() {
    if (!this.activeConv?.id || !this.isAdmin) return;
    const next = !this.activeConv.isLocked;
    this.chatService.setConversationLocked(this.activeConv.id, next).subscribe({
      next: () => { this.activeConv.isLocked = next; },
      error: () => this.toastr.error('Failed to update lock state')
    });
  }

  private subscribeToMeta(conversationId: number) {
    const topic = '/topic/conversation/' + conversationId + '/meta';
    this.metaTopic = topic;
    this.wsService.subscribe(topic).pipe(takeUntil(this.destroy$)).subscribe((evt: any) => {
      if (!evt || evt.conversationId !== conversationId) return;
      this.activeConv.isLocked = !!evt.locked;
      if (evt.locked) this.toastr.info('This conversation was locked by an admin');
    });
  }

  openReports() {
    this.view = 'reports';
    this.chatService.getAdminReports(0, 30).subscribe({
      next: (res: any) => {
        const page = res?.data || res || {};
        this.reports = page.content || [];
      },
      error: () => this.toastr.error('Failed to load moderation queue')
    });
  }

  openReport(report: any) {
    if (!report?.conversationId) return;
    this.openConversation({ id: report.conversationId, conversationType: 'BRANCH' });
  }

  /** Is the conversation locked (read-only for everyone)? */
  get convLocked(): boolean {
    return !!this.activeConv?.isLocked;
  }

  // ── display helpers ─────────────────────────────────────────────────────────

  displayTitle(conv: any): string {
    if (!conv) return '';
    if (conv.title) return conv.title;
    switch (conv.conversationType) {
      case 'BRANCH': return this.branchName(conv.branchId);
      case 'DEPARTMENT': return 'Department #' + conv.departmentId;
      case 'MEMO': return 'Memo #' + conv.memoId;
      case 'DIRECT': return 'Direct message';
      default: return 'Conversation';
    }
  }

  typeIcon(conv: any): string {
    switch (conv?.conversationType) {
      case 'BRANCH': return 'fa-building';
      case 'DEPARTMENT': return 'fa-sitemap';
      case 'MEMO': return 'fa-file-alt';
      case 'GROUP': return 'fa-users';
      case 'DIRECT': return 'fa-user';
      default: return 'fa-comments';
    }
  }

  branchName(branchId: number): string {
    const b = this.branches.find(x => Number(x.id) === Number(branchId));
    return b ? b.name : 'Branch #' + branchId;
  }

  getSenderName(userId: number): string {
    if (userId == null) return '';
    const u = this.users.find(x => x.id === userId);
    return u ? u.name : 'User #' + userId;
  }

  isCurrentUser(userId: number): boolean {
    return userId === this.currentUserId;
  }

  isProblemReport(msg: any): boolean {
    return msg?.message?.startsWith('\u26A0');
  }

  scrollBottom() {
    const el = document.querySelector('.cw-messages');
    if (el) el.scrollTop = el.scrollHeight;
  }

  scrollThreadBottom() {
    const el = document.querySelector('.cw-thread-replies');
    if (el) el.scrollTop = el.scrollHeight;
  }

  private uuid(): string {
    const c: any = (globalThis as any).crypto;
    if (c && typeof c.randomUUID === 'function') return c.randomUUID();
    return 'cmid-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (!this.isOpen) return;
    if (this.view === 'members') this.backToChat();
    else if (this.view === 'chat' || this.view === 'people' || this.view === 'branches'
        || this.view === 'saved' || this.view === 'search' || this.view === 'reports') this.back();
    else this.isOpen = false;
  }
}
