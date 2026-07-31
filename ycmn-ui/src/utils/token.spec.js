import { describe, it, expect, beforeEach } from 'vitest'
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
