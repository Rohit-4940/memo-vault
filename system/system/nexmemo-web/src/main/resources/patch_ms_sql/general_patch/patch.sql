-- ============================================================
-- FILE: patch_ms_sql/general_patch/patch.sql
-- Initial seed data for NexMemo
-- DB: SQL Server (MSSQL)
-- ============================================================

-- ── 1. SUPER_ADMIN role (id = 1) ─────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM role WHERE id = 1)
BEGIN
    SET IDENTITY_INSERT role ON
    INSERT INTO role (id, version, role_name, status, role_type, role_access, is_role_approvable, role_approval_limit_amount, created_date, modified_date, created_by_id, modified_by_id)
    VALUES (1, 0, 'SUPER_ADMIN', 'ACTIVE', 'SUPER_ADMIN', 'ALL', 0, NULL, '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL, NULL)
    SET IDENTITY_INSERT role OFF
END
GO

-- ── 2. ADMIN role (id = 1000) ────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM role WHERE id = 1000)
BEGIN
    SET IDENTITY_INSERT role ON
    INSERT INTO role (id, version, role_name, status, role_type, role_access, is_role_approvable, role_approval_limit_amount, created_date, modified_date, created_by_id, modified_by_id)
    VALUES (1000, 0, 'ADMIN', 'ACTIVE', 'ADMIN', 'ALL', 0, NULL, '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL, NULL)
    SET IDENTITY_INSERT role OFF
END
GO

-- ── 3. USER role (id = 2000) ─────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM role WHERE id = 2000)
BEGIN
    SET IDENTITY_INSERT role ON
    INSERT INTO role (id, version, role_name, status, role_type, role_access, is_role_approvable, role_approval_limit_amount, created_date, modified_date, created_by_id, modified_by_id)
    VALUES (2000, 0, 'USER', 'ACTIVE', 'USER', 'OWN', 0, NULL, '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL, NULL)
    SET IDENTITY_INSERT role OFF
END
GO

-- ── 4. SUPER_ADMIN user (id = 1) ─────────────────────────────────────────────
-- Password: admin@1234 (BCrypt hash)
IF NOT EXISTS (SELECT 1 FROM users WHERE id = 1)
BEGIN
    SET IDENTITY_INSERT users ON
    INSERT INTO users (id, version, name, user_name, email, password, status, role_id, email_verified, account_locked, failed_login_attempts, force_password_change, created_date, modified_date, created_by_id, modified_by_id)
    VALUES (1, 0, 'Super Admin', 'SPADMIN', 'admin@nexmemo.com', '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ACTIVE', 1, 1, 0, 0, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', NULL, NULL)
    SET IDENTITY_INSERT users OFF
END
GO

-- ── 5. ADMIN user (id = 2) ───────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM users WHERE id = 2)
BEGIN
    SET IDENTITY_INSERT users ON
    INSERT INTO users (id, version, name, user_name, email, password, status, role_id, email_verified, account_locked, failed_login_attempts, force_password_change, created_date, modified_date, created_by_id, modified_by_id)
    VALUES (2, 0, 'System Admin', 'ADMIN', 'sysadmin@nexmemo.com', '$2a$12$LGS/PqYCBHN09t0RQJ.BJOmrDL79lBjpLpOuiqjN1fF2VBQF7KWXO', 'ACTIVE', 1000, 1, 0, 0, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 1, NULL)
    SET IDENTITY_INSERT users OFF
END
GO

-- ── 6. Menus ─────────────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM menu WHERE id = 1)
BEGIN
    SET IDENTITY_INSERT menu ON
    INSERT INTO menu (id, version, menu_name, fa_icon, front_url, parent_id, orders, status, created_date, modified_date)
    VALUES
    (1, 0, 'Dashboard',              'fas fa-tachometer-alt',     '/layout/dashboard',            NULL, 1,  'ACTIVE', GETDATE(), GETDATE()),
    (2, 0, 'Branch',                 'fas fa-building',           '/layout/branch',               NULL, 2,  'ACTIVE', GETDATE(), GETDATE()),
    (3, 0, 'Department',             'fas fa-sitemap',            '/layout/department',           NULL, 3,  'ACTIVE', GETDATE(), GETDATE()),
    (4, 0, 'Role and Permission',    'fas fa-user-shield',        '/layout/role-permission',      NULL, 4,  'ACTIVE', GETDATE(), GETDATE()),
    (5, 0, 'Users',                  'fas fa-users',              '/layout/users',                NULL, 5,  'ACTIVE', GETDATE(), GETDATE()),
    (6, 0, 'Memo Type',              'fas fa-tag',                '/layout/memo-type',            NULL, 6,  'ACTIVE', GETDATE(), GETDATE()),
    (7, 0, 'Memo-Catalogue',         'fas fa-folder-open',        '/layout/memo-catalogue',       NULL, 7,  'ACTIVE', GETDATE(), GETDATE()),
    (8, 0, 'Template',               'fas fa-file-alt',           '/layout/template',             NULL, 8,  'ACTIVE', GETDATE(), GETDATE()),
    (9, 0, 'Document-List',          'fas fa-list',               '/layout/document-list',        NULL, 9,  'ACTIVE', GETDATE(), GETDATE()),
    (10, 0, 'Transfer Document',      'fas fa-exchange-alt',      '/layout/transfer-document',    NULL, 10, 'ACTIVE', GETDATE(), GETDATE()),
    (11, 0, 'Document Compressor',    'fas fa-compress-alt',      '/layout/document-compressor',  NULL, 11, 'ACTIVE', GETDATE(), GETDATE()),
    (12, 0, 'Budget',                 'fas fa-chart-pie',          '/layout/budget',               NULL, 12, 'ACTIVE', GETDATE(), GETDATE()),
    (13, 0, 'Request For Budget',     'fas fa-hand-holding-usd',  '/layout/request-budget',       NULL, 13, 'ACTIVE', GETDATE(), GETDATE()),
    (14, 0, 'System Configuration',   'fas fa-cogs',              '/layout/system-config',        NULL, 14, 'ACTIVE', GETDATE(), GETDATE()),
    (15, 0, 'Task Manager',           'fas fa-tasks',              '/layout/task-manager',         NULL, 15, 'ACTIVE', GETDATE(), GETDATE()),
    (16, 0, 'Announcement',           'fas fa-bullhorn',          '/layout/announcement',         NULL, 16, 'ACTIVE', GETDATE(), GETDATE());
    SET IDENTITY_INSERT menu OFF
END
GO

-- ── 7. Role-Permission assignments (SUPER_ADMIN = all) ────────────────────────
IF NOT EXISTS (SELECT 1 FROM role_permission_rights WHERE role_id = 1 AND menu_id = 1)
BEGIN
    INSERT INTO role_permission_rights (version, role_id, menu_id, created_date, modified_date)
    VALUES
    (0, 1, 1, GETDATE(), GETDATE()), (0, 1, 2, GETDATE(), GETDATE()),
    (0, 1, 3, GETDATE(), GETDATE()), (0, 1, 4, GETDATE(), GETDATE()),
    (0, 1, 5, GETDATE(), GETDATE()), (0, 1, 6, GETDATE(), GETDATE()),
    (0, 1, 7, GETDATE(), GETDATE()), (0, 1, 8, GETDATE(), GETDATE()),
    (0, 1, 9, GETDATE(), GETDATE()), (0, 1, 10, GETDATE(), GETDATE()),
    (0, 1, 11, GETDATE(), GETDATE()), (0, 1, 12, GETDATE(), GETDATE()),
    (0, 1, 13, GETDATE(), GETDATE()), (0, 1, 14, GETDATE(), GETDATE()),
    (0, 1, 15, GETDATE(), GETDATE()), (0, 1, 16, GETDATE(), GETDATE());
END
GO

-- ── 8. Role-Permission assignments (ADMIN = all) ──────────────────────────────
IF NOT EXISTS (SELECT 1 FROM role_permission_rights WHERE role_id = 1000 AND menu_id = 1)
BEGIN
    INSERT INTO role_permission_rights (version, role_id, menu_id, created_date, modified_date)
    VALUES
    (0, 1000, 1, GETDATE(), GETDATE()), (0, 1000, 2, GETDATE(), GETDATE()),
    (0, 1000, 3, GETDATE(), GETDATE()), (0, 1000, 4, GETDATE(), GETDATE()),
    (0, 1000, 5, GETDATE(), GETDATE()), (0, 1000, 6, GETDATE(), GETDATE()),
    (0, 1000, 7, GETDATE(), GETDATE()), (0, 1000, 8, GETDATE(), GETDATE()),
    (0, 1000, 9, GETDATE(), GETDATE()), (0, 1000, 10, GETDATE(), GETDATE()),
    (0, 1000, 11, GETDATE(), GETDATE()), (0, 1000, 12, GETDATE(), GETDATE()),
    (0, 1000, 13, GETDATE(), GETDATE()), (0, 1000, 14, GETDATE(), GETDATE()),
    (0, 1000, 15, GETDATE(), GETDATE()), (0, 1000, 16, GETDATE(), GETDATE());
END
GO

-- ── 9. Role-Permission assignments (USER = limited) ───────────────────────────
IF NOT EXISTS (SELECT 1 FROM role_permission_rights WHERE role_id = 2000 AND menu_id = 1)
BEGIN
    INSERT INTO role_permission_rights (version, role_id, menu_id, created_date, modified_date)
    VALUES
    (0, 2000, 1, GETDATE(), GETDATE()),  (0, 2000, 6, GETDATE(), GETDATE()),
    (0, 2000, 7, GETDATE(), GETDATE()),  (0, 2000, 9, GETDATE(), GETDATE()),
    (0, 2000, 12, GETDATE(), GETDATE()), (0, 2000, 13, GETDATE(), GETDATE()),
    (0, 2000, 15, GETDATE(), GETDATE());
END
GO
