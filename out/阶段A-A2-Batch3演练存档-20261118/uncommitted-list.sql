SELECT subject_id, COUNT(*) AS rows_cnt FROM biz_folder_type_default
WHERE subject_id > 200
GROUP BY subject_id, object_type, folder_id, folder_type,
         NULLIF(NULLIF(TRIM(COALESCE(scope_level,'')),''),'all'), scope_org_id
HAVING COUNT(*) > 1;
