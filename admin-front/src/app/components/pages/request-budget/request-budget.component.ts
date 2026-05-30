import { Component } from '@angular/core';
import { PlaceholderComponent } from '../placeholder/placeholder.component';

@Component({
  selector: 'app-request-budget',
  standalone: true,
  imports: [PlaceholderComponent],
  template: '<app-placeholder title="Request For Budget" icon="fas fa-hand-holding-usd"></app-placeholder>'
})
export class RequestBudgetComponent {}
