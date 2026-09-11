import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { getToken, clearToken, isTokenValid } from './token'

const b64url = (s) => btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
const makeToken = (payload) =>
  `${b64url(JSON.stringify({ alg: 'none' }))}.${b64url(JSON.stringify(payload))}.sig`

describe('token utils', () => {
  beforeEach(() => localStorage.clear())

  it('getToken/clearToken 基本往返', () => {
    localStorage.setItem('token', 'abc')
    expect(getToken()).toBe('abc')
    clearToken()
    expect(getToken()).toBeNull()
  })

  it('空 token 视为无效', () => {
    expect(isTokenValid()).toBe(false)
  })

  it('未过期 token 有效', () => {
    localStorage.setItem('token', makeToken({ exp: Math.floor(Date.now() / 1000) + 3600 }))
    expect(isTokenValid()).toBe(true)
  })

  it('已过期 token 无效', () => {
    localStorage.setItem('token', makeToken({ exp: Math.floor(Date.now() / 1000) - 60 }))
    expect(isTokenValid()).toBe(false)
  })

  it('无法解析的 token 无效', () => {
    localStorage.setItem('token', 'not-a-jwt')
    expect(isTokenValid()).toBe(false)
  })
})

// ---- 下载专用短时 token（F2 2026-08-09；空 token 403 修复 2026-08-14）----
// 每个用例 resetModules 拿独立模块实例，避免模块级状态（dlToken/dlFailedAt/定时器）串扰
describe('download token 拉取与失败重试', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.resetModules()
    vi.useFakeTimers()
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    localStorage.setItem('token', makeToken({ exp: Math.floor(Date.now() / 1000) + 3600 }))
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  const loadToken = () => import('./token')

  it('成功获取后 getDownloadToken 返回 token，TTL 内不重复请求', async () => {
    fetchMock.mockResolvedValue({ json: () => Promise.resolve({ data: { token: 'T1' } }) })
    const { refreshDownloadToken, getDownloadToken } = await loadToken()
    await refreshDownloadToken()
    expect(getDownloadToken()).toBe('T1')
    await refreshDownloadToken() // TTL 内缓存命中
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('fetch 未返回前 getDownloadToken 为 null（缩略图不拼空 token 的前提）', async () => {
    fetchMock.mockResolvedValue({ json: () => Promise.resolve({ data: { token: 'T1' } }) })
    const { getDownloadToken } = await loadToken()
    expect(getDownloadToken()).toBeNull() // 同步读：尚未就绪
  })

  it('失败后 30s 内防抖：重复调用不再发请求', async () => {
    fetchMock.mockRejectedValue(new Error('network'))
    const { refreshDownloadToken, getDownloadToken } = await loadToken()
    await refreshDownloadToken()
    expect(getDownloadToken()).toBeNull()
    getDownloadToken() // 失败后立即再取（渲染链重跑场景）
    getDownloadToken()
    expect(fetchMock).toHaveBeenCalledTimes(1) // 防抖窗口内不重复请求
  })

  it('失败后 30s 自动重试成功，token 就绪', async () => {
    fetchMock.mockRejectedValueOnce(new Error('boom'))
    fetchMock.mockResolvedValueOnce({ json: () => Promise.resolve({ data: { token: 'T2' } }) })
    const { getDownloadToken } = await loadToken()
    getDownloadToken() // 触发首次拉取（失败）
    await vi.advanceTimersByTimeAsync(30 * 1000) // 30s 后自动重试
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(getDownloadToken()).toBe('T2')
  })

  it('TTL 过期后再次调用重新拉取', async () => {
    fetchMock.mockResolvedValue({ json: () => Promise.resolve({ data: { token: 'T1' } }) })
    const { refreshDownloadToken, getDownloadToken } = await loadToken()
    await refreshDownloadToken()
    expect(getDownloadToken()).toBe('T1')
    await vi.advanceTimersByTimeAsync(51 * 60 * 1000) // 50min TTL 过期
    fetchMock.mockResolvedValueOnce({ json: () => Promise.resolve({ data: { token: 'T3' } }) })
    await refreshDownloadToken()
    expect(getDownloadToken()).toBe('T3')
  })
})
