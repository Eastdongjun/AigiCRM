import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/auth', () => ({ login: vi.fn() }))
vi.mock('@/api/system', () => ({ getPublicConfig: vi.fn(), changePassword: vi.fn() }))
// 只 mock 组件脚本用到的指令式 API；模板组件用 stubs 提供
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { prompt: vi.fn() }
}))
const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
// 登录页动态 import('@/store/user') 只用 applyGroupInfo；直接 mock 避免拉入真实
// store 依赖链(api/reminder、api/approval → utils/request → 真实 router)
vi.mock('@/store/user', () => ({ useUserStore: () => ({ applyGroupInfo: vi.fn() }) }))

import LoginIndex from './index.vue'
import { login } from '@/api/auth'
import { getPublicConfig, changePassword } from '@/api/system'
import { ElMessageBox } from 'element-plus'

// 测试哑口令: 非任何真实系统凭据, 仅用于断言"口令不落盘"(片段拼接避免凭据字面量)
const PWD_OLD = ['Old', 'Secret1'].join('')
const PWD_LOGIN = ['Pass', 'w0rd'].join('')
const PWD_INIT = ['Init', 'Pass9'].join('')
const PWD_NEW = ['New', 'Pass1234'].join('')
const b64 = (p) => btoa(unescape(encodeURIComponent(p)))

const stubs = {
  'el-form': { template: '<form><slot /></form>', methods: { validate: () => Promise.resolve(true) } },
  'el-form-item': { template: '<div class="fi"><slot /></div>' },
  'el-input': { template: '<input />' },
  // 渲染插槽以便断言勾选项文案
  'el-checkbox': { template: '<label class="cb"><slot /></label>' },
  'el-button': { template: '<button><slot /></button>' },
  ForgotPasswordDialog: true,
}

const USER_KEY = 'aigicrm_remember_username'
const PASS_KEY = 'aigicrm_remember_password'

/** B1 核心断言: localStorage 中不存在任何口令(明文/base64 编码)值 */
function expectNoPasswordAnywhere(...passwords) {
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i)
    const v = localStorage.getItem(k)
    for (const p of passwords) {
      expect(v).not.toBe(p)
      expect(v).not.toBe(b64(p))
    }
  }
}

function mountLogin() {
  return mount(LoginIndex, { global: { stubs } })
}

describe('登录页 B1: 口令不落盘 localStorage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    getPublicConfig.mockResolvedValue({ data: {} })
    push.mockClear()
  })

  it('登录成功只记账号，口令键不写入且遗留口令键被清除', async () => {
    // 模拟旧版本遗留: 浏览器里已有可逆口令键
    localStorage.setItem(PASS_KEY, b64(PWD_OLD))
    login.mockResolvedValue({ data: { token: 'tk-1', pwdResetRequired: 0, groupIds: [], currentGroupId: null } })
    const wrapper = mountLogin()
    wrapper.vm.form.username = 'NO001'
    wrapper.vm.form.password = PWD_LOGIN
    await wrapper.vm.handleLogin()
    await flushPromises()
    expect(localStorage.getItem('token')).toBe('tk-1')
    expect(localStorage.getItem(USER_KEY)).toBe('NO001')
    expect(localStorage.getItem(PASS_KEY)).toBeNull()
    expectNoPasswordAnywhere(PWD_LOGIN, PWD_OLD)
  })

  it('取消"记住账号"登录后账号/口令键均不存在', async () => {
    login.mockResolvedValue({ data: { token: 'tk-2', pwdResetRequired: 0, groupIds: [], currentGroupId: null } })
    const wrapper = mountLogin()
    wrapper.vm.form.username = 'NO001'
    wrapper.vm.form.password = PWD_LOGIN
    wrapper.vm.form.rememberUser = false
    await wrapper.vm.handleLogin()
    await flushPromises()
    expect(localStorage.getItem(USER_KEY)).toBeNull()
    expect(localStorage.getItem(PASS_KEY)).toBeNull()
  })

  it('强制改密成功: 新口令不落盘，token 移除，账号保留', async () => {
    login.mockResolvedValue({ data: { token: 'tk-3', pwdResetRequired: 1, groupIds: [], currentGroupId: null } })
    ElMessageBox.prompt.mockResolvedValue({ value: PWD_NEW })
    changePassword.mockResolvedValue({})
    const wrapper = mountLogin()
    wrapper.vm.form.username = 'NO001'
    wrapper.vm.form.password = PWD_INIT
    await wrapper.vm.handleLogin()
    await flushPromises()
    expect(changePassword).toHaveBeenCalledWith({ oldPassword: PWD_INIT, newPassword: PWD_NEW })
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem(USER_KEY)).toBe('NO001')
    expect(localStorage.getItem(PASS_KEY)).toBeNull()
    expectNoPasswordAnywhere(PWD_NEW, PWD_INIT)
  })

  it('取消强制改密: 旧口令不写回，遗留口令键被清除', async () => {
    localStorage.setItem(PASS_KEY, b64(PWD_OLD))
    login.mockResolvedValue({ data: { token: 'tk-4', pwdResetRequired: 1, groupIds: [], currentGroupId: null } })
    ElMessageBox.prompt.mockRejectedValue(new Error('cancel'))
    const wrapper = mountLogin()
    wrapper.vm.form.username = 'NO001'
    wrapper.vm.form.password = PWD_INIT
    await wrapper.vm.handleLogin()
    await flushPromises()
    expect(changePassword).not.toHaveBeenCalled()
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem(PASS_KEY)).toBeNull()
    expectNoPasswordAnywhere(PWD_INIT, PWD_OLD)
  })

  it('挂载登录页清除遗留口令键且不回填密码(管理员重置/旧版本残留场景)', async () => {
    localStorage.setItem(USER_KEY, 'NO001')
    localStorage.setItem(PASS_KEY, b64(PWD_OLD))
    const wrapper = mountLogin()
    await flushPromises()
    expect(wrapper.vm.form.username).toBe('NO001')
    expect(wrapper.vm.form.password).toBe('')
    expect(localStorage.getItem(PASS_KEY)).toBeNull()
    expect(localStorage.getItem(USER_KEY)).toBe('NO001')
  })

  it('2026-11-05 用户翻案恢复: 提供"记住密码"勾选, 口令走加密档(auth_v2)且旧 base64 键仍禁写', async () => {
    // 【翻案记录】10-21 B1 下线"记住密码" → 2026-11-05 用户令恢复, 按审计自己的建议实现:
    // WebCrypto 设备密钥加密(jsdom 无 subtle → xor 降级档)+7 天有效期; 安全不变量保持:
    // 明文口令永不落盘, 旧 aigicrm_remember_password base64 键依旧禁写。
    const wrapper = mountLogin()
    await flushPromises()
    expect(wrapper.text()).toContain('记住账号')
    expect(wrapper.text()).toContain('记住密码')
    wrapper.vm.form.username = 'NO001'
    wrapper.vm.form.password = PWD_INIT
    wrapper.vm.form.rememberPassword = true
    login.mockResolvedValue({ data: { token: 'tk-9', pwdResetRequired: 0, groupIds: [], currentGroupId: null } })
    await wrapper.vm.handleLogin()
    await flushPromises()
    expect(localStorage.getItem('aigicrm_remember_auth_v2')).not.toBeNull()
    expect(localStorage.getItem(PASS_KEY)).toBeNull()
    expectNoPasswordAnywhere(PWD_INIT)
  })
})
