import { Component, OnInit, inject } from '@angular/core';
import { NgIf, NgFor, NgClass, SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ToastrService } from 'ngx-toastr';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { NgbPaginationModule } from '@ng-bootstrap/ng-bootstrap';
import {
  DocumentService,
  DocumentDto,
  DocumentFolderDto,
  DocumentVersionDto,
  DocumentPermissionDto,
  DocumentShareLinkDto,
  DocumentQuotaDto,
  PreviewMetadataDto,
  DuplicateCheckResult
} from '../../../shared-services/api-service/document.service';

/**
 * Secure Document Vault (Enterprise Phase 4).
 *
 * <p>Replaces the former "Document List" placeholder at the same route so the seeded menu entry keeps
 * working. Everything the UI shows comes from the server, which re-authorizes every call; previews are
 * pulled as blobs (never by navigating to an authenticated URL), so no token leaks into history and
 * the server's {@code nosniff}/CSP headers are irrelevant to the app's own rendering.</p>
 */
@Component({
  selector: 'app-document-list',
  standalone: true,
  imports: [NgIf, NgFor, NgClass, SlicePipe, FormsModule, NgbPaginationModule],
  templateUrl: './document-list.component.html',
  styleUrl: './document-list.component.scss'
})
export class DocumentListComponent implements OnInit {
  private documentService = inject(DocumentService);
  private toastr = inject(ToastrService);
  private sanitizer = inject(DomSanitizer);

  folders: DocumentFolderDto[] = [];
  selectedFolderId: number | null = null;

  documents: DocumentDto[] = [];
  page = 1;
  size = 20;
  totalElements = 0;
  totalPages = 1;
  loading = false;

  searchQuery = '';
  classificationFilter = '';
  statusFilter = '';
  tagFilter = '';

  classifications = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'];
  statuses = ['ACTIVE', 'ARCHIVED', 'LEGAL_HOLD'];

  quota: DocumentQuotaDto | null = null;

  private searchSubject = new Subject<string>();
  private searchSub!: Subscription;

  // ── Upload modal ──
  showUpload = false;
  uploadFile: File | null = null;
  uploadTitle = '';
  uploadDescription = '';
  uploadClassification = 'INTERNAL';
  uploadTags = '';
  uploadFolderId: number | null = null;
  reuseExisting = false;
  uploading = false;

  // ── Folder management ──
  showFolderInput = false;
  folderName = '';
  editingFolderId: number | null = null;
  editingFolderName = '';

  // ── Preview modal ──
  showPreview = false;
  previewMeta: PreviewMetadataDto | null = null;
  previewUrl: SafeResourceUrl | null = null;
  previewText = '';
  previewLoading = false;
  previewDoc: DocumentDto | null = null;

  // ── Versions modal ──
  showVersions = false;
  versionsDoc: DocumentDto | null = null;
  versions: DocumentVersionDto[] = [];
  newVersionFile: File | null = null;

  // ── Permissions modal ──
  showPermissions = false;
  permissionsDoc: DocumentDto | null = null;
  permissions: DocumentPermissionDto[] = [];
  newGrant = { subjectType: 'USER', subjectId: 0, canRead: true, canWrite: false, canDelete: false, canShare: false };

  // ── Share modal ──
  showShare = false;
  shareDoc: DocumentDto | null = null;
  shareLinks: DocumentShareLinkDto[] = [];
  newLink = { expiresInHours: 24, maxDownloads: 0, password: '' };

  // ── Audit modal ──
  showAudit = false;
  auditDoc: DocumentDto | null = null;
  auditEvents: any[] = [];

  ngOnInit() {
    this.searchSub = this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(() => { this.page = 1; this.loadDocuments(); });

    this.loadFolders();
    this.loadDocuments();
    this.loadQuota();
  }

  ngOnDestroy() {
    this.searchSub?.unsubscribe();
  }

  // ── Quota ─────────────────────────────────────────────────────────────────

  loadQuota() {
    this.documentService.quota().subscribe({
      next: (res: any) => this.quota = (res?.data ?? res) as DocumentQuotaDto,
      error: () => { /* quota is informational; ignore */ }
    });
  }

  // ── Folders ───────────────────────────────────────────────────────────────

  loadFolders() {
    this.documentService.listFolders().subscribe({
      next: (res: any) => this.folders = (res?.data ?? res) || [],
      error: () => this.folders = []
    });
  }

  selectFolder(id: number | null) {
    this.selectedFolderId = id;
    this.page = 1;
    this.loadDocuments();
  }

  submitFolder() {
    const name = this.folderName.trim();
    if (!name) return;
    this.documentService.createFolder(name, this.selectedFolderId).subscribe({
      next: () => {
        this.toastr.success('Folder created');
        this.folderName = '';
        this.showFolderInput = false;
        this.loadFolders();
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not create folder')
    });
  }

  startRenameFolder(f: DocumentFolderDto) {
    this.editingFolderId = f.id;
    this.editingFolderName = f.name;
  }

  submitRenameFolder() {
    if (this.editingFolderId == null) return;
    const name = this.editingFolderName.trim();
    if (!name) return;
    this.documentService.renameFolder(this.editingFolderId, name).subscribe({
      next: () => {
        this.toastr.success('Folder renamed');
        this.editingFolderId = null;
        this.loadFolders();
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not rename folder')
    });
  }

  deleteFolder(f: DocumentFolderDto) {
    if (!confirm(`Delete folder "${f.name}"? It must be empty.`)) return;
    this.documentService.deleteFolder(f.id).subscribe({
      next: () => {
        this.toastr.success('Folder deleted');
        if (this.selectedFolderId === f.id) this.selectFolder(null);
        this.loadFolders();
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Folder must be empty before deletion')
    });
  }

  // ── Documents ─────────────────────────────────────────────────────────────

  loadDocuments() {
    this.loading = true;
    this.documentService.list(this.page - 1, this.size, {
      search: this.searchQuery.trim() || undefined,
      folderId: this.selectedFolderId,
      classification: this.classificationFilter || undefined,
      status: this.statusFilter || undefined,
      tag: this.tagFilter.trim() || undefined
    }).subscribe({
      next: (res: any) => {
        const data = res?.data ?? res;
        this.documents = data.content || [];
        this.totalElements = data.totalElements || 0;
        this.totalPages = data.totalPages || 1;
        this.loading = false;
      },
      error: () => { this.loading = false; this.documents = []; }
    });
  }

  onSearch() { this.searchSubject.next(this.searchQuery); }
  onFilterChange() { this.page = 1; this.loadDocuments(); }
  onPageChange(page: number) { this.page = page; this.loadDocuments(); }
  refresh() { this.loadDocuments(); this.loadQuota(); }

  // ── Upload ────────────────────────────────────────────────────────────────

  openUpload() {
    this.showUpload = true;
    this.uploadFile = null;
    this.uploadTitle = '';
    this.uploadDescription = '';
    this.uploadClassification = 'INTERNAL';
    this.uploadTags = '';
    this.uploadFolderId = this.selectedFolderId;
    this.reuseExisting = false;
  }

  onUploadFileSelected(e: Event) {
    const input = e.target as HTMLInputElement;
    if (input.files?.length) {
      this.uploadFile = input.files[0];
      if (!this.uploadTitle) this.uploadTitle = this.uploadFile.name;
    }
  }

  /** Pre-flight de-duplication: warns the user before storing a second copy of the same bytes. */
  checkDuplicate() {
    if (!this.uploadFile) return;
    this.documentService.checkDuplicate(this.uploadFile).subscribe({
      next: (res: any) => {
        const result = (res?.data ?? res) as DuplicateCheckResult;
        if (result.matches?.length) {
          this.reuseExisting = true;
          this.toastr.info(`Identical document already stored: "${result.matches[0].title}". The existing copy will be reused.`);
        } else {
          this.toastr.success('No duplicate found — a new copy will be stored.');
        }
      },
      error: () => { /* non-fatal */ }
    });
  }

  submitUpload() {
    if (!this.uploadFile) { this.toastr.warning('Choose a file'); return; }
    this.uploading = true;
    const tags = this.uploadTags.split(',').map(t => t.trim()).filter(t => t.length > 0);
    this.documentService.upload(this.uploadFile, {
      title: this.uploadTitle || this.uploadFile.name,
      description: this.uploadDescription || undefined,
      classification: this.uploadClassification,
      folderId: this.uploadFolderId,
      reuseExisting: this.reuseExisting,
      tags
    }).subscribe({
      next: () => {
        this.toastr.success('Document stored');
        this.uploading = false;
        this.showUpload = false;
        this.loadDocuments();
        this.loadQuota();
      },
      error: (err) => {
        this.uploading = false;
        this.toastr.error(err?.error?.message || 'Upload failed');
      }
    });
  }

  // ── Preview ───────────────────────────────────────────────────────────────

  openPreview(doc: DocumentDto) {
    this.previewDoc = doc;
    this.showPreview = true;
    this.previewLoading = true;
    this.previewMeta = null;
    this.previewUrl = null;
    this.previewText = '';

    this.documentService.previewMetadata(doc.id).subscribe({
      next: (res: any) => {
        this.previewMeta = (res?.data ?? res) as PreviewMetadataDto;
        if (this.previewMeta?.previewable) {
          this.loadPreviewBytes(doc);
        } else {
          this.previewLoading = false;
        }
      },
      error: () => { this.previewLoading = false; }
    });
  }

  private loadPreviewBytes(doc: DocumentDto) {
    this.documentService.previewBlob(doc.id).subscribe({
      next: (blob) => {
        const kind = this.previewMeta?.previewKind;
        if (kind === 'TEXT') {
          blob.text().then(text => { this.previewText = text; this.previewLoading = false; });
        } else {
          const url = URL.createObjectURL(blob);
          this.previewUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url);
          this.previewLoading = false;
        }
      },
      error: (err) => {
        this.previewLoading = false;
        this.toastr.error(err?.error?.message || 'Preview unavailable');
      }
    });
  }

  closePreview() {
    if (this.previewUrl) {
      const anyUrl: any = this.previewUrl;
      // blob: URLs from bypassSecurityTrustResourceUrl expose the original string via changingThisBreaksApplicationSecurity.
      const raw = anyUrl?.changingThisBreaksApplicationSecurity;
      if (typeof raw === 'string') URL.revokeObjectURL(raw);
    }
    this.showPreview = false;
    this.previewUrl = null;
    this.previewText = '';
    this.previewMeta = null;
    this.previewDoc = null;
  }

  // ── Download ──────────────────────────────────────────────────────────────

  download(doc: DocumentDto) {
    this.documentService.download(doc.id).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = doc.originalFilename || doc.title || 'document';
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => this.toastr.error('Download failed')
    });
  }

  // ── Versions ──────────────────────────────────────────────────────────────

  openVersions(doc: DocumentDto) {
    this.versionsDoc = doc;
    this.versions = [];
    this.newVersionFile = null;
    this.showVersions = true;
    this.loadVersions(doc.id);
  }

  private loadVersions(id: number) {
    this.documentService.versions(id).subscribe({
      next: (res: any) => this.versions = (res?.data ?? res) || [],
      error: () => this.versions = []
    });
  }

  onNewVersionSelected(e: Event) {
    const input = e.target as HTMLInputElement;
    if (input.files?.length) this.newVersionFile = input.files[0];
  }

  submitNewVersion() {
    if (!this.versionsDoc || !this.newVersionFile) return;
    this.documentService.addVersion(this.versionsDoc.id, this.newVersionFile).subscribe({
      next: () => {
        this.toastr.success('New version stored');
        this.newVersionFile = null;
        this.loadVersions(this.versionsDoc!.id);
        this.loadDocuments();
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not store version')
    });
  }

  restoreVersion(v: DocumentVersionDto) {
    if (!this.versionsDoc) return;
    if (!confirm(`Restore version ${v.versionNo}? This appends a new revision that reuses the old bytes.`)) return;
    this.documentService.restoreVersion(this.versionsDoc.id, v.versionNo).subscribe({
      next: () => {
        this.toastr.success(`Restored version ${v.versionNo}`);
        this.loadVersions(this.versionsDoc!.id);
        this.loadDocuments();
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not restore version')
    });
  }

  // ── Move ──────────────────────────────────────────────────────────────────

  moveToFolder(doc: DocumentDto, folderId: string) {
    const id = folderId === '' ? null : Number(folderId);
    this.documentService.move(doc.id, id).subscribe({
      next: () => { this.toastr.success('Document moved'); this.loadDocuments(); this.loadFolders(); },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not move document')
    });
  }

  // ── Status / retention ────────────────────────────────────────────────────

  archive(doc: DocumentDto) {
    this.documentService.setStatus(doc.id, 'ARCHIVED').subscribe({
      next: () => { this.toastr.success('Document archived'); this.loadDocuments(); },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not archive')
    });
  }

  reactivate(doc: DocumentDto) {
    this.documentService.setStatus(doc.id, 'ACTIVE').subscribe({
      next: () => { this.toastr.success('Document reactivated'); this.loadDocuments(); },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not reactivate')
    });
  }

  delete(doc: DocumentDto) {
    if (!confirm(`Delete "${doc.title}"? This revokes every share link on it.`)) return;
    this.documentService.delete(doc.id, false).subscribe({
      next: () => { this.toastr.success('Document deleted'); this.loadDocuments(); this.loadQuota(); },
      error: (err) => {
        const msg = err?.error?.message || 'Could not delete';
        this.toastr.error(msg);
      }
    });
  }

  // ── Permissions ───────────────────────────────────────────────────────────

  openPermissions(doc: DocumentDto) {
    this.permissionsDoc = doc;
    this.showPermissions = true;
    this.newGrant = { subjectType: 'USER', subjectId: 0, canRead: true, canWrite: false, canDelete: false, canShare: false };
    this.loadPermissions(doc.id);
  }

  private loadPermissions(id: number) {
    this.documentService.permissions(id).subscribe({
      next: (res: any) => this.permissions = (res?.data ?? res) || [],
      error: () => this.permissions = []
    });
  }

  submitGrant() {
    if (!this.permissionsDoc || !this.newGrant.subjectId) {
      this.toastr.warning('A target id is required');
      return;
    }
    this.documentService.grant(this.permissionsDoc.id, this.newGrant).subscribe({
      next: () => {
        this.toastr.success('Access granted');
        this.loadPermissions(this.permissionsDoc!.id);
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not grant access')
    });
  }

  revokePermission(p: DocumentPermissionDto) {
    if (!this.permissionsDoc) return;
    this.documentService.revokePermission(this.permissionsDoc.id, p.id).subscribe({
      next: () => { this.toastr.success('Access revoked'); this.loadPermissions(this.permissionsDoc!.id); },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not revoke access')
    });
  }

  // ── Share links ───────────────────────────────────────────────────────────

  openShare(doc: DocumentDto) {
    this.shareDoc = doc;
    this.showShare = true;
    this.newLink = { expiresInHours: 24, maxDownloads: 0, password: '' };
    this.loadShareLinks(doc.id);
  }

  private loadShareLinks(id: number) {
    this.documentService.shareLinks(id).subscribe({
      next: (res: any) => this.shareLinks = (res?.data ?? res) || [],
      error: () => this.shareLinks = []
    });
  }

  createLink() {
    if (!this.shareDoc) return;
    const request: any = { expiresInHours: this.newLink.expiresInHours };
    if (this.newLink.maxDownloads > 0) request.maxDownloads = this.newLink.maxDownloads;
    if (this.newLink.password) request.password = this.newLink.password;
    this.documentService.createShareLink(this.shareDoc.id, request).subscribe({
      next: (res: any) => {
        const link = res?.data ?? res;
        this.toastr.success('Share link created');
        this.shareLinks = [link, ...this.shareLinks];
        this.newLink = { expiresInHours: 24, maxDownloads: 0, password: '' };
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not create share link')
    });
  }

  copyLink(link: DocumentShareLinkDto) {
    navigator.clipboard?.writeText(link.shareUrl).then(
      () => this.toastr.success('Link copied'),
      () => this.toastr.error('Could not copy link')
    );
  }

  revokeLink(link: DocumentShareLinkDto) {
    if (!this.shareDoc) return;
    if (!confirm('Revoke this share link? Anyone holding it will lose access immediately.')) return;
    this.documentService.revokeShareLink(this.shareDoc.id, link.id).subscribe({
      next: () => { link.revoked = true; this.toastr.success('Share link revoked'); },
      error: (err) => this.toastr.error(err?.error?.message || 'Could not revoke link')
    });
  }

  // ── Audit ─────────────────────────────────────────────────────────────────

  openAudit(doc: DocumentDto) {
    this.auditDoc = doc;
    this.showAudit = true;
    this.auditEvents = [];
    this.documentService.audit(doc.id).subscribe({
      next: (res: any) => this.auditEvents = (res?.data ?? res) || [],
      error: () => this.auditEvents = []
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  get activeFolderName(): string {
    if (this.selectedFolderId == null) return 'All Documents';
    return this.folders.find(f => f.id === this.selectedFolderId)?.name || 'Folder';
  }

  formatBytes(bytes?: number | null): string {
    if (!bytes || bytes <= 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  fileIcon(name?: string): string {
    const ext = (name || '').split('.').pop()?.toLowerCase() || '';
    const icons: Record<string, string> = {
      pdf: 'fa-file-pdf', doc: 'fa-file-word', docx: 'fa-file-word',
      xls: 'fa-file-excel', xlsx: 'fa-file-excel',
      jpg: 'fa-file-image', jpeg: 'fa-file-image', png: 'fa-file-image', gif: 'fa-file-image', webp: 'fa-file-image',
      zip: 'fa-file-archive', gz: 'fa-file-archive', tar: 'fa-file-archive', rar: 'fa-file-archive', '7z': 'fa-file-archive',
      mp4: 'fa-file-video', mp3: 'fa-file-audio', txt: 'fa-file-alt', csv: 'fa-file-csv'
    };
    return icons[ext] || 'fa-file';
  }

  classificationBadge(c: string): string {
    switch (c) {
      case 'PUBLIC': return 'badge-public';
      case 'INTERNAL': return 'badge-internal';
      case 'CONFIDENTIAL': return 'badge-confidential';
      case 'RESTRICTED': return 'badge-restricted';
      default: return 'badge-internal';
    }
  }

  statusBadge(s: string): string {
    switch (s) {
      case 'ACTIVE': return 'badge-success';
      case 'ARCHIVED': return 'badge-secondary';
      case 'LEGAL_HOLD': return 'badge-danger';
      default: return 'badge-secondary';
    }
  }

  trackById(_idx: number, item: { id: number }) { return item.id; }
}
