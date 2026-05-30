import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SidebarService {
  private isExpandedSubject = new BehaviorSubject<boolean>(true);
  isExpanded$ = this.isExpandedSubject.asObservable();

  toggleSidebar() {
    this.isExpandedSubject.next(!this.isExpandedSubject.value);
  }

  setExpanded(expanded: boolean) {
    this.isExpandedSubject.next(expanded);
  }
}
