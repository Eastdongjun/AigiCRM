-- C2/C4 夹具断言（夹具迁移后执行）
SELECT '== C2 断言：blank folder_type → space_type IS NULL（非空串） ==' AS sec;
SELECT legacy_ref, space_type, space_type IS NULL AS is_null, space_type = '' AS is_empty
  FROM biz_file_grant WHERE subject_id = 77 AND source = 'legacy';
SELECT COUNT(*) AS c2_bad FROM biz_file_grant
 WHERE subject_id = 77 AND source = 'legacy' AND NOT (space_type IS NULL);
SELECT '== C4a 断言：任一永久则永久（组内 NULL+2026-12-31 → NULL） ==' AS sec;
SELECT legacy_ref, expire_at, expire_at IS NULL AS is_null
  FROM biz_file_grant WHERE subject_id = 78 AND source = 'legacy';
SELECT COUNT(*) AS c4a_bad FROM biz_file_grant
 WHERE subject_id = 78 AND source = 'legacy' AND expire_at IS NOT NULL;
SELECT '== C4b 断言：两值取 MAX（2027-06-30） ==' AS sec;
SELECT legacy_ref, expire_at
  FROM biz_file_grant WHERE subject_id = 79 AND source = 'legacy';
SELECT COUNT(*) AS c4b_ok FROM biz_file_grant
 WHERE subject_id = 79 AND source = 'legacy' AND expire_at = '2027-06-30 00:00:00';
