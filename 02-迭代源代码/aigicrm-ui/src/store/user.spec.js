import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.mock('@/api/auth', () => ({ getUserInfo: vi.fn(), logout: vi.fn().mockResolvedValue({}) }))
vi.mock('@/api/reminder', () => ({ getPendingCount: vi.fn() }))
vi.mock('@/api/approval', () => ({ getApprovalStatistics: vi.fn() }))
vi.mock('@/api/system', () => ({ getPublicConfig: vi.fn() }))

import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from './user'
import { getUserInfo } from '@/api/auth'

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('hasPermission 基于权限集合判断', () => {
    const store = useUserStore()
    expect(store.hasPermission('customer:view')).toBe(false)
    store.permissions = new Set(['customer:view', 'customer:add'])
    expect(store.hasPermission('customer:view')).toBe(true)
    expect(store.hasPermission('customer:delete')).toBe(false)
  })

  it('fetchUserInfo 将后端权限数组转为 Set', async () => {
    getUserInfo.mockResolvedValue({ data: {
      userId: 1, username: 'admin', realName: '管理员', avatar: '',
      deptName: '总部', officeName: '',
      roles: ['admin'], roleName: '管理员', dataScope: 'dept',
      permissions: ['a:view', 'a:add'], menus: []
    } })
    const store = useUserStore()
    await store.fetchUserInfo()
    expect(store.username).toBe('admin')
    expect(store.dataScope).toBe('dept')
    expect(store.permissions).toBeInstanceOf(Set)
    expect(store.hasPermission('a:add')).toBe(true)
  })

  it('fetchUserInfo 同步当前集团上下文', async () => {
    getUserInfo.mockResolvedValue({ data: {
      userId: 2, username: 'multi-group', realName: '多集团用户', avatar: '',
      deptName: '总部', officeName: '', roles: [], roleName: '业务员', dataScope: 'self',
      permissions: [], menus: [], groupIds: [3, 7], currentGroupId: 7
    } })
    const store = useUserStore()
    await store.fetchUserInfo()
    expect(store.groupIds).toEqual([3, 7])
    expect(store.currentGroupId).toBe(7)
    expect(store.canSwitchGroup).toBe(true)
  })

  it('logout 重置状态并清除 token', () => {
    localStorage.setItem('token', 'abc')
    const store = useUserStore()
    store.username = 'admin'
    store.permissions = new Set(['x'])
    store.logout()
    expect(store.username).toBe('')
    expect(store.permissions.size).toBe(0)
    expect(localStorage.getItem('token')).toBeNull()
  })

  // 【B1】普通改密(layout submitPassword → userStore.logout)与退出登录统一走此清理
  it('logout 清除口令遗留键，保留"记住账号"键（B1）', () => {
    localStorage.setItem('token', 'abc')
    localStorage.setItem('aigicrm_remember_username', 'NO001')
    localStorage.setItem('aigicrm_remember_password', btoa(['legacy', '-enc'].join('')))
    const store = useUserStore()
    store.logout()
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('aigicrm_remember_password')).toBeNull()
    expect(localStorage.getItem('aigicrm_remember_username')).toBe('NO001')
  })
})
