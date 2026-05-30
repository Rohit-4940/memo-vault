import {Pageable} from '../model/pagable';


export class PaginationUtils {
  public static PAGE_SIZE = 25;

  static getPageable(meta: any): Pageable {
    const pageable = new Pageable();
    pageable.pageNumber = meta?.currentPage + 1;
    pageable.pageSize = meta?.pageSize || 10;
    pageable.totalElements = meta?.totalRecord || 0;
    pageable.totalPages = meta?.totalPage || 0;
    return pageable;
  }
}
