import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class MemoFormFieldService {
  constructor(private http: HttpClient) {}

  getFieldsByMemoType(memoTypeId: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memo-form-fields/memo-type/${memoTypeId}`);
  }

  createField(memoTypeId: number, field: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/memo-form-fields/memo-type/${memoTypeId}`, field);
  }

  updateField(id: number, memoTypeId: number, field: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/memo-form-fields/${id}/memo-type/${memoTypeId}`, field);
  }

  deleteField(id: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/memo-form-fields/${id}`);
  }

  saveAllFields(memoTypeId: number, fields: any[]): Observable<any[]> {
    return this.http.put<any[]>(`${ApiConfig.URL}/api/v1/memo-form-fields/memo-type/${memoTypeId}/batch`, fields);
  }
}
