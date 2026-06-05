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
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/system-configs/seed-defaults`, {});
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

  getPaginated(search: string, page: number, size: number, sortBy: string, sortDir: string, filters?: any): Observable<any> {
    let params = `?page=${page - 1}&size=${size}&sortBy=${sortBy}&sortDir=${sortDir}`;
    if (search) params += `&search=${encodeURIComponent(search)}`;
    if (filters) Object.keys(filters).forEach(k => { if (filters[k]) params += `&filters[${k}]=${encodeURIComponent(filters[k])}`; });
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/system-configs/paginated${params}`);
  }
}
