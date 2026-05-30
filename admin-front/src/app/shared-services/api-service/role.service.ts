import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class RoleService {
  constructor(private http: HttpClient) {}

  getAllRoles(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/roles`);
  }

  getRole(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/roles/${id}`);
  }

  createRole(data: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/roles`, data);
  }

  updateRole(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/roles/${id}`, data);
  }

  deleteRole(id: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/roles/${id}`);
  }
}
