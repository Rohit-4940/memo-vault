import { Component } from '@angular/core';
import { PlaceholderComponent } from '../placeholder/placeholder.component';

@Component({
  selector: 'app-document-list',
  standalone: true,
  imports: [PlaceholderComponent],
  template: '<app-placeholder title="Document List" icon="fas fa-list"></app-placeholder>'
})
export class DocumentListComponent {}
