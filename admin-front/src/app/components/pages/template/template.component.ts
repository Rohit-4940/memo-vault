import { Component } from '@angular/core';
import { PlaceholderComponent } from '../placeholder/placeholder.component';

@Component({
  selector: 'app-template',
  standalone: true,
  imports: [PlaceholderComponent],
  template: '<app-placeholder title="Template" icon="fas fa-file-alt"></app-placeholder>'
})
export class TemplateComponent {}
