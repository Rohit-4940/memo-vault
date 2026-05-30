import { Component } from '@angular/core';
import { PlaceholderComponent } from '../placeholder/placeholder.component';

@Component({
  selector: 'app-document-compressor',
  standalone: true,
  imports: [PlaceholderComponent],
  template: '<app-placeholder title="Document Compressor" icon="fas fa-compress-alt"></app-placeholder>'
})
export class DocumentCompressorComponent {}
