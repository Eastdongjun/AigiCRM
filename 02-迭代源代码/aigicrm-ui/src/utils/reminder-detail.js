// 提醒管理 → 提醒详情下钻的纯逻辑（2026-09-13）
//
// 需求：提醒管理列表（到期提醒 / 分享提醒 / 开票提醒）每一行都要能点进去看详情页。
// 列表行来自三个不同数据源（expiring-list / reminder-list share_* / reminder-list invoice_*），
// 字段口径不统一：这里统一"如何取提醒 ID、如何定位详情路由、类型与状态怎么说"。

import { CUSTOMER_STATUS_MAP } from './customer-status'

export const REMINDER_DETAIL_ROUTE_NAME = 'ReminderDetail'

/** 无提醒实体（今日到期提醒尚未物化）时的路由占位 ID，页面据此降级为"仅客户信息" */
export const NO_REMINDER_ID = 'none'

/** 提醒类型中文标签（后端 biz_reminder.reminder_type 取值） */
export const REMINDER_TYPE_LABELS = {
  expiring: '到期提醒',
  invoice: '开票提醒',
  invoice_overdue: '开票超期提醒',
  invoice_never: '待首次开票提醒',
  share_to: '客户分享给你',
  share_by: '你分享了客户',
  share_owner: '你的客户被分享'
}

/** 列表行"类型"列的分组标签（对应页面左侧三色标签） */
export const REMINDER_GROUP_LABELS = {
  expiring: '到期',
  invoice: '开票',
  share: '分享'
}

const PROCESSED_STATUSES = new Set(['processed', 'read', 'done', 'followed'])

export function reminderTypeLabel(type) {
  return REMINDER_TYPE_LABELS[type] || (type ? String(type) : '提醒')
}

export function reminderStatusText(status) {
  if (!status) return '—'
  return PROCESSED_STATUSES.has(status) ? '已处理' : '待处理'
}

export function reminderStatusTagType(status) {
  if (!status) return 'info'
  return PROCESSED_STATUSES.has(status) ? 'success' : 'warning'
}

/**
 * 取行对应的提醒 ID：分享/开票提醒行只有 reminderId；到期提醒行（客户维度）用 id。
 * 两者都没有时返回 null（详情页降级为只读客户信息）。
 */
export function rowReminderId(row) {
  const id = row?.reminderId ?? row?.id
  return id === null || id === undefined || id === '' ? null : String(id)
}

/** 列表行的提醒类型：优先 reminderType，退回页面内部 _type（invoice/share/expiring） */
export function rowReminderType(row) {
  if (row?.reminderType) return row.reminderType
  if (row?._type === 'invoice' && row?._invoiceType) return row._invoiceType
  return row?._type || null
}

/** 详情页路由地址：ID 走 path，客户/类型走 query，刷新或直接分享仍可打开 */
export function buildReminderDetailLocation(row) {
  const reminderId = rowReminderId(row)
  return {
    name: REMINDER_DETAIL_ROUTE_NAME,
    params: { id: reminderId || NO_REMINDER_ID },
    query: {
      customerId: row?.customerId == null ? undefined : String(row.customerId),
      type: rowReminderType(row) || undefined
    }
  }
}

/** 到期剩余天数（客户 expireDate 与今天的天数差；无日期返回 null） */
export function daysUntil(expireDate, today = new Date()) {
  if (!expireDate) return null
  const target = new Date(String(expireDate).replace(/-/g, '/'))
  if (Number.isNaN(target.getTime())) return null
  const base = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const end = new Date(target.getFullYear(), target.getMonth(), target.getDate())
  return Math.round((end - base) / 86400000)
}

/** 到期紧急度文案（与提醒列表 getStatusText 同口径） */
export function remainingDaysText(days) {
  if (days === null || days === undefined) return '—'
  if (days < 0) return `已过期 ${Math.abs(days)} 天`
  if (days <= 7) return '即将逾期'
  if (days <= 30) return '临期提醒'
  if (days <= 90) return '关注'
  return '正常'
}

/** 到期紧急度标签色（与提醒列表 getStatusType 同口径） */
export function remainingDaysTagType(days) {
  if (days === null || days === undefined) return 'info'
  if (days < 0 || days <= 7) return 'danger'
  if (days <= 30) return 'warning'
  if (days <= 90) return 'info'
  return 'success'
}

/** 客户状态中文标签（复用客户模块状态字典） */
export function customerStatusLabel(status) {
  return CUSTOMER_STATUS_MAP[status]?.text || (status ? String(status) : '—')
}

/** 省市区拼接（详情页"地区"一行） */
export function customerRegion(customer) {
  if (!customer) return null
  const region = [customer.province, customer.city, customer.district].filter(Boolean).join('')
  return region || customer.region || null
}

/**
 * 统一客户摘要字段：详情接口 /reminder/{id} 的 customer 块与 /customer/{id} 原始实体字段名有差异
 * （region vs province/city/district），这里归一到同一形状，页面只认一份。
 */
export function normalizeCustomerBrief(raw) {
  if (!raw) return null
  return {
    id: raw.id ?? null,
    customerCode: raw.customerCode || null,
    customerName: raw.customerName || null,
    status: raw.status || null,
    industry: raw.industry || null,
    region: customerRegion(raw),
    expireDate: raw.expireDate || null,
    lastInvoiceDate: raw.lastInvoiceDate || null,
    ownerId: raw.ownerId ?? null,
    ownerName: raw.ownerName || null
  }
}

/**
 * 提醒"时间口径"文案：
 * - 到期提醒(expiring)：按客户 expireDate 算剩余/过期天数（后端 overdueDays 对到期提醒即剩余天数）
 * - 开票超期(invoice_overdue)：按 overdueDays 算已超期未开票天数
 * - 开票待首次(invoice_never) / 分享类：无天数概念
 */
export function reminderTimingText(reminder = {}, customer = null) {
  const type = reminder.reminderType
  if (type === 'invoice_overdue') {
    return `已超期 ${reminder.overdueDays ?? '—'} 天未开票`
  }
  if (type === 'invoice_never') return '待首次开票'
  if (type && String(type).startsWith('share')) return '客户分享提醒'
  const days = daysUntil(customer?.expireDate)
  if (days === null) {
    return reminder.overdueDays == null ? '—' : `剩余 ${reminder.overdueDays} 天到期`
  }
  return days < 0 ? `已过期 ${Math.abs(days)} 天` : `剩余 ${days} 天到期`
}
