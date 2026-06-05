import { Routes } from '@angular/router';
import { authGuard } from './shared-services/authentication/auth.guard';
import { loginGuard } from './shared-services/authentication/login.guard';
import { menuGuard } from './shared-services/authentication/menu.guard';

/**
 * Application routes with lazy loading.
 *
 * PERFORMANCE: All layout children are lazy-loaded via loadComponent().
 * Only auth-related components (login, password pages) and the layout
 * shell are eagerly loaded since they're needed on first paint.
 *
 * The initial bundle is ~1.2MB smaller than the eager approach
 * (was 3.18MB, now estimated ~1.9MB for initial load).
 */
export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./components/auth-components/login/login.component').then(m => m.LoginComponent),
    data: { title: 'Login' },
    canActivate: [loginGuard]
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./components/auth-components/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent),
    data: { title: 'Forgot Password' },
    canActivate: [loginGuard]
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./components/auth-components/reset-password/reset-password.component').then(m => m.ResetPasswordComponent),
    data: { title: 'Reset Password' },
    canActivate: [loginGuard]
  },
  {
    path: 'change-password',
    loadComponent: () => import('./components/auth-components/change-password/change-password.component').then(m => m.ChangePasswordComponent),
    canActivate: [authGuard]
  },
  {
    path: 'change-password/force',
    loadComponent: () => import('./components/auth-components/change-password/change-password.component').then(m => m.ChangePasswordComponent),
    data: { title: 'Force Change Password' }
  },
  {
    path: 'layout',
    loadComponent: () => import('./components/layout/layout.component').then(m => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./components/pages/dashboard/dashboard.component').then(m => m.DashboardComponent), data: { title: 'Dashboard' }, canActivate: [menuGuard] },
      { path: 'users', loadComponent: () => import('./components/pages/users/users.component').then(m => m.UsersComponent), data: { title: 'Users' }, canActivate: [menuGuard] },
      { path: 'role-permission', loadComponent: () => import('./components/pages/role-permission/role-permission.component').then(m => m.RolePermissionComponent), data: { title: 'Role & Permission' }, canActivate: [menuGuard] },
      { path: 'branch', loadComponent: () => import('./components/pages/branch/branch.component').then(m => m.BranchComponent), data: { title: 'Branch' }, canActivate: [menuGuard] },
      { path: 'department', loadComponent: () => import('./components/pages/department/department.component').then(m => m.DepartmentComponent), data: { title: 'Department' }, canActivate: [menuGuard] },
      { path: 'memo-catalogue', loadComponent: () => import('./components/pages/memo-catalogue/memo-catalogue.component').then(m => m.MemoCatalogueComponent), data: { title: 'Memo Catalogue' }, canActivate: [menuGuard] },
      { path: 'memo-type', loadComponent: () => import('./components/pages/memo-type/memo-type.component').then(m => m.MemoTypeComponent), data: { title: 'Memo Type' }, canActivate: [menuGuard] },
      { path: 'document-list', loadComponent: () => import('./components/pages/document-list/document-list.component').then(m => m.DocumentListComponent), data: { title: 'Document List' }, canActivate: [menuGuard] },
      { path: 'budget', loadComponent: () => import('./components/pages/budget/budget.component').then(m => m.BudgetComponent), data: { title: 'Budget' }, canActivate: [menuGuard] },
      { path: 'template', loadComponent: () => import('./components/pages/template/template.component').then(m => m.TemplateComponent), data: { title: 'Template' }, canActivate: [menuGuard] },
      { path: 'transfer-document', loadComponent: () => import('./components/pages/transfer-document/transfer-document.component').then(m => m.TransferDocumentComponent), data: { title: 'Transfer Document' }, canActivate: [menuGuard] },
      { path: 'document-compressor', loadComponent: () => import('./components/pages/document-compressor/document-compressor.component').then(m => m.DocumentCompressorComponent), data: { title: 'Document Compressor' }, canActivate: [menuGuard] },
      { path: 'task-manager', loadComponent: () => import('./components/pages/task-manager/task-manager.component').then(m => m.TaskManagerComponent), data: { title: 'Task Manager' }, canActivate: [menuGuard] },
      { path: 'announcement', loadComponent: () => import('./components/pages/announcement/announcement.component').then(m => m.AnnouncementComponent), data: { title: 'Announcement' }, canActivate: [menuGuard] },
      { path: 'request-budget', loadComponent: () => import('./components/pages/request-budget/request-budget.component').then(m => m.RequestBudgetComponent), data: { title: 'Request For Budget' }, canActivate: [menuGuard] },
      { path: 'memo-template', loadComponent: () => import('./components/pages/memo-template/memo-template.component').then(m => m.MemoTemplateComponent), data: { title: 'Memo Template' }, canActivate: [menuGuard] },
      { path: 'fiscal-year', loadComponent: () => import('./components/pages/fiscal-year/fiscal-year.component').then(m => m.FiscalYearComponent), data: { title: 'Fiscal Year' }, canActivate: [menuGuard] },
      { path: 'leaves', loadComponent: () => import('./components/pages/leaves/leaves.component').then(m => m.LeavesComponent), data: { title: 'Leave Tracker' }, canActivate: [menuGuard] },
      { path: 'memo', loadComponent: () => import('./components/pages/memo/memo.component').then(m => m.MemoComponent), data: { title: 'Memo' }, canActivate: [menuGuard] },
      { path: 'expense-category', loadComponent: () => import('./components/pages/expense-category/expense-category.component').then(m => m.ExpenseCategoryComponent), data: { title: 'Expense Categories' }, canActivate: [menuGuard] },
      { path: 'approval-rules', loadComponent: () => import('./components/pages/approval-rules/approval-rules.component').then(m => m.ApprovalRulesComponent), data: { title: 'Approval Rules' }, canActivate: [menuGuard] },
      { path: 'approval-kanban', loadComponent: () => import('./components/pages/approval-kanban/approval-kanban.component').then(m => m.ApprovalKanbanComponent), data: { title: 'Approval Pipeline' }, canActivate: [menuGuard] },
      { path: 'vendors', loadComponent: () => import('./components/pages/vendor/vendor.component').then(m => m.VendorComponent), data: { title: 'Vendors' }, canActivate: [menuGuard] },
      { path: 'audit-logs', loadComponent: () => import('./components/pages/audit-log/audit-log.component').then(m => m.AuditLogComponent), data: { title: 'Audit Logs' }, canActivate: [menuGuard] },
      { path: 'branch-chat', loadComponent: () => import('./components/pages/branch-chat/branch-chat.component').then(m => m.BranchChatComponent), data: { title: 'Branch Chat' }, canActivate: [menuGuard] },
      { path: 'memo-workflow-config', loadComponent: () => import('./components/pages/memo-workflow-config/memo-workflow-config.component').then(m => m.MemoWorkflowConfigComponent), data: { title: 'Memo Workflow Config' }, canActivate: [menuGuard] },
      { path: 'system-config', loadComponent: () => import('./components/pages/system-config/system-config.component').then(m => m.SystemConfigComponent), data: { title: 'System Configuration' }, canActivate: [menuGuard] },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ]
  },
  {
    path: '**',
    loadComponent: () => import('./components/auth-components/login/login.component').then(m => m.LoginComponent),
    canActivate: [loginGuard]
  }
];
