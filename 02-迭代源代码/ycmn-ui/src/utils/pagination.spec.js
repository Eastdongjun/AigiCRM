import { describe, it, expect } from 'vitest'
import { paginate, totalOf } from './pagination'

const data = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

describe('paginate 客户端分页切片', () => {
  it('第 1 页返回前 pageSize 条', () => {
    expect(paginate(data, 1, 3)).toEqual([1, 2, 3])
  })

  it('第 2 页从 (page-1)*size 开始', () => {
    expect(paginate(data, 2, 3)).toEqual([4, 5, 6])
  })

  it('末页不足 pageSize 时返回剩余部分', () => {
    expect(paginate(data, 4, 3)).toEqual([10])
  })

  it('page 越界（超过总页数）返回空数组', () => {
    expect(paginate(data, 99, 3)).toEqual([])
  })

  it('page 为 0 或负数收敛到第 1 页', () => {
    expect(paginate(data, 0, 3)).toEqual([1, 2, 3])
    expect(paginate(data, -5, 3)).toEqual([1, 2, 3])
  })

  it('pageSize <= 0 收敛为 1', () => {
    expect(paginate(data, 2, 0)).toEqual([2])
    expect(paginate(data, 2, -1)).toEqual([2])
  })

  it('空数组 / 非数组安全返回 []', () => {
    expect(paginate([], 1, 3)).toEqual([])
    expect(paginate(null, 1, 3)).toEqual([])
    expect(paginate(undefined, 1, 3)).toEqual([])
  })

  it('page/pageSize 为字符串数字也能工作', () => {
    expect(paginate(data, '1', '4')).toEqual([1, 2, 3, 4])
  })
})

describe('totalOf 客户端总数', () => {
  it('返回数组长度', () => {
    expect(totalOf(data)).toBe(10)
  })
  it('非数组返回 0', () => {
    expect(totalOf(null)).toBe(0)
    expect(totalOf(undefined)).toBe(0)
  })
})
