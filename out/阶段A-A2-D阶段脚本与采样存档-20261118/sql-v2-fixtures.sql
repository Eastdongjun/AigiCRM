-- C2/C4 合成夹具（在 COPY_DB 的旧表插入；subject 用 77/78/79 隔离，不与真实数据撞组）
-- C2: folder_type 空串 → 迁移后 space_type 必须 IS NULL（不是 ''）
-- C4a: 同组一 NULL 一有值 → 任一永久则永久（expire IS NULL）
-- C4b: 同组两有值 → MAX
SELECT '== 插入合成夹具（旧表） ==' AS sec;
INSERT INTO biz_folder_type_default
  (folder_type, object_type, folder_id, scope_org_id, scope_level, subject_type, subject_id, perms, expires_at, created_by, created_at, tenant_id)
VALUES
  ('',         'FILE', NULL, NULL, NULL, 'ROLE', 77, 'view', NULL, 1, NOW(), 1),
  ('public',   'FILE', NULL, NULL, NULL, 'ROLE', 78, 'view', NULL, 1, NOW(), 1),
  ('public',   'FILE', NULL, NULL, NULL, 'ROLE', 78, 'view', '2026-12-31 00:00:00', 1, NOW(), 1),
  ('private',  'FILE', NULL, NULL, NULL, 'ROLE', 79, 'view', '2026-12-31 00:00:00', 1, NOW(), 1),
  ('private',  'FILE', NULL, NULL, NULL, 'ROLE', 79, 'view', '2027-06-30 00:00:00', 1, NOW(), 1);
SELECT '== 夹具后旧表行数（62+5=67） ==' AS sec;
SELECT COUNT(*) AS old_rows FROM biz_folder_type_default;
