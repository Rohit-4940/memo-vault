import { Component } from '@angular/core';
import { PlaceholderComponent } from '../placeholder/placeholder.component';

@Component({
  selector: 'app-task-manager',
  standalone: true,
  imports: [PlaceholderComponent],
  template: '<app-placeholder title="Task Manager" icon="fas fa-tasks"></app-placeholder>'
})
export class TaskManagerComponent {}
