import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {ApiUtils} from '../../@core/utils/api/ApiUtils';
import {HumanResources} from '../../@core/model/human-resources';

@Injectable({
  providedIn: 'root'
})
export class HumanResourcesService {

  static API = 'v1/hr';
  constructor(readonly http: HttpClient) {}
  protected getApi(): string {
    return HumanResourcesService.API;
  }

  public getAllHumanResourceAll(): Observable<any> {
    const req = ApiUtils.getRequest(`${HumanResourcesService.API}/all`);
    return this.http.get(req.url);
  }
  public getAllHumanResources(pageNumber: number, pageSize: number): Observable<any> {
    const req = ApiUtils.getRequest(`${this.getApi()}/list?page=${pageNumber}&size=${pageSize}`);
    return this.http.get(req.url);
  }

  public getHumanResourcesByPagination(pageNumber: number, pageSize: number): Observable<any> {
    const req = ApiUtils.getRequest(`${HumanResourcesService.API}/list?page=${pageNumber}&size=${pageSize}`);
    return this.http.get(req.url);
  }

  public createHumanResources(human: HumanResources): Observable<any> {
    const req = ApiUtils.getRequest(`${HumanResourcesService.API}/create`);
    return this.http.post(req.url, human);
  }
  public updateHumanResources(humanUpdate: HumanResources): Observable<any> {
    const req = ApiUtils.getRequest(`${HumanResourcesService.API}/update`);
    return this.http.post(req.url, humanUpdate);
  }
  public deleteHumanResources(id: string): Observable<any> {
    const req = ApiUtils.getRequest(`${HumanResourcesService.API}/id?id=${id}`);
    return this.http.delete(req.url);
  }
}
