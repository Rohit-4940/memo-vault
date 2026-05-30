import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class SystemConfigService {
  constructor(private http: HttpClient) {}

  getAllConfigs(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/system-configs`);
  }

  getConfig(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/system-configs/${id}`);
  }

  updateConfig(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/system-configs/${id}`, data);
  }

  seedDefaults(): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/system-configs/seed`, {});
  }

  getEmailConfig(): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/system-configs/email`);
  }

  updateEmailConfig(data: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/system-configs/email`, data);
  }

  getConfigByKey(key: string): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/system-configs/key/${key}`);
  }
}
