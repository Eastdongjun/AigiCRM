# FILE-RECYCLE-TASK-001 — 回收站夹清理任务每晚 SQL 报错（P2，QC 过夜复核发现 2026-11-20）

- 现象：FileRecycleCleanupTask 每晚 03:30 ERROR `bad SQL grammar [SELECT id FROM biz_file_folder WHERE deleted = 1 AND COALESCE(deleted_at, updated_at) < ?]`
- 根因：`biz_file_folder` 无 `updated_at` 列（写 SQL 时误用 biz_file 的列）——引入于 e8161848（文件 P2 体验批，11-14），非 11-19 法布施分夹窗（本窗未动 RecycleService/表列，git 坐实）
- 影响面：文件夹回收站保留期清理自 11-14 起未执行（失败方向安全=不误删，仅不清理）；文件(非夹)清理路径不受影响
- 修法：COALESCE 去掉 updated_at（沿 1ae9e8e9"保留期只认 deleted_at"口径），补单测钉住列存在性
- 状态：OPEN → 01-claude-20（并入下一批，不紧急）
