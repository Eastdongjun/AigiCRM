import { describe, expect, it } from 'vitest'
import { nextTick, ref } from 'vue'
import { usePagedList } from './usePagedList'

describe('usePagedList', () => {
  it('按页切片并计算总页数', () => {
    const source = ref(Array.from({ length: 12 }, (_, i) => i + 1))
    const p = usePagedList(source, { pageSize: 5 })
    expect(p.total.value).toBe(12)
    expect(p.totalPages.value).toBe(3)
    expect(p.paged.value).toEqual([1, 2, 3, 4, 5])
    p.current.value = 3
    expect(p.paged.value).toEqual([11, 12])
  })

  it('数据收缩时回收到最后一页', async () => {
    const source = ref(Array.from({ length: 12 }, (_, i) => i))
    const p = usePagedList(source, { pageSize: 5 })
    p.current.value = 3
    source.value = [1, 2]
    await nextTick()
    expect(p.current.value).toBe(1)
    expect(p.paged.value).toEqual([1, 2])
  })

  it('reset 回到第一页', () => {
    const p = usePagedList(ref([1, 2, 3]), { pageSize: 1 })
    p.current.value = 3
    p.reset()
    expect(p.current.value).toBe(1)
    expect(p.paged.value).toEqual([1])
  })
})
