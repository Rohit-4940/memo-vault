import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { NgIf, NgFor, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';
import { ToastrService } from 'ngx-toastr';
import { WebSocketService } from '../../../shared-services/websocket.service';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { NgbPaginationModule } from '@ng-bootstrap/ng-bootstrap';

interface QueueItem {
  file: File;
  status: 'pending' | 'processing' | 'done' | 'error';
  result?: any;
}

interface HistoryItem {
  id: number;
  originalName: string;
  originalSize: number;
  originalType: string;
  compressedName: string;
  compressedSize: number;
  compressionType: string;
  status: string;
  durationMs: number;
  ratioPercent: number;
  savings: number;
  savingsPercent: number;
  errorMessage: string;
  createdDate: string;
  completedDate: string;
}

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
  private http = inject(HttpClient);
  private toastr = inject(ToastrService);
  private wsService = inject(WebSocketService);

  queue: QueueItem[] = [];
  compressing = false;
  isDragover = false;
  compressType = 'GZIP';
  compressionTypes = [
    { value: 'GZIP', label: 'GZIP', desc: 'Fast, single-file', icon: 'fas fa-file-archive' },
    { value: 'ZIP', label: 'ZIP', desc: 'Universal archive', icon: 'fas fa-file-zipper' },
    { value: 'TAR', label: 'TAR', desc: 'Uncompressed archive', icon: 'fas fa-tape' },
    { value: 'TAR_GZ', label: 'TAR.GZ', desc: 'Compressed archive', icon: 'fas fa-box' },
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

  ngOnInit() {
    this.searchSub = this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(() => this.loadHistory());

    this.progressSub = this.wsService.subscribe('/topic/compress/*').subscribe({
      next: (event) => {
        const jobId = event.jobId;
        const status = event.status;
        const fileName = event.fileName;
        const percent = event.progressPercent;

        if (percent === 100 && (status === 'COMPLETED' || status === 'FAILED')) {
          this.updateQueueItem(fileName, status === 'COMPLETED' ? 'done' : 'error');
          this.loadHistory();
          this.loadStats();
        }
      }
    });

    this.loadHistory();
  }

  ngOnDestroy() {
    this.searchSub?.unsubscribe();
    this.progressSub?.unsubscribe();
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
    this.queue = this.queue.filter(q => q !== item);
  }

  clearQueue() {
    this.queue = [];
  }

  compressAll() {
    if (this.queue.length === 0 || this.compressing) return;
    this.compressing = true;

    const pending = this.queue.filter(q => q.status === 'pending' || q.status === 'error');
    if (pending.length === 0) { this.compressing = false; return; }

    let completed = 0;
    for (const item of pending) {
      item.status = 'processing';
      const fd = new FormData();
      fd.append('file', item.file);
      fd.append('type', this.compressType);

      this.http.post(`${ApiConfig.URL}/api/v1/files/compress`, fd).subscribe({
        next: (res: any) => {
          item.status = 'done';
          item.result = res?.data || res;
          completed++;
          if (completed === pending.length) {
            this.compressing = false;
            this.loadHistory();
            this.loadStats();
            this.toastr.success(`${completed} file(s) compressed`);
          }
        },
        error: (err) => {
          item.status = 'error';
          completed++;
          if (completed === pending.length) {
            this.compressing = false;
            this.loadHistory();
            this.loadStats();
            this.toastr.error('Some files failed to compress');
          }
        }
      });
    }
  }

  private updateQueueItem(fileName: string, status: 'done' | 'error') {
    const item = this.queue.find(q => q.file.name === fileName);
    if (item) item.status = status;
    if (this.queue.every(q => q.status === 'done' || q.status === 'error')) {
      this.compressing = false;
    }
  }

  loadHistory() {
    this.loadingHistory = true;
    const params: any = { page: this.historyPage - 1, size: this.historySize };
    if (this.statusFilter) params.status = this.statusFilter;
    if (this.searchQuery.trim()) params.search = this.searchQuery.trim();

    this.http.get(`${ApiConfig.URL}/api/v1/files/compress/history`, { params }).subscribe({
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
    this.http.get(`${ApiConfig.URL}/api/v1/files/compress/history`, {
      params: { page: 0, size: 1 }
    }).subscribe({
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
    this.http.get(`${ApiConfig.URL}/api/v1/files/compress/${item.id}/download`, {
      responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = item.compressedName || 'download';
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => this.toastr.error('Download failed')
    });
  }

  deleteJob(item: HistoryItem) {
    if (!confirm(`Delete compression record for "${item.originalName}"?`)) return;
    this.http.delete(`${ApiConfig.URL}/api/v1/files/compress/${item.id}`).subscribe({
      next: () => {
        this.historyItems = this.historyItems.filter(h => h.id !== item.id);
        this.historyTotalElements--;
        this.loadStats();
        this.toastr.success('Record deleted');
      },
      error: () => this.toastr.error('Failed to delete')
    });
  }

  onSearch() { this.searchSubject.next(this.searchQuery); }
  onFilterChange() { this.historyPage = 1; this.loadHistory(); }
  onPageChange(page: number) { this.historyPage = page; this.loadHistory(); }
  refreshHistory() { this.loadHistory(); this.loadStats(); }

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
