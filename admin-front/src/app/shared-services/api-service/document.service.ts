import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

// ── Response models (mirror the backend DTOs) ───────────────────────────────

export interface DocumentDto {
  id: number;
  title: string;
  description?: string;
  originalFilename: string;
  contentType?: string;
  sizeBytes: number;
  sha256?: string;
  classification: string;
  status: string;
  sourceType?: string;
  sourceRefId?: number;
  folderId?: number | null;
  retentionUntil?: string | null;
  holdReason?: string | null;
  currentVersion?: number;
  tags: string[];
  createdDate?: string;
  modifiedDate?: string;
}

export interface DocumentFolderDto {
  id: number;
  name: string;
  parentId?: number | null;
  path: string;
  documentCount?: number | null;
}

export interface DocumentVersionDto {
  id: number;
  documentId: number;
  versionNo: number;
  originalFilename: string;
  contentType?: string;
  sizeBytes: number;
  sha256?: string;
  notes?: string;
  uploadedById?: number;
  createdDate?: string;
}

export interface DocumentPermissionDto {
  id: number;
  documentId: number;
  subjectType: string;
  subjectId: number;
  canRead: boolean;
  canWrite: boolean;
  canDelete: boolean;
  canShare: boolean;
}

export interface DocumentShareLinkDto {
  id: number;
  documentId: number;
  token: string;
  shareUrl: string;
  expiresAt?: string | null;
  maxDownloads?: number | null;
  downloadCount: number;
  passwordProtected: boolean;
  revoked: boolean;
  createdDate?: string;
}

export interface DocumentQuotaDto {
  usedBytes: number;
  limitBytes?: number | null;
  remainingBytes?: number | null;
  documentCount: number;
  unlimited: boolean;
  percentUsed?: number | null;
}

export interface PreviewMetadataDto {
  previewKind: string;   // IMAGE | PDF | TEXT | NONE
  previewable: boolean;
  contentType?: string;
  sizeBytes: number;
  filename?: string;
}

export interface DuplicateCheckResult {
  sha256: string;
  sizeBytes: number;
  matches: DocumentDto[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface DocumentFilters {
  search?: string;
  folderId?: number | null;
  classification?: string;
  status?: string;
  tag?: string;
}

/**
 * Document vault API client (Enterprise Phase 4).
 *
 * <p>The backend is authoritative: the server re-checks authorization on every call and rejects any
 * attempt to read a document the caller cannot see. This client therefore never assumes access based
 * on a cached list; it always surfaces the server's decision.</p>
 */
@Injectable({ providedIn: 'root' })
export class DocumentService {
  constructor(private http: HttpClient) {}

  private base(): string {
    return `${ApiConfig.URL}/api/v1/documents`;
  }

  // ── Upload / list / read ──────────────────────────────────────────────────

  upload(file: File, meta: {
    title?: string; description?: string; classification?: string;
    branchId?: number; departmentId?: number; folderId?: number | null;
    reuseExisting?: boolean; tags?: string[];
  } = {}): Observable<any> {
    const fd = new FormData();
    fd.append('file', file);
    if (meta.title) fd.append('title', meta.title);
    if (meta.description) fd.append('description', meta.description);
    if (meta.classification) fd.append('classification', meta.classification);
    if (meta.branchId != null) fd.append('branchId', String(meta.branchId));
    if (meta.departmentId != null) fd.append('departmentId', String(meta.departmentId));
    if (meta.folderId != null) fd.append('folderId', String(meta.folderId));
    if (meta.reuseExisting) fd.append('reuseExisting', 'true');
    (meta.tags || []).forEach(t => fd.append('tags', t));
    return this.http.post(this.base(), fd);
  }

  list(page: number = 0, size: number = 20, filters: DocumentFilters = {}): Observable<any> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.search) params = params.set('search', filters.search);
    if (filters.folderId != null) params = params.set('folderId', String(filters.folderId));
    if (filters.classification) params = params.set('classification', filters.classification);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.tag) params = params.set('tag', filters.tag);
    return this.http.get(this.base(), { params });
  }

  get(id: number): Observable<any> {
    return this.http.get(`${this.base()}/${id}`);
  }

  updateMetadata(id: number, meta: {
    title?: string; description?: string; classification?: string;
    retentionUntil?: string | null; tags?: string[];
  }): Observable<any> {
    let params = new HttpParams();
    if (meta.title != null) params = params.set('title', meta.title);
    if (meta.description != null) params = params.set('description', meta.description);
    if (meta.classification != null) params = params.set('classification', meta.classification);
    if (meta.retentionUntil != null) params = params.set('retentionUntil', meta.retentionUntil);
    (meta.tags || []).forEach(t => params = params.append('tags', t));
    return this.http.patch(`${this.base()}/${id}`, {}, { params });
  }

  delete(id: number, force: boolean = false): Observable<any> {
    const params = new HttpParams().set('force', String(force));
    return this.http.delete(`${this.base()}/${id}`, { params });
  }

  // ── Catalogue organisation & lifecycle ────────────────────────────────────

  move(id: number, folderId: number | null): Observable<any> {
    return this.http.patch(`${this.base()}/${id}/folder`, { folderId });
  }

  setStatus(id: number, status: string, reason?: string): Observable<any> {
    return this.http.patch(`${this.base()}/${id}/status`, { status, reason });
  }

  // ── Folder tree ───────────────────────────────────────────────────────────

  listFolders(): Observable<any> {
    return this.http.get(`${ApiConfig.URL}/api/v1/document-folders`);
  }

  createFolder(name: string, parentId?: number | null): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/api/v1/document-folders`, { name, parentId });
  }

  renameFolder(id: number, name: string): Observable<any> {
    return this.http.patch(`${ApiConfig.URL}/api/v1/document-folders/${id}`, { name });
  }

  moveFolder(id: number, parentId: number | null): Observable<any> {
    return this.http.patch(`${ApiConfig.URL}/api/v1/document-folders/${id}/parent`, { parentId });
  }

  deleteFolder(id: number): Observable<any> {
    return this.http.delete(`${ApiConfig.URL}/api/v1/document-folders/${id}`);
  }

  // ── Versions ──────────────────────────────────────────────────────────────

  versions(id: number): Observable<any> {
    return this.http.get(`${this.base()}/${id}/versions`);
  }

  addVersion(id: number, file: File, notes?: string): Observable<any> {
    const fd = new FormData();
    fd.append('file', file);
    if (notes) fd.append('notes', notes);
    return this.http.post(`${this.base()}/${id}/versions`, fd);
  }

  restoreVersion(id: number, versionNo: number): Observable<any> {
    return this.http.post(`${this.base()}/${id}/versions/${versionNo}/restore`, {});
  }

  // ── Preview / download ────────────────────────────────────────────────────

  previewMetadata(id: number, version?: number): Observable<any> {
    let params = new HttpParams();
    if (version != null) params = params.set('version', String(version));
    return this.http.get(`${this.base()}/${id}/preview-metadata`, { params });
  }

  /** Fetches preview bytes as a blob so the browser never navigates to a server URL with a token. */
  previewBlob(id: number, version?: number): Observable<Blob> {
    let params = new HttpParams();
    if (version != null) params = params.set('version', String(version));
    return this.http.get(`${this.base()}/${id}/preview`, { params, responseType: 'blob' });
  }

  download(id: number, version?: number): Observable<Blob> {
    let params = new HttpParams();
    if (version != null) params = params.set('version', String(version));
    return this.http.get(`${this.base()}/${id}/download`, { params, responseType: 'blob' });
  }

  // ── De-duplication & quota ────────────────────────────────────────────────

  duplicates(sha256: string): Observable<any> {
    return this.http.get(`${this.base()}/duplicates`, { params: new HttpParams().set('sha256', sha256) });
  }

  checkDuplicate(file: File): Observable<any> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post(`${this.base()}/duplicates/check`, fd);
  }

  quota(): Observable<any> {
    return this.http.get(`${this.base()}/quota`);
  }

  // ── Permissions ───────────────────────────────────────────────────────────

  permissions(id: number): Observable<any> {
    return this.http.get(`${this.base()}/${id}/permissions`);
  }

  grant(id: number, request: {
    subjectType: string; subjectId: number;
    canRead?: boolean; canWrite?: boolean; canDelete?: boolean; canShare?: boolean;
  }): Observable<any> {
    return this.http.post(`${this.base()}/${id}/permissions`, request);
  }

  revokePermission(id: number, permissionId: number): Observable<any> {
    return this.http.delete(`${this.base()}/${id}/permissions/${permissionId}`);
  }

  // ── Share links ───────────────────────────────────────────────────────────

  shareLinks(id: number): Observable<any> {
    return this.http.get(`${this.base()}/${id}/share-links`);
  }

  createShareLink(id: number, request: {
    expiresInHours?: number; maxDownloads?: number; password?: string;
  }): Observable<any> {
    return this.http.post(`${this.base()}/${id}/share-links`, request);
  }

  revokeShareLink(id: number, linkId: number): Observable<any> {
    return this.http.delete(`${this.base()}/${id}/share-links/${linkId}`);
  }

  // ── Audit ─────────────────────────────────────────────────────────────────

  audit(id: number): Observable<any> {
    return this.http.get(`${this.base()}/${id}/audit`);
  }

  // ── Public share landing (no auth) ────────────────────────────────────────

  shareLanding(token: string): Observable<any> {
    return this.http.get(`${this.base()}/shared/${token}`);
  }
}
