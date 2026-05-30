import { Routes } from '@angular/router';
import { LayoutComponent } from './components/layout/layout.component';
import { authGuard } from './shared-services/authentication/auth.guard';
import { loginGuard } from './shared-services/authentication/login.guard';
import { LoginComponent } from './components/auth-components/login/login.component';
import { DashboardComponent } from './components/pages/dashboard/dashboard.component';
import { UsersComponent } from './components/pages/users/users.component';
import { RolePermissionComponent } from './components/pages/role-permission/role-permission.component';
import { BranchComponent } from './components/pages/branch/branch.component';
import { MemoCatalogueComponent } from './components/pages/memo-catalogue/memo-catalogue.component';
import { DocumentListComponent } from './components/pages/document-list/document-list.component';
import { BudgetComponent } from './components/pages/budget/budget.component';
import { TemplateComponent } from './components/pages/template/template.component';
import { TransferDocumentComponent } from './components/pages/transfer-document/transfer-document.component';
import { DocumentCompressorComponent } from './components/pages/document-compressor/document-compressor.component';
import { SystemConfigComponent } from './components/pages/system-config/system-config.component';
import { TaskManagerComponent } from './components/pages/task-manager/task-manager.component';
import { AnnouncementComponent } from './components/pages/announcement/announcement.component';
import { DepartmentComponent } from './components/pages/department/department.component';
import { MemoTypeComponent } from './components/pages/memo-type/memo-type.component';
import { RequestBudgetComponent } from './components/pages/request-budget/request-budget.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, data: { title: 'Login' }, canActivate: [loginGuard] },
  {
    path: 'layout', component: LayoutComponent, canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent, data: { title: 'Dashboard' } },
      { path: 'users', component: UsersComponent, data: { title: 'Users' } },
      { path: 'role-permission', component: RolePermissionComponent, data: { title: 'Role & Permission' } },
      { path: 'branch', component: BranchComponent, data: { title: 'Branch' } },
      { path: 'department', component: DepartmentComponent, data: { title: 'Department' } },
      { path: 'memo-catalogue', component: MemoCatalogueComponent, data: { title: 'Memo Catalogue' } },
      { path: 'memo-type', component: MemoTypeComponent, data: { title: 'Memo Type' } },
      { path: 'document-list', component: DocumentListComponent, data: { title: 'Document List' } },
      { path: 'budget', component: BudgetComponent, data: { title: 'Budget' } },
      { path: 'template', component: TemplateComponent, data: { title: 'Template' } },
      { path: 'transfer-document', component: TransferDocumentComponent, data: { title: 'Transfer Document' } },
      { path: 'document-compressor', component: DocumentCompressorComponent, data: { title: 'Document Compressor' } },
      { path: 'task-manager', component: TaskManagerComponent, data: { title: 'Task Manager' } },
      { path: 'announcement', component: AnnouncementComponent, data: { title: 'Announcement' } },
      { path: 'request-budget', component: RequestBudgetComponent, data: { title: 'Request For Budget' } },
      { path: 'system-config', component: SystemConfigComponent, data: { title: 'System Configuration' } },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ]
  },
  { path: '**', component: LoginComponent, canActivate: [loginGuard] }
];
