import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

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
}
