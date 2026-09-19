// 激励统计 → 明细下钻的纯逻辑回归测试（2026-09-13）
// 需求背景：激励统计表格的行原先点不进去，需要下钻到该细分的明细子页面。
import { describe, it, expect } from 'vitest'
import {
  DETAIL_ROUTE_NAME, SLOT_FIELDS, buildDetailLocation, filterRecordsByCategory, slotFiles, sumScore, slotCounts,
} from './incentive-detail'

const records = [
  { id: 1, categoryId: 30, score: 2, filePreIds: [11, 12], filePreId: '[11,12]', filePreName: '方案A.pdf, 方案B.pdf', fileTrackIds: [21], fileTrackName: '跟踪表.pdf' },
  { id: 2, categoryId: 30, score: 3, filePreIds: [], filePreId: '[13]', filePreName: '单个附件.pdf' },
  { id: 3, categoryId: 31, score: 1, filePreIds: [14], filePreId: '[14]', filePreName: '' },
  { id: 4, categoryId: '30', score: null },
]

describe('激励明细下钻逻辑', () => {
  it('按细分（categoryId）过滤记录，字符串/数字 id 都能匹配', () => {
    expect(filterRecordsByCategory(records, 30).map((r) => r.id)).toEqual([1, 2, 4])
    expect(filterRecordsByCategory(records, '30').map((r) => r.id)).toEqual([1, 2, 4])
    expect(filterRecordsByCategory(records, 31).map((r) => r.id)).toEqual([3])
    expect(filterRecordsByCategory(records, 99)).toEqual([])
    expect(filterRecordsByCategory(null, 30)).toEqual([])
  })

  it('明细路由：childId 走 path，其余走 query，空值不产生脏参数', () => {
    const location = buildDetailLocation({
      childId: 30, parentId: 4, parentName: '产品试点', childName: '项目方案', score: 2, period: '2026全年',
    })
    expect(location.name).toBe(DETAIL_ROUTE_NAME)
    expect(location.params).toEqual({ childId: '30' })
    expect(location.query).toEqual({
      parentId: '4', parentName: '产品试点', childName: '项目方案', score: '2', period: '2026全年',
    })

    const bare = buildDetailLocation({ childId: 30 })
    expect(bare.query.parentName).toBeUndefined()
    expect(bare.query.score).toBeUndefined()
    expect(bare.query.period).toBeUndefined()
  })

  it('槽位附件：ids 与逗号分隔名按序配对，缺名给占位名，单值字段兜底', () => {
    expect(slotFiles(records[0], SLOT_FIELDS[0])).toEqual([
      { id: 11, name: '方案A.pdf' }, { id: 12, name: '方案B.pdf' },
    ])
    // filePreIds 为空时回退到单值 filePreId
    expect(slotFiles(records[1], SLOT_FIELDS[0])).toEqual([{ id: 13, name: '单个附件.pdf' }])
    // 只有 id 没有名字 → 占位名
    expect(slotFiles(records[2], SLOT_FIELDS[0])).toEqual([{ id: 14, name: '附件1' }])
    // 完全没有该槽位 → 空数组（表格显示 —）
    expect(slotFiles(records[1], SLOT_FIELDS[1])).toEqual([])
    expect(slotFiles(records[0], SLOT_FIELDS[3])).toEqual([{ id: 21, name: '跟踪表.pdf' }])
    expect(slotFiles(null, SLOT_FIELDS[0])).toEqual([])
  })

  it('合计与各槽位计数用于明细页表头', () => {
    const subset = filterRecordsByCategory(records, 30)
    expect(sumScore(subset)).toBe(5)          // 2 + 3 + (null→0)
    expect(sumScore([])).toBe(0)
    const counts = slotCounts(subset)
    expect(counts.pre).toBe(3)                // 两条记录共 3 个安装前附件
    expect(counts.mid).toBe(0)
  })
})
