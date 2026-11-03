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

async function mountWithPermissions(permissions, roles = []) {
  const wrapper = mount(PermScopeManager, { global: { stubs } })
  useUserStore().permissions = new Set(permissions)
  useUserStore().roles = roles
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

  it('admin 且持有 system:config:edit 时可保存角色权限范围', async () => {
    const wrapper = await mountWithPermissions(['system:config:view', 'system:config:edit'], ['admin'])
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

  it('非 admin 即使持 system:config:edit 也只读（与后端 requireAdmin 对齐）', async () => {
    const wrapper = await mountWithPermissions(['system:config:view', 'system:config:edit'], ['sales'])
    expect(wrapper.vm.canEditScope).toBe(false)

    await wrapper.vm.saveScope(col, item, 'office')
    await flushPromises()

    expect(updateRolePermScopes).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })
})

describe('PermScopeManager 权限码 → 后端 (module, action) 显式映射', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    mockApi()
  })

  it('只对真实消费的权限码显示范围控件', async () => {
    const wrapper = await mountWithPermissions(['system:config:view'], ['admin'])
    for (const p of ['customer:view', 'customer:edit', 'customer:delete', 'incentive:view',
      'file:view', 'reminder:view', 'approval:view', 'task:view', 'sea:view', 'system:log:view']) {
      expect(wrapper.vm.isScopeDriven({ permission: p }), p).toBe(true)
    }
    for (const p of ['system:user:add', 'system:role:edit', 'sea:config', 'customer:date', 'statistics:view']) {
      expect(wrapper.vm.isScopeDriven({ permission: p }), p).toBe(false)
    }
    // 不再用 split(':')[1]：三段码必须映射到 view 且 module 为 system
    expect(wrapper.vm.actionOf('system:log:view')).toBe('view')
    expect(wrapper.vm.moduleOf('system:log:view')).toBe('system')
    expect(wrapper.vm.actionOf('system:user:add')).toBe('add')
    // 七档范围选项与角色/人员表单一致（含 custom）
    expect(wrapper.vm.scopeOptions.map(o => o.value)).toContain('custom')
  })

  it('已保存的 (system, view) 配置能回显到 system:log:view 行', async () => {
    getRolePermScopes.mockResolvedValue({
      data: [{ module: 'system', action: 'view', scope_level: 'office', anchor: 'ORG' }],
    })
    const wrapper = await mountWithPermissions(['system:config:view'], ['admin'])
    const logItem = { id: 2, label: '操作日志', permission: 'system:log:view' }
    expect(wrapper.vm.scopeOf(col, logItem)).toBe('office')
    expect(wrapper.vm.anchorOf(col, logItem)).toBe('ORG')
  })

  it('system:log:view 保存为 (system, view) 而非死配置 (system, log)', async () => {
    const wrapper = await mountWithPermissions(['system:config:view', 'system:config:edit'], ['admin'])
    const logItem = { id: 2, label: '操作日志', permission: 'system:log:view' }
    await wrapper.vm.saveScope(col, logItem, 'office')
    await flushPromises()

    // 【2026-11-03 权限中心·锚点推荐】未显式选锚点时按推荐保存: 查看类(view)=按组织 ORG
    expect(updateRolePermScopes).toHaveBeenCalledWith('admin', [
      { module: 'system', action: 'view', scopeLevel: 'office', anchor: 'ORG' },
    ])
  })

  it('未接入映射的权限码不写入范围配置', async () => {
    const wrapper = await mountWithPermissions(['system:config:view', 'system:config:edit'], ['admin'])
    await wrapper.vm.saveScope(col, { id: 3, label: '新增人员', permission: 'system:user:add' }, 'office')
    await flushPromises()

    expect(updateRolePermScopes).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })
})
