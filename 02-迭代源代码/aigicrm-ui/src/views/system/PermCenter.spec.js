import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 【2026-11-03 统一权限中心】页面最小契约:
 * - 四页签齐备(功能与范围/专项权限/生效预览/变更审计), 子组件打桩(各自有独立 spec)
 * - 生效预览: 选人 → 消费 /system/perm-center/preview → 人话渲染(范围行/公海池/激励开关)
 * - 变更审计: 消费 /system/rule-versions 分页表
 */
vi.mock('@/api/system', () => ({
  getRoleList: vi.fn().mockResolvedValue({ data: [] }),
  getUserList: vi.fn().mockResolvedValue({ data: { records: [
    { id: 87, username: 'wf1103', realName: '测试用户' }
  ] } }),
  getPermCenterPreview: vi.fn().mockResolvedValue({ data: {
    user: { userId: 87, username: 'wf1103', realName: '测试用户', roleCodes: ['06'], dataScope: 'region', admin: false },
    permissions: ['customer:view', 'customer:finance:edit'],
    scopes: [
      { module: 'customer', action: 'view', label: '客户·查看', scope: 'region', anchor: 'ORG', visibleUserCount: 38, visibleDeptCount: 5, visibleCustomerCount: 214 },
      { module: 'customer', action: 'finance_edit', label: '开票回款·编辑', scope: 'self', anchor: 'OWNER', visibleUserCount: 1 }
    ],
    seaPools: [{ poolId: 1, poolName: '内蒙池', status: 1, view: true, edit: true, delete: false, claim: false }],
    incentive: { canViewShared: true, canViewAll: false, canEdit: false, canDelete: false }
  } }),
  getRuleVersions: vi.fn().mockResolvedValue({ data: { total: 1, records: [
    { createdAt: '2026-11-03T10:00:00', ruleKey: 'perm_scope:ROLE:06:customer:view', oldValue: 'self/OWNER', newValue: 'region/ORG', operatorName: 'admin' }
  ] } }),
}))
vi.mock('@/api/sea', () => ({
  getSeaConfigs: vi.fn().mockResolvedValue({ data: [] }),
  getOrgTree: vi.fn().mockResolvedValue({ data: [] }),
  createSeaConfig: vi.fn(), updateSeaConfig: vi.fn(), deleteSeaConfig: vi.fn(),
}))
// 【2026-11-09 页签合并】文件权限并入专项权限大页签, FileSpaceDefaults 随 special 面板挂载
vi.mock('@/api/file', () => ({
  getFolderTypeDefaults: vi.fn().mockResolvedValue({ data: [] }),
  saveFolderTypeDefault: vi.fn(),
  removeFolderTypeDefault: vi.fn(),
  getAclRoles: vi.fn().mockResolvedValue({ data: [] }),
}))

import PermCenter from './PermCenter.vue'
import ElementPlus from 'element-plus'

const mountCenter = () => mount(PermCenter, {
  global: {
    plugins: [ElementPlus, createPinia()],
    // 子面板各挂各的 API, 本 spec 只钉权限中心壳与预览/审计流, 子组件整体打桩
    stubs: { PermScopeManager: true, SeaConfigManager: true, PermGrantDialog: true },
  },
})

beforeEach(() => setActivePinia(createPinia()))

describe('权限中心(2026-11-03 门面统一)', () => {
  it('四页签齐备: 功能与范围 / 专项权限 / 生效预览 / 变更审计', () => {
    const wrapper = mountCenter()
    const text = wrapper.text()
    for (const label of ['功能与范围', '专项权限', '生效预览', '变更审计']) {
      expect(text).toContain(label)
    }
    // 收口提示: 唯一配置入口
    expect(text).toContain('唯一配置入口')
  })

  it('【2026-11-09 页签合并+骨架重构】文件权限为专项权限页签内子域, 无独立文件权限页签', async () => {
    const wrapper = mountCenter()
    const chips = wrapper.findAll('.pc-chip').map(w => w.text())
    expect(chips).toEqual(['功能与范围', '专项权限', '生效预览', '变更审计'])
    expect(chips.filter(t => t.includes('文件权限'))).toHaveLength(0) // 独立页签不得复活
    // 激活专项权限 → 二级分段三枚齐备, 默认子域=激励授权
    const tab = wrapper.findAll('.pc-chip').find(w => w.text().includes('专项权限'))
    await tab.trigger('click')
    await flushPromises()
    const subs = wrapper.findAll('.pc-subchip').map(w => w.text())
    expect(subs).toEqual(['激励授权', '公海池', '文件权限'])
    expect(wrapper.text()).toContain('激励专项授权')
    // 切到文件权限子域 → 空间授权矩阵挂载
    const sub = wrapper.findAll('.pc-subchip').find(w => w.text().includes('文件权限'))
    await sub.trigger('click')
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('公共空间（公司文件）')
    expect(text).toContain('四项激励文件')
    // 公海池子域懒挂载
    const seaSub = wrapper.findAll('.pc-subchip').find(w => w.text().includes('公海池'))
    await seaSub.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('公海池名单')
  })

  it('生效预览: 选人后渲染引擎算出的范围表/公海判定/激励开关', async () => {
    const wrapper = mountCenter()
    // lazy 页签: 先激活"生效预览"再驱动预览流
    const tab = wrapper.findAll('.pc-chip').find(w => w.text().includes('生效预览'))
    await tab.trigger('click')
    await flushPromises()
    const vm = wrapper.vm
    vm.previewUserId = 87
    await vm.loadPreview()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('客户·查看')
    expect(text).toContain('region')
    expect(text).toContain('按组织')
    expect(text).toContain('38 人')
    expect(text).toContain('214 户')
    expect(text).toContain('开票回款·编辑')
    expect(text).toContain('内蒙池')
    expect(text).toContain('查看分享: 开')
    expect(text).toContain('查看全部: 关')
  })

  it('变更审计: 消费 rule-versions 分页表(范围配置变更入册)', async () => {
    const wrapper = mountCenter()
    const tab = wrapper.findAll('.pc-chip').find(w => w.text().includes('变更审计'))
    await tab.trigger('click')
    await flushPromises()
    await wrapper.vm.loadAudit()
    await flushPromises()
    expect(wrapper.text()).toContain('perm_scope:ROLE:06:customer:view')
    expect(wrapper.text()).toContain('self/OWNER')
    expect(wrapper.text()).toContain('region/ORG')
    expect(wrapper.text()).toContain('admin')
  })
})
