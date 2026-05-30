import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class MemoTypeService {
  constructor(private http: HttpClient) {}

  getAllMemoTypes(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memo-types`);
  }

  getMemoType(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/memo-types/${id}`);
  }

  createMemoType(data: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/memo-types`, data);
  }

  updateMemoType(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memo-types/${id}`, data);
  }

  deleteMemoType(id: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/memo-types/${id}`);
  }
}
