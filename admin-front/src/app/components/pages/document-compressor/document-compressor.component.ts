import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { NgIf, NgFor, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { WebSocketService } from '../../../shared-services/websocket.service';
import { FileService, CompressionResponse } from '../../../shared-services/api-service/file.service';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { NgbPaginationModule } from '@ng-bootstrap/ng-bootstrap';

interface QueueItem {
  file: File;
  status: 'pending' | 'processing' | 'done' | 'error';
  jobId?: number;
  progressPercent?: number;
  message?: string;
}

type HistoryItem = CompressionResponse;

interface Stats {
  totalJobs: number;
  totalOriginalBytes: number;
  totalCompressedBytes: number;
  totalSavings: number;
}

@Component({
  selector: 'app-document-compressor',
  standalone: true,
  imports: [NgIf, NgFor, NgClass, FormsModule, NgbPaginationModule],
  templateUrl: './document-compressor.component.html',
  styleUrl: './document-compressor.component.scss'
})
export class DocumentCompressorComponent implements OnInit, OnDestroy {
  private fileService = inject(FileService);
  private toastr = inject(ToastrService);
  private wsService = inject(WebSocketService);

  queue: QueueItem[] = [];
  compressing = false;
  isDragover = false;
  compressType = 'GZIP';
  preset = 'BALANCED';
  compressionTypes = [
    { value: 'GZIP', label: 'GZIP', desc: 'Fast, single-file', icon: 'fas fa-file-archive' },
    { value: 'ZIP', label: 'ZIP', desc: 'Universal archive', icon: 'fas fa-file-zipper' },
    { value: 'TAR', label: 'TAR', desc: 'Uncompressed archive', icon: 'fas fa-tape' },
    { value: 'TAR_GZ', label: 'TAR.GZ', desc: 'Compressed archive', icon: 'fas fa-box' },
  ];
  presets = [
    { value: 'FAST', label: 'Fast', desc: 'Quick, larger' },
    { value: 'BALANCED', label: 'Balanced', desc: 'Recommended' },
    { value: 'MAXIMUM', label: 'Maximum', desc: 'Smallest output' },
    { value: 'IMAGE_OPTIMIZE', label: 'Image', desc: 'Re-encode images' },
  ];

  historyItems: HistoryItem[] = [];
  historyPage = 1;
  historySize = 20;
  historyTotalElements = 0;
  historyTotalPages = 1;
  loadingHistory = false;
  searchQuery = '';
  statusFilter = '';
  stats: Stats | null = null;

  private searchSubject = new Subject<string>();
  private searchSub!: Subscription;
  private progressSub!: Subscription;
  private pollTimers = new Map<number, ReturnType<typeof setTimeout>>();

  ngOnInit() {
    this.searchSub = this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(() => this.loadHistory());

    // Private per-user queue: the broker scopes /user/** to the authenticated principal, so no
    // other user's job progress (and therefore no filenames) can be observed here.
    this.progressSub = this.wsService.subscribe('/user/queue/compress').subscribe({
      next: (event) => this.onProgress(event)
    });

    this.loadHistory();
  }

  ngOnDestroy() {
    this.searchSub?.unsubscribe();
    this.progressSub?.unsubscribe();
    this.pollTimers.forEach(t => clearTimeout(t));
    this.pollTimers.clear();
  }

  onDragOver(e: DragEvent) { e.preventDefault(); this.isDragover = true; }
  onDragLeave(e: DragEvent) { e.preventDefault(); this.isDragover = false; }

  onDrop(e: DragEvent) {
    e.preventDefault();
    this.isDragover = false;
    if (e.dataTransfer?.files.length) {
      this.addFiles(Array.from(e.dataTransfer.files));
    }
  }

  onFilesSelected(e: Event) {
    const input = e.target as HTMLInputElement;
    if (input.files?.length) {
      this.addFiles(Array.from(input.files));
      input.value = '';
    }
  }

  private addFiles(files: File[]) {
    for (const file of files) {
      if (!this.queue.find(q => q.file.name === file.name && q.file.size === file.size)) {
        this.queue.push({ file, status: 'pending' });
      }
    }
  }

  removeFromQueue(item: QueueItem) {
    if (item.status === 'processing' && item.jobId != null) {
      this.cancelJobById(item.jobId);
    }
    this.queue = this.queue.filter(q => q !== item);
  }

  clearQueue() {
    this.queue.filter(q => q.status === 'processing' && q.jobId != null)
      .forEach(q => this.cancelJobById(q.jobId!));
    this.queue = [];
  }

  compressAll() {
    if (this.queue.length === 0 || this.compressing) return;
    const pending = this.queue.filter(q => q.status === 'pending' || q.status === 'error');
    if (pending.length === 0) { return; }

    this.compressing = true;
    let submitted = 0;

    for (const item of pending) {
      item.status = 'processing';
      item.progressPercent = 0;
      this.fileService.compress(item.file, this.compressType, this.preset).subscribe({
        next: (res: any) => {
          const job = res?.data ?? res;
          item.jobId = job?.id;
          const initialProgress = job?.progressPercent;
if (initialProgress != null && (item.progressPercent == null || initialProgress >= item.progressPercent)) {
  item.progressPercent = initialProgress;
}
          if (item.jobId != null) {
            this.pollJob(item.jobId);
          } else {
            item.status = 'error';
          }
          submitted++;
          if (submitted === pending.length) {
            this.loadStats();
          }
        },
        error: (err) => {
          item.status = 'error';
          item.message = err?.error?.message || 'Failed to submit';
          submitted++;
          this.toastr.error(`${item.file.name}: ${item.message}`);
        }
      });
    }
  }

  private onProgress(event: any) {
    if (!event) return;
    const item = this.queue.find(q => q.jobId === event.jobId);
    if (item) {
      const incomingProgress = event.progressPercent;
if (incomingProgress != null && (item.progressPercent == null || incomingProgress >= item.progressPercent)) {
  item.progressPercent = incomingProgress;
}
      item.message = event.message;
      if (event.status === 'COMPLETED') {
        this.finishItem(item, 'done');
      } else if (event.status === 'FAILED') {
        item.message = event.message;
        this.finishItem(item, 'error');
      } else if (event.status === 'CANCELLED') {
        this.finishItem(item, 'error');
      }
    }
  }

  /** Fallback poll: guarantees completion detection even if the WebSocket is unavailable. */
  private pollJob(jobId: number) {
    if (this.pollTimers.has(jobId)) return;
    const tick = () => {
      this.fileService.getJob(jobId).subscribe({
        next: (res: any) => {
          const job = res?.data ?? res;
          const item = this.queue.find(q => q.jobId === jobId);
          if (item) {
            const polledProgress = job?.progressPercent;
if (polledProgress != null && (item.progressPercent == null || polledProgress >= item.progressPercent)) {
  item.progressPercent = polledProgress;
}
          }
          if (job?.status === 'COMPLETED') {
            if (item) this.finishItem(item, 'done');
            return;
          }
          if (job && ['FAILED', 'CANCELLED'].includes(job.status)) {
            if (item) { item.message = job.errorMessage; this.finishItem(item, 'error'); }
            return;
          }
          this.pollTimers.set(jobId, setTimeout(tick, 2000));
        },
        error: () => {
          this.pollTimers.set(jobId, setTimeout(tick, 3000));
        }
      });
    };
    tick();
  }

  private finishItem(item: QueueItem, status: 'done' | 'error') {
    item.status = status;
    if (item.jobId != null) {
      const timer = this.pollTimers.get(item.jobId);
      if (timer) { clearTimeout(timer); this.pollTimers.delete(item.jobId); }
    }
    if (this.queue.every(q => q.status === 'done' || q.status === 'error')) {
      this.compressing = false;
      this.loadHistory();
      this.loadStats();
    }
  }

  private cancelJobById(jobId: number) {
    this.fileService.cancelJob(jobId).subscribe({ error: () => {} });
  }

  loadHistory() {
    this.loadingHistory = true;
    this.fileService.getHistory(this.historyPage - 1, this.historySize, this.statusFilter || undefined, this.searchQuery.trim() || undefined)
      .subscribe({
        next: (res: any) => {
          const data = res?.data || res;
          this.historyItems = data.items || [];
          this.historyPage = (data.page || 0) + 1;
          this.historySize = data.size || 20;
          this.historyTotalElements = data.totalElements || 0;
          this.historyTotalPages = data.totalPages || 1;

          if (!this.stats) {
            this.stats = {
              totalJobs: data.totalElements || 0,
              totalOriginalBytes: data.totalOriginalBytes || 0,
              totalCompressedBytes: data.totalCompressedBytes || 0,
              totalSavings: data.totalSavings || 0,
            };
          }
          this.loadingHistory = false;
        },
        error: () => { this.loadingHistory = false; }
      });
  }

  private loadStats() {
    this.fileService.getHistory(0, 1).subscribe({
      next: (res: any) => {
        const data = res?.data || res;
        this.stats = {
          totalJobs: data.totalElements || 0,
          totalOriginalBytes: data.totalOriginalBytes || 0,
          totalCompressedBytes: data.totalCompressedBytes || 0,
          totalSavings: data.totalSavings || 0,
        };
      }
    });
  }

  downloadJob(item: HistoryItem) {
    this.fileService.downloadJob(item.id).subscribe({
      next: (event: any) => {
        if (event.type === 4) { // HttpEventType.Response
          const a = document.createElement('a');
          a.href = URL.createObjectURL(event.body);
          a.download = item.compressedName || 'download';
          a.click();
          URL.revokeObjectURL(a.href);
        }
      },
      error: () => this.toastr.error('Download failed')
    });
  }

  verifyJob(item: HistoryItem) {
    this.fileService.verifyJob(item.id).subscribe({
      next: (res: any) => {
        const job = res?.data ?? res;
        item.verified = job?.verified;
        if (job?.verified) {
          this.toastr.success(`Integrity verified for ${item.originalName}`);
        } else {
          this.toastr.error(`Integrity check FAILED for ${item.originalName}`);
        }
      },
      error: () => this.toastr.error('Verification failed')
    });
  }

  cancelJob(item: HistoryItem) {
    this.fileService.cancelJob(item.id).subscribe({
      next: () => { this.toastr.info('Cancellation requested'); this.refreshHistory(); },
      error: () => this.toastr.error('Could not cancel')
    });
  }

  retryJob(item: HistoryItem) {
    this.fileService.retryJob(item.id).subscribe({
      next: () => { this.toastr.info('Retrying'); this.refreshHistory(); },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not retry')
    });
  }

  deleteJob(item: HistoryItem) {
    if (!confirm(`Delete compression record for "${item.originalName}"?`)) return;
    this.fileService.deleteJob(item.id).subscribe({
      next: () => {
        this.historyItems = this.historyItems.filter(h => h.id !== item.id);
        this.historyTotalElements--;
        this.loadStats();
        this.toastr.success('Record deleted');
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Failed to delete')
    });
  }

  onSearch() { this.searchSubject.next(this.searchQuery); }
  onFilterChange() { this.historyPage = 1; this.loadHistory(); }
  onPageChange(page: number) { this.historyPage = page; this.loadHistory(); }
  refreshHistory() { this.loadHistory(); this.loadStats(); }

  isTerminal(status: string): boolean {
    return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED';
  }

  statusBadgeClass(status: string): string {
    switch (status) {
      case 'COMPLETED': return 'badge-success';
      case 'FAILED': return 'badge-danger';
      case 'CANCELLED': return 'badge-secondary';
      case 'QUEUED': return 'badge-pending';
      default: return 'badge-progress';
    }
  }

  getFileIcon(name: string): string {
    const ext = name.split('.').pop()?.toLowerCase() || '';
    const icons: Record<string, string> = {
      pdf: 'fa-file-pdf', doc: 'fa-file-word', docx: 'fa-file-word',
      xls: 'fa-file-excel', xlsx: 'fa-file-excel',
      jpg: 'fa-file-image', jpeg: 'fa-file-image', png: 'fa-file-image', gif: 'fa-file-image', webp: 'fa-file-image',
      zip: 'fa-file-archive', gz: 'fa-file-archive', tar: 'fa-file-archive', rar: 'fa-file-archive', '7z': 'fa-file-archive',
      mp4: 'fa-file-video', avi: 'fa-file-video', mkv: 'fa-file-video', mov: 'fa-file-video',
      mp3: 'fa-file-audio', wav: 'fa-file-audio', flac: 'fa-file-audio',
      txt: 'fa-file-alt', csv: 'fa-file-csv',
    };
    return icons[ext] || 'fa-file';
  }

  formatBytes(bytes: number): string {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  trackByIndex(idx: number) { return idx; }
}
