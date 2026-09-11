import { describe, expect, it, vi } from 'vitest'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/utils/request', () => ({
  default: { get }
}))

import { getPublicConfig } from './system'

describe('system api', () => {
  it('公共配置请求静默失败，避免登录页误报网络错误', () => {
    getPublicConfig()

    expect(get).toHaveBeenCalledWith('/system/configs/public', { silent: true })
  })
})
