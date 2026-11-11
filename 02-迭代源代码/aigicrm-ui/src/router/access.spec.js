import { describe, expect, it } from 'vitest'
import { firstAccessiblePath } from './access'

const routes = [{
  path: '/',
  children: [
    { path: 'dashboard', meta: { permission: 'statistics:view' } },
    { path: 'customer', meta: { permission: 'customer:view' } }
  ]
}]

describe('firstAccessiblePath', () => {
  it('prefers the first permitted route', () => {
    expect(firstAccessiblePath(routes, { hasPermission: permission => permission === 'statistics:view' }))
      .toBe('/dashboard')
  })

  it('falls through to another permitted module', () => {
    expect(firstAccessiblePath(routes, { hasPermission: permission => permission === 'customer:view' }))
      .toBe('/customer')
  })

  it('returns 403 when no business permission is available', () => {
    expect(firstAccessiblePath(routes, { hasPermission: () => false })).toBe('/403')
  })
})
