-- ============================================================
-- FILE: patch_ms_sql/general_patch/location.sql
-- Nepal location data migration
-- NOTE: Seed data is now handled by LocationSeeder (Java @Component @Order(2))
-- DB: SQL Server (MSSQL)
-- ============================================================

-- ── name_nepali column (fallback if Liquibase hasn't run) ─────────────────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_NAME = 'province' AND COLUMN_NAME = 'name_nepali')
BEGIN
    ALTER TABLE province ADD name_nepali NVARCHAR(255) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_NAME = 'district' AND COLUMN_NAME = 'name_nepali')
BEGIN
    ALTER TABLE district ADD name_nepali NVARCHAR(255) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_NAME = 'municipality_vdc' AND COLUMN_NAME = 'name_nepali')
BEGIN
    ALTER TABLE municipality_vdc ADD name_nepali NVARCHAR(255) NULL;
END
GO
