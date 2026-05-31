import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DatePipe, SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../../shared-services/api-service/chat.service';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-branch-chat',
  standalone: true,
  imports: [NgForOf, NgIf, DatePipe, SlicePipe, FormsModule],
  templateUrl: './branch-chat.component.html',
  styleUrl: './branch-chat.component.scss'
})
export class BranchChatComponent implements OnInit {
  allBranches: any[] = [];
  conversations: any[] = [];
  activeConvId: string | null = null;
  activeConv: any = null;
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

  private chatService = inject(ChatService);
  private branchService = inject(BranchService);
  private toastr = inject(ToastrService);

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.currentUserId = storage.userId || 0;
    this.currentRole = storage.roleName || '';
    this.adminMode = this.currentRole === 'ADMIN' || this.currentRole === 'SUPER_ADMIN';
    this.loadAllBranches();
    this.loadUsers();
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
    if (conv) this.loadMessages(conv);
  }

  selectConversationByBranchId(branchId: number) {
    const conv = this.conversations.find(c => c.type === 'branch' && Number(c.branchId) === Number(branchId));
    if (conv) this.selectConversation(conv);
  }

  openProblemFormForCurrentConv() {
    if (this.activeConv?.branchId) {
      this.openProblemForm(this.activeConv.branchId);
    } else if (this.allBranches.length > 0) {
      this.openProblemForm(this.allBranches[0].id);
    }
  }

  loadMessages(conv: any) {
    this.loading = true;
    this.messages = [];
    if (conv.type === 'branch') {
      this.chatService.getBranchMessages(conv.branchId).subscribe({
        next: (res: any) => {
          this.messages = res?.data || res || [];
          this.chatService.markBranchRead(conv.branchId).subscribe();
          this.unreadCounts[conv.id] = 0;
          this.scrollBottom();
          this.loading = false;
        },
        error: () => this.loading = false
      });
    }
  }

  sendMessage() {
    if (!this.newMessage.trim() || !this.activeConv) return;
    const payload = {
      branchId: this.activeConv.branchId,
      conversationType: 'BRANCH_CHAT',
      message: this.newMessage
    };
    this.chatService.sendMessage(payload).subscribe({
      next: () => {
        this.newMessage = '';
        this.loadMessages(this.activeConv);
      },
      error: () => this.toastr.error('Failed to send message')
    });
  }

  /* ─── Report a Problem ─── */
  openProblemForm(branchId: number) {
    this.problemBranchId = branchId;
    this.problemSubject = '';
    this.problemDescription = '';
    this.showProblemForm = true;
    this.activeConvId = null;
    this.activeConv = null;
  }

  submitProblemReport() {
    if (!this.problemBranchId || !this.problemDescription.trim()) {
      this.toastr.warning('Please describe the problem');
      return;
    }
    const msg = '⚠ PROBLEM REPORT: ' + (this.problemSubject || 'General Issue')
      + '\n---\n' + this.problemDescription
      + '\n--- Reported from Branch #' + this.problemBranchId;
    const payload = {
      branchId: this.problemBranchId,
      conversationType: 'BRANCH_CHAT',
      message: msg
    };
    this.chatService.sendMessage(payload).subscribe({
      next: () => {
        this.toastr.success('Problem reported to admin');
        this.showProblemForm = false;
        this.selectConversationByBranchId(this.problemBranchId!);
      },
      error: () => this.toastr.error('Failed to submit report')
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
    return msg?.message?.startsWith('⚠');
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
}
