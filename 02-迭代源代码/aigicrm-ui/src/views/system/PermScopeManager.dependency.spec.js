import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 【一期 2026-11-17 TDD】依赖禁用提示 / 专项面板直达 / 保存反馈 契约 spec（独立 harness，与
 * PermScopeManager.spec.js 隔离）:
 * - C1 依赖禁用提示: dependsOn 父码未勾的行必须渲染警告 tag『需先勾选: {父功能名}』
 *   且 checkbox 禁用(C1a); 父码勾选后 tag 消失、解锁(C1b); meta 缺父条目时回退显示码本身(C1c)
 * - C2 专项面板直达: incentive:* 行渲染『去激励授权面板→』点击 emit goto-panel='incentive'(C2a);
 *   file:* → goto-panel='file'(C2b); 走范围控件的码(task:view)不得渲染直达按钮(C2c)
 * - C3 保存反馈: 勾选行 → togglePerm 成功 → ElMessage.success 含『已保存并生效』(C3a);
 *   togglePerm 返回 false → 不得出现 success(C3b, 失败提示由 composable 内部负责)
 * 双向覆盖: C1a/C1b、C3a/C3b 互为正负例——实现若移除提示/反馈, 对应用例必红。
 */

// vi.mock 工厂无法引用外部变量(提升)——常量内联于工厂, 断言经 __test 句柄取值
vi.mock('@/composables/usePermissionData', async () => {
  const { ref } = await import('vue')
  const ROLE_ID = 11
  const rolePermSet = new Set()
  const state = { toggleResult: true }
  const togglePermMock = vi.fn(async () => state.toggleResult)
  const rolePermMap = ref({ [ROLE_ID]: rolePermSet })
  const permModules = ref([
    {
      name: '法布施',
      items: [
        { id: 175, label: '法布施内容浏览', permission: 'fabushi:view' },
        { id: 174, label: '统计看板', permission: 'fabushi:stats:view' },
        { id: 999, label: '法布施统计导出', permission: 'fabushi:stats:export', dependsOn: 'fabushi:stats:view' },
        { id: 200, label: '激励查询', permission: 'incentive:view' },
        { id: 201, label: '文件分享', permission: 'file:share' },
        { id: 202, label: '任务查看', permission: 'task:view' },
      ],
    },
  ])
  const allRoles = ref([{ id: ROLE_ID, roleName: '销售员', roleCode: '06' }])
  const menuTree = ref([{ id: 174, label: '统计看板', permission: 'fabushi:stats:view', children: [] }])
  const metaByCode = ref({
    'fabushi:stats:view': { name: '统计看板' },
    'fabushi:stats:export': { name: '法布施统计导出' },
    'incentive:view': { name: '激励查询' },
    'file:share': { name: '文件分享' },
    'task:view': { name: '任务查看' },
  })
  const permLoadError = ref(false)
  return {
    usePermissionData: () => ({
      allRoles,
      permModules,
      menuTree,
      metaByCode,
      rolePermMap,
      permLoadError,
      loadPermData: vi.fn(async () => {}),
      togglePerm: togglePermMock,
      hasPerm: (role, item) => (rolePermMap.value[role.id] || new Set()).has(item.id),
    }),
    __test: { state, togglePermMock, rolePermSet, metaByCode, ROLE_ID, PARENT_ID: 174 },
  }
})

vi.mock('@/api/system', () => ({
  getRolePermScopes: vi.fn(async () => ({ data: [] })),
  updateRolePermScopes: vi.fn(async () => ({ data: [] })),
  getAssigneePermScopes: vi.fn(async () => ({ data: [] })),
  updateAssigneePermScopes: vi.fn(async () => ({ data: [] })),
  getPositionList: vi.fn(async () => ({ data: [] })),
  getUserList: vi.fn(async () => ({ data: { records: [] } })),
  updateRole: vi.fn(async () => ({ data: [] })),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

// el-checkbox stub: click → change 事件转发(disabled 时抑制), 供 C3 勾选链测试

import PermScopeManager from './PermScopeManager.vue'
import { getRolePermScopes } from '@/api/system'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { __test } from '@/composables/usePermissionData'

const stubs = {
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { template: '<div><slot /></div>' },
  'el-select': { props: ['disabled'], template: '<div class="select"><slot /></div>' },
  'el-option': true,
  // 透传 disabled 供 C1 断言(向后兼容: 既有用例不断言 checkbox 属性)
  'el-checkbox': {
    props: ['modelValue', 'disabled'],
    emits: ['change'],
    template: '<div class="checkbox" :data-disabled="disabled ? \'1\' : \'0\'" @click="!disabled && $emit(\'change\')" />',
  },
  'el-button': { template: '<button><slot /></button>' },
  'el-tag': { props: ['type'], template: '<span class="el-tag" :class="type ? `el-tag--${type}` : \'\'"><slot /></span>' },
  'el-link': { template: '<a><slot /></a>' },
  'el-empty': { props: ['description'], template: '<div class="empty">{{ description }}</div>' },
}

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0))

async function mountMgr() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useUserStore().permissions = new Set(['system:config:view', 'system:scope:grant'])
  useUserStore().roles = ['admin']
  const wrapper = mount(PermScopeManager, { global: { stubs, plugins: [pinia] } })
  await flushPromises()
  await flushPromises()
  return wrapper
}

const rowOf = (wrapper, code) =>
  wrapper.findAll('div.psm-row').find((w) => w.find('code').text() === code)

describe('PermScopeManager 依赖提示契约(一期 C1)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __test.rolePermSet.clear()
    __test.state.toggleResult = true
  })

  it('C1a 依赖缺失: 子码行渲染『需先勾选: 统计看板』警告tag 且 checkbox 禁用', async () => {
    const wrapper = await mountMgr()
    const row = rowOf(wrapper, 'fabushi:stats:export')
    expect(row).toBeTruthy()
    const warn = row.find('.el-tag--warning')
    expect(warn.exists()).toBe(true)
    expect(warn.text()).toBe('需先勾选：统计看板')
    const cb = row.find('.checkbox[data-disabled]')
    expect(cb.exists()).toBe(true)
    expect(cb.attributes('data-disabled')).toBe('1')
    wrapper.unmount()
  })

  it('C1b 依赖满足: 父码勾选后 tag 消失且 checkbox 解锁', async () => {
    __test.rolePermSet.add(__test.PARENT_ID)
    const wrapper = await mountMgr()
    const row = rowOf(wrapper, 'fabushi:stats:export')
    expect(row.find('.el-tag--warning').exists()).toBe(false)
    const cb = row.find('.checkbox[data-disabled]')
    expect(cb.exists()).toBe(true)
    expect(cb.attributes('data-disabled')).toBe('0')
    wrapper.unmount()
  })

  it('C1c dependsLabel 回退: meta 缺父条目时显示 dependsOn 码本身', async () => {
    delete __test.metaByCode.value['fabushi:stats:view']
    const wrapper = await mountMgr()
    const row = rowOf(wrapper, 'fabushi:stats:export')
    expect(row.find('.el-tag--warning').text()).toBe('需先勾选：fabushi:stats:view')
    __test.metaByCode.value['fabushi:stats:view'] = { name: '统计看板' }
    wrapper.unmount()
  })
})

describe('PermScopeManager 专项面板直达契约(一期 C2)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __test.state.toggleResult = true
  })

  it('C2a incentive 行: 渲染『去激励授权面板』按钮, 点击 emit goto-panel=incentive', async () => {
    const wrapper = await mountMgr()
    const row = rowOf(wrapper, 'incentive:view')
    const btn = row.findAll('button').find((b) => b.text().includes('去激励授权面板'))
    expect(btn).toBeTruthy()
    await btn.trigger('click')
    expect(wrapper.emitted('goto-panel')).toEqual([['incentive']])
    wrapper.unmount()
  })

  it('C2b file 行: 渲染『去文件夹权限』按钮, 点击 emit goto-panel=file', async () => {
    const wrapper = await mountMgr()
    const row = rowOf(wrapper, 'file:share')
    const btn = row.findAll('button').find((b) => b.text().includes('去文件夹权限'))
    expect(btn).toBeTruthy()
    await btn.trigger('click')
    expect(wrapper.emitted('goto-panel')).toEqual([['file']])
    wrapper.unmount()
  })

  it('C2c task:view 走范围控件分支: 不得渲染直达按钮', async () => {
    const wrapper = await mountMgr()
    const row = rowOf(wrapper, 'task:view')
    expect(row.find('.select').exists()).toBe(true)
    expect(row.findAll('button').filter((b) => b.text().startsWith('去')).length).toBe(0)
    wrapper.unmount()
  })
})

describe('PermScopeManager 保存反馈契约(一期 C3)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __test.rolePermSet.clear()
    __test.rolePermSet.add(__test.PARENT_ID) // 依赖满足, 勾选可操作
    __test.state.toggleResult = true
    getRolePermScopes.mockResolvedValue({ data: [] })
  })

  it('C3a 勾选成功: ElMessage.success 且文案含『已保存并生效』, togglePerm 收到 (role, item)', async () => {
    const wrapper = await mountMgr()
    const row = rowOf(wrapper, 'fabushi:stats:export')
    await row.find('.psm-row-check').trigger('click')
    await flushPromises()
    expect(__test.togglePermMock).toHaveBeenCalledTimes(1)
    const [role, item] = __test.togglePermMock.mock.calls[0]
    expect(role.id).toBe(__test.ROLE_ID)
    expect(item.permission).toBe('fabushi:stats:export')
    expect(ElMessage.success).toHaveBeenCalledTimes(1)
    expect(ElMessage.success.mock.calls[0][0]).toContain('已保存并生效')
    wrapper.unmount()
  })

  it('C3b 保存失败: togglePerm 返回 false → 不得出现 success 提示', async () => {
    __test.state.toggleResult = false
    const wrapper = await mountMgr()
    const row = rowOf(wrapper, 'fabushi:stats:export')
    await row.find('.psm-row-check').trigger('click')
    await flushPromises()
    expect(ElMessage.success).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
