import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { NgIf, NgFor, NgClass, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';
import { ToastrService } from 'ngx-toastr';
import { WebSocketService } from '../../../shared-services/websocket.service';
import { UserService } from '../../../shared-services/api-service/user.service';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { NgbPaginationModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

interface UserDto {
  id: number; name: string; username: string; email: string;
  branchIds?: number[]; roleName?: string;
}

interface BranchDto {
  id: number; name: string; branchCode?: string;
}

interface RecipientChip {
  id: number; name: string; branchName?: string; branchId?: number;
}

interface TransferItem {
  id: number; referenceNo: string;
  senderId: number; senderName: string; senderBranchId?: number; senderBranchName?: string;
  recipientId: number; recipientName: string; recipientBranchId?: number; recipientBranchName?: string;
  subject: string; message?: string;
  transferStatus: string; priority: string;
  totalFiles: number; totalSize: number;
  sentDate?: string; receivedDate?: string; createdDate: string;
}

interface TransferDetail extends TransferItem {
  archivedDate?: string; notes?: string;
  files: { id: number; originalName: string; fileSize: number; mimeType?: string }[];
}

interface Stats {
  totalSent: number; totalReceived: number;
  pendingCount: number; completedCount: number;
  totalFilesTransferred: number; totalBytesTransferred: number;
}

@Component({
  selector: 'app-transfer-document',
  standalone: true,
  imports: [NgIf, NgFor, NgClass, DatePipe, FormsModule, NgbPaginationModule, NgbTooltipModule],
  templateUrl: './transfer-document.component.html',
  styleUrl: './transfer-document.component.scss'
})
export class TransferDocumentComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private toastr = inject(ToastrService);
  private wsService = inject(WebSocketService);
  private userService = inject(UserService);
  private branchService = inject(BranchService);

  activeTab: 'sent' | 'received' = 'sent';
  showCreateForm = false;

  // Users & Branches
  users: UserDto[] = [];
  branches: BranchDto[] = [];
  filteredUsers: UserDto[] = [];

  // Create form — multi-recipient
  recipientChips: RecipientChip[] = [];
  recipientInput = '';
  recipientInputFocused = false;
  selectedBranchId: number | null = null;
  createForm = {
    subject: '',
    message: '',
    priority: 'MEDIUM'
  };
  selectedFiles: File[] = [];
  dragOver = false;
  creating = false;

  // Sent transfers
  sentItems: TransferItem[] = [];
  sentPage = 1;
  sentSize = 20;
  sentTotalElements = 0;
  sentTotalPages = 1;
  loadingSent = false;
  sentSearch = '';
  sentStatusFilter = '';

  // Received transfers
  receivedItems: TransferItem[] = [];
  receivedPage = 1;
  receivedSize = 20;
  receivedTotalElements = 0;
  receivedTotalPages = 1;
  loadingReceived = false;
  receivedSearch = '';
  receivedStatusFilter = '';

  // Detail
  selectedTransfer: TransferDetail | null = null;
  loadingDetail = false;

  // Stats
  stats: Stats | null = null;

  // Search debounce
  private searchSubject = new Subject<{ tab: string; value: string }>();
  private searchSub!: Subscription;
  private transferSub!: Subscription;

  priorities = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

  statusBadgeMap: Record<string, string> = {
    DRAFT: 'bg-secondary', SENT: 'bg-primary',
    DELIVERED: 'bg-info', RECEIVED: 'bg-success',
    CANCELLED: 'bg-danger', ARCHIVED: 'bg-dark'
  };

  ngOnInit() {
    this.searchSub = this.searchSubject.pipe(
      debounceTime(300), distinctUntilChanged()
    ).subscribe(({ tab }) => {
      if (tab === 'sent') this.loadSent();
      else this.loadReceived();
    });

    this.transferSub = this.wsService.subscribe('/topic/transfer/*').subscribe({
      next: () => { this.loadSent(); this.loadReceived(); this.loadStats(); }
    });

    this.userService.getAllUsers().subscribe({
      next: (res: any) => {
        this.users = res?.data || res || [];
      }
    });

    this.branchService.getAllBranches().subscribe({
      next: (res: any) => {
        this.branches = res?.data || res || [];
      }
    });

    this.loadAll();
  }

  ngOnDestroy() {
    this.searchSub?.unsubscribe();
    this.transferSub?.unsubscribe();
  }

  private loadAll() {
    this.loadSent();
    this.loadReceived();
    this.loadStats();
  }

  // ─── Tabs ────────────────────────────────────────────────

  switchTab(tab: 'sent' | 'received') {
    this.activeTab = tab;
    if (tab === 'sent') this.loadSent();
    else this.loadReceived();
  }

  // ─── Stats ────────────────────────────────────────────────

  private loadStats() {
    this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/stats`).subscribe({
      next: (res: any) => { this.stats = res?.data || res; }
    });
  }

  // ─── Multi-Recipient Chip Input ──────────────────────────

  onRecipientInputChange() {
    if (!this.recipientInput.trim()) {
      this.filteredUsers = [];
      return;
    }
    const q = this.recipientInput.toLowerCase();
    const selectedIds = new Set(this.recipientChips.map(c => c.id));
    this.filteredUsers = this.users.filter(u =>
      !selectedIds.has(u.id) &&
      (u.name.toLowerCase().includes(q) || u.username.toLowerCase().includes(q) || u.email.toLowerCase().includes(q))
    ).slice(0, 8);
  }

  selectRecipient(user: UserDto) {
    const branch = user.branchIds?.length ? this.branches.find(b => b.id === user.branchIds![0]) : null;
    this.recipientChips.push({
      id: user.id,
      name: user.name,
      branchId: branch?.id,
      branchName: branch?.name
    });
    this.recipientInput = '';
    this.filteredUsers = [];
  }

  removeRecipient(idx: number) {
    this.recipientChips.splice(idx, 1);
  }

  onRecipientInputBlur() {
    setTimeout(() => { this.recipientInputFocused = false; this.filteredUsers = []; }, 200);
  }

  onRecipientInputKeydown(e: KeyboardEvent) {
    if (e.key === 'Backspace' && !this.recipientInput && this.recipientChips.length) {
      this.recipientChips.pop();
    }
    if (e.key === 'Enter' && this.filteredUsers.length === 1) {
      this.selectRecipient(this.filteredUsers[0]);
      e.preventDefault();
    }
  }

  // ─── Create Transfer ─────────────────────────────────────

  toggleCreateForm() {
    this.showCreateForm = !this.showCreateForm;
    if (!this.showCreateForm) this.resetForm();
  }

  onFilesSelected(e: Event) {
    const input = e.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectedFiles = [...this.selectedFiles, ...Array.from(input.files)];
    }
    input.value = '';
  }

  onDrop(e: DragEvent) {
    e.preventDefault();
    this.dragOver = false;
    if (e.dataTransfer?.files?.length) {
      this.selectedFiles = [...this.selectedFiles, ...Array.from(e.dataTransfer.files)];
    }
  }

  onDragOver(e: DragEvent) { e.preventDefault(); this.dragOver = true; }
  onDragLeave() { this.dragOver = false; }

  removeFile(idx: number) {
    this.selectedFiles.splice(idx, 1);
  }

  clearFiles() { this.selectedFiles = []; }

  private resetForm() {
    this.recipientChips = [];
    this.recipientInput = '';
    this.selectedBranchId = null;
    this.createForm = { subject: '', message: '', priority: 'MEDIUM' };
    this.selectedFiles = [];
  }

  createTransfer() {
    if (!this.recipientChips.length || !this.createForm.subject || this.selectedFiles.length === 0) {
      this.toastr.warning('Recipient(s), subject, and at least one file are required');
      return;
    }
    this.creating = true;
    const fd = new FormData();
    const recipientIds = this.recipientChips.map(c => c.id).join(',');
    const recipientNames = this.recipientChips.map(c => c.name).join(',');
    const recipientBranchIds = this.recipientChips.map(c => c.branchId || '').join(',');
    const recipientBranchNames = this.recipientChips.map(c => c.branchName || '').join(',');

    fd.append('recipientIds', recipientIds);
    fd.append('recipientNames', recipientNames);
    fd.append('recipientBranchIds', recipientBranchIds);
    fd.append('recipientBranchNames', recipientBranchNames);
    fd.append('subject', this.createForm.subject);
    if (this.createForm.message) fd.append('message', this.createForm.message);
    fd.append('priority', this.createForm.priority);
    for (const file of this.selectedFiles) fd.append('files', file);

    this.http.post(`${ApiConfig.URL}/api/v1/files/transfer`, fd).subscribe({
      next: () => {
        this.toastr.success('Transfer created');
        this.showCreateForm = false;
        this.resetForm();
        this.loadAll();
        this.creating = false;
      },
      error: () => { this.toastr.error('Failed to create transfer'); this.creating = false; }
    });
  }

  // ─── Actions ─────────────────────────────────────────────

  sendTransfer(item: TransferItem) {
    this.http.post(`${ApiConfig.URL}/api/v1/files/transfer/${item.id}/send`, {}).subscribe({
      next: () => { this.toastr.success('Transfer sent'); this.loadAll(); },
      error: () => this.toastr.error('Failed to send')
    });
  }

  receiveTransfer(item: TransferItem) {
    this.http.post(`${ApiConfig.URL}/api/v1/files/transfer/${item.id}/receive`, {}).subscribe({
      next: () => { this.toastr.success('Transfer received'); this.loadAll(); },
      error: () => this.toastr.error('Failed to receive')
    });
  }

  deliverTransfer(item: TransferItem) {
    this.http.post(`${ApiConfig.URL}/api/v1/files/transfer/${item.id}/deliver`, {}).subscribe({
      next: () => { this.toastr.success('Transfer delivered'); this.loadAll(); },
      error: () => this.toastr.error('Failed to deliver')
    });
  }

  archiveTransfer(item: TransferItem) {
    this.http.post(`${ApiConfig.URL}/api/v1/files/transfer/${item.id}/archive`, {}).subscribe({
      next: () => { this.toastr.success('Transfer archived'); this.loadAll(); },
      error: () => this.toastr.error('Failed to archive')
    });
  }

  cancelTransfer(item: TransferItem) {
    if (!confirm(`Cancel transfer "${item.referenceNo}"?`)) return;
    this.http.delete(`${ApiConfig.URL}/api/v1/files/transfer/${item.id}`).subscribe({
      next: () => { this.toastr.success('Transfer cancelled'); this.loadAll(); },
      error: () => this.toastr.error('Failed to cancel')
    });
  }

  // ─── Detail ───────────────────────────────────────────────

  openDetail(item: TransferItem) {
    this.loadingDetail = true;
    this.selectedTransfer = null;
    this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/${item.id}`).subscribe({
      next: (res: any) => { this.selectedTransfer = res?.data || res; this.loadingDetail = false; },
      error: () => { this.toastr.error('Failed to load detail'); this.loadingDetail = false; }
    });
  }

  closeDetail() { this.selectedTransfer = null; }

  downloadFile(file: { id: number; originalName: string }) {
    if (!this.selectedTransfer) return;
    this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/${this.selectedTransfer.id}/files/${file.id}/download`, {
      responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = file.originalName;
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => this.toastr.error('Download failed')
    });
  }

  downloadAll() {
    if (!this.selectedTransfer) return;
    this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/${this.selectedTransfer.id}/download-all`, {
      responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `transfer-${this.selectedTransfer!.referenceNo}.zip`;
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => this.toastr.error('Download failed')
    });
  }

  // ─── Search / Filter / Pagination ─────────────────────────

  onSentSearch() { this.searchSubject.next({ tab: 'sent', value: this.sentSearch }); }
  onReceivedSearch() { this.searchSubject.next({ tab: 'received', value: this.receivedSearch }); }

  onSentFilterChange() { this.sentPage = 1; this.loadSent(); }
  onReceivedFilterChange() { this.receivedPage = 1; this.loadReceived(); }

  onSentPageChange(page: number) { this.sentPage = page; this.loadSent(); }
  onReceivedPageChange(page: number) { this.receivedPage = page; this.loadReceived(); }

  // ─── Data Loading ─────────────────────────────────────────

  private loadSent() {
    this.loadingSent = true;
    const params: any = { page: this.sentPage - 1, size: this.sentSize };
    if (this.sentStatusFilter) params.status = this.sentStatusFilter;
    if (this.sentSearch.trim()) params.search = this.sentSearch.trim();
    this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/sent`, { params }).subscribe({
      next: (res: any) => {
        const data = res?.data || res;
        this.sentItems = data.items || [];
        this.sentPage = (data.page || 0) + 1;
        this.sentSize = data.size || 20;
        this.sentTotalElements = data.totalElements || 0;
        this.sentTotalPages = data.totalPages || 1;
        this.loadingSent = false;
      },
      error: () => { this.loadingSent = false; }
    });
  }

  private loadReceived() {
    this.loadingReceived = true;
    const params: any = { page: this.receivedPage - 1, size: this.receivedSize };
    if (this.receivedStatusFilter) params.status = this.receivedStatusFilter;
    if (this.receivedSearch.trim()) params.search = this.receivedSearch.trim();
    this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/received`, { params }).subscribe({
      next: (res: any) => {
        const data = res?.data || res;
        this.receivedItems = data.items || [];
        this.receivedPage = (data.page || 0) + 1;
        this.receivedSize = data.size || 20;
        this.receivedTotalElements = data.totalElements || 0;
        this.receivedTotalPages = data.totalPages || 1;
        this.loadingReceived = false;
      },
      error: () => { this.loadingReceived = false; }
    });
  }

  // ─── Helpers ──────────────────────────────────────────────

  formatBytes(bytes: number): string {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024; const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  getStatusBadge(status: string): string {
    return this.statusBadgeMap[status] || 'bg-secondary';
  }

  getFileIcon(name: string): string {
    const ext = name.split('.').pop()?.toLowerCase() || '';
    const icons: Record<string, string> = {
      pdf: 'fa-file-pdf', doc: 'fa-file-word', docx: 'fa-file-word',
      xls: 'fa-file-excel', xlsx: 'fa-file-excel',
      jpg: 'fa-file-image', jpeg: 'fa-file-image', png: 'fa-file-image', gif: 'fa-file-image',
      zip: 'fa-file-archive', gz: 'fa-file-archive', tar: 'fa-file-archive', rar: 'fa-file-archive',
      txt: 'fa-file-alt', csv: 'fa-file-csv',
    };
    return icons[ext] || 'fa-file';
  }

  getCurrentItems(): TransferItem[] {
    return this.activeTab === 'sent' ? this.sentItems : this.receivedItems;
  }

  getUserBranchName(userId: number): string {
    const user = this.users.find(u => u.id === userId);
    if (user?.branchIds?.length) {
      const b = this.branches.find(b => b.id === user.branchIds![0]);
      return b?.name || '';
    }
    return '';
  }

  getUserAvatar(name: string): string {
    return name.split(' ').map(s => s[0]).join('').toUpperCase().slice(0, 2);
  }

  trackById(idx: number, item: any) { return item.id; }
}
