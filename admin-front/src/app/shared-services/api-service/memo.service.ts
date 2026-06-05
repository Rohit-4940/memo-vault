import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
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
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/submit`, null);
  }

  private toHttpParams(obj: Record<string, any>): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(obj)) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return params;
  }

  approveMemo(id: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/approve`, null, {
      params: this.toHttpParams({ remarks })
    });
  }

  rejectMemo(id: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/reject`, null, {
      params: this.toHttpParams({ remarks })
    });
  }

  returnMemo(id: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/return`, null, {
      params: this.toHttpParams({ remarks })
    });
  }

  forwardMemo(id: number, toUserId: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/forward`, null, {
      params: this.toHttpParams({ toUserId, remarks })
    });
  }

  backwardMemo(id: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/backward`, null, {
      params: this.toHttpParams({ remarks })
    });
  }

  cancelMemo(id: number): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/cancel`, null);
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
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memos/${id}/acknowledge`, null);
  }

  getStageInstances(id: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/${id}/stages`);
  }

  approveStage(id: number, stageId: number, comment?: string): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/memos/${id}/approve-stage`, null, {
      params: this.toHttpParams({ stageId, comment })
    });
  }

  rejectStage(id: number, stageId: number, comment?: string): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/memos/${id}/reject-stage`, null, {
      params: this.toHttpParams({ stageId, comment })
    });
  }

  returnToStage(id: number, stageId: number, comment?: string): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/memos/${id}/return-stage`, null, {
      params: this.toHttpParams({ stageId, comment })
    });
  }

  delegateStage(id: number, stageId: number, toUserId: number): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/memos/${id}/delegate-stage`, null, {
      params: this.toHttpParams({ stageId, toUserId })
    });
  }
}
