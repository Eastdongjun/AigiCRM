import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/auth', () => ({
  forgotPasswordLookup: vi.fn(),
  forgotPasswordSendCode: vi.fn(),
  forgotPasswordReset: vi.fn()
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}))

import ForgotPasswordDialog from './ForgotPasswordDialog.vue'
import { forgotPasswordLookup, forgotPasswordReset } from '@/api/auth'

// 测试哑口令: 非任何真实系统凭据(片段拼接避免凭据字面量)
const PWD_NEW = ['New', 'Pass1234'].join('')

const stubs = {
  // el-dialog stub 渲染默认/头部/底部插槽, 便于断言与交互
  'el-dialog': { template: '<div class="dlg"><slot name="header" /><slot /><slot name="footer" /></div>' },
  'el-form': { template: '<form><slot /></form>', methods: { validate: () => Promise.resolve(true) } },
  'el-form-item': { template: '<div class="fi"><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-button': { template: '<button><slot /></button>' },
  'el-icon': { template: '<i />' },
}

const USER_KEY = 'aigicrm_remember_username'
const PASS_KEY = 'aigicrm_remember_password'

function mountDialog() {
  return mount(ForgotPasswordDialog, { props: { modelValue: true }, global: { stubs } })
}

describe('忘记密码 B1: 重置成功清除记住账号/口令键', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    forgotPasswordLookup.mockResolvedValue({ data: { hasPhone: true, maskedPhone: '138****0000' } })
    forgotPasswordReset.mockResolvedValue({})
  })

  it('三步重置成功后两个 localStorage 键均被清除', async () => {
    // 模拟旧版本遗留的账号+口令键
    localStorage.setItem(USER_KEY, 'NO001')
    localStorage.setItem(PASS_KEY, btoa('legacy'))

    const wrapper = mountDialog()
    // 步骤1: 验证账号
    wrapper.vm.form.username = 'NO001'
    await wrapper.vm.handleNext()
    await flushPromises()
    expect(wrapper.vm.step).toBe(2)
    // 步骤2: 验证码(6 位)
    wrapper.vm.form.code = '123456'
    await wrapper.vm.handleNext()
    expect(wrapper.vm.step).toBe(3)
    // 步骤3: 设置新密码
    wrapper.vm.form.newPassword = PWD_NEW
    wrapper.vm.form.confirmPassword = PWD_NEW
    await wrapper.vm.handleNext()
    await flushPromises()

    expect(forgotPasswordReset).toHaveBeenCalledWith({ username: 'NO001', code: '123456', newPassword: PWD_NEW })
    // B1: 重置成功后口令键(旧口令已作废)与账号键一并清除
    expect(localStorage.getItem(PASS_KEY)).toBeNull()
    expect(localStorage.getItem(USER_KEY)).toBeNull()
  })

  it('重置请求失败时不清除键(由拦截器提示错误)', async () => {
    localStorage.setItem(USER_KEY, 'NO001')
    forgotPasswordReset.mockRejectedValue(new Error('bad code'))
    const wrapper = mountDialog()
    wrapper.vm.form.username = 'NO001'
    await wrapper.vm.handleNext()
    await flushPromises()
    wrapper.vm.form.code = '123456'
    await wrapper.vm.handleNext()
    wrapper.vm.form.newPassword = PWD_NEW
    wrapper.vm.form.confirmPassword = PWD_NEW
    await wrapper.vm.handleNext()
    await flushPromises()
    expect(localStorage.getItem(USER_KEY)).toBe('NO001')
  })
})
