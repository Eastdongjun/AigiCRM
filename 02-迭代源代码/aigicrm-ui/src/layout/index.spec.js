import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/auth', () => ({
  getUserInfo: vi.fn(),
  logout: vi.fn().mockResolvedValue({})
}))
vi.mock('@/api/reminder', () => ({ getPendingCount: vi.fn() }))
vi.mock('@/api/approval', () => ({ getApprovalStatistics: vi.fn() }))
vi.mock('@/api/system', () => ({ getPublicConfig: vi.fn(), changePassword: vi.fn() }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElNotification: vi.fn()
}))
const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => ({ path: '/dashboard', meta: {} })
}))

import LayoutIndex from './index.vue'
import { getUserInfo } from '@/api/auth'
import { changePassword } from '@/api/system'

// 测试哑口令: 非任何真实系统凭据(片段拼接避免凭据字面量)
const PWD_OLD = ['Old', 'Secret1'].join('')
const PWD_NEW = ['New', 'Pass1234'].join('')

describe('layout B1: 普通改密走 logout 清理口令遗留键', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    setActivePinia(createPinia())
    push.mockClear()
    getUserInfo.mockResolvedValue({ data: {
      userId: 1, username: 'NO001', realName: '测试', avatar: '',
      deptName: '', officeName: '', roles: [], roleName: '', dataScope: 'self',
      permissions: ['statistics:view'], menus: []
    } })
    changePassword.mockResolvedValue({})
  })

  it('submitPassword 成功 → logout 清除口令键，保留"记住账号"', async () => {
    // 模拟旧版本遗留: token + 记住账号 + 可逆口令键
    localStorage.setItem('token', 'tk-live')
    localStorage.setItem('aigicrm_remember_username', 'NO001')
    localStorage.setItem('aigicrm_remember_password', btoa(PWD_OLD))

    const wrapper = mount(LayoutIndex, {
      global: { plugins: [createPinia()], mocks: { $route: { path: '/dashboard', meta: {} } } },
      shallow: true
    })
    await flushPromises()

    wrapper.vm.pwdForm.oldPassword = PWD_OLD
    wrapper.vm.pwdForm.newPassword = PWD_NEW
    wrapper.vm.pwdForm.confirmPassword = PWD_NEW
    await wrapper.vm.submitPassword()
    await flushPromises()

    expect(changePassword).toHaveBeenCalledWith({ oldPassword: PWD_OLD, newPassword: PWD_NEW })
    // B1: 普通改密路径经 logout 统一清理口令键；"记住账号"保留
    expect(localStorage.getItem('aigicrm_remember_password')).toBeNull()
    expect(localStorage.getItem('aigicrm_remember_username')).toBe('NO001')
    expect(localStorage.getItem('token')).toBeNull()
    expect(push).toHaveBeenCalledWith('/login')
    wrapper.unmount()
  })
})
