import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { NgForOf, NgIf, DatePipe, SlicePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../../shared-services/api-service/chat.service';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { WebSocketService } from '../../../shared-services/websocket.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-branch-chat',
  standalone: true,
  imports: [NgForOf, NgIf, DatePipe, SlicePipe, NgClass, FormsModule],
  templateUrl: './branch-chat.component.html',
  styleUrl: './branch-chat.component.scss'
})
export class BranchChatComponent implements OnInit, OnDestroy {
  allBranches: any[] = [];
  conversations: any[] = [];
  activeConvId: string | null = null;
  activeConv: any = null;
  /** Backend conversation id (Phase 1 conversation model) for the active branch. */
  activeConversationId: number | null = null;
  /** Backend conversation object (src of isLocked for the lock banner). */
  activeConversation: any = null;
  messages: any[] = [];
  newMessage = '';
  loading = false;
  branchSearch = '';
  currentUserId: number = 0;
  currentRole = '';
  users: any[] = [];
  unreadCounts: { [key: string]: number } = {};
  allBranchMessages: any[] = [];
  adminMode = false;
  showProblemForm = false;
  problemBranchId: number | null = null;
  problemSubject = '';
  problemDescription = '';

  /** Emoji palette offered by the quick reactor. */
  readonly reactionPalette = ['👍', '❤️', '😂', '🎉', '👀', '✅'];
  /** Message whose emoji picker is currently open (null = none). */
  pickerMessageId: number | null = null;

  // @mention autocomplete state.
  mentionCandidates: any[] = [];
  private mentionStart = -1;

  // ── Phase 2c: threads ──────────────────────────────────────────────────────
  threadId: number | null = null;
  threadReplies: any[] = [];
  threadReplyText = '';
  threadLoading = false;

  // ── Phase 2f: attachments ───────────────────────────────────────────────────
  attaching = false;
  pendingFile: File | null = null;

  // ── Phase 2g: typing ───────────────────────────────────────────────────────
  typingUsers = new Map<number, string>();
  private typingTimer: any = null;

  // ── Phase 2h: notification settings ────────────────────────────────────────
  notificationLevel = 'ALL';

  private chatService = inject(ChatService);
  private branchService = inject(BranchService);
  private wsService = inject(WebSocketService);
  private toastr = inject(ToastrService);
  private destroy$ = new Subject<void>();
  private subscribedTopic = '';
  private reactionsTopic = '';
  private typingTopic = '';
  private metaTopic = '';

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.currentUserId = storage.userId || 0;
    this.currentRole = storage.roleName || '';
    this.adminMode = this.currentRole === 'ADMIN' || this.currentRole === 'SUPER_ADMIN';
    this.loadAllBranches();
    this.loadUsers();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    this.unsubscribeFromTopic();
  }

  get filteredBranches(): any[] {
    if (!this.branchSearch) return this.allBranches;
    const q = this.branchSearch.toLowerCase();
    return this.allBranches.filter((b: any) =>
      b.name?.toLowerCase().includes(q) || b.branchCode?.toLowerCase().includes(q)
    );
  }

  loadAllBranches() {
    this.branchService.getAllBranches().subscribe({
      next: (data: any) => {
        this.allBranches = Array.isArray(data) ? data : data?.data || [];
        this.buildConversations();
        if (this.adminMode) this.loadAdminFeed();
      }
    });
  }

  loadUsers() {
    this.chatService.getAllUsers().subscribe({
      next: (res: any) => { this.users = res?.data || res || []; }
    });
  }

  buildConversations() {
    this.conversations = [];
    for (const branch of this.allBranches) {
      this.conversations.push({
        id: 'branch-' + branch.id,
        type: 'branch',
        branchId: branch.id,
        name: branch.name,
        code: branch.branchCode,
        lastMessage: ''
      });
    }
    this.loadUnreadCounts();
  }

  loadUnreadCounts() {
    if (!this.adminMode) return;
    this.chatService.getAllBranchMessages(0, 200).subscribe({
      next: (res: any) => {
        const msgs = res?.data?.content || res?.content || [];
        const counts: any = {};
        for (const m of msgs) {
          if (!m.isRead) {
            const key = 'branch-' + m.branchId;
            counts[key] = (counts[key] || 0) + 1;
          }
        }
        this.unreadCounts = counts;
      }
    });
  }

  loadAdminFeed() {
    this.chatService.getAllBranchMessages(0, 100).subscribe({
      next: (res: any) => {
        this.allBranchMessages = res?.data?.content || res?.content || [];
      }
    });
  }

  selectConversation(conv: any) {
    this.activeConvId = conv?.id || null;
    this.activeConv = conv || null;
    this.showProblemForm = false;
    this.pickerMessageId = null;
    this.closeMentions();
    this.closeThread();
    this.unsubscribeFromTopic();
    this.activeConversationId = null;
    if (!conv?.branchId) return;

    this.loading = true;
    this.messages = [];
    // Get-or-create the BRANCH conversation (idempotent) and join the participant model.
    this.chatService.createConversation({ conversationType: 'BRANCH', branchId: conv.branchId }).subscribe({
      next: (res: any) => {
        const conversation = res?.data || res;
        if (!conversation?.id) {
          this.loading = false;
          return;
        }
        this.activeConversationId = conversation.id;
        this.activeConversation = conversation;
        this.subscribeToConversation(conversation.id);
        this.subscribeToReactions(conversation.id);
        this.subscribeToTyping(conversation.id);
        this.subscribeToMeta(conversation.id);
        this.loadNotificationSetting(conversation.id);
        this.loadHistory(conversation.id);
      },
      error: (err: any) => {
        this.loading = false;
        this.toastr.error(err?.status === 403
          ? 'You do not have access to this branch conversation'
          : 'Failed to open conversation');
      }
    });
  }

  selectConversationByBranchId(branchId: number) {
    const conv = this.conversations.find(c => c.type === 'branch' && Number(c.branchId) === Number(branchId));
    if (conv) this.selectConversation(conv);
  }

  private loadHistory(conversationId: number) {
    this.chatService.getConversationMessages(conversationId).subscribe({
      next: (res: any) => {
        const page = res?.data || res || {};
        const content: any[] = page.content || [];
        this.messages = [...content].reverse();
        const last = this.messages.length > 0 ? this.messages[this.messages.length - 1] : null;
        this.chatService.markConversationRead(conversationId, last?.id).subscribe();
        this.chatService.markConversationDelivered(conversationId).subscribe({ next: () => {}, error: () => {} });
        if (this.activeConvId) this.unreadCounts[this.activeConvId] = 0;
        this.loading = false;
        this.scrollBottom();
      },
      error: () => { this.loading = false; }
    });
  }

  openProblemFormForCurrentConv() {
    if (this.activeConv?.branchId) {
      this.openProblemForm(this.activeConv.branchId);
    } else if (this.allBranches.length > 0) {
      this.openProblemForm(this.allBranches[0].id);
    }
  }

  sendMessage() {
    const text = this.newMessage.trim();
    if (!text || !this.activeConversationId || this.convLocked) return;
    this.chatService.sendConversationMessage(this.activeConversationId, {
      clientMessageId: this.uuid(),
      message: text
    }).subscribe({
      next: () => { this.newMessage = ''; },
      error: (err: any) => this.toastr.error(err?.status === 403
        ? 'You cannot post in this conversation'
        : 'Failed to send message')
    });
  }

  // ── @mentions ──────────────────────────────────────────────────────────────

  /** See ChatWidgetComponent.onComposerInput — same token contract. */
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

  /**
   * Splits a message body into plain-text and mention segments so mentions render as styled
   * chips without innerHTML (XSS-safe).
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

  /* ─── Report a Problem ─── */
  openProblemForm(branchId: number) {
    this.problemBranchId = branchId;
    this.problemSubject = '';
    this.problemDescription = '';
    this.showProblemForm = true;
    this.activeConvId = null;
    this.activeConv = null;
    this.activeConversationId = null;
    this.activeConversation = null;
    this.pickerMessageId = null;
    this.closeMentions();
    this.closeThread();
    this.unsubscribeFromTopic();
  }

  submitProblemReport() {
    if (!this.problemBranchId || !this.problemDescription.trim()) {
      this.toastr.warning('Please describe the problem');
      return;
    }
    const msg = '\u26A0 PROBLEM REPORT: ' + (this.problemSubject || 'General Issue')
      + '\n---\n' + this.problemDescription
      + '\n--- Reported from Branch #' + this.problemBranchId;

    this.chatService.createConversation({ conversationType: 'BRANCH', branchId: this.problemBranchId }).subscribe({
      next: (res: any) => {
        const conversation = res?.data || res;
        this.chatService.sendConversationMessage(conversation.id, {
          clientMessageId: this.uuid(), message: msg
        }).subscribe({
          next: () => {
            this.toastr.success('Problem reported to admin');
            this.showProblemForm = false;
            this.selectConversationByBranchId(this.problemBranchId!);
          },
          error: () => this.toastr.error('Failed to submit report')
        });
      },
      error: () => this.toastr.error('Failed to submit report')
    });
  }

  /* ─── Phase 2c–2j: threads, pin/bookmark, attachments, typing, notifications, lock ─── */
  openThread(msg: any) {
    if (!msg?.id) return;
    this.threadId = msg.id;
    this.threadReplies = [];
    this.threadReplyText = '';
    this.threadLoading = true;
    if (this.activeConversationId) {
      this.chatService.getReplies(this.activeConversationId, msg.id).subscribe({
        next: (res: any) => { this.threadReplies = res?.data || res || []; this.threadLoading = false; },
        error: () => { this.threadLoading = false; this.toastr.error('Failed to load thread'); }
      });
    }
  }

  threadParent(): any {
    return this.threadId != null ? this.messages.find(m => m.id === this.threadId) || null : null;
  }

  closeThread() {
    this.threadId = null;
    this.threadReplies = [];
    this.threadReplyText = '';
    this.threadLoading = false;
  }

  sendReply() {
    const text = this.threadReplyText.trim();
    if (!text || this.threadId == null || !this.activeConversationId || this.convLocked) return;
    this.chatService.sendConversationMessage(this.activeConversationId, {
      clientMessageId: this.uuid(),
      message: text,
      parentMessageId: this.threadId
    }).subscribe({
      next: (res: any) => {
        this.threadReplyText = '';
        const reply = res?.data || res;
        if (reply?.id) {
          this.threadReplies = [...this.threadReplies, reply];
          const parent = this.messages.find(m => m.id === this.threadId);
          if (parent) parent.replyCount = (parent.replyCount || 0) + 1;
        }
        setTimeout(() => {
          const el = document.querySelector('.bc-thread-replies');
          if (el) el.scrollTop = el.scrollHeight;
        }, 80);
      },
      error: (err: any) => this.toastr.error(err?.status === 403 ? 'You cannot post here' : 'Failed to reply')
    });
  }

  replyTo(msg: any) { this.openThread(msg); }

  canPin(msg: any): boolean {
    return !!msg && !msg.isDeleted && (this.isCurrentUser(msg.senderId)
      || this.currentRole === 'ADMIN' || this.currentRole === 'SUPER_ADMIN');
  }

  togglePinMsg(msg: any) {
    if (!this.activeConversationId) return;
    this.chatService.togglePin(this.activeConversationId, msg.id).subscribe({
      next: (res: any) => this.upsertMessage(res?.data || res),
      error: (err: any) => this.toastr.error(err?.status === 403
        ? 'Only the author or an admin can pin' : 'Failed to update pin')
    });
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

  markUnread() {
    if (!this.activeConversationId) return;
    const latest = this.messages[this.messages.length - 1];
    this.chatService.markConversationUnread(this.activeConversationId, latest?.id).subscribe({
      next: () => this.toastr.success('Marked as unread'),
      error: () => this.toastr.error('Failed to mark unread')
    });
  }

  loadNotificationSetting(conversationId: number) {
    this.chatService.getNotificationSetting(conversationId).subscribe({
      next: (res: any) => {
        const v = res?.data || res;
        if (v) this.notificationLevel = v;
      },
      error: () => {}
    });
  }

  cycleNotification() {
    if (!this.activeConversationId) return;
    const next = this.notificationLevel === 'ALL' ? 'MENTIONS'
      : this.notificationLevel === 'MENTIONS' ? 'MUTE' : 'ALL';
    this.chatService.updateNotificationSetting(this.activeConversationId, next).subscribe({
      next: (res: any) => {
        this.notificationLevel = res?.data || next;
        this.toastr.success('Notifications: ' + this.notificationLevel);
      },
      error: () => this.toastr.error('Failed to update notification setting')
    });
  }

  onFilePicked(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0] || null;
    input.value = '';
    if (!file) return;
    this.pendingFile = file;
    this.attachPendingFile();
  }

  private attachPendingFile() {
    if (!this.pendingFile || !this.activeConversationId || this.attaching) return;
    this.attaching = true;
    const text = this.newMessage.trim();
    if (text) {
      this.chatService.sendConversationMessage(this.activeConversationId, {
        clientMessageId: this.uuid(), message: text
      }).subscribe({
        next: (res: any) => { this.newMessage = ''; this.doUpload(res?.data?.id); },
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
    this.chatService.uploadAttachment(this.activeConversationId!, messageId, file).subscribe({
      next: () => { this.attaching = false; this.toastr.success('File attached'); },
      error: (err: any) => {
        this.attaching = false;
        const msg = err?.error?.message || '';
        this.toastr.error(msg.includes('malware') || msg.includes('scanner')
          ? 'Attachment rejected (malware scan)'
          : (msg.includes('25 MB') || msg.includes('not allowed') ? msg : 'Failed to upload file'));
      }
    });
  }

  fileName(a: any): string { return a?.fileName || 'file'; }

  fileSize(a: any): string {
    const b = Number(a?.fileSizeBytes || 0);
    if (b < 1024) return b + ' B';
    if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
    return (b / (1024 * 1024)).toFixed(1) + ' MB';
  }

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

  broadcastTyping() {
    if (!this.activeConversationId || this.typingTimer) return;
    this.chatService.sendTyping(this.activeConversationId).subscribe({ next: () => {}, error: () => {} });
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
      setTimeout(() => { if (this.typingUsers.has(evt.userId)) this.typingUsers.delete(evt.userId); }, 2500);
    });
  }

  get convLocked(): boolean {
    return !!this.activeConversation?.isLocked;
  }

  private subscribeToMeta(conversationId: number) {
    const topic = '/topic/conversation/' + conversationId + '/meta';
    this.metaTopic = topic;
    this.wsService.subscribe(topic).pipe(takeUntil(this.destroy$)).subscribe((evt: any) => {
      if (!evt || evt.conversationId !== conversationId) return;
      this.activeConversation = { ...(this.activeConversation || {}), isLocked: !!evt.locked };
      this.activeConv.isLocked = !!evt.locked;
      if (evt.locked) this.toastr.info('This conversation was locked by an admin');
    });
  }

  toggleLock() {
    if (!this.activeConversationId || !this.adminMode) return;
    const next = !this.activeConversation?.isLocked;
    this.chatService.setConversationLocked(this.activeConversationId, next).subscribe({
      next: () => {
        this.activeConversation = { ...(this.activeConversation || {}), isLocked: next };
        this.activeConv.isLocked = next;
      },
      error: () => this.toastr.error('Failed to update lock state')
    });
  }

  /* ─── Helpers ─── */
  getSenderName(userId: number): string {
    const u = this.users.find((x: any) => x.id === userId);
    return u ? u.name : 'User #' + userId;
  }

  isCurrentUser(userId: number): boolean {
    return userId === this.currentUserId;
  }

  getBranchName(branchId: number): string {
    const b = this.allBranches.find(x => Number(x.id) === Number(branchId));
    return b ? b.name : 'Branch #' + branchId;
  }

  getUnreadCount(branchId: number): number {
    return this.unreadCounts['branch-' + branchId] || 0;
  }

  hasMessages(branchId: number): boolean {
    return this.allBranchMessages.some((m: any) => Number(m.branchId) === Number(branchId));
  }

  getLatestMessage(branchId: number): any {
    const msgs = this.allBranchMessages.filter((m: any) => Number(m.branchId) === Number(branchId));
    if (msgs.length === 0) return null;
    return msgs.sort((a: any, b: any) => new Date(b.createdDate).getTime() - new Date(a.createdDate).getTime())[0];
  }

  hasUnread(branchId: number): boolean {
    return this.getUnreadCount(branchId) > 0;
  }

  getMessageCount(branchId: number): number {
    return this.allBranchMessages.filter((m: any) => Number(m.branchId) === Number(branchId)).length;
  }

  isProblemReport(msg: any): boolean {
    return msg?.message?.startsWith('\u26A0');
  }

  isConvActive(conv: any): boolean {
    return this.activeConvId === conv?.id;
  }

  convMatchesSearch(conv: any): boolean {
    if (!this.branchSearch) return true;
    const q = this.branchSearch.toLowerCase();
    return conv?.name?.toLowerCase().includes(q) || conv?.code?.toLowerCase().includes(q);
  }

  get canSend(): boolean {
    return this.newMessage.trim().length > 0;
  }

  scrollBottom() {
    setTimeout(() => {
      const el = document.querySelector('.chat-messages');
      if (el) el.scrollTop = el.scrollHeight;
    }, 100);
  }

  private subscribeToConversation(conversationId: number) {
    const topic = '/topic/conversation/' + conversationId;
    this.subscribedTopic = topic;
    this.wsService.subscribe(topic).pipe(takeUntil(this.destroy$)).subscribe((msg: any) => {
      if (!msg || msg.id == null) return;
      const existed = this.messages.some(m => m.id === msg.id);
      this.upsertMessage(msg);
      if (!existed) {
        setTimeout(() => this.scrollBottom(), 100);
        if (!this.isCurrentUser(msg.senderId) && this.activeConversationId) {
          this.chatService.markConversationRead(this.activeConversationId, msg.id).subscribe();
        }
      }
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

  // ── reactions ─────────────────────────────────────────────────────────────

  toggleReactionPicker(msg: any, event?: Event) {
    if (event) event.stopPropagation();
    this.pickerMessageId = this.pickerMessageId === msg.id ? null : msg.id;
  }

  hasReacted(msg: any, emoji: string): boolean {
    return !!msg?.reactions?.some((r: any) => r.emoji === emoji && r.reactedByMe);
  }

  toggleReaction(msg: any, emoji: string) {
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

  /** Append new messages, replace edited/deleted ones (reconciled by id). */
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
    if (this.reactionsTopic) {
      this.wsService.unsubscribe(this.reactionsTopic);
      this.reactionsTopic = '';
    }
    if (this.typingTopic) {
      this.wsService.unsubscribe(this.typingTopic);
      this.typingTopic = '';
    }
    if (this.metaTopic) {
      this.wsService.unsubscribe(this.metaTopic);
      this.metaTopic = '';
    }
  }

  private uuid(): string {
    const c: any = (globalThis as any).crypto;
    if (c && typeof c.randomUUID === 'function') return c.randomUUID();
    return 'cmid-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
  }
}
