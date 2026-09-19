import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const roles = [{ id: 1, roleCode: 'admin', roleName: '系统管理员' }]
const menuTree = [{
  id: 11, menuName: '客户管理', menuType: 2, permission: 'customer:view',
  children: [{ id: 115, menuName: '客户列表', menuType: 2, permission: 'customer:view', children: [] }],
}]

vi.mock('@/api/system', () => ({
  getRoleList: vi.fn(),
  getMenuTree: vi.fn(),
  updateRole: vi.fn(),
  getRolePermScopes: vi.fn(),
  updateRolePermScopes: vi.fn(),
  getAssigneePermScopes: vi.fn(),
  updateAssigneePermScopes: vi.fn(),
  getPositionList: vi.fn(),
  getUserList: vi.fn(),
}))
vi.mock('@/utils/request', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: { menuIds: [] } }) },
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

import PermScopeManager from './PermScopeManager.vue'
import { getRoleList, getMenuTree, getRolePermScopes, updateRolePermScopes, updateAssigneePermScopes } from '@/api/system'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const stubs = {
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { template: '<div><slot /></div>' },
  'el-select': { props: ['disabled'], template: '<div class="select"><slot /></div>' },
  'el-option': true,
  'el-checkbox': { template: '<div class="checkbox" />' },
  'el-button': { template: '<button><slot /></button>' },
  'el-link': { template: '<a><slot /></a>' },
  'el-empty': { props: ['description'], template: '<div class="empty">{{ description }}</div>' },
}

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0))

function mockApi() {
  getRoleList.mockResolvedValue({ data: roles })
  getMenuTree.mockResolvedValue({ data: menuTree })
  getRolePermScopes.mockResolvedValue({
    data: [{ module: 'customer', action: 'view', scope_level: 'self', anchor: 'OWNER' }],
  })
  updateRolePermScopes.mockResolvedValue({})
  updateAssigneePermScopes.mockResolvedValue({})
}

async function mountWithPermissions(permissions) {
  const wrapper = mount(PermScopeManager, { global: { stubs } })
  useUserStore().permissions = new Set(permissions)
  await flushPromises()
  return wrapper
}

const col = { key: 'ROLE:admin', label: '系统管理员', isRole: true, role: roles[0] }
const item = { id: 1, label: '查看客户', permission: 'customer:view' }

describe('PermScopeManager 编辑门控（system:config:edit）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    mockApi()
  })

  it('持有 system:config:edit 时可保存角色权限范围', async () => {
    const wrapper = await mountWithPermissions(['system:config:view', 'system:config:edit'])
    expect(wrapper.vm.canEditScope).toBe(true)

    await wrapper.vm.saveScope(col, item, 'office')
    await flushPromises()

    expect(updateRolePermScopes).toHaveBeenCalledWith('admin', [
      { module: 'customer', action: 'view', scopeLevel: 'office', anchor: 'OWNER' },
    ])
  })

  it('只有 system:config:view 时控件禁用、入口拦截并给出只读提示', async () => {
    const wrapper = await mountWithPermissions(['system:config:view'])
    expect(wrapper.vm.canEditScope).toBe(false)
    // 只读提示渲染
    expect(wrapper.text()).toContain('只读')

    await wrapper.vm.saveScope(col, item, 'office')
    await wrapper.vm.resetRole(col)
    await wrapper.vm.resetAssignee({ ...col, key: 'USER:40' })
    await flushPromises()

    // 不再发出写入请求，也不再出现"界面已改、实际没变"的假成功
    expect(updateRolePermScopes).not.toHaveBeenCalled()
    expect(updateAssigneePermScopes).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })
})
