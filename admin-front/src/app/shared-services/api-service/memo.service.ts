import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class MemoService {
  constructor(private http: HttpClient) {}

  getAllMemos(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos`);
  }

  getMemoById(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/memos/${id}`);
  }

  getMyMemos(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/my`);
  }

  getPendingApprovals(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/pending-approvals`);
  }

  getByStatus(status: string): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/status/${status}`);
  }

  getByBranch(branchId: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/branch/${branchId}`);
  }

  searchMemos(query: string): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/search`, { params: { q: query } });
  }

  createMemo(data: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/memos`, data);
  }

  submitMemo(id: number): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/submit`, {});
  }

  approveMemo(id: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/approve`, { remarks });
  }

  rejectMemo(id: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/reject`, { remarks });
  }

  returnMemo(id: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/return`, { remarks });
  }

  forwardMemo(id: number, toUserId: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/forward`, { toUserId, remarks });
  }

  backwardMemo(id: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/backward`, { remarks });
  }

  cancelMemo(id: number): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/cancel`, {});
  }

  getApprovalHistory(id: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/${id}/approval-history`);
  }

  getMemoRouting(id: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/${id}/routing`);
  }
}
