-- 【2026-11-09 ACL 词表归一】biz_folder_acl.perms 旧长词表 → 现行短词表
-- 背景: 2026-08 写入的 7 行用旧词表(file:upload,folder:create,...), 而现行判定
-- (FolderPermissionService.aclGranted 按 P_UPLOAD="upload" 等短名逐 token 精确比对)
-- 与写入校验(FolderAclService.ALLOWED 只收短名)从不认识长词表——生产全部 ACL 授权行
-- 自写入起即失效, 仅 admin/夹主/上级链/公共兜底在起作用。
-- 映射: file:view→view / file:upload→upload / file:edit→edit / file:delete→delete /
--       file:share→share / file:download→download / folder:rename→edit /
--       folder:delete→delete / folder:create(子夹创建由父夹 upload 权判定, 无独立 token)→剔除
-- 重复 token(如 rename+edit 均映射 edit)无害: 判定只做逐 token 等值比对, 下次保存由
-- 前端 checkbox 重写为干净列表。幂等可重跑(短名不含 'file:'/'folder:' 前缀)。
UPDATE biz_folder_acl SET perms =
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
    REPLACE(REPLACE(REPLACE(REPLACE(
        perms,
        'file:view', 'view'),
        'file:upload', 'upload'),
        'file:edit', 'edit'),
        'file:delete', 'delete'),
        'file:share', 'share'),
        'file:download', 'download'),
        'folder:rename', 'edit'),
        'folder:delete', 'delete'),
        ',folder:create,', ','),
        ',folder:create', ''),
        'folder:create,', ''),
        'folder:create', '');
