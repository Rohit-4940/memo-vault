import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {ApiUtils} from '../../@core/utils/api/ApiUtils';

@Injectable({
  providedIn: 'root'
})
export class LoanService {

  static API = 'v1/loans';

  http = inject(HttpClient);

  protected getApi(): string {
    return LoanService.API;
  }

  public getAllLoan(pageNumber: number, pageSize: number): Observable<any> {
    const req = ApiUtils.getRequest(`${this.getApi()}/all?page=${pageNumber}&size=${pageSize}`);
    return this.http.get(req.url);
  }
}
