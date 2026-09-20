import { describe, expect, it } from 'vitest'
import { UNKNOWN_MONTH_KEY, groupRecordsByMonth, monthKeyOf, monthLabelOf, relativeDay, filterRecordsByKeyword } from './time-label'

const now = new Date('2026-09-13T10:00:00')

describe('relativeDay', () => {
  it('当天/昨天给出固定文案', () => {
    expect(relativeDay('2026-09-13 08:30', now)).toBe('今天')
    expect(relativeDay('2026-09-12 23:59', now)).toBe('昨天')
  })

  it('30 天内给出「N 天前」', () => {
    expect(relativeDay('2026-09-11 09:00', now)).toBe('2 天前')
    expect(relativeDay('2026-09-01 09:00', now)).toBe('12 天前')
    expect(relativeDay('2026-08-14 09:00', now)).toBe('30 天前')
  })

  it('超过 30 天返回空串（页面只显示原日期）', () => {
    expect(relativeDay('2026-08-13 09:00', now)).toBe('')
    expect(relativeDay('2026-01-01 09:00', now)).toBe('')
  })

  it('未来日期按「今天」处理，脏值安全', () => {
    expect(relativeDay('2026-09-20 09:00', now)).toBe('今天')
    expect(relativeDay('', now)).toBe('')
    expect(relativeDay(null, now)).toBe('')
    expect(relativeDay('not-a-date', now)).toBe('')
  })

  it('兼容 ISO 串（后端 LocalDateTime 序列化）', () => {
    expect(relativeDay('2026-09-13T07:20:00', now)).toBe('今天')
  })
})

describe('月份分组（时间轴）', () => {
  it('monthKeyOf / monthLabelOf 口径', () => {
    expect(monthKeyOf('2026-09-13 10:00:00')).toBe('2026-09')
    expect(monthKeyOf('2026-01-05T08:00:00')).toBe('2026-01')
    expect(monthKeyOf('bad')).toBe(UNKNOWN_MONTH_KEY)
    expect(monthLabelOf('2026-09')).toBe('2026年9月')
    expect(monthLabelOf(UNKNOWN_MONTH_KEY)).toBe('时间待补')
    expect(monthLabelOf('')).toBe('时间待补')
  })

  it('按月倒序分组，组内也按时间倒序', () => {
    const groups = groupRecordsByMonth([
      { id: 1, createdAt: '2026-08-20 09:00:00' },
      { id: 2, createdAt: '2026-09-01 10:00:00' },
      { id: 3, createdAt: '2026-09-13 18:00:00' },
      { id: 4, createdAt: '2026-09-02 07:00:00' },
    ])
    expect(groups.map((g) => g.key)).toEqual(['2026-09', '2026-08'])
    expect(groups[0].count).toBe(3)
    expect(groups[0].items.map((r) => r.id)).toEqual([3, 4, 2])
    expect(groups[1].items.map((r) => r.id)).toEqual([1])
    expect(groups[0].label).toBe('2026年9月')
  })

  it('无有效时间的记录归入「时间待补」组且排最后', () => {
    const groups = groupRecordsByMonth([
      { id: 1, createdAt: null },
      { id: 2, createdAt: '2026-09-13 10:00:00' },
      { id: 3 },
    ])
    expect(groups.map((g) => g.key)).toEqual(['2026-09', UNKNOWN_MONTH_KEY])
    expect(groups[1].label).toBe('时间待补')
    expect(groups[1].items.map((r) => r.id)).toEqual([1, 3])
  })

  it('空/非数组安全返回空数组（不改动入参顺序）', () => {
    const input = [{ id: 1, createdAt: '2026-08-01' }, { id: 2, createdAt: '2026-09-01' }]
    const snapshot = input.map((r) => r.id)
    expect(groupRecordsByMonth([])).toEqual([])
    expect(groupRecordsByMonth(null)).toEqual([])
    groupRecordsByMonth(input)
    expect(input.map((r) => r.id)).toEqual(snapshot)
  })
})


/**
 * 【2026-09-20 详情工具条】激励查询详情时间轴新口径：
 * 1. groupRecordsByMonth(order): asc=按时间增量(早→晚) / desc=最新在前(默认, 原口径)
 * 2. filterRecordsByKeyword: 详情页内多字段检索(分类/产品/上传人/描述/备注/附件名)
 */
const toolbarRecs = [
  { id: 3, createdAt: '2026-09-02 10:00', categoryName: '交流拜访', userName: '王磊磊', remark: '现场沟通' },
  { id: 1, createdAt: '2026-08-01 09:00', categoryName: '产品试点', userName: '李帮文', preInstallQuestion: '泵体密封改造', fileName: '方案A.pdf' },
  { id: 2, createdAt: '2026-08-15 15:00', categoryName: '交流拜访', userName: '王磊磊', relatedProducts: '涂层类', filePreName: '现场照片.jpg,设备.png' },
]

describe('groupRecordsByMonth 方向(详情工具条)', () => {
  it('desc(默认): 组间与组内均最新在前——原口径不变', () => {
    const g = groupRecordsByMonth(toolbarRecs)
    expect(g.map((x) => x.key)).toEqual(['2026-09', '2026-08'])
    expect(g[1].items.map((x) => x.id)).toEqual([2, 1])
  })

  it('asc: 按时间增量从早到晚(组间+组内)', () => {
    const g = groupRecordsByMonth(toolbarRecs, 'asc')
    expect(g.map((x) => x.key)).toEqual(['2026-08', '2026-09'])
    expect(g[0].items.map((x) => x.id)).toEqual([1, 2])
  })
})

describe('filterRecordsByKeyword 详情检索', () => {
  it('空关键字返回全量', () => {
    expect(filterRecordsByKeyword(toolbarRecs, '  ')).toHaveLength(3)
  })

  it('命中分类/上传人/描述/备注/附件名(大小写不敏感)', () => {
    expect(filterRecordsByKeyword(toolbarRecs, '试点').map((r) => r.id)).toEqual([1])
    expect(filterRecordsByKeyword(toolbarRecs, '王磊磊').map((r) => r.id)).toEqual([3, 2])
    expect(filterRecordsByKeyword(toolbarRecs, '泵体').map((r) => r.id)).toEqual([1])
    expect(filterRecordsByKeyword(toolbarRecs, '现场').map((r) => r.id)).toEqual([3, 2])
    expect(filterRecordsByKeyword(toolbarRecs, '设备.png').map((r) => r.id)).toEqual([2])
    expect(filterRecordsByKeyword(toolbarRecs, 'PDF').map((r) => r.id)).toEqual([1])
  })

  it('无命中返回空数组；records 为空安全', () => {
    expect(filterRecordsByKeyword(toolbarRecs, '不存在')).toEqual([])
    expect(filterRecordsByKeyword(null, 'x')).toEqual([])
  })
})
