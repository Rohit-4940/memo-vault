import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-placeholder',
  standalone: true,
  template: `
    <div class="page-container animate-fade-in">
      <div class="page-header">
        <h1><i [class]="icon"></i> {{ title }}</h1>
        <div class="header-actions">
          <button class="btn btn-primary" (click)="showToast()"><i class="fas fa-plus"></i> Create {{ title }}</button>
        </div>
      </div>
      <div class="card">
        <div class="empty-state">
          <i [class]="icon" style="font-size: 3rem;"></i>
          <h3 style="font-weight: 600; color: var(--nexmemo-text);">{{ title }}</h3>
          <p>This module is under development. Full CRUD with search, filters, and reporting coming soon.</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 1.5rem; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
    .page-header h1 { margin: 0; font-size: 1.5rem; font-weight: 700; color: var(--nexmemo-text); }
    .card { background: white; border-radius: 10px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); border: 1px solid var(--nexmemo-border); }
    .empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 4rem 2rem; color: var(--nexmemo-text-muted); gap: 0.75rem; }
    .empty-state i { color: var(--nexmemo-primary-light); }
    .empty-state p { max-width: 400px; text-align: center; line-height: 1.6; }
  `]
})
export class PlaceholderComponent {
  @Input() title = 'Page';
  @Input() icon = 'fas fa-file';

  showToast() {
    alert('This feature will be available in the next release.');
  }
}
