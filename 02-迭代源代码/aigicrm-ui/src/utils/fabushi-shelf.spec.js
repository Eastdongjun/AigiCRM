import { describe, expect, it } from 'vitest'
import { filterShelf, progressPct, shelfStatusLabel, sortShelf } from './fabushi-shelf'

const row = (id, extra = {}) => ({
  id,
  bookId: id,
  title: `书${id}`,
  author: '作者',
  categoryCode: 'guoxue',
  status: 'reading',
  pinned: 0,
  sortNo: id,
  groupId: null,
  lastPage: 30,
  totalPages: 100,
  createdAt: `2026-11-0${id} 10:00:00`,
  updatedAt: `2026-11-2${id} 10:00:00`,
  ...extra
})

describe('fabushi shelf helpers', () => {
  it('按状态/关键词/分组筛选', () => {
    const rows = [row(1), row(2, { status: 'wish', categoryCode: 'xiaoshuo' }), row(3, { groupId: 9 })]
    expect(filterShelf(rows, { status: 'wish' }).map((x) => x.id)).toEqual([2])
    expect(filterShelf(rows, { keyword: '作者' })).toHaveLength(3)
    expect(filterShelf(rows, { group: { type: 'auto', categoryCode: 'xiaoshuo' } }).map((x) => x.id)).toEqual([2])
    expect(filterShelf(rows, { group: { type: 'custom', value: 9 } }).map((x) => x.id)).toEqual([3])
  })

  it('置顶永远优先，手动/加入/最近排序口径稳定', () => {
    const rows = [row(1, { pinned: 0, sortNo: 2, updatedAt: '2026-11-25 10:00:00' }),
      row(2, { pinned: 1, sortNo: 8, updatedAt: '2026-11-21 10:00:00' })]
    expect(sortShelf(rows, 'manual').map((x) => x.id)).toEqual([2, 1])
    expect(sortShelf(rows, 'added').map((x) => x.id)).toEqual([2, 1])
    expect(sortShelf(rows, 'recent').map((x) => x.id)).toEqual([2, 1])
    expect(sortShelf(rows, 'title').map((x) => x.id)).toEqual([1, 2])
  })

  it('进度百分比钳制 0-100，缺总页数回退书目页数', () => {
    expect(progressPct({ lastPage: 30, totalPages: 100 })).toBe(30)
    expect(progressPct({ lastPage: 130, totalPages: 100 })).toBe(100)
    expect(progressPct({ lastPage: 3, totalPages: null, pages: 10 })).toBe(30)
    expect(progressPct({ lastPage: 1, totalPages: null, pages: null })).toBe(0)
    expect(shelfStatusLabel('wish')).toBe('想读')
    expect(shelfStatusLabel('favorite')).toBe('收藏')
  })
})
