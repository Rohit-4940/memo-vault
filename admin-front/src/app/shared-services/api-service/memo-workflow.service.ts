import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class MemoWorkflowService {
  constructor(private http: HttpClient) {}

  getConfig(memoTypeId: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/memo-workflows/memo-type/${memoTypeId}/config`);
  }

  saveConfig(memoTypeId: number, config: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memo-workflows/memo-type/${memoTypeId}/config`, config);
  }

  deleteConfig(memoTypeId: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/memo-workflows/memo-type/${memoTypeId}/config`);
  }

  getStages(workflowConfigId: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memo-workflows/config/${workflowConfigId}/stages`);
  }

  saveStages(workflowConfigId: number, stages: any[]): Observable<any[]> {
    return this.http.put<any[]>(`${ApiConfig.URL}/api/v1/memo-workflows/config/${workflowConfigId}/stages`, stages);
  }

  getPermissions(memoTypeId: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memo-workflows/memo-type/${memoTypeId}/permissions`);
  }

  savePermissions(memoTypeId: number, permissions: any[]): Observable<any[]> {
    return this.http.put<any[]>(`${ApiConfig.URL}/api/v1/memo-workflows/memo-type/${memoTypeId}/permissions`, permissions);
  }
}
