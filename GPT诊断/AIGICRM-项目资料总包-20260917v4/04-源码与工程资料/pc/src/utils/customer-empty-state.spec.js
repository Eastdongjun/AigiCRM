import { describe, expect, it } from 'vitest'
import { customerEmptyText } from './customer-empty-state'

describe('客户列表空状态', () => {
  it('数据范围为空时提示检查范围', () => {
    expect(customerEmptyText([])).toContain('数据范围')
  })

  it('有原始数据但筛选为空时提示筛选条件', () => {
    expect(customerEmptyText([{ id: 1 }])).toBe('暂无符合筛选条件的客户')
  })
})
