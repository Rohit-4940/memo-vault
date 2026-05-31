import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class MemoTemplateService {
  constructor(private http: HttpClient) {}

  getAllTemplates(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memo-templates`);
  }

  getTemplate(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/memo-templates/${id}`);
  }

  createTemplate(data: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/memo-templates`, data);
  }

  updateTemplate(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memo-templates/${id}`, data);
  }

  deleteTemplate(id: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/memo-templates/${id}`);
  }

  getApprovalLevels(templateId: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memo-templates/${templateId}/approval-levels`);
  }

  saveApprovalLevels(templateId: number, levels: any[]): Observable<any[]> {
    return this.http.put<any[]>(`${ApiConfig.URL}/api/v1/memo-templates/${templateId}/approval-levels`, levels);
  }

  getDefaultCcUsers(templateId: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memo-templates/${templateId}/default-cc`);
  }

  addDefaultCcUser(templateId: number, userId: number): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/memo-templates/${templateId}/default-cc/${userId}`, {});
  }

  removeDefaultCcUser(templateId: number, ccId: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/memo-templates/${templateId}/default-cc/${ccId}`);
  }
}
