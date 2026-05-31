import { Component, OnInit, inject, HostListener } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../../shared-services/api-service/chat.service';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-chat-widget',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './chat-widget.component.html',
  styleUrl: './chat-widget.component.scss'
})
export class ChatWidgetComponent implements OnInit {
  isOpen = false;
  allBranches: any[] = [];
  conversations: any[] = [];
  activeConv: any = null;
  messages: any[] = [];
  newMessage = '';
  loading = false;
  branchSearch = '';
  currentUserId = 0;
  currentRole = '';
  users: any[] = [];
  unreadCounts: { [key: string]: number } = {};
  showBranches = true;

  private chatService = inject(ChatService);
  private branchService = inject(BranchService);
  private toastr = inject(ToastrService);

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.currentUserId = storage.userId || 0;
    this.currentRole = storage.roleName || '';
    this.loadBranches();
    this.loadUsers();
  }

  get isAdmin(): boolean {
    return this.currentRole === 'ADMIN' || this.currentRole === 'SUPER_ADMIN';
  }

  get totalUnread(): number {
    return Object.values(this.unreadCounts).reduce((a, b) => a + b, 0);
  }

  get filteredConversations(): any[] {
    if (!this.branchSearch) return this.conversations;
    const q = this.branchSearch.toLowerCase();
    return this.conversations.filter(c =>
      c.name?.toLowerCase().includes(q) || c.code?.toLowerCase().includes(q)
    );
  }

  loadBranches() {
    this.branchService.getAllBranches().subscribe({
      next: (data: any) => {
        this.allBranches = Array.isArray(data) ? data : data?.data || [];
        this.conversations = this.allBranches.map(b => ({
          id: 'branch-' + b.id, type: 'branch', branchId: b.id,
          name: b.name, code: b.branchCode
        }));
        if (this.isAdmin) this.loadUnreadCounts();
      }
    });
  }

  loadUsers() {
    this.chatService.getAllUsers().subscribe({
      next: (res: any) => { this.users = res?.data || res || []; }
    });
  }

  loadUnreadCounts() {
    this.chatService.getAllBranchMessages(0, 500).subscribe({
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

  toggleOpen() {
    this.isOpen = !this.isOpen;
    if (this.isOpen && this.isAdmin) this.loadUnreadCounts();
    if (!this.isOpen) {
      this.activeConv = null;
      this.messages = [];
      this.showBranches = true;
    }
  }

  selectBranch(branch: any) {
    this.activeConv = branch;
    this.showBranches = false;
    this.loading = true;
    this.messages = [];
    this.chatService.getBranchMessages(branch.branchId).subscribe({
      next: (res: any) => {
        this.messages = res?.data || res || [];
        if (this.isAdmin) {
          this.chatService.markBranchRead(branch.branchId).subscribe();
          this.unreadCounts[branch.id] = 0;
        }
        this.loading = false;
        setTimeout(() => this.scrollBottom(), 100);
      },
      error: () => this.loading = false
    });
  }

  backToList() {
    this.activeConv = null;
    this.messages = [];
    this.showBranches = true;
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
        this.loadMessagesAfterSend();
      },
      error: () => this.toastr.error('Failed to send')
    });
  }

  loadMessagesAfterSend() {
    this.chatService.getBranchMessages(this.activeConv.branchId).subscribe({
      next: (res: any) => {
        this.messages = res?.data || res || [];
        setTimeout(() => this.scrollBottom(), 100);
      }
    });
  }

  reportProblem() {
    let branchId = this.activeConv?.branchId;
    if (!branchId && this.allBranches.length > 0) branchId = this.allBranches[0].id;
    if (!branchId) return;
    const msg = '⚠ PROBLEM REPORT\n---\n' + prompt('Describe your problem:');
    if (!msg || msg === '⚠ PROBLEM REPORT\n---\n') return;
    this.chatService.sendMessage({
      branchId, conversationType: 'BRANCH_CHAT', message: msg
    }).subscribe({
      next: () => {
        this.toastr.success('Problem reported to admins');
        this.selectBranch(this.activeConv || this.conversations.find(c => c.branchId === branchId));
      }
    });
  }

  getSenderName(userId: number): string {
    const u = this.users.find((x: any) => x.id === userId);
    return u ? u.name : 'User #' + userId;
  }

  isCurrentUser(userId: number): boolean {
    return userId === this.currentUserId;
  }

  isProblemReport(msg: any): boolean {
    return msg?.message?.startsWith('⚠');
  }

  scrollBottom() {
    const el = document.querySelector('.cw-messages');
    if (el) el.scrollTop = el.scrollHeight;
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.isOpen) {
      if (this.activeConv) { this.backToList(); }
      else { this.isOpen = false; this.showBranches = true; }
    }
  }
}
