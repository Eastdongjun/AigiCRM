import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/system', () => ({
  getUserList: vi.fn(),
  getUserById: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetPassword: vi.fn(),
  deleteUser: vi.fn(),
  getDeptList: vi.fn(),
  getRoleList: vi.fn(),
}))
vi.mock('@/api/customer', () => ({ getSimpleUserList: vi.fn() }))

import UserView from './user.vue'
import { getUserList, getDeptList, getRoleList } from '@/api/system'
import { getSimpleUserList } from '@/api/customer'

const stubs = {
  'el-button': { template: '<button><slot /></button>' },
  'el-input': { template: '<input />' },
  'el-tree-select': { template: '<div class="tree" />' },
  'el-select': { template: '<div class="select" />' },
  'el-option': true,
  'el-table': { template: '<div class="table"><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-pagination': { template: '<div class="pager" />' },
  'el-dialog': { template: '<div class="dialog"><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-cascader': { template: '<div class="cascader" />' },
  'el-progress': true,
}

const users = [
  { id: 1, username: 'NO001', realName: '马吉亮', phone: '13800000000', status: 1, roleName: '销售总监' },
  { id: 2, username: 'NM005', realName: '王磊磊', phone: '13900000000', status: 1, roleName: '销售员' },
]
const depts = [
  { id: 1, parentId: 0, deptName: '公司', deptType: 1 },
  { id: 2, parentId: 1, deptName: '山西大区', deptType: 2 },
  { id: 3, parentId: 2, deptName: '太原办销售部', deptType: 4 },
]

function mockData() {
  getUserList.mockResolvedValue({ data: { records: users, total: 2 } })
  getDeptList.mockResolvedValue({ data: depts })
  getRoleList.mockResolvedValue({ data: [{ id: 11, roleName: '销售员' }] })
  getSimpleUserList.mockResolvedValue({ data: [] })
}

describe('system/user.vue 服务端分页测试', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    mockData()
  })

  it('onMounted 加载用户列表（服务端分页 current/size 传参）', async () => {
    const wrapper = mount(UserView, { global: { stubs } })
    await flushPromises()
    expect(getUserList).toHaveBeenCalled()
    const call = getUserList.mock.calls[0][0]
    expect(call.current).toBe(1)
    expect(call.size).toBe(15)
    // total 显示
    expect(wrapper.text()).toContain('共2条')
  })

  it('search 重置页码到 1 后重新加载', async () => {
    const wrapper = mount(UserView, { global: { stubs } })
    await flushPromises()
    await wrapper.vm.search()
    expect(getUserList).toHaveBeenCalledTimes(2)
    expect(getUserList.mock.calls[1][0].current).toBe(1)
  })

  it('descendantIds 收集选中部门的全部子树 id', async () => {
    const wrapper = mount(UserView, { global: { stubs } })
    await flushPromises()
    // 选中"公司"(id=1) → 自身 + 山西大区 + 太原办
    const ids = wrapper.vm.descendantIds(1)
    expect(ids).toContain(1)
    expect(ids).toContain(2)
    expect(ids).toContain(3)
    // 选中"太原办"(id=3) → 仅自身
    expect(wrapper.vm.descendantIds(3)).toEqual([3])
  })

  it('buildTree 由扁平部门构建层级', async () => {
    const wrapper = mount(UserView, { global: { stubs } })
    await flushPromises()
    const tree = wrapper.vm.buildTree(depts)
    expect(tree).toHaveLength(1)
    expect(tree[0].children[0].deptName).toBe('山西大区')
    expect(tree[0].children[0].children[0].deptName).toBe('太原办销售部')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
