import { describe, expect, it } from 'vitest'
import { formatWan, formatInt } from './format'

// 【UI 整改 2026-10-08】金额空值兜底 + 千分位统一口径（ui-score-report P0-1）
describe('formatWan 金额(万)格式化', () => {
  it('null/undefined/空串返回占位符 —（不渲染数字，单位由调用方随值渲染）', () => {
    expect(formatWan(null)).toBe('—')
    expect(formatWan(undefined)).toBe('—')
    expect(formatWan('')).toBe('—')
  })

  it('0 是有效金额，格式化为 0.00 而不是占位符', () => {
    expect(formatWan(0)).toBe('0.00')
  })

  it('数值千分位 + 固定 2 位小数', () => {
    expect(formatWan(1234567.8)).toBe('1,234,567.80')
    expect(formatWan(88)).toBe('88.00')
    expect(formatWan(0.5)).toBe('0.50')
  })

  it('数字字符串按数值格式化', () => {
    expect(formatWan('358')).toBe('358.00')
  })

  it('非数值字符串原样返回（不吞脏数据）', () => {
    expect(formatWan('abc')).toBe('abc')
  })
})

// 【UI 整改 2026-10-09·B6 三轮】统计计数千分位唯一口径（统计页年累计/月均接入）
describe('formatInt 统计数格式化', () => {
  it('空值返回占位符', () => {
    expect(formatInt(null)).toBe('—')
    expect(formatInt(undefined)).toBe('—')
    expect(formatInt('')).toBe('—')
  })

  it('整数千分位分组', () => {
    expect(formatInt(1041)).toBe('1,041')
    expect(formatInt(1234567)).toBe('1,234,567')
    expect(formatInt(0)).toBe('0')
  })

  it('小数保留 1 位并千分位（月均场景）', () => {
    expect(formatInt(104.1)).toBe('104.1')
    expect(formatInt(1234.56)).toBe('1,234.6')
  })

  it('数字字符串按数值格式化', () => {
    expect(formatInt('274')).toBe('274')
  })
})
