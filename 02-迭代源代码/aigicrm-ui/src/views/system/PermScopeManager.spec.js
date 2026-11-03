import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const roles = [{ id: 1, roleCode: 'admin', roleName: '系统管理员' }, { id: 2, roleCode: '05', roleName: '行政' }]
const menuTree = [
  {
    id: 11, menuName: '客户管理', menuType: 2, permission: 'customer:view',
    children: [
      { id: 115, menuName: '客户列表', menuType: 2, permission: 'customer:view', children: [] },
      { id: 146, menuName: '开票回款编辑', menuType: 3, permission: 'customer:finance:edit', children: [] },
      { id: 88, menuName: '菜单查看', menuType: 3, permission: 'system:menu:view', children: [] },
    ],
  },
  {
    id: 90, menuName: '系统管理', menuType: 1, permission: null,
    children: [{ id: 91, menuName: '新增人员', menuType: 3, permission: 'system:user:add', children: [] }],
  },
]

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

async function mountWithPermissions(permissions, roleCodes = []) {
  const wrapper = mount(PermScopeManager, { global: { stubs } })
  useUserStore().permissions = new Set(permissions)
  useUserStore().roles = roleCodes
  await flushPromises()
  await flushPromises()
  return wrapper
}

// 新骨架: 单主体视角(选中角色/岗位/用户), 权限项行卡
const itemView = { id: 1, label: '查看客户', permission: 'customer:view' }
const logItem = { id: 2, label: '操作日志', permission: 'system:log:view' }
const financeItem = { id: 146, label: '开票回款编辑', permission: 'customer:finance:edit' }

describe('PermScopeManager 新骨架(2026-11-03 权限中心: 给谁→模块→明细)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    mockApi()
  })

  it('骨架渲染: 角色 chips + 模块导航 + 明细行 + 人话摘要', async () => {
    const wrapper = await mountWithPermissions(['system:config:view'], ['admin'])
    const text = wrapper.text()
    // 角色 chips(两个角色都渲染, 第一个默认选中)
    expect(wrapper.findAll('.psm-who-chip').length).toBe(2)
    expect(text).toContain('系统管理员')
    expect(text).toContain('行政')
    // 模块导航(客户管理/系统管理 分组)
    expect(wrapper.findAll('.psm-mod').length).toBeGreaterThanOrEqual(2)
    // 明细行: 权限项名 + 权限码
    expect(text).toContain('开票回款编辑')
    expect(text).toContain('customer:finance:edit')
    // 人话摘要条
    expect(text).toContain('在「')
  })

  it('切换角色会加载对应范围配置', async () => {
    getRolePermScopes.mockClear()
    const wrapper = await mountWithPermissions(['system:config:view'], ['admin'])
    getRolePermScopes.mockClear()
    await wrapper.vm.switchRole(roles[1])
    await flushPromises()
    expect(getRolePermScopes).toHaveBeenCalledWith('05')
  })
})

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

    await wrapper.vm.saveScope(itemView, 'office')
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

    await wrapper.vm.saveScope(itemView, 'office')
    await wrapper.vm.resetCurrent()
    await flushPromises()

    // 不再发出写入请求，也不再出现"界面已改、实际没变"的假成功
    expect(updateRolePermScopes).not.toHaveBeenCalled()
    expect(updateAssigneePermScopes).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })

  it('非 admin 即使持 system:config:edit 也只读（与后端 requireAdmin 对齐）', async () => {
    const wrapper = await mountWithPermissions(['system:config:view', 'system:config:edit'], ['sales'])
    expect(wrapper.vm.canEditScope).toBe(false)

    await wrapper.vm.saveScope(itemView, 'office')
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
    for (const p of ['customer:view', 'customer:edit', 'customer:delete', 'customer:finance:edit', 'incentive:view',
      'file:view', 'reminder:view', 'approval:view', 'task:view', 'sea:view', 'system:log:view']) {
      expect(wrapper.vm.isScopeDriven({ permission: p }), p).toBe(true)
    }
    for (const p of ['system:user:add', 'system:role:edit', 'sea:config', 'customer:date', 'statistics:view', 'system:menu:view']) {
      expect(wrapper.vm.isScopeDriven({ permission: p }), p).toBe(false)
    }
    // 七档范围选项与角色/人员表单一致（含 custom）
    expect(wrapper.vm.scopeOptions.map(o => o.value)).toContain('custom')
  })

  it('锚点推荐: 查看类默认按组织, 编辑/财务类默认按人', async () => {
    const wrapper = await mountWithPermissions(['system:config:view'], ['admin'])
    expect(wrapper.vm.recommendedAnchorOf(itemView)).toBe('ORG')
    expect(wrapper.vm.recommendedAnchorOf({ permission: 'customer:edit' })).toBe('OWNER')
    expect(wrapper.vm.recommendedAnchorOf(financeItem)).toBe('OWNER')
  })

  it('已保存的 (system, view) 配置能回显到 system:log:view 行', async () => {
    getRolePermScopes.mockResolvedValue({
      data: [{ module: 'system', action: 'view', scope_level: 'office', anchor: 'ORG' }],
    })
    const wrapper = await mountWithPermissions(['system:config:view'], ['admin'])
    expect(wrapper.vm.scopeOf(logItem)).toBe('office')
    expect(wrapper.vm.anchorOf(logItem)).toBe('ORG')
  })

  it('system:log:view 保存为 (system, view) 而非死配置 (system, log)', async () => {
    const wrapper = await mountWithPermissions(['system:config:view', 'system:config:edit'], ['admin'])
    await wrapper.vm.saveScope(logItem, 'office')
    await flushPromises()

    // 【锚点推荐】未显式选锚点时按推荐保存: 查看类(view)=按组织 ORG
    expect(updateRolePermScopes).toHaveBeenCalledWith('admin', [
      { module: 'system', action: 'view', scopeLevel: 'office', anchor: 'ORG' },
    ])
  })

  it('未接入映射的权限码不写入范围配置', async () => {
    const wrapper = await mountWithPermissions(['system:config:view', 'system:config:edit'], ['admin'])
    await wrapper.vm.saveScope({ id: 3, label: '新增人员', permission: 'system:user:add' }, 'office')
    await flushPromises()

    expect(updateRolePermScopes).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })
})
