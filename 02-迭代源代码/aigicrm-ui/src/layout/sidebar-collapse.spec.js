import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// 【GL-14 2026-11-24】侧栏分组折叠与折叠态持久化（提交 e2645d72 / ecd97e15 / D16，此前没有测试；
// layout/index.spec.js 只覆盖“普通改密走 logout 清理口令键”一条路径）。
// 钉住: 学分管理/法布施/流程协同/统计分析 四个组收成可折叠分组且默认展开, 其余组是带标签的平铺;
//       整栏折叠: 宽度 200 ↔ 64、Logo 文字与分组标签隐藏、菜单进入折叠态;
//       折叠态记在本机存储, 重新挂载(刷新/重登)后保持; 本机存储不可用时折叠照常可用、不抛错。
// 接口与路由用替身; Element Plus 组件与图标用会渲染插槽的桩, 菜单/侧栏/子菜单的桩声明了属性以便断言。

vi.mock('@/api/auth', () => ({
  getUserInfo: vi.fn(),
  logout: vi.fn().mockResolvedValue({}),
}))
vi.mock('@/api/reminder', () => ({ getPendingCount: vi.fn() }))
vi.mock('@/api/approval', () => ({ getApprovalStatistics: vi.fn() }))
vi.mock('@/api/system', () => ({ getPublicConfig: vi.fn(), changePassword: vi.fn() }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElNotification: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ path: '/dashboard', meta: {} }),
}))

import LayoutIndex from './index.vue'
import { getUserInfo as getUserInfoMock } from '@/api/auth'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const getUserInfo = /** @type {import('vitest').Mock} */ (getUserInfoMock)

const KEY = 'aigicrm_sidebar_collapsed'

const MENUS = [
  { id: 1, menuName: '工作台', menuType: 1, children: [{ id: 11, menuName: '首页', menuType: 2, path: '/' }] },
  { id: 2, menuName: '客户管理', menuType: 1, children: [{ id: 21, menuName: '客户列表', menuType: 2, path: '/customer' }] },
  { id: 3, menuName: '学分管理', menuType: 1, children: [
    { id: 31, menuName: '学分榜', menuType: 2, path: '/score' },
    { id: 32, menuName: '学分规则', menuType: 2, path: '/score/rule' },
  ] },
  { id: 4, menuName: '法布施', menuType: 1, children: [{ id: 41, menuName: '认知广场', menuType: 2, path: '/fabushi/feed' }] },
  { id: 5, menuName: '系统管理', menuType: 1, children: [{ id: 51, menuName: '用户管理', menuType: 2, path: '/system/user' }] },
]

const SLOT = { template: '<div><slot name="header" /><slot name="title" /><slot /><slot name="footer" /></div>' }
const stubs = Object.assign(
  {
    'el-container': SLOT, 'el-header': SLOT, 'el-main': SLOT, 'el-scrollbar': SLOT, 'el-icon': SLOT,
    'el-dropdown': SLOT, 'el-dropdown-menu': SLOT, 'el-dropdown-item': SLOT, 'el-avatar': SLOT, 'el-tooltip': SLOT,
    'el-dialog': SLOT, 'el-form': SLOT, 'el-form-item': SLOT, 'el-input': SLOT, 'el-button': SLOT,
    'el-breadcrumb': SLOT, 'el-breadcrumb-item': SLOT,
    'el-menu': { name: 'ElMenu', props: ['collapse', 'defaultOpeneds', 'defaultActive'], template: '<nav class="el-menu"><slot /></nav>' },
    'el-aside': { name: 'ElAside', props: ['width'], template: '<aside class="el-aside"><slot /></aside>' },
    'el-sub-menu': { props: ['index'], template: '<div class="el-sub-menu" :data-index="index"><slot name="title" /><slot /></div>' },
    'el-menu-item': { props: ['index'], template: '<div class="el-menu-item" :data-index="index"><slot name="title" /><slot /></div>' },
    'router-view': { template: '<div class="router-view-stub" />' },
  },
  // 全局注册的图标在测试里没人注册, 统一给桩(含分组图标的后备名字)
  Object.fromEntries(['ArrowDown', 'Bell', 'Lock', 'OfficeBuilding', 'QuestionFilled', 'SwitchButton', 'User', 'Fold', 'Expand',
    'Medal', 'Reading', 'List', 'DataAnalysis'].map((name) => [name, { template: '<i />' }])),
)

const mountLayout = () => mount(LayoutIndex, {
  global: {
    stubs,
    mocks: { $route: { path: '/dashboard', meta: {} } },
    config: { warnHandler: (msg) => warnings.push(msg) },
  },
})

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  setActivePinia(createPinia())
  warnings = []
  getUserInfo.mockResolvedValue({ data: {
    userId: 1, username: 'spec_user', realName: '测试', avatar: '',
    deptName: '', officeName: '', roles: [], roleName: '', dataScope: 'self',
    permissions: ['statistics:view'], menus: MENUS,
  } })
})

const hidden = (element) => element.element.style.display === 'none'
const labels = (wrapper) => wrapper.findAll('.menu-group-label').map((label) => label.text())
// 顶层的可折叠分组(el-menu 的直接子节点); 分组里页面的二级子菜单不是它们的直接子节点
const groupIndexes = (wrapper) => wrapper.findAll('nav.el-menu > .el-sub-menu').map((group) => group.attributes('data-index'))

describe('layout 侧栏分组折叠与折叠态持久化', () => {
  it('学分管理、法布施收成可折叠分组且默认展开；其余组是带标签的平铺', async () => {
    const wrapper = mountLayout()
    await flushPromises()
    expect(warnings).toEqual([])

    expect(groupIndexes(wrapper)).toEqual(['group-3', 'group-4'])
    expect(labels(wrapper)).toEqual(['工作台', '客户管理', '系统管理'])
    expect(wrapper.findComponent({ name: 'ElMenu' }).props('defaultOpeneds')).toEqual(['group-3', 'group-4'])
    // 分组里的页面仍然都在
    expect(wrapper.findAll('.el-menu-item').map((item) => item.attributes('data-index')))
      .toEqual(['/', '/customer', '/score', '/score/rule', '/fabushi/feed', '/system/user'])
  })

  it('流程协同、统计分析也是可折叠分组(四个组一个都不能漏)', async () => {
    getUserInfo.mockResolvedValue({ data: { userId: 1, username: 'u', permissions: [], menus: [
      { id: 6, menuName: '流程协同', menuType: 1, children: [{ id: 61, menuName: '审批', menuType: 2, path: '/approval' }] },
      { id: 7, menuName: '统计分析', menuType: 1, children: [{ id: 71, menuName: '经营统计', menuType: 2, path: '/statistics' }] },
    ] } })
    const wrapper = mountLayout()
    await flushPromises()

    expect(groupIndexes(wrapper)).toEqual(['group-6', 'group-7'])
    expect(labels(wrapper)).toEqual([])
  })

  it('点折叠按钮: 宽度 200 → 64, Logo 文字与分组标签隐藏, 菜单进入折叠态, 状态记到本机', async () => {
    const wrapper = mountLayout()
    await flushPromises()

    expect(wrapper.findComponent({ name: 'ElAside' }).props('width')).toBe('200px')
    expect(wrapper.findComponent({ name: 'ElMenu' }).props('collapse')).toBe(false)
    expect(hidden(wrapper.find('.logo-text'))).toBe(false)
    expect(wrapper.findAll('.menu-group-label').every((label) => !hidden(label))).toBe(true)
    expect(localStorage.getItem(KEY)).toBeNull()

    await wrapper.find('.collapse-btn').trigger('click')

    expect(wrapper.findComponent({ name: 'ElAside' }).props('width')).toBe('64px')
    expect(wrapper.findComponent({ name: 'ElMenu' }).props('collapse')).toBe(true)
    expect(hidden(wrapper.find('.logo-text'))).toBe(true)
    expect(wrapper.findAll('.menu-group-label').every(hidden)).toBe(true)
    expect(localStorage.getItem(KEY)).toBe('1')

    await wrapper.find('.collapse-btn').trigger('click')

    expect(wrapper.findComponent({ name: 'ElAside' }).props('width')).toBe('200px')
    expect(hidden(wrapper.find('.logo-text'))).toBe(false)
    expect(localStorage.getItem(KEY)).toBe('0')
    wrapper.unmount()
  })

  it('上次折叠过: 重新挂载(刷新 / 重登)后仍是折叠的; 展开后重新挂载是展开的', async () => {
    localStorage.setItem(KEY, '1')
    const first = mountLayout()
    await flushPromises()
    expect(first.findComponent({ name: 'ElAside' }).props('width')).toBe('64px')
    first.unmount()

    const second = mountLayout()
    await flushPromises()
    expect(second.findComponent({ name: 'ElAside' }).props('width')).toBe('64px')
    await second.find('.collapse-btn').trigger('click')
    second.unmount()

    const third = mountLayout()
    await flushPromises()
    expect(third.findComponent({ name: 'ElAside' }).props('width')).toBe('200px')
    third.unmount()
  })

  it('本机存储不可用(隐私模式等): 读不到按展开处理, 折叠照常可用, 不抛错', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('SecurityError') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('QuotaExceededError') })

    const wrapper = mountLayout()
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElAside' }).props('width')).toBe('200px')

    await wrapper.find('.collapse-btn').trigger('click')
    expect(wrapper.findComponent({ name: 'ElAside' }).props('width')).toBe('64px')
    expect(warnings).toEqual([])
    wrapper.unmount()
  })
})
