// 展示格式化工具（唯一来源，2026-10-04）
//
// formatSize 此前在 useFilePage 与 useUserList 各内联一份且口径已漂移
// （空值 '—' vs '0'、有无 B 档、小数位与单位空格不一致），现统一采用更合理的文件页口径：
//   · 空值（0/null/undefined）→ '—'："无数据"语义，避免把"没数据"误读成"0 字节"
//   · <1KB 原样字节（如 '512B'）：不再经 KB 四舍五入丢失小文件信息（旧用户页会显示 '0KB'）
//   · KB/MB/GB 保留 1 位小数且单位前带空格（如 '1.5 MB'）：比取整更精确
// 人员管理页（UserTable 存储用量）随本口径展示，属本批次钉明的新契约。

/** 文件大小展示：空值返回 '—'；B 档原样，KB/MB/GB 保留 1 位小数（单位前带空格） */
export function formatSize(bytes) {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
}
