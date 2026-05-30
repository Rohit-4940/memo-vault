import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class BranchService {
  constructor(private http: HttpClient) {}

  getAllBranches(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/branches`);
  }

  createBranch(data: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/branches`, data);
  }

  updateBranch(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/branches/${id}`, data);
  }

  deleteBranch(id: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/branches/${id}`);
  }
}
