import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/system', () => ({
  getConfigList: vi.fn(),
  updateConfigs: vi.fn(),
  seedOnboardingDemo: vi.fn(),
  getDictData: vi.fn(),
  getDictTypes: vi.fn(),
  createDictData: vi.fn()
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: vi.fn() }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }))

import Onboarding from './index.vue'
import {
  getConfigList as getConfigListMock, updateConfigs as updateConfigsMock, getDictData as getDictDataMock,
  getDictTypes as getDictTypesMock, createDictData as createDictDataMock, seedOnboardingDemo as seedOnboardingDemoMock,
} from '@/api/system'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)。
// 【GL-14 2026-11-24】这个文件是 85b362d4 新增的, 这几处 mock 调用不在类型基线里, 默认门禁一直是红的。
const getConfigList = /** @type {import('vitest').Mock} */ (getConfigListMock)
const updateConfigs = /** @type {import('vitest').Mock} */ (updateConfigsMock)
const getDictData = /** @type {import('vitest').Mock} */ (getDictDataMock)
const getDictTypes = /** @type {import('vitest').Mock} */ (getDictTypesMock)
const createDictData = /** @type {import('vitest').Mock} */ (createDictDataMock)
const seedOnboardingDemo = /** @type {import('vitest').Mock} */ (seedOnboardingDemoMock)

const stubs = {
  'el-card': { template: '<div><slot name="header" /><slot /></div>' },
  'el-steps': { template: '<div><slot /></div>' },
  'el-step': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-alert': { template: '<div />' },
  'el-checkbox-group': { template: '<div><slot /></div>' },
  'el-checkbox': { template: '<label><slot /></label>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-result': { template: '<div><slot name="extra" /></div>' }
}

function mountOnboarding() {
  return mount(Onboarding, { global: { stubs, directives: { loading: {} } } })
}

describe('Onboarding wizard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getConfigList.mockResolvedValue({
      data: [
        { id: 1, configKey: 'system.name', configValue: 'AIGICRM', description: null },
        { id: 2, configKey: 'system.subtitle', configValue: '市场资源管理系统', description: null }
      ]
    })
    getDictData.mockResolvedValue({ data: [{ dictLabel: '制造业' }, { dictLabel: '互联网' }] })
    getDictTypes.mockResolvedValue({ data: [{ id: 9, dictCode: 'industry' }] })
    updateConfigs.mockResolvedValue({})
    createDictData.mockResolvedValue({})
    seedOnboardingDemo.mockResolvedValue({ data: { inserted: 3, demoSeeded: true } })
  })

  it('企业信息保存后进入行业步；完成写入 onboarding.completed', async () => {
    const wrapper = mountOnboarding()
    await flushPromises()
    expect(wrapper.vm.companyName).toBe('AIGICRM')

    wrapper.vm.companyName = '新CRM'
    await wrapper.vm.next()
    await flushPromises()
    expect(wrapper.vm.step).toBe(1)
    expect(updateConfigs.mock.calls[0][0]).toEqual(expect.arrayContaining([
      expect.objectContaining({ configKey: 'system.name', configValue: '新CRM' })
    ]))

    wrapper.vm.selectedIndustries = ['制造业', '新能源']
    await wrapper.vm.next()
    await flushPromises()
    expect(createDictData).toHaveBeenCalledWith(expect.objectContaining({ dictLabel: '新能源', dictTypeId: 9 }))
    expect(wrapper.vm.step).toBe(2)

    await wrapper.vm.finish()
    await flushPromises()
    const allRows = updateConfigs.mock.calls.flatMap(c => c[0])
    expect(allRows).toEqual(expect.arrayContaining([
      expect.objectContaining({ configKey: 'onboarding.completed', configValue: 'true' })
    ]))
  })

  it('演示数据按钮调用 seedOnboardingDemo 并置标记', async () => {
    const wrapper = mountOnboarding()
    await flushPromises()
    expect(wrapper.vm.demoSeeded).toBe(false)
    await wrapper.vm.seedDemoData()
    await flushPromises()
    expect(seedOnboardingDemo).toHaveBeenCalled()
    expect(wrapper.vm.demoSeeded).toBe(true)
  })

  it('系统名称为空时停在第一步并提示', async () => {
    const wrapper = mountOnboarding()
    await flushPromises()
    wrapper.vm.companyName = '   '
    await wrapper.vm.next()
    expect(wrapper.vm.step).toBe(0)
    expect(updateConfigs).not.toHaveBeenCalled()
  })
})
