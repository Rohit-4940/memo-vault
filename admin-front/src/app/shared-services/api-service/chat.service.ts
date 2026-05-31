import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class ChatService {
  constructor(private http: HttpClient) {}

  sendMessage(payload: {
    receiverId?: number; memoId?: number; branchId?: number;
    conversationType?: string; message: string
  }): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/chat/send`, payload);
  }

  getMemoMessages(memoId: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/memo/${memoId}`);
  }

  getBranchMessages(branchId: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/branch/${branchId}`);
  }

  getBranchMessagesPaginated(branchId: number, page: number = 0, size: number = 50): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/branch/${branchId}/paginated`, { params: { page, size } });
  }

  getConversation(otherId: number, page: number = 0, size: number = 50): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/conversation/${otherId}`, { params: { page, size } });
  }

  getUnreadCount(): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/unread-count`);
  }

  getAllBranchMessages(page: number = 0, size: number = 50): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/admin/all-branch-messages`, { params: { page, size } });
  }

  getActiveBranchIds(): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/admin/active-branches`);
  }

  getAllUsers(): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/chat/admin/users`);
  }

  markBranchRead(branchId: number): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/chat/branch/${branchId}/mark-read`, {});
  }

  markAsRead(id: number): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/chat/${id}/read`, {});
  }
}
