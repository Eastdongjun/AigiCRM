# FILE-FABU-FOLDER-003 — pageFiles 对 fabushi 跳过夹过滤，点夹列表恒空（P1 · QC 测试服复测发现 2026-11-20）

- 现象：文件管理法布施 tab 点「书籍/分享心得」→ 列表 0 行（角标 fileCount=null 顺带同源疑点）
- 证据：DB 夹内 5/12 文件在位；API `?scope=fabushi&folderId=18` total=0；不带 folderId total=17
- 根因：FileService.pageFiles ~303 行注释"fabushi…且无文件夹，同样跳过夹树逻辑"——分夹批次只改了 getFolderTree，列表过滤的旧跳过未删（**跨层漏改第三例**：H5 路由→switchTab→本例）
- 修法：fabushi scope 的 folderId 过滤放行（两系统夹全员可见，正常走可见集判定）
- 验收：点夹列表=夹内文件数；角标计数与列表一致
- 状态：CLOSED 2026-11-20：e5d5c396 放行 folderId 过滤 + 443bef47 角标回填/死参清理；API+浏览器双证（夹19=2/夹20=4，角标同源）（与 002 文案批合并上生产）
