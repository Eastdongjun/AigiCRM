import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const route = { params: {}, query: {} }
const push = vi.fn()
// 部分 mock：保留 createRouter 真实现（utils/request → router 实例）
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useRoute: () => route, useRouter: () => ({ push }) }
})
vi.mock('@/utils/request', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: { exists: false } }) },
}))
vi.mock('@/api/customer', () => ({
  addCustomer: vi.fn(),
  updateCustomer: vi.fn(),
  getCustomer: vi.fn(),
  getSimpleUserList: vi.fn(),
  getContacts: vi.fn(),
  addContact: vi.fn(),
  getIndicator: vi.fn(),
  saveIndicator: vi.fn(),
  getCustomerGroupNames: vi.fn(),
}))
vi.mock('@/api/system', () => ({
  getDeptList: vi.fn(),
  getDictData: vi.fn(),
}))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))

import { useCustomerForm } from './useCustomerForm'
import {
  addCustomer, getCustomer, getSimpleUserList, getContacts,
  getIndicator, saveIndicator, getCustomerGroupNames, updateCustomer, addContact,
} from '@/api/customer'
import { getDeptList, getDictData } from '@/api/system'
import { ElMessage } from 'element-plus'

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

function mockLoad() {
  getDeptList.mockResolvedValue({ data: [] })
  getSimpleUserList.mockResolvedValue({ data: [{ id: 7, realName: '张上级' }] })
  getDictData.mockResolvedValue({ data: [] })
  getContacts.mockResolvedValue({ data: [] })
  getIndicator.mockResolvedValue({ data: null })
  getCustomerGroupNames.mockResolvedValue({ data: ['陕煤集团', '国家能源集团'] })
  addCustomer.mockResolvedValue({ data: 1001 })
  updateCustomer.mockResolvedValue({ data: null })
  addContact.mockResolvedValue({ data: null })
  saveIndicator.mockResolvedValue({ data: null })
}

describe('useCustomerForm 新增客户字段', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    route.params = {}
    mockLoad()
  })

  it('默认带上「终端=否」与空的「管理上级」', () => {
    const { form } = useCustomerForm()
    expect(form.hasTerminal).toBe(0)
    expect(form.managerId).toBeNull()
  })

  it('提交新客户时把终端与管理上级一起提交', async () => {
    const ctx = useCustomerForm()
    ctx.formRef.value = { validate: async () => true }
    ctx.form.customerName = '山西测试客户'
    ctx.form.hasTerminal = 1
    ctx.form.managerId = 7

    await ctx.handleSubmit()
    await flushPromises()

    expect(addCustomer).toHaveBeenCalledTimes(1)
    const payload = addCustomer.mock.calls[0][0]
    expect(payload.hasTerminal).toBe(1)
    expect(payload.managerId).toBe(7)
  })

  it('编辑既有客户时回填终端与管理上级（历史数据 null 不覆盖表单值）', async () => {
    route.params = { id: '301' }
    getCustomer.mockResolvedValue({
      data: { id: 301, customerName: '已有客户', hasTerminal: 1, managerId: 7, status: 'normal' },
    })

    const ctx = useCustomerForm()
    await ctx.loadFormData()

    expect(ctx.form.hasTerminal).toBe(1)
    expect(ctx.form.managerId).toBe(7)
  })

  // ===== 2026-09-13 字段缺陷修复 =====

  it('「最近开票日」默认留空，注册日默认今天（避免未开票客户被记为当天开票）', () => {
    const { form } = useCustomerForm()
    expect(form.lastInvoiceDate).toBe('')
    expect(form.registerDate).toBe(new Date().toISOString().split('T')[0])
  })

  it('日期清空时提交 null 而不是空串', async () => {
    const ctx = useCustomerForm()
    ctx.formRef.value = { validate: async () => true }
    ctx.form.customerName = '无票客户'
    ctx.form.lastInvoiceDate = ''

    await ctx.handleSubmit()
    await flushPromises()

    const payload = addCustomer.mock.calls[0][0]
    expect(payload.lastInvoiceDate).toBeNull()
  })

  it('表单不再包含「所属部门」（后端按负责人部门覆盖，属死字段）', async () => {
    const ctx = useCustomerForm()
    ctx.formRef.value = { validate: async () => true }
    ctx.form.customerName = '部门测试客户'

    await ctx.handleSubmit()
    await flushPromises()

    expect('deptId' in ctx.form).toBe(false)
    expect('deptId' in addCustomer.mock.calls[0][0]).toBe(false)
  })

  it('联系人填了内容却没填姓名时拦截提交并提示行号', async () => {
    const ctx = useCustomerForm()
    ctx.formRef.value = { validate: async () => true }
    ctx.form.customerName = '联系人校验客户'
    ctx.contacts.value = [{ name: '', position: '采购经理', phone: '13800000000', remark: '' }]

    await ctx.handleSubmit()
    await flushPromises()

    expect(addCustomer).not.toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith('第 1 行联系人未填姓名，请补全或删除该行')
  })

  it('整行全空的联系人视为未填写，不拦截提交也不落库', async () => {
    const ctx = useCustomerForm()
    ctx.formRef.value = { validate: async () => true }
    ctx.form.customerName = '空行客户'
    ctx.contacts.value = [{ name: '', position: '', phone: '', remark: '' }]

    await ctx.handleSubmit()
    await flushPromises()

    expect(addCustomer).toHaveBeenCalledTimes(1)
    expect(addContact).not.toHaveBeenCalled()
  })

  it('有姓名的联系人按 trim 后提交', async () => {
    const ctx = useCustomerForm()
    ctx.formRef.value = { validate: async () => true }
    ctx.form.customerName = '联系人提交客户'
    ctx.contacts.value = [{ name: ' 李四 ', position: ' 采购 ', phone: ' 13800000000 ', remark: '' }]

    await ctx.handleSubmit()
    await flushPromises()

    expect(addContact).toHaveBeenCalledWith(1001, {
      name: '李四', position: '采购', phone: '13800000000', remark: '',
    })
  })

  it('新建客户录入客情评分后，随客户一起保存指标', async () => {
    const ctx = useCustomerForm()
    ctx.formRef.value = { validate: async () => true }
    ctx.form.customerName = '核心客户'
    ctx.indicatorForm.relationshipScore = 3
    ctx.indicatorForm.indicator03 = 2

    await ctx.handleSubmit()
    await flushPromises()

    // composable 在 setup 之外调用，computed/ref 需显式取值
    expect(ctx.indicatorTotal.value).toBe(5)
    expect(saveIndicator).toHaveBeenCalledWith(1001, {
      relationshipScore: 3, indicator02: 0, indicator03: 2, indicator04: 0, indicator05: 0,
    })
  })

  it('客情全 0 且库内无指标行时不创建空指标记录', async () => {
    const ctx = useCustomerForm()
    ctx.formRef.value = { validate: async () => true }
    ctx.form.customerName = '零分客户'

    await ctx.handleSubmit()
    await flushPromises()

    expect(saveIndicator).not.toHaveBeenCalled()
  })

  it('编辑时回填并回写已有指标行（即使全 0 也保留记录）', async () => {
    route.params = { id: '302' }
    getCustomer.mockResolvedValue({ data: { id: 302, customerName: '已有客户', status: 'normal' } })
    getIndicator.mockResolvedValue({ data: { id: 9, relationshipScore: 2, indicator05: 3 } })

    const ctx = useCustomerForm()
    await ctx.loadFormData()
    expect(ctx.indicatorForm.relationshipScore).toBe(2)
    expect(ctx.indicatorForm.indicator05).toBe(3)

    ctx.formRef.value = { validate: async () => true }
    await ctx.handleSubmit()
    await flushPromises()

    expect(saveIndicator).toHaveBeenCalledWith('302', {
      relationshipScore: 2, indicator02: 0, indicator03: 0, indicator04: 0, indicator05: 3,
    })
  })

  it('加载历史集团名作为「所属集团」下拉候选', async () => {
    const ctx = useCustomerForm()
    await ctx.loadFormData()

    expect(getCustomerGroupNames).toHaveBeenCalled()
    expect(ctx.groupNameOptions.value).toEqual(['陕煤集团', '国家能源集团'])
  })

  it('集团名接口失败时下拉候选为空且不影响表单加载', async () => {
    getCustomerGroupNames.mockRejectedValue(new Error('403'))

    const ctx = useCustomerForm()
    await ctx.loadFormData()

    expect(ctx.groupNameOptions.value).toEqual([])
  })
})
