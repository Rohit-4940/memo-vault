import { Component } from '@angular/core';
import { PlaceholderComponent } from '../placeholder/placeholder.component';

@Component({
  selector: 'app-transfer-document',
  standalone: true,
  imports: [PlaceholderComponent],
  template: '<app-placeholder title="Transfer Document" icon="fas fa-exchange-alt"></app-placeholder>'
})
export class TransferDocumentComponent {}
