import { Routes } from '@angular/router';
import { LayoutComponent } from './components/layout/layout.component';
import { authGuard } from './shared-services/authentication/auth.guard';
import { loginGuard } from './shared-services/authentication/login.guard';
import { menuGuard } from './shared-services/authentication/menu.guard';
import { LoginComponent } from './components/auth-components/login/login.component';
import { ForgotPasswordComponent } from './components/auth-components/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './components/auth-components/reset-password/reset-password.component';
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
import { MemoTemplateComponent } from './components/pages/memo-template/memo-template.component';
import { FiscalYearComponent } from './components/pages/fiscal-year/fiscal-year.component';
import { LeavesComponent } from './components/pages/leaves/leaves.component';
import { MemoComponent } from './components/pages/memo/memo.component';
import { ExpenseCategoryComponent } from './components/pages/expense-category/expense-category.component';
import { ApprovalRulesComponent } from './components/pages/approval-rules/approval-rules.component';
import { ApprovalKanbanComponent } from './components/pages/approval-kanban/approval-kanban.component';
import { VendorComponent } from './components/pages/vendor/vendor.component';
import { AuditLogComponent } from './components/pages/audit-log/audit-log.component';
import { BranchChatComponent } from './components/pages/branch-chat/branch-chat.component';
import { MemoWorkflowConfigComponent } from './components/pages/memo-workflow-config/memo-workflow-config.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, data: { title: 'Login' }, canActivate: [loginGuard] },
  { path: 'forgot-password', component: ForgotPasswordComponent, data: { title: 'Forgot Password' }, canActivate: [loginGuard] },
  { path: 'reset-password', component: ResetPasswordComponent, data: { title: 'Reset Password' }, canActivate: [loginGuard] },
  {
    path: 'layout', component: LayoutComponent, canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent, data: { title: 'Dashboard' }, canActivate: [menuGuard] },
      { path: 'users', component: UsersComponent, data: { title: 'Users' }, canActivate: [menuGuard] },
      { path: 'role-permission', component: RolePermissionComponent, data: { title: 'Role & Permission' }, canActivate: [menuGuard] },
      { path: 'branch', component: BranchComponent, data: { title: 'Branch' }, canActivate: [menuGuard] },
      { path: 'department', component: DepartmentComponent, data: { title: 'Department' }, canActivate: [menuGuard] },
      { path: 'memo-catalogue', component: MemoCatalogueComponent, data: { title: 'Memo Catalogue' }, canActivate: [menuGuard] },
      { path: 'memo-type', component: MemoTypeComponent, data: { title: 'Memo Type' }, canActivate: [menuGuard] },
      { path: 'document-list', component: DocumentListComponent, data: { title: 'Document List' }, canActivate: [menuGuard] },
      { path: 'budget', component: BudgetComponent, data: { title: 'Budget' }, canActivate: [menuGuard] },
      { path: 'template', component: TemplateComponent, data: { title: 'Template' }, canActivate: [menuGuard] },
      { path: 'transfer-document', component: TransferDocumentComponent, data: { title: 'Transfer Document' }, canActivate: [menuGuard] },
      { path: 'document-compressor', component: DocumentCompressorComponent, data: { title: 'Document Compressor' }, canActivate: [menuGuard] },
      { path: 'task-manager', component: TaskManagerComponent, data: { title: 'Task Manager' }, canActivate: [menuGuard] },
      { path: 'announcement', component: AnnouncementComponent, data: { title: 'Announcement' }, canActivate: [menuGuard] },
      { path: 'request-budget', component: RequestBudgetComponent, data: { title: 'Request For Budget' }, canActivate: [menuGuard] },
      { path: 'memo-template', component: MemoTemplateComponent, data: { title: 'Memo Template' }, canActivate: [menuGuard] },
      { path: 'fiscal-year', component: FiscalYearComponent, data: { title: 'Fiscal Year' }, canActivate: [menuGuard] },
      { path: 'leaves', component: LeavesComponent, data: { title: 'Leave Tracker' }, canActivate: [menuGuard] },
      { path: 'memo', component: MemoComponent, data: { title: 'Memo' }, canActivate: [menuGuard] },
      { path: 'expense-category', component: ExpenseCategoryComponent, data: { title: 'Expense Categories' }, canActivate: [menuGuard] },
      { path: 'approval-rules', component: ApprovalRulesComponent, data: { title: 'Approval Rules' }, canActivate: [menuGuard] },
      { path: 'approval-kanban', component: ApprovalKanbanComponent, data: { title: 'Approval Pipeline' }, canActivate: [menuGuard] },
      { path: 'vendors', component: VendorComponent, data: { title: 'Vendors' }, canActivate: [menuGuard] },
      { path: 'audit-logs', component: AuditLogComponent, data: { title: 'Audit Logs' }, canActivate: [menuGuard] },
      { path: 'branch-chat', component: BranchChatComponent, data: { title: 'Branch Chat' }, canActivate: [menuGuard] },
      { path: 'memo-workflow-config', component: MemoWorkflowConfigComponent, data: { title: 'Memo Workflow Config' }, canActivate: [menuGuard] },
      { path: 'system-config', component: SystemConfigComponent, data: { title: 'System Configuration' }, canActivate: [menuGuard] },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ]
  },
  { path: '**', component: LoginComponent, canActivate: [loginGuard] }
];
