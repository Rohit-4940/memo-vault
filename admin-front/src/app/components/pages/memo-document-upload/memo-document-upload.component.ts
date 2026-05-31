import { Component, Input, inject, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { NgIf, NgClass, DatePipe, NgFor } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpEventType } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';

interface UploadingFile {
  name: string;
  progress: number;
  status: 'uploading' | 'success' | 'error';
}

@Component({
  selector: 'app-memo-document-upload',
  standalone: true,
  imports: [NgIf, NgClass, NgFor, DatePipe, FormsModule],
  template: `
    <div class="memo-doc-upload">
      <div class="doc-card">
        <div class="doc-card-header">
          <h3><i class="fas fa-file-upload"></i> Documents</h3>
          <span class="doc-count" *ngIf="documents.length">{{ documents.length }} file{{ documents.length !== 1 ? 's' : '' }}</span>
        </div>

        <div class="doc-card-body">
          <div
            class="drop-zone"
            [ngClass]="{ 'drag-over': dragOver, 'has-error': validationError }"
            (dragover)="onDragOver($event)"
            (dragleave)="onDragLeave($event)"
            (drop)="onDrop($event)"
            (click)="fileInput.click()"
          >
            <input
              #fileInput
              type="file"
              multiple
              [accept]="allowedAccept"
              (change)="onFileSelected($event)"
              class="file-input"
            />
            <div class="drop-zone-content">
              <div class="drop-icon">
                <i class="fas fa-cloud-upload-alt"></i>
              </div>
              <p class="drop-text" *ngIf="!dragOver">
                <strong>Click to browse</strong> or drag and drop files here
              </p>
              <p class="drop-text" *ngIf="dragOver">
                <strong>Drop files here</strong>
              </p>
              <p class="drop-hint">PDF, PNG, JPG, DOC, DOCX, XLS, XLSX — Max 10MB each</p>
            </div>
          </div>

          <div class="validation-error" *ngIf="validationError">
            <i class="fas fa-exclamation-circle"></i> {{ validationError }}
          </div>

          <div class="uploading-list" *ngIf="uploadingFiles.length">
            <div class="upload-item" *ngFor="let f of uploadingFiles">
              <div class="upload-info">
                <i class="fas fa-file"></i>
                <span class="upload-name">{{ f.name }}</span>
              </div>
              <div class="upload-progress-wrap">
                <div class="upload-progress-bar">
                  <div class="upload-progress-fill" [style.width.%]="f.progress"></div>
                </div>
                <span class="upload-percent" *ngIf="f.status === 'uploading'">{{ f.progress }}%</span>
                <i class="fas fa-check-circle text-success" *ngIf="f.status === 'success'"></i>
                <i class="fas fa-times-circle text-danger" *ngIf="f.status === 'error'"></i>
              </div>
            </div>
          </div>

          <div class="loading-wrap" *ngIf="loading">
            <div class="spinner"></div>
            <span>Loading documents...</span>
          </div>

          <div class="empty-state" *ngIf="!loading && documents.length === 0">
            <i class="fas fa-file-alt"></i>
            <p>No documents uploaded yet</p>
            <span class="empty-hint">Upload receipts, invoices, or supporting files</span>
          </div>

          <div class="documents-list" *ngIf="documents.length">
            <div class="doc-item" *ngFor="let doc of documents">
              <div class="doc-icon">
                <i [class]="getFileIcon(doc.fileName || doc.originalFileName || doc.name)"></i>
              </div>
              <div class="doc-info">
                <span class="doc-name">{{ doc.fileName || doc.originalFileName || doc.name }}</span>
                <div class="doc-meta">
                  <span class="doc-size">{{ formatFileSize(doc.fileSize || doc.size) }}</span>
                  <span class="doc-sep" *ngIf="doc.uploadedAt || doc.createdAt">|</span>
                  <span class="doc-date">{{ (doc.uploadedAt || doc.createdAt | date:'mediumDate') || '' }}</span>
                </div>
              </div>
              <div class="doc-type-badge">
                <span class="badge">{{ getFileExtension(doc.fileName || doc.originalFileName || doc.name) }}</span>
              </div>
              <button class="btn-delete" (click)="confirmDelete(doc)" title="Delete document">
                <i class="fas fa-trash-alt"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
    :host { display: block; }
    .memo-doc-upload { width: 100%; }
    .doc-card {
      background: rgba(255,255,255,0.75);
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
      border: 1px solid var(--nexmemo-border);
      border-radius: var(--radius-lg);
      box-shadow: 0 8px 32px rgba(0,0,0,0.06);
      overflow: hidden;
      transition: box-shadow var(--transition-normal);
    }
    .doc-card:hover { box-shadow: 0 8px 32px rgba(79,70,229,0.08); }
    .doc-card-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 1rem 1.5rem; border-bottom: 1px solid var(--nexmemo-border-light);
    }
    .doc-card-header h3 {
      font-size: 1rem; font-weight: 600; color: var(--nexmemo-text);
      display: flex; align-items: center; gap: 0.5rem;
    }
    .doc-card-header h3 i { color: var(--nexmemo-primary); font-size: 1rem; }
    .doc-count {
      font-size: 0.75rem; font-weight: 500; color: var(--nexmemo-text-secondary);
      background: var(--nexmemo-bg); padding: 0.2rem 0.65rem;
      border-radius: var(--radius-full);
    }
    .doc-card-body { padding: 1.25rem 1.5rem 1.5rem; }

    .drop-zone {
      position: relative; border: 2px dashed var(--nexmemo-border);
      border-radius: var(--radius-md); padding: 2rem 1.5rem;
      text-align: center; cursor: pointer;
      transition: all var(--transition-fast);
      background: var(--nexmemo-primary-light);
    }
    .drop-zone:hover { border-color: var(--nexmemo-primary); background: #e8ecff; }
    .drop-zone.drag-over {
      border-color: var(--nexmemo-primary); background: #dde3ff;
      transform: scale(1.01); box-shadow: 0 0 0 4px rgba(79,70,229,0.1);
    }
    .drop-zone.has-error { border-color: var(--nexmemo-danger); background: var(--nexmemo-danger-light); }
    .file-input { display: none; }
    .drop-zone-content { pointer-events: none; }
    .drop-icon { font-size: 2.5rem; color: var(--nexmemo-primary); margin-bottom: 0.75rem; }
    .drop-zone.drag-over .drop-icon { animation: bounce 0.6s ease infinite; }
    .drop-text { font-size: 0.9rem; color: var(--nexmemo-text); margin: 0 0 0.35rem; }
    .drop-text strong { color: var(--nexmemo-primary); }
    .drop-hint { font-size: 0.75rem; color: var(--nexmemo-text-muted); margin: 0; }

    .validation-error {
      display: flex; align-items: center; gap: 0.5rem;
      margin-top: 0.75rem; padding: 0.6rem 0.85rem;
      background: var(--nexmemo-danger-light); color: var(--nexmemo-danger-dark);
      border-radius: var(--radius-sm); font-size: 0.8rem;
    }

    .uploading-list { margin-top: 1rem; display: flex; flex-direction: column; gap: 0.5rem; }
    .upload-item {
      display: flex; align-items: center; justify-content: space-between;
      padding: 0.6rem 0.85rem; background: var(--nexmemo-bg);
      border-radius: var(--radius-sm); gap: 0.75rem;
    }
    .upload-info { display: flex; align-items: center; gap: 0.5rem; min-width: 0; flex: 1; }
    .upload-info i { color: var(--nexmemo-text-muted); font-size: 0.9rem; }
    .upload-name { font-size: 0.8rem; color: var(--nexmemo-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .upload-progress-wrap { display: flex; align-items: center; gap: 0.5rem; flex-shrink: 0; }
    .upload-progress-bar {
      width: 100px; height: 6px; background: var(--nexmemo-border);
      border-radius: var(--radius-full); overflow: hidden;
    }
    .upload-progress-fill {
      height: 100%; background: linear-gradient(90deg, var(--nexmemo-primary), var(--nexmemo-secondary));
      border-radius: var(--radius-full); transition: width 0.2s ease;
    }
    .upload-percent { font-size: 0.75rem; color: var(--nexmemo-text-secondary); min-width: 2.5rem; text-align: right; }
    .text-success { color: var(--nexmemo-success); }
    .text-danger { color: var(--nexmemo-danger); }

    .loading-wrap {
      display: flex; align-items: center; justify-content: center;
      gap: 0.75rem; padding: 2rem; color: var(--nexmemo-text-secondary);
      font-size: 0.85rem;
    }

    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      justify-content: center; padding: 2.5rem 1rem;
      color: var(--nexmemo-text-muted); gap: 0.35rem;
    }
    .empty-state i { font-size: 2.5rem; color: var(--nexmemo-border); margin-bottom: 0.25rem; }
    .empty-state p { font-size: 0.9rem; color: var(--nexmemo-text-secondary); margin: 0; }
    .empty-hint { font-size: 0.75rem; }

    .documents-list { margin-top: 1rem; display: flex; flex-direction: column; gap: 0.5rem; }
    .doc-item {
      display: flex; align-items: center; gap: 0.85rem;
      padding: 0.75rem 1rem; background: var(--nexmemo-surface);
      border: 1px solid var(--nexmemo-border-light);
      border-radius: var(--radius-md);
      transition: all var(--transition-fast);
    }
    .doc-item:hover {
      border-color: var(--nexmemo-border); background: var(--nexmemo-surface-hover);
      box-shadow: var(--shadow-sm);
    }
    .doc-icon {
      width: 40px; height: 40px; display: flex; align-items: center;
      justify-content: center; border-radius: var(--radius-sm);
      background: var(--nexmemo-primary-light); flex-shrink: 0;
    }
    .doc-icon i { font-size: 1.1rem; color: var(--nexmemo-primary); }
    .doc-info { flex: 1; min-width: 0; }
    .doc-name {
      display: block; font-size: 0.85rem; font-weight: 500;
      color: var(--nexmemo-text); white-space: nowrap;
      overflow: hidden; text-overflow: ellipsis;
    }
    .doc-meta { display: flex; align-items: center; gap: 0.35rem; margin-top: 0.15rem; }
    .doc-size { font-size: 0.72rem; color: var(--nexmemo-text-muted); }
    .doc-sep { font-size: 0.72rem; color: var(--nexmemo-border); }
    .doc-date { font-size: 0.72rem; color: var(--nexmemo-text-muted); }
    .doc-type-badge { flex-shrink: 0; }
    .doc-type-badge .badge {
      font-size: 0.65rem; font-weight: 600; text-transform: uppercase;
      padding: 0.2rem 0.5rem; border-radius: var(--radius-xs);
      background: var(--nexmemo-bg); color: var(--nexmemo-text-secondary);
    }
    .btn-delete {
      width: 32px; height: 32px; display: flex; align-items: center;
      justify-content: center; background: none; border: none;
      border-radius: var(--radius-sm); cursor: pointer;
      color: var(--nexmemo-text-muted); font-size: 0.85rem;
      transition: all var(--transition-fast); flex-shrink: 0;
    }
    .btn-delete:hover {
      background: var(--nexmemo-danger-light); color: var(--nexmemo-danger);
    }

    .spinner {
      display: inline-block; width: 1.25rem; height: 1.25rem;
      border: 2px solid var(--nexmemo-border);
      border-top-color: var(--nexmemo-primary);
      border-radius: 50%; animation: spin 0.6s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }
    @keyframes bounce { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-6px); } }
    `
  ]
})
export class MemoDocumentUploadComponent implements OnInit, OnChanges {
  @Input() memoId!: number;

  private http = inject(HttpClient);
  private toastr = inject(ToastrService);

  documents: any[] = [];
  loading = false;
  dragOver = false;
  validationError = '';
  uploadingFiles: UploadingFile[] = [];

  allowedExtensions = ['pdf', 'png', 'jpg', 'jpeg', 'doc', 'docx', 'xls', 'xlsx'];
  maxFileSize = 10 * 1024 * 1024;
  allowedAccept = '.pdf,.png,.jpg,.jpeg,.doc,.docx,.xls,.xlsx';

  ngOnInit() {
    if (this.memoId) this.loadDocuments();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['memoId'] && changes['memoId'].currentValue && !changes['memoId'].isFirstChange()) {
      this.loadDocuments();
    }
  }

  loadDocuments() {
    if (!this.memoId) return;
    this.loading = true;
    this.documents = [];
    this.http.get<any>(`${ApiConfig.URL}/api/v1/memo-documents/memo/${this.memoId}`).subscribe({
      next: (data) => { this.documents = data || []; this.loading = false; },
      error: () => { this.loading = false; this.toastr.error('Failed to load documents'); }
    });
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = true;
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = false;
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = false;
    const files = event.dataTransfer?.files;
    if (files?.length) this.handleFiles(Array.from(files));
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.handleFiles(Array.from(input.files));
    input.value = '';
  }

  handleFiles(files: File[]) {
    this.validationError = '';
    for (const file of files) {
      const ext = file.name.split('.').pop()?.toLowerCase() || '';
      if (!this.allowedExtensions.includes(ext)) {
        this.validationError = `"${file.name}" has an unsupported file type. Allowed: PDF, PNG, JPG, DOC, DOCX, XLS, XLSX.`;
        continue;
      }
      if (file.size > this.maxFileSize) {
        this.validationError = `"${file.name}" exceeds the 10MB size limit.`;
        continue;
      }
      this.uploadFile(file);
    }
  }

  uploadFile(file: File) {
    const entry: UploadingFile = { name: file.name, progress: 0, status: 'uploading' };
    this.uploadingFiles.push(entry);

    const formData = new FormData();
    formData.append('file', file);

    this.http.post(`${ApiConfig.URL}/api/v1/memo-documents/memo/${this.memoId}/upload`, formData, {
      reportProgress: true,
      observe: 'events'
    }).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          entry.progress = Math.round((event.loaded / event.total) * 100);
        } else if (event.type === HttpEventType.Response) {
          entry.status = 'success';
          this.toastr.success(`"${file.name}" uploaded successfully`);
          setTimeout(() => {
            this.uploadingFiles = this.uploadingFiles.filter(f => f !== entry);
          }, 2000);
          this.loadDocuments();
        }
      },
      error: () => {
        entry.status = 'error';
        this.toastr.error(`Failed to upload "${file.name}"`);
      }
    });
  }

  confirmDelete(doc: any) {
    const name = doc.fileName || doc.originalFileName || doc.name || 'this document';
    if (confirm(`Are you sure you want to delete "${name}"?`)) {
      this.deleteDocument(doc);
    }
  }

  deleteDocument(doc: any) {
    this.http.delete(`${ApiConfig.URL}/api/v1/memo-documents/${doc.id}`).subscribe({
      next: () => {
        this.toastr.success('Document deleted');
        this.loadDocuments();
      },
      error: () => this.toastr.error('Failed to delete document')
    });
  }

  getFileIcon(fileName: string): string {
    const ext = fileName?.split('.').pop()?.toLowerCase() || '';
    switch (ext) {
      case 'pdf': return 'fas fa-file-pdf';
      case 'png': case 'jpg': case 'jpeg': return 'fas fa-file-image';
      case 'doc': case 'docx': return 'fas fa-file-word';
      case 'xls': case 'xlsx': return 'fas fa-file-excel';
      default: return 'fas fa-file';
    }
  }

  getFileExtension(fileName: string): string {
    return fileName?.split('.').pop()?.toUpperCase() || '';
  }

  formatFileSize(bytes: number): string {
    if (!bytes) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }
}
