import { Component } from '@angular/core';
import { PlaceholderComponent } from '../placeholder/placeholder.component';

@Component({
  selector: 'app-memo-catalogue',
  standalone: true,
  imports: [PlaceholderComponent],
  template: '<app-placeholder title="Memo Catalogue" icon="fas fa-folder-open"></app-placeholder>'
})
export class MemoCatalogueComponent {}
