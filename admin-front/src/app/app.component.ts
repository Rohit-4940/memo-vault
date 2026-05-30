import {Component, inject, OnInit} from '@angular/core';
import {NavigationEnd, Router, RouterOutlet} from '@angular/router';
import {SpinnerComponent} from './@theme/spinner/spinner.component';
import {Title} from '@angular/platform-browser';
import {filter} from 'rxjs/operators';
import {environment} from '../environments/environment';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SpinnerComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit{
  router: Router = inject(Router);
  titleService: Title = inject(Title);

  ngOnInit() {
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        let activeRoute = this.router.routerState.root;
        while (activeRoute.firstChild) {
          activeRoute = activeRoute.firstChild;
        }
        const routeTitle = activeRoute.snapshot.data['title'];
        const title = routeTitle
          ? `NexMemo - ${routeTitle}`
          : 'NexMemo - Memo Management System';
        this.titleService.setTitle(title);
      });
  }
}
