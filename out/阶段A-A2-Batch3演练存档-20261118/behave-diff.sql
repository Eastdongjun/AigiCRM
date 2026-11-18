SELECT 'only_in_before' AS side, COUNT(*) AS cnt FROM (
  SELECT * FROM _a2_before EXCEPT SELECT * FROM _a2_after) t
UNION ALL
SELECT 'only_in_after', COUNT(*) FROM (
  SELECT * FROM _a2_after EXCEPT SELECT * FROM _a2_before) t;
