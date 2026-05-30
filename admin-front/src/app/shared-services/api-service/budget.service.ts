import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class BudgetService {
  constructor(private http: HttpClient) {}

  getAllBudgets(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/budgets`);
  }

  getBudget(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/budgets/${id}`);
  }

  createBudget(data: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/budgets`, data);
  }

  updateBudget(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/budgets/${id}`, data);
  }

  deleteBudget(id: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/budgets/${id}`);
  }
}
