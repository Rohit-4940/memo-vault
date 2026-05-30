import { Component } from '@angular/core';
import { PlaceholderComponent } from '../placeholder/placeholder.component';

@Component({
  selector: 'app-announcement',
  standalone: true,
  imports: [PlaceholderComponent],
  template: '<app-placeholder title="Announcement" icon="fas fa-bullhorn"></app-placeholder>'
})
export class AnnouncementComponent {}
