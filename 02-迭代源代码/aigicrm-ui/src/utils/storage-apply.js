// 存储扩容申请 · 纯逻辑唯一来源（D1-A 2026-10-18）
//
// 涉及三处展示/配置口径，集中在此避免页面各自换算：
//   1. 系统设置：file.storage.max.quota 以 bytes 存储，UI 以 GB 输入（1–1024，默认 100）；
//      file.storage.apply.users 以 JSON 数组存储，历史脏值需容错。
//   2. 文件页：/file/storage/apply-info 返回 {used,quota,maxQuota,canApply,reason,enabled,allowDecrease}，
//      quota/maxQuota 均为 bytes；弹窗按 GB 展示与提交。
//   3. 审批详情：storage 单 extraData 为 {quota,oldQuota,reason}（不传 oldQuota 由后端补），
//      详情抽屉需要格式化为「当前 → 申请」并识别是否还有额外键需要回退原始 JSON。

export const STORAGE_GB_BYTES = 1024 * 1024 * 1024

export const STORAGE_MAX_QUOTA_MIN_GB = 1
export const STORAGE_MAX_QUOTA_MAX_GB = 1024
export const STORAGE_MAX_QUOTA_DEFAULT_GB = 100

export const STORAGE_APPLY_ENABLED_KEY = 'file.storage.apply.enabled'
export const STORAGE_APPLY_USERS_KEY = 'file.storage.apply.users'
export const STORAGE_MAX_QUOTA_KEY = 'file.storage.max.quota'

/** storage 审批单 extraData 的已知键；全部命中时详情抽屉不再重复展示原始 JSON */
const STORAGE_EXTRA_KEYS = ['quota', 'oldQuota', 'reason']

/** bytes → GB（非法/非正数返回 0，供输入框与校验兜底） */
export function bytesToGb(bytes) {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return 0
  return n / STORAGE_GB_BYTES
}

/** GB → bytes（四舍五入为整数字节） */
export function gbToBytes(gb) {
  const n = Number(gb)
  if (!Number.isFinite(n) || n <= 0) return 0
  return Math.round(n * STORAGE_GB_BYTES)
}

/**
 * 单用户配额上限归一化：缺省/非法 → 100GB；越界收敛到 1–1024GB。
 * 返回值恒为 bytes 数字，供 configMap 序列化。
 */
export function normalizeStorageMaxQuotaBytes(raw) {
  const n = Number(raw)
  const fallback = STORAGE_MAX_QUOTA_DEFAULT_GB * STORAGE_GB_BYTES
  if (!Number.isFinite(n) || n <= 0) return fallback
  const gb = Math.round(n / STORAGE_GB_BYTES)
  const clamped = Math.min(STORAGE_MAX_QUOTA_MAX_GB, Math.max(STORAGE_MAX_QUOTA_MIN_GB, gb))
  return clamped * STORAGE_GB_BYTES
}

const toUserId = (value) => {
  if (typeof value === 'number') return Number.isInteger(value) && value > 0 ? value : null
  if (typeof value === 'string' && value.trim() !== '') {
    const n = Number(value)
    return Number.isInteger(n) && n > 0 ? n : null
  }
  return null
}

const normalizeUserIdList = (list) => {
  const seen = new Set()
  const ids = []
  for (const item of list) {
    const id = toUserId(item)
    if (id !== null && !seen.has(id)) {
      seen.add(id)
      ids.push(id)
    }
  }
  return ids
}

const parseUserIdText = (text) => normalizeUserIdList(String(text ?? '').split(','))

/**
 * 兼容历史脏值的「可申请人员」解析：
 *   · 正常 JSON 数组 → 数字化去重
 *   · JSON 单值 / 逗号串（如 "1,2"）→ 尽量还原
 *   · 非 JSON / 对象 / 空值 → 空数组
 */
export function normalizeStorageApplyUsers(raw) {
  if (Array.isArray(raw)) return normalizeUserIdList(raw)
  if (typeof raw === 'number') return normalizeUserIdList([raw])
  if (typeof raw === 'string') {
    const text = raw.trim()
    if (!text) return []
    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) return normalizeUserIdList(parsed)
      if (typeof parsed === 'number' || typeof parsed === 'string') return parseUserIdText(parsed)
      return []
    } catch (e) {
      return parseUserIdText(text)
    }
  }
  return []
}

const asPlainObject = (value) => (
  value && typeof value === 'object' && !Array.isArray(value) ? value : null
)

/** 解析 storage 审批单 extraData（容忍对象或 JSON 字符串；非法返回 null） */
export function parseStorageExtra(extraData) {
  if (typeof extraData === 'string') {
    const text = extraData.trim()
    if (!text) return null
    try {
      return asPlainObject(JSON.parse(text))
    } catch (e) {
      return null
    }
  }
  return asPlainObject(extraData)
}

/** bytes 配额展示：按 GB 输出并去掉多余 0（10GB → "10 GB"，1.5GB → "1.5 GB"） */
export function formatStorageQuota(bytes) {
  const gb = bytesToGb(bytes)
  if (!gb) return '—'
  const rounded = gb >= 10 ? Math.round(gb * 10) / 10 : Math.round(gb * 100) / 100
  return `${rounded} GB`
}

/** 审批详情「配额变更」文案：当前 10 GB → 申请 20 GB */
export function storageQuotaChangeText(extraData) {
  const data = parseStorageExtra(extraData)
  if (!data) return '—'
  return `当前 ${formatStorageQuota(data.oldQuota)} → 申请 ${formatStorageQuota(data.quota)}`
}

/** 审批详情「申请理由」文案（空值统一 '—'） */
export function storageApplyReasonText(extraData) {
  const data = parseStorageExtra(extraData)
  if (!data) return '—'
  const text = data.reason === null || data.reason === undefined ? '' : String(data.reason).trim()
  return text || '—'
}

/**
 * storage 单 extraData 是否只含已知键（quota/oldQuota/reason）：
 * 是 → 详情抽屉已用描述项展示配额与理由，不再重复渲染原始 JSON；
 * 否 → 保留原始 JSON 以暴露未知附加信息。
 */
export function isStorageExtraOnly(extraData) {
  const data = parseStorageExtra(extraData)
  if (!data) return false
  return Object.keys(data).every(key => STORAGE_EXTRA_KEYS.includes(key))
}
