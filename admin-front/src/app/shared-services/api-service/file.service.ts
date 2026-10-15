import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

export interface CompressionResponse {
  id: number;
  originalName: string;
  originalSize: number;
  originalType: string;
  compressedName: string;
  compressedSize: number;
  compressionType: string;
  preset: string;
  status: string;
  progressPercent: number;
  attempts: number;
  batchId: string;
  verified: boolean;
  archiveEntryCount: number;
  archiveUncompressedBytes: number;
  durationMs: number;
  ratioPercent: number;
  savings: number;
  savingsPercent: number;
  errorMessage: string;
  createdDate: string;
  completedDate: string;
}

export interface CompressionHistoryResponse {
  items: CompressionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  totalOriginalBytes: number;
  totalCompressedBytes: number;
  totalSavings: number;
}

@Injectable({ providedIn: 'root' })
export class FileService {
  constructor(private http: HttpClient) {}

  uploadSignature(file: File): Observable<any> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post(`${ApiConfig.URL}/api/v1/files/signature`, fd);
  }

  uploadFile(file: File, dir: string = 'documents'): Observable<any> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('dir', dir);
    return this.http.post(`${ApiConfig.URL}/api/v1/files/upload`, fd);
  }

  getSignature(userId: number): string {
    return `${ApiConfig.URL}/api/v1/files/signature/${userId}`;
  }

  downloadFile(filePath: string): Observable<Blob> {
    return this.http.get(`${ApiConfig.URL}/api/v1/files/download`, {
      params: { path: filePath },
      responseType: 'blob'
    });
  }

  deleteFile(filePath: string): Observable<any> {
    return this.http.delete(`${ApiConfig.URL}/api/v1/files`, { params: { path: filePath } });
  }

  compress(file: File, type: string, preset: string = 'BALANCED'): Observable<any> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('type', type);
    fd.append('preset', preset);
    return this.http.post(`${ApiConfig.URL}/api/v1/files/compress`, fd);
  }

  compressBatch(files: File[], type: string, preset: string = 'BALANCED'): Observable<any> {
    const fd = new FormData();
    files.forEach(f => fd.append('files', f));
    fd.append('type', type);
    fd.append('preset', preset);
    return this.http.post(`${ApiConfig.URL}/api/v1/files/compress/batch`, fd);
  }

  getHistory(page: number = 0, size: number = 20, status?: string, search?: string): Observable<any> {
    let params: any = { page, size };
    if (status) params.status = status;
    if (search) params.search = search;
    return this.http.get(`${ApiConfig.URL}/api/v1/files/compress/history`, { params });
  }

  getJob(id: number): Observable<any> {
    return this.http.get(`${ApiConfig.URL}/api/v1/files/compress/${id}`);
  }

  cancelJob(id: number): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/api/v1/files/compress/${id}/cancel`, {});
  }

  retryJob(id: number): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/api/v1/files/compress/${id}/retry`, {});
  }

  verifyJob(id: number): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/api/v1/files/compress/${id}/verify`, {});
  }

  getDownloadUrl(id: number): string {
    return `${ApiConfig.URL}/api/v1/files/compress/${id}/download`;
  }

  downloadJob(id: number): Observable<HttpEvent<Blob>> {
    return this.http.get(`${ApiConfig.URL}/api/v1/files/compress/${id}/download`, {
      responseType: 'blob',
      reportProgress: true,
      observe: 'events'
    });
  }

  deleteJob(id: number): Observable<any> {
    return this.http.delete(`${ApiConfig.URL}/api/v1/files/compress/${id}`);
  }

  // ─── Document Transfer ─────────────────────────────────────────────────

  createTransfer(formData: FormData): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/api/v1/files/transfer`, formData);
  }

  sendTransfer(id: number): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/api/v1/files/transfer/${id}/send`, {});
  }

  getSentTransfers(page: number = 0, size: number = 20, status?: string, search?: string): Observable<any> {
    let params: any = { page, size };
    if (status) params.status = status;
    if (search) params.search = search;
    return this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/sent`, { params });
  }

  getReceivedTransfers(page: number = 0, size: number = 20, status?: string, search?: string): Observable<any> {
    let params: any = { page, size };
    if (status) params.status = status;
    if (search) params.search = search;
    return this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/received`, { params });
  }

  getTransferDetail(id: number): Observable<any> {
    return this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/${id}`);
  }

  receiveTransfer(id: number): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/api/v1/files/transfer/${id}/receive`, {});
  }

  deliverTransfer(id: number): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/api/v1/files/transfer/${id}/deliver`, {});
  }

  archiveTransfer(id: number): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/api/v1/files/transfer/${id}/archive`, {});
  }

  cancelTransfer(id: number): Observable<any> {
    return this.http.delete(`${ApiConfig.URL}/api/v1/files/transfer/${id}`);
  }

  downloadTransferFile(transferId: number, fileId: number): Observable<Blob> {
    return this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/${transferId}/files/${fileId}/download`, {
      responseType: 'blob'
    });
  }

  downloadTransferAll(transferId: number): Observable<Blob> {
    return this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/${transferId}/download-all`, {
      responseType: 'blob'
    });
  }

  getTransferStats(): Observable<any> {
    return this.http.get(`${ApiConfig.URL}/api/v1/files/transfer/stats`);
  }
}
