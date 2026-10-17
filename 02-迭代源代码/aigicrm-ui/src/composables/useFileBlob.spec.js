import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { useFileBlob, PREVIEW_SIZE_LIMIT } from './useFileBlob'

vi.mock('@/utils/token', () => ({ getToken: () => 'MAIN-JWT' }))

// 在真实组件 setup 内实例化组合式(它注册了 onBeforeUnmount)
function setup() {
  let api
  const wrapper = mount(defineComponent({
    setup() { api = useFileBlob(); return () => h('div') },
  }))
  return { api, wrapper }
}

function okResponse({ length = 10, body = 'hello', type = 'text/plain' } = {}) {
  return {
    ok: true,
    status: 200,
    headers: new Headers({ 'content-length': String(length) }),
    blob: async () => new Blob([body], { type }),
    arrayBuffer: async () => new TextEncoder().encode(body).buffer,
    text: async () => body,
  }
}

describe('useFileBlob', () => {
  let realFetch
  beforeEach(() => { realFetch = globalThis.fetch })
  afterEach(() => { globalThis.fetch = realFetch; vi.restoreAllMocks() })

  it('带主 JWT 的 Authorization 头发同源请求，并按 expect 返回类型', async () => {
    const fetchMock = vi.fn(async () => okResponse())
    globalThis.fetch = fetchMock
    const { api, wrapper } = setup()
    const text = await api.load('/api/file/download/1?token=dl', { expect: 'text' })
    expect(text).toBe('hello')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [, opts] = fetchMock.mock.calls[0]
    expect(opts.headers.Authorization).toBe('Bearer MAIN-JWT')
    expect(api.loading.value).toBe(false)
    expect(api.error.value).toBe('')
    wrapper.unmount()
  })

  it('sizeHint 超过阈值直接判 tooLarge，不发请求', async () => {
    const fetchMock = vi.fn()
    globalThis.fetch = fetchMock
    const { api, wrapper } = setup()
    const r = await api.load('/api/file/download/1', { sizeHint: PREVIEW_SIZE_LIMIT + 1 })
    expect(r).toBeNull()
    expect(api.tooLarge.value).toBe(true)
    expect(fetchMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('响应 content-length 超过阈值判 tooLarge，不读取 body', async () => {
    const resp = okResponse({ length: PREVIEW_SIZE_LIMIT + 1 })
    resp.blob = vi.fn()
    globalThis.fetch = vi.fn(async () => resp)
    const { api, wrapper } = setup()
    const r = await api.load('/api/file/download/1')
    expect(r).toBeNull()
    expect(api.tooLarge.value).toBe(true)
    expect(resp.blob).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('非 2xx 映射为中文错误文案(403=没有查看权限, 404=文件不存在)', async () => {
    globalThis.fetch = vi.fn(async () => ({ ok: false, status: 403, headers: new Headers() }))
    const { api, wrapper } = setup()
    expect(await api.load('/x')).toBeNull()
    expect(api.error.value).toBe('没有查看权限')
    globalThis.fetch = vi.fn(async () => ({ ok: false, status: 404, headers: new Headers() }))
    expect(await api.load('/x')).toBeNull()
    expect(api.error.value).toBe('文件不存在或已被删除')
    wrapper.unmount()
  })

  it('空 url 直接报错不发请求', async () => {
    const fetchMock = vi.fn()
    globalThis.fetch = fetchMock
    const { api, wrapper } = setup()
    expect(await api.load('')).toBeNull()
    expect(api.error.value).toBe('文件地址缺失')
    expect(fetchMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('新请求中止旧请求：旧请求的 AbortError 不写 error、不复位新请求的 loading', async () => {
    let firstSignal
    const fetchMock = vi.fn()
      .mockImplementationOnce((url, opts) => new Promise((_, reject) => {
        firstSignal = opts.signal
        opts.signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })))
      }))
      .mockImplementationOnce(async () => okResponse({ body: 'second' }))
    globalThis.fetch = fetchMock
    const { api, wrapper } = setup()
    const p1 = api.load('/first', { expect: 'text' })
    const p2 = api.load('/second', { expect: 'text' })
    const [r1, r2] = await Promise.all([p1, p2])
    expect(firstSignal.aborted).toBe(true)
    expect(r1).toBeNull()
    expect(r2).toBe('second')
    expect(api.error.value).toBe('')
    expect(api.loading.value).toBe(false)
    wrapper.unmount()
  })
})
