-- 法布施文件分夹(FILE-FABU-FOLDER-001 2026-11-19 用户拍板方案A: 真文件夹+PC/MP 双端)
-- 两个系统级文件夹: 「书籍」(sort 1)/「分享心得」(sort 2), folder_type='fabushi'(新类型,
-- 仅这两个系统夹使用; 服务端禁删/禁改名/禁建子夹)。归类口径=引用关系:
--   被 biz_fabushi_book.pdf_file_id 引用 → 书籍; 其余 scope=fabushi → 分享心得。
-- 上传路由(服务端定死): 挂书 PDF 通道→书籍; 帖子附件(MP 原生/H5 fabushi 票, 无 folderId)→分享心得;
--   H5 files 票带目标夹→按票内目标。attachPdf 校验 file.folder=书籍。
-- 回滚(逆写): DELETE 两系统夹行(folder_type='fabushi'); 回填可用
--   UPDATE biz_file SET folder_id=NULL WHERE file_scope='fabushi' 还原平铺(结构无损, 文件行不动)。
-- 对账(窗口三查):
--   ① SELECT COUNT(*) FROM biz_file_folder WHERE folder_type='fabushi';  -- 应=2
--   ② SELECT folder_id, COUNT(*) FROM biz_file WHERE file_scope='fabushi' AND deleted=0 GROUP BY folder_id;
--     -- 仅两系统夹 id 有值; 书籍夹行数=SELECT COUNT(*) FROM biz_fabushi_book WHERE pdf_file_id IS NOT NULL(去重后)
--   ③ SELECT COUNT(*) FROM biz_file WHERE file_scope='fabushi' AND deleted=0 AND folder_id IS NULL; -- 应=0
-- 幂等: 夹插入 NOT EXISTS(folder_type+folder_name); 回填仅 folder_id IS NULL 行, 重跑安全。

-- 1) 系统夹(书籍在前)
INSERT INTO biz_file_folder (folder_name, folder_type, parent_id, owner_id, sort_order, downloadable)
SELECT x.name, 'fabushi', NULL, NULL, x.sort, 1 FROM (
  SELECT '书籍' AS name, 1 AS sort UNION ALL
  SELECT '分享心得', 2
) x
WHERE NOT EXISTS (
  SELECT 1 FROM biz_file_folder f
  WHERE f.folder_type = 'fabushi' AND f.folder_name = x.name
);

-- 2) 存量回填(按引用关系; 仅未归类行)
UPDATE biz_file SET folder_id = (
  SELECT f.id FROM biz_file_folder f WHERE f.folder_type = 'fabushi' AND f.sort_order = 1
) WHERE file_scope = 'fabushi' AND folder_id IS NULL
  AND id IN (SELECT pdf_file_id FROM biz_fabushi_book WHERE pdf_file_id IS NOT NULL);

UPDATE biz_file SET folder_id = (
  SELECT f.id FROM biz_file_folder f WHERE f.folder_type = 'fabushi' AND f.sort_order = 2
) WHERE file_scope = 'fabushi' AND folder_id IS NULL;
