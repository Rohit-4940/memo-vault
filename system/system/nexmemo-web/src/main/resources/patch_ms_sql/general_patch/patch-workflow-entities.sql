-- ============================================================
-- FILE: patch_ms_sql/general_patch/patch-workflow-entities.sql
-- Seed data for workflow entities
-- DB: SQL Server (MSSQL)
-- ============================================================

-- ── 1. Memo Categories ─────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM memo_category WHERE code = 'GENERAL')
BEGIN
    SET IDENTITY_INSERT memo_category ON
    INSERT INTO memo_category (id, version, name, code, description, sla_hours, is_active, created_date, modified_date)
    VALUES
    (1, 0, 'General Memo',    'GENERAL',     'Standard internal memos',         NULL, 1, GETDATE(), GETDATE()),
    (2, 0, 'Financial',        'FINANCIAL',   'Budget and financial requests',   48,   1, GETDATE(), GETDATE()),
    (3, 0, 'HR Memo',          'HR',          'Human resource related memos',    24,   1, GETDATE(), GETDATE()),
    (4, 0, 'Procurement',      'PROCUREMENT', 'Vendor and procurement memos',    72,   1, GETDATE(), GETDATE()),
    (5, 0, 'Urgent',           'URGENT',      'Urgent time-sensitive memos',     4,    1, GETDATE(), GETDATE());
    SET IDENTITY_INSERT memo_category OFF
END
GO

-- ── 2. Category Document Requirements ──────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM category_document_requirement WHERE category_id = 2)
BEGIN
    SET IDENTITY_INSERT category_document_requirement ON
    INSERT INTO category_document_requirement (id, version, category_id, document_name, is_required, created_date, modified_date)
    VALUES
    (1, 0, 2, 'Budget Justification',  1, GETDATE(), GETDATE()),
    (2, 0, 2, 'Expense Breakdown',     1, GETDATE(), GETDATE()),
    (3, 0, 3, 'Supporting Letter',     1, GETDATE(), GETDATE()),
    (4, 0, 4, 'Vendor Quote',          1, GETDATE(), GETDATE()),
    (5, 0, 4, 'Terms of Reference',    0, GETDATE(), GETDATE()),
    (6, 0, 5, 'Urgency Justification', 1, GETDATE(), GETDATE());
    SET IDENTITY_INSERT category_document_requirement OFF
END
GO

-- ── 3. Notification Configs ────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM notification_config WHERE event_type = 'MEMO_SUBMITTED')
BEGIN
    SET IDENTITY_INSERT notification_config ON
    INSERT INTO notification_config (id, version, event_type, channel, template_subject, template_body, is_active, created_date, modified_date)
    VALUES
    (1,  0, 'MEMO_CREATED',   'IN_APP', 'Memo Created',    'Your memo {{memoNo}} has been created as {{status}}',   1, GETDATE(), GETDATE()),
    (2,  0, 'MEMO_SUBMITTED', 'IN_APP', 'Memo Submitted',  'Memo {{memoNo}} has been submitted for approval',        1, GETDATE(), GETDATE()),
    (3,  0, 'MEMO_SUBMITTED', 'EMAIL',  'Memo Submitted',  'Memo {{memoNo}} ({{title}}) requires your approval',     1, GETDATE(), GETDATE()),
    (4,  0, 'MEMO_APPROVED',  'IN_APP', 'Memo Approved',   'Memo {{memoNo}} has been approved',                      1, GETDATE(), GETDATE()),
    (5,  0, 'MEMO_APPROVED',  'EMAIL',  'Memo Approved',   'Your memo {{memoNo}} ({{title}}) has been fully approved',1, GETDATE(), GETDATE()),
    (6,  0, 'MEMO_REJECTED',  'IN_APP', 'Memo Rejected',   'Memo {{memoNo}} has been rejected',                      1, GETDATE(), GETDATE()),
    (7,  0, 'MEMO_REJECTED',  'EMAIL',  'Memo Rejected',   'Your memo {{memoNo}} has been rejected: {{reason}}',     1, GETDATE(), GETDATE()),
    (8,  0, 'MEMO_RETURNED',  'IN_APP', 'Memo Returned',   'Memo {{memoNo}} has been returned for revision',         1, GETDATE(), GETDATE()),
    (9,  0, 'SLA_BREACH',     'IN_APP', 'SLA Breach',      'Memo {{memoNo}} has breached SLA at stage {{stage}}',    1, GETDATE(), GETDATE()),
    (10, 0, 'SLA_BREACH',     'EMAIL',  'SLA Breach',      'SLA breach: Memo {{memoNo}} overdue at {{stage}}',       1, GETDATE(), GETDATE());
    SET IDENTITY_INSERT notification_config OFF
END
GO

-- ── 4. Escalation Rules ────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM escalation_rule WHERE name = 'Financial SLA Escalation')
BEGIN
    SET IDENTITY_INSERT escalation_rule ON
    INSERT INTO escalation_rule (id, version, name, category_id, stage_id, timeout_hours, escalation_action, escalate_to_role, escalate_to_user, is_global, created_date, modified_date)
    VALUES
    (1, 0, 'Financial SLA Escalation',  2, NULL, 48, 'NOTIFY_SUPERVISOR',       NULL, NULL, 0, GETDATE(), GETDATE()),
    (2, 0, 'HR SLA Escalation',        3, NULL, 24, 'REASSIGN_TO_SUPERVISOR',   NULL, NULL, 0, GETDATE(), GETDATE()),
    (3, 0, 'Procurement SLA Escalation',4, NULL, 72, 'ESCALATE_TO_HIGHER_LEVEL', 1,    NULL, 0, GETDATE(), GETDATE()),
    (4, 0, 'Urgent SLA Escalation',    5, NULL, 4,  'AUTO_APPROVE',             NULL, NULL, 0, GETDATE(), GETDATE()),
    (5, 0, 'Global Escalation',        NULL, NULL, 120, 'NOTIFY_ALL_APPROVERS',  NULL, NULL, 1, GETDATE(), GETDATE());
    SET IDENTITY_INSERT escalation_rule OFF
END
GO

-- ── 5. Workflow Stages ─────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM workflow_stage WHERE name = 'Draft Review')
BEGIN
    SET IDENTITY_INSERT workflow_stage ON
    INSERT INTO workflow_stage (id, version, name, order_index, color, icon, sla_hours, require_comment, require_document, enable_escalation, escalation_rule_id, notify_email, notify_sms, notify_in_app, created_date, modified_date)
    VALUES
    (1, 0, 'Draft Review',      0, '#6366f1', 'fas fa-pen',          24, 1, 0, 1, 5, 0, 0, 1, GETDATE(), GETDATE()),
    (2, 0, 'Supervisor Review', 1, '#f59e0b', 'fas fa-user-check',   48, 1, 1, 1, 5, 1, 0, 1, GETDATE(), GETDATE()),
    (3, 0, 'Manager Approval',  2, '#10b981', 'fas fa-user-tie',     72, 1, 0, 1, 5, 1, 0, 1, GETDATE(), GETDATE()),
    (4, 0, 'Final Approval',    3, '#3b82f6', 'fas fa-check-double', 48, 1, 0, 0, NULL, 1, 0, 1, GETDATE(), GETDATE());
    SET IDENTITY_INSERT workflow_stage OFF
END
GO

-- ── 6. Workflow Configs ────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM memo_workflow_config WHERE id = 1)
BEGIN
    SET IDENTITY_INSERT memo_workflow_config ON
    INSERT INTO memo_workflow_config (id, version, category_id, sla_hours, require_document_verification, enable_versioning, created_date, modified_date)
    VALUES
    (1, 0, 1, NULL, 0, 1, GETDATE(), GETDATE()),
    (2, 0, 2, 48,   1, 1, GETDATE(), GETDATE()),
    (3, 0, 3, 24,   0, 1, GETDATE(), GETDATE()),
    (4, 0, 4, 72,   1, 1, GETDATE(), GETDATE()),
    (5, 0, 5, 4,    0, 1, GETDATE(), GETDATE());
    SET IDENTITY_INSERT memo_workflow_config OFF
END
GO

-- ── 7. Approval Rule Matrices ───────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM approval_rule_matrix WHERE id = 1)
BEGIN
    SET IDENTITY_INSERT approval_rule_matrix ON
    INSERT INTO approval_rule_matrix (id, version, category_id, stage_id, role_id, user_id, priority, can_approve, can_reject, can_return, can_forward, can_delegate, created_date, modified_date)
    VALUES
    (1, 0, 1, 1, 2000, NULL, 1, 1, 1, 0, 0, 0, GETDATE(), GETDATE()),
    (2, 0, 1, 2, 1000, NULL, 1, 1, 1, 1, 1, 1, GETDATE(), GETDATE()),
    (3, 0, 1, 3, 1000, NULL, 2, 1, 1, 1, 1, 1, GETDATE(), GETDATE()),
    (4, 0, 2, 1, 2000, NULL, 1, 1, 1, 0, 0, 0, GETDATE(), GETDATE()),
    (5, 0, 2, 2, 1000, NULL, 1, 1, 1, 1, 1, 1, GETDATE(), GETDATE());
    SET IDENTITY_INSERT approval_rule_matrix OFF
END
GO

-- ── 8. Fiscal Year (required by reference_sequence FK) ──────────────────────────
IF NOT EXISTS (SELECT 1 FROM fiscal_year WHERE id = 1)
BEGIN
    SET IDENTITY_INSERT fiscal_year ON
    INSERT INTO fiscal_year (id, version, name, start_date, end_date, is_active, status, created_date, modified_date)
    VALUES (1, 0, 'FY 2082/83', '2025-07-16', '2026-07-15', 1, 'ACTIVE', GETDATE(), GETDATE());
    SET IDENTITY_INSERT fiscal_year OFF
END
GO

-- ── 9. Reference Sequence (MANUAL) for memo number generator ───────────────────
IF NOT EXISTS (SELECT 1 FROM reference_sequence WHERE fiscal_year_id = 1 AND scope_type = 'GLOBAL' AND sequence_type = 'MEMO_NO')
BEGIN
    INSERT INTO reference_sequence (version, fiscal_year_id, scope_type, scope_id, sequence_type, prefix, last_sequence, suffix, created_date, modified_date)
    VALUES (0, 1, 'GLOBAL', 0, 'MEMO_NO', 'NEXM', 0, NULL, GETDATE(), GETDATE())
END
GO

PRINT 'Workflow seed data inserted successfully'
GO
