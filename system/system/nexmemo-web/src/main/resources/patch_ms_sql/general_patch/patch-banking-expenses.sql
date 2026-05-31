-- ============================================================
-- BANKING EXPENSE MODULES - Seed Data
-- Expense Categories, Approval Rules, Menus & Permissions
-- ============================================================

-- ── 1. Expense Categories (base: parent_id IS NULL) ────────
IF NOT EXISTS (SELECT 1 FROM expense_category WHERE id = 1)
BEGIN
    SET IDENTITY_INSERT expense_category ON
    INSERT INTO expense_category (id, version, category_name, category_code, parent_category_id,
        budget_code_pattern, max_amount, approval_levels_required, active, created_date, modified_date)
    VALUES
    (1,  0, 'Travel & Transport',          'EXP-TRAVEL',  NULL,   'TRAV-%',   500000.00, 3, 1, GETDATE(), GETDATE()),
    (2,  0, 'Procurement & Supplies',      'EXP-PROC',    NULL,   'PROC-%',   2000000.00,3, 1, GETDATE(), GETDATE()),
    (3,  0, 'Operational Expenses',        'EXP-OPS',     NULL,   'OPS-%',    300000.00, 2, 1, GETDATE(), GETDATE()),
    (4,  0, 'Human Resources',             'EXP-HR',      NULL,   'HR-%',     1500000.00,2, 1, GETDATE(), GETDATE()),
    (5,  0, 'Marketing & Communications',  'EXP-MKT',     NULL,   'MKT-%',    800000.00, 2, 1, GETDATE(), GETDATE()),
    (6,  0, 'Capital Expenditure (CAPEX)', 'EXP-CAPEX',   NULL,   'CAPEX-%',  5000000.00,3, 1, GETDATE(), GETDATE()),
    (7,  0, 'IT & Software',               'EXP-IT',      NULL,   'IT-%',     1000000.00,2, 1, GETDATE(), GETDATE()),
    (8,  0, 'Training & Development',      'EXP-TRAIN',   NULL,   'TRAIN-%',  400000.00, 2, 1, GETDATE(), GETDATE()),
    (9,  0, 'Utilities & Rent',            'EXP-UTIL',    NULL,   'UTIL-%',   600000.00, 2, 1, GETDATE(), GETDATE()),
    (10, 0, 'Miscellaneous',               'EXP-MISC',    NULL,   'MISC-%',   100000.00, 1, 1, GETDATE(), GETDATE());

    -- Sub-categories under Travel
    INSERT INTO expense_category (id, version, category_name, category_code, parent_category_id,
        budget_code_pattern, max_amount, approval_levels_required, active, created_date, modified_date)
    VALUES
    (11, 0, 'Domestic Travel',             'EXP-TRAVEL-DOM',  1, 'TRAV-DOM-%',   200000.00, 2, 1, GETDATE(), GETDATE()),
    (12, 0, 'International Travel',        'EXP-TRAVEL-INT',  1, 'TRAV-INT-%',   500000.00, 3, 1, GETDATE(), GETDATE()),
    (13, 0, 'Fuel & Mileage',              'EXP-TRAVEL-FUEL', 1, 'TRAV-FUEL-%',  100000.00, 1, 1, GETDATE(), GETDATE());

    -- Sub-categories under Procurement
    INSERT INTO expense_category (id, version, category_name, category_code, parent_category_id,
        budget_code_pattern, max_amount, approval_levels_required, active, created_date, modified_date)
    VALUES
    (14, 0, 'Office Supplies',             'EXP-PROC-OFFICE', 2, 'PROC-OFF-%',   300000.00, 2, 1, GETDATE(), GETDATE()),
    (15, 0, 'Equipment & Furniture',       'EXP-PROC-EQUIP',  2, 'PROC-EQP-%',   2000000.00,3, 1, GETDATE(), GETDATE()),
    (16, 0, 'Stationery',                  'EXP-PROC-STAT',   2, 'PROC-STA-%',   100000.00, 1, 1, GETDATE(), GETDATE());

    -- Sub-categories under HR
    INSERT INTO expense_category (id, version, category_name, category_code, parent_category_id,
        budget_code_pattern, max_amount, approval_levels_required, active, created_date, modified_date)
    VALUES
    (17, 0, 'Recruitment & Hiring',        'EXP-HR-RECR', 4, 'HR-RECR-%',   500000.00, 2, 1, GETDATE(), GETDATE()),
    (18, 0, 'Employee Welfare',            'EXP-HR-WELF', 4, 'HR-WELF-%',   300000.00, 1, 1, GETDATE(), GETDATE()),
    (19, 0, 'Medical & Insurance',         'EXP-HR-MED',  4, 'HR-MED-%',    700000.00, 2, 1, GETDATE(), GETDATE());
    SET IDENTITY_INSERT expense_category OFF
END
GO

-- ── 2. Approval Rules ─────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM approval_rule WHERE id = 1)
BEGIN
    SET IDENTITY_INSERT approval_rule ON
    INSERT INTO approval_rule (id, version, rule_name, min_amount, max_amount,
        approval_levels_required, auto_approve, committee_required,
        expense_category_id, department_id, branch_id, priority_order,
        active, created_date, modified_date)
    VALUES
    -- General rules (NULL category = applies to all)
    (1,  0, 'Low Value - Auto Approve',       0.00,      50000.00,   1, 1, 0, NULL, NULL, NULL, 1, 1, GETDATE(), GETDATE()),
    (2,  0, 'Medium Value - Single Approve',  50000.01,  500000.00,  1, 0, 0, NULL, NULL, NULL, 2, 1, GETDATE(), GETDATE()),
    (3,  0, 'High Value - Two Levels',        500000.01, 2000000.00, 2, 0, 0, NULL, NULL, NULL, 3, 1, GETDATE(), GETDATE()),
    (4,  0, 'Large Value - Committee',        2000000.01,10000000.00,3, 0, 1, NULL, NULL, NULL, 4, 1, GETDATE(), GETDATE()),

    -- Category-specific rules
    (5,  0, 'Travel - Domestic',              0.00,      200000.00,  2, 0, 0, 1,  NULL, NULL, 1, 1, GETDATE(), GETDATE()),
    (6,  0, 'Travel - International',         0.00,      500000.00,  3, 0, 1, 1,  NULL, NULL, 2, 1, GETDATE(), GETDATE()),
    (7,  0, 'CAPEX - Board Approval',         0.00,      5000000.00, 3, 0, 1, 6,  NULL, NULL, 1, 1, GETDATE(), GETDATE()),
    (8,  0, 'IT - Tech Purchase',             0.00,      1000000.00, 2, 0, 0, 7,  NULL, NULL, 1, 1, GETDATE(), GETDATE());
    SET IDENTITY_INSERT approval_rule OFF
END
GO

-- ── 3. New Menu entries for banking expense modules ──────
IF NOT EXISTS (SELECT 1 FROM menu WHERE id = 17)
BEGIN
    SET IDENTITY_INSERT menu ON
    INSERT INTO menu (id, version, menu_name, fa_icon, front_url, parent_id, orders, status, created_date, modified_date)
    VALUES
    (17, 0, 'Expense Category',      'fas fa-tags',              '/layout/expense-category',  NULL, 17, 'ACTIVE', GETDATE(), GETDATE()),
    (18, 0, 'Approval Rules',        'fas fa-check-double',      '/layout/approval-rules',     NULL, 18, 'ACTIVE', GETDATE(), GETDATE()),
    (19, 0, 'Memo',                  'fas fa-file-alt',           '/layout/memo',               NULL, 19, 'ACTIVE', GETDATE(), GETDATE()),
    (20, 0, 'Fiscal Year',           'fas fa-calendar-alt',       '/layout/fiscal-year',        NULL, 20, 'ACTIVE', GETDATE(), GETDATE()),
    (21, 0, 'Leave Tracker',         'fas fa-umbrella-beach',     '/layout/leave-tracker',      NULL, 21, 'ACTIVE', GETDATE(), GETDATE()),
    (22, 0, 'Memo Template',         'fas fa-file-alt',           '/layout/template',           NULL, 22, 'ACTIVE', GETDATE(), GETDATE()),
    (23, 0, 'Dashboard',             'fas fa-chart-line',         '/layout/dashboard',          NULL, 23, 'ACTIVE', GETDATE(), GETDATE());
    SET IDENTITY_INSERT menu OFF
END
GO

-- ── 4. Role-Permission assignments (SUPER_ADMIN: all 23) ──
IF NOT EXISTS (SELECT 1 FROM role_permission_rights WHERE role_id = 1 AND menu_id = 17)
BEGIN
    INSERT INTO role_permission_rights (version, role_id, menu_id, created_date, modified_date)
    VALUES
    (0, 1, 17, GETDATE(), GETDATE()), (0, 1, 18, GETDATE(), GETDATE()),
    (0, 1, 19, GETDATE(), GETDATE()), (0, 1, 20, GETDATE(), GETDATE()),
    (0, 1, 21, GETDATE(), GETDATE()), (0, 1, 22, GETDATE(), GETDATE()),
    (0, 1, 23, GETDATE(), GETDATE());
END
GO

-- ── 5. Role-Permission assignments (ADMIN: all 23) ────────
IF NOT EXISTS (SELECT 1 FROM role_permission_rights WHERE role_id = 1000 AND menu_id = 17)
BEGIN
    INSERT INTO role_permission_rights (version, role_id, menu_id, created_date, modified_date)
    VALUES
    (0, 1000, 17, GETDATE(), GETDATE()), (0, 1000, 18, GETDATE(), GETDATE()),
    (0, 1000, 19, GETDATE(), GETDATE()), (0, 1000, 20, GETDATE(), GETDATE()),
    (0, 1000, 21, GETDATE(), GETDATE()), (0, 1000, 22, GETDATE(), GETDATE()),
    (0, 1000, 23, GETDATE(), GETDATE());
END
GO

-- ── 6. Role-Permission assignments (USER: memo + dashboard + expense-category) ──
IF NOT EXISTS (SELECT 1 FROM role_permission_rights WHERE role_id = 2000 AND menu_id = 17)
BEGIN
    INSERT INTO role_permission_rights (version, role_id, menu_id, created_date, modified_date)
    VALUES
    (0, 2000, 17, GETDATE(), GETDATE()), (0, 2000, 19, GETDATE(), GETDATE()),
    (0, 2000, 23, GETDATE(), GETDATE());
END
GO
