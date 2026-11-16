import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { clampPage, resolveResumePage, useFabushiPdfReader } from './useFabushiPdfReader'

// P3 PC/MP 共用阅读器：页码边界与续读定位是「翻页回传/重开续读」的正确性根，
// 单独锁死，避免后续改渲染逻辑时回归这两条口径。
describe('clampPage', () => {
  it('区间内原样返回', () => {
    expect(clampPage(5, 29)).toBe(5)
  })
  it('超过总页数夹到末页', () => {
    expect(clampPage(99, 29)).toBe(29)
  })
  it('小于 1 / 非数字回第 1 页', () => {
    expect(clampPage(0, 29)).toBe(1)
    expect(clampPage(-3, 29)).toBe(1)
    expect(clampPage('abc', 29)).toBe(1)
    expect(clampPage(null, 29)).toBe(1)
  })
  it('总页数未知时只保底不小于 1', () => {
    expect(clampPage(7, 0)).toBe(7)
    expect(clampPage(7, null)).toBe(7)
    expect(clampPage(0, 0)).toBe(1)
  })
})

describe('resolveResumePage', () => {
  it('进度页码在范围内直接续读', () => {
    expect(resolveResumePage({ lastPage: 12 }, 29)).toBe(12)
  })
  it('进度页码超过真实页数（换了挂载文件）夹到末页', () => {
    expect(resolveResumePage({ lastPage: 99 }, 29)).toBe(29)
  })
  it('无进度/异常进度从第 1 页开始', () => {
    expect(resolveResumePage(null, 29)).toBe(1)
    expect(resolveResumePage({}, 29)).toBe(1)
    expect(resolveResumePage({ lastPage: 0 }, 29)).toBe(1)
    expect(resolveResumePage({ lastPage: 'NaN' }, 29)).toBe(1)
  })
})

describe('useFabushiPdfReader 初始态', () => {
  it('挂载后处于 loading、页码 1、总页数 0，未加载不触碰网络', () => {
    const api = mount(defineComponent({
      setup() {
        const reader = useFabushiPdfReader({
          bookId: ref('1'),
          pdfFileId: ref('2'),
          token: ref('t'),
        })
        // 断言初始态后立即卸载，不触发 load()（PDF.js 动态导入不进入本用例）
        expect(reader.state.value).toBe('loading')
        expect(reader.currentPage.value).toBe(1)
        expect(reader.totalPages.value).toBe(0)
        return () => h('div')
      },
    }))
    api.unmount()
  })
})
