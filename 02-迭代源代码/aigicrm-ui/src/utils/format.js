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

// 【UI 整改 2026-10-08】金额展示此前无千分位约定且空值裸渲染（客户详情开票/回款金额列
// 历史导入行出现孤立"万"字，见 ui-score-report P0-1），现统一按本函数口径：
//   · null/undefined/'' → '—'（"无数据"语义；0 是有效金额，正常格式化为 '0.00'）
//   · 数值千分位 + 固定 2 位小数（如 1234567.8 → '1,234,567.80'），单位"万"由调用方随值渲染
/** 金额(万)展示：空值返回 '—'，数值千分位 + 2 位小数 */
export function formatWan(value) {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 【UI 整改 2026-10-09·B6 三轮】统计计数千分位唯一口径：此前 formatWan 仅 2 个文件引用，
// 统计页「年累计 1041」「月均 104.1」等汇总数裸渲染无千分位，现统一走本 formatter。
/** 统计数展示：整数千分位；小数(如月均 104.1)保留 1 位小数并千分位；空值返回 '—' */
export function formatInt(value) {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value)
  return n.toLocaleString('zh-CN', Number.isInteger(n)
    ? { maximumFractionDigits: 0 }
    : { maximumFractionDigits: 1 })
}

// 【UI 整改 2026-10-08·A2/C10 空值占位统一】全站表格空值唯一口径 '—'：
// 此前空白/'—'/'未设置' 三套并存（同一张客户表内两种、人员表内两种），现收口到本 formatter。
// 用法：文本类列 :formatter="dashIfEmpty"；带自定义 #default 插槽的列在插值处写 `{{ v || '—' }}`。
/** el-table-column formatter：null/undefined/空白串 → '—'，其余原样（0/false 等合法值不吞） */
export function dashIfEmpty(row, column, cellValue) {
  if (cellValue === null || cellValue === undefined) return '—'
  const s = String(cellValue)
  return s.trim() === '' ? '—' : cellValue
}
