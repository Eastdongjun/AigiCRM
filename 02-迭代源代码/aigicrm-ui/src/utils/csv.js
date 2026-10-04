// 前端 CSV 导出公共工具（E4 去重 2026-10-04）：CSV 转义 + BOM 防中文乱码 + Blob 触发下载。
// 原客户排行导出(useIncentiveStats.exportRank)与个人排行导出(PersonRankTableView.exportPersonRank)
// 两份逐行相同的实现合并至此，行为与导出内容保持不变（含 click→revoke→remove 的原时序）。
// 与 utils/download.js 职责不同：后者是 fetch 远程文件 blob 下载，本模块处理内存中的表格数据。

/** CSV 字段转义：含 引号/逗号/换行 时整体加引号，内部引号翻倍（RFC 4180） */
export const csvEscape = (v) => {
  const s = String(v ?? '')
  return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
}

/**
 * 导出 CSV 文件并触发浏览器下载
 * @param {Array<Array>} rows 二维数组（首行为表头），每个单元格经 csvEscape 转义
 * @param {string} filename 下载文件名（含 .csv 扩展名）
 */
export function exportCsv(rows, filename) {
  // \uFEFF 为 BOM，Excel 打开含中文的 CSV 不乱码
  const csv = '﻿' + rows.map(row => row.map(csvEscape).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  URL.revokeObjectURL(url)
  a.remove()
}
