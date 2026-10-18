import { describe, expect, it } from 'vitest'
import {
  STORAGE_GB_BYTES,
  bytesToGb,
  formatStorageQuota,
  gbToBytes,
  isStorageExtraOnly,
  normalizeStorageApplyUsers,
  normalizeStorageMaxQuotaBytes,
  storageApplyReasonText,
  storageQuotaChangeText
} from './storage-apply'

describe('storage-apply', () => {
  it('converts GB/bytes and clamps the per-user quota limit into 1-1024GB', () => {
    expect(bytesToGb(STORAGE_GB_BYTES * 10)).toBe(10)
    expect(gbToBytes(1.5)).toBe(STORAGE_GB_BYTES * 1.5)
    expect(normalizeStorageMaxQuotaBytes(undefined)).toBe(100 * STORAGE_GB_BYTES)
    expect(normalizeStorageMaxQuotaBytes(0)).toBe(100 * STORAGE_GB_BYTES)
    expect(normalizeStorageMaxQuotaBytes(2048 * STORAGE_GB_BYTES)).toBe(1024 * STORAGE_GB_BYTES)
    expect(normalizeStorageMaxQuotaBytes(0.2 * STORAGE_GB_BYTES)).toBe(1 * STORAGE_GB_BYTES)
    expect(formatStorageQuota(10 * STORAGE_GB_BYTES)).toBe('10 GB')
    expect(formatStorageQuota(1.5 * STORAGE_GB_BYTES)).toBe('1.5 GB')
    expect(formatStorageQuota(null)).toBe('—')
  })

  it('tolerates dirty persisted apply-user values', () => {
    expect(normalizeStorageApplyUsers('[1,"2",2,"x",-1,0]')).toEqual([1, 2])
    expect(normalizeStorageApplyUsers('1,2,3')).toEqual([1, 2, 3])
    expect(normalizeStorageApplyUsers('not-json')).toEqual([])
    expect(normalizeStorageApplyUsers({ users: [1] })).toEqual([])
    expect(normalizeStorageApplyUsers([3, '3', null, ''])).toEqual([3])
  })

  it('parses storage approval extraData into quota change and reason', () => {
    const extra = JSON.stringify({
      quota: 20 * STORAGE_GB_BYTES,
      oldQuota: 10 * STORAGE_GB_BYTES,
      reason: '项目资料归档'
    })
    expect(storageQuotaChangeText(extra)).toBe('当前 10 GB → 申请 20 GB')
    expect(storageApplyReasonText(extra)).toBe('项目资料归档')
    expect(isStorageExtraOnly(extra)).toBe(true)
    expect(isStorageExtraOnly(JSON.stringify({ ...JSON.parse(extra), note: '附加信息' }))).toBe(false)
    expect(storageQuotaChangeText('{bad json')).toBe('—')
    expect(isStorageExtraOnly('{}')).toBe(true)
  })
})
