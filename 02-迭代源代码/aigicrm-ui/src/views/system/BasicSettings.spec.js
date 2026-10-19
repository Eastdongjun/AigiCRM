import { describe, it, expect, vi } from 'vitest'
import { mount, config } from '@vue/test-utils'

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

vi.mock('@/api/system', () => ({
  getConfigList: vi.fn().mockResolvedValue({ data: [] }),
  updateConfigs: vi.fn(),
  getRoleList: vi.fn().mockResolvedValue({ data: [] }),
}))
vi.mock('@/store/user', () => ({ useUserStore: () => ({ hasPermission: () => true }) }))
vi.mock('@/composables/usePermissionData', async () => {
  const { ref } = await import('vue')
  return {
    usePermissionData: () => ({ allRoles: ref([]), allUsers: ref([]), loadAllUsers: vi.fn() }),
  }
})
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

import BasicSettings from './BasicSettings.vue'

config.global.stubs = {
  'el-form': { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': true,
  'el-input-number': true,
  'el-select': true,
  'el-option': true,
  'el-switch': true,
  'el-divider': { template: '<div><slot /></div>' },
  'el-upload': true,
  'el-button': { template: '<button><slot /></button>' },
}

describe('system/BasicSettings.vue 上传令牌（P2-6）', () => {
  it('上传 headers 走共享 getToken 且为渲染期活读, 不裸读/不快照 localStorage', async () => {
    localStorage.setItem('token', 'first-token')
    const wrapper = mount(BasicSettings)
    await flushPromises()
    expect(wrapper.vm.uploadHeaders().Authorization).toBe('Bearer first-token')

    // 令牌在挂载后更新(如另页签重登): 任一次重渲染后 headers 必须读到新值——
    // 裸读 const token = localStorage.getItem('token') 是 setup 时一次性快照, 永远发旧值
    localStorage.setItem('token', 'second-token')
    wrapper.vm.configMap['system.subtitle'] = '改个副标题触发重渲染'
    await flushPromises()
    expect(wrapper.vm.uploadHeaders().Authorization).toBe('Bearer second-token')

    localStorage.removeItem('token')
  })
})
