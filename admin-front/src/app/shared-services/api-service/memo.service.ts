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

  getMemosPaginated(page: number = 0, size: number = 20, sort: string = 'createdDate', dir: string = 'desc'): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/memos/paginated`, {
      params: { page, size, sort, dir }
    });
  }

  getMemoById(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/memos/${id}`);
  }

  getMyMemos(page: number = 0, size: number = 20): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/memos/my`, {
      params: { page, size }
    });
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
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/${id}/history`);
  }

  getMemoRouting(id: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/${id}/routing`);
  }

  getMemoCc(id: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/${id}/cc`);
  }

  getMyCcMemos(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/my-cc`);
  }

  acknowledgeMemo(id: number): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/acknowledge`, {});
  }
}
