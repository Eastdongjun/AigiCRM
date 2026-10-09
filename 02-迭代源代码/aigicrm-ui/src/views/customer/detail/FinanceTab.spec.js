import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref, nextTick } from 'vue'
import ElementPlus from 'element-plus'
import FinanceTab from './FinanceTab.vue'
import { CustomerDetailKey } from '@/composables/useCustomerDetail'

/**
 * 【PR-18 台账状态机】财务 Tab 状态标记与操作门控断言(2026-09-20):
 * - 状态列三态: pending=待核对 / confirmed=已确认 / voided=已作废(含原因提示)
 * - 操作按状态收口: 仅 pending 可"确认/删除"(后端同口径), 非 voided 可"作废"
 * - 作废行置灰类名 ledger-voided-row(留痕展示)
 * - 口径脚注提示"作废不计入统计/保护期"
 */
function mountFinanceTab({ invoices = [], payments = [], readonly = false, handlers = {}, dialogs = {}, formValues = {} }) {
  const noop = () => {}
  return mount(FinanceTab, {
    global: {
      plugins: [ElementPlus],
      provide: {
        [CustomerDetailKey]: {
          readonly: ref(readonly),
          invoices: ref(invoices),
          invoiceSummary: ref({ totalCount: 0, totalAmount: 0 }),
          payments: ref(payments),
          paymentSummary: ref({ totalCount: 0, totalAmount: 0 }),
          showInvoiceDialog: ref(!!dialogs.invoice),
          showPaymentDialog: ref(!!dialogs.payment),
          invoiceForm: ref({ invoiceDate: '', invoiceNumber: '', invoiceType: '增值税专票', invoiceCompany: '', amount: null, ...formValues.invoice }),
          paymentForm: ref({ paymentDate: '', paymentAmount: null, paymentMethod: '', remark: '', ...formValues.payment }),
          handleAddInvoice: handlers.handleAddInvoice || noop,
          handleDeleteInvoice: handlers.handleDeleteInvoice || noop,
          handleAddPayment: handlers.handleAddPayment || noop,
          handleDeletePayment: handlers.handleDeletePayment || noop,
          handleConfirmInvoice: handlers.handleConfirmInvoice || noop,
          handleVoidInvoice: handlers.handleVoidInvoice || noop,
          handleConfirmPayment: handlers.handleConfirmPayment || noop,
          handleVoidPayment: handlers.handleVoidPayment || noop,
        },
      },
    },
  })
}

const invoiceRow = (status) => ({
  id: 1, invoiceDate: '2026-01-10', invoiceNumber: 'F-001', invoiceType: '增值税专票',
  invoiceCompany: '测试单位', amount: 12.5, status, voidedReason: status === 'voided' ? '开错票重开' : null,
})

describe('客户详情 FinanceTab 台账状态机', () => {
  it('状态列按 pending/confirmed/voided 三态渲染, 作废行置灰留痕', async () => {
    const wrapper = mountFinanceTab({
      invoices: [invoiceRow('pending'), invoiceRow('confirmed'), invoiceRow('voided')],
    })
    for (let i = 0; i < 6; i++) await nextTick() // el-table 列注册/单元格渲染跨帧完成(双表时更多)
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('待核对')
    expect(text).toContain('已确认')
    expect(text).toContain('已作废')
    // 作废原因经 el-tooltip 悬停展示(popper 懒渲染), 这里断言行级置灰类与标签
    expect(wrapper.html()).toContain('ledger-voided-row')
  })

  it('操作门控(裁决 2026-09-24): 全状态可删除(留痕), 确认仅 pending, 作废 pending+confirmed', async () => {
    const wrapper = mountFinanceTab({
      invoices: [invoiceRow('pending'), invoiceRow('confirmed'), invoiceRow('voided')],
    })
    for (let i = 0; i < 6; i++) await nextTick() // el-table 列注册/单元格渲染跨帧完成(双表时更多)
    await flushPromises()
    const rows = wrapper.findAll('.el-table__row')
    const btnText = (row) => row.findAll('button').map((b) => b.text()).join(',')
    expect(btnText(rows[0])).toContain('确认')
    expect(btnText(rows[0])).toContain('删除')
    // 裁决 2026-09-24: 删除不再限 pending——任何状态可删(必填原因+审计留痕+逻辑删)
    expect(btnText(rows[1])).not.toContain('确认')
    expect(btnText(rows[1])).toContain('删除')
    expect(btnText(rows[1])).toContain('作废')
    expect(btnText(rows[2])).toContain('删除')
    expect(btnText(rows[2])).not.toContain('作废')
  })

  it('点击确认/作废调用对应处理器', async () => {
    const onConfirm = vi.fn()
    const onVoid = vi.fn()
    const wrapper = mountFinanceTab({
      invoices: [invoiceRow('pending')],
      handlers: { handleConfirmInvoice: onConfirm, handleVoidInvoice: onVoid },
    })
    for (let i = 0; i < 6; i++) await nextTick()
    await flushPromises()
    wrapper.findAll('button').find((b) => b.text().trim() === '确认').trigger('click')
    expect(onConfirm).toHaveBeenCalledOnce()
    wrapper.findAll('button').find((b) => b.text().trim() === '作废').trigger('click')
    expect(onVoid).toHaveBeenCalledOnce()
  })

  it('回款区同样有状态列与确认/作废操作', async () => {
    const onConfirmPay = vi.fn()
    const wrapper = mountFinanceTab({
      payments: [{ id: 9, paymentDate: '2026-02-01', paymentAmount: 3.2, paymentMethod: '银行', remark: '', status: 'pending' }],
      handlers: { handleConfirmPayment: onConfirmPay },
    })
    for (let i = 0; i < 6; i++) await nextTick()
    await flushPromises()
    expect(wrapper.text()).toContain('待核对')
    wrapper.findAll('button').find((b) => b.text().trim() === '确认').trigger('click')
    expect(onConfirmPay).toHaveBeenCalledOnce()
  })

  it('口径脚注说明作废不计入统计与保护期', async () => {
    const wrapper = mountFinanceTab({})
    await nextTick()
    expect(wrapper.text()).toContain('不计入上方累计与有效总额')
    expect(wrapper.text()).toContain('不延长保护期')
  })

  // 【C11 补漏 2026-10-09】弹窗 :rules 挂载断言。
  // 说明: EP 2.14.5 暴露的 form.validate() 在 jsdom 下恒 resolve(实测门面 fields 虽注册但
  // 校验不执行, field 级 validate 正常), 故不断言"点提交被拦截"; 生产兜底=useCustomerDetail
  // 提交守卫(拒空/拒 0)+rules 的 blur/change 内联错误。这里钉住规则挂载与金额语义不回退。
  it('开票弹窗: :rules 挂到必填字段, form-item prop 齐备(缺 prop 校验不生效)', async () => {
    const wrapper = mountFinanceTab({ dialogs: { invoice: true } })
    for (let i = 0; i < 6; i++) await nextTick()
    await flushPromises()
    const form = wrapper.findComponent({ name: 'ElForm' })
    expect(form.exists()).toBe(true)
    expect(Object.keys(form.props('rules') || {})).toEqual(['invoiceDate', 'invoiceNumber', 'amount'])
    const itemProps = form.findAllComponents({ name: 'ElFormItem' }).map((i) => i.props('prop'))
    expect(itemProps).toEqual(['invoiceDate', 'invoiceNumber', undefined, undefined, 'amount'])
  })

  it('回款弹窗: :rules 挂到日期与金额字段', async () => {
    const wrapper = mountFinanceTab({ dialogs: { payment: true } })
    for (let i = 0; i < 6; i++) await nextTick()
    await flushPromises()
    const form = wrapper.findComponent({ name: 'ElForm' })
    expect(Object.keys(form.props('rules') || {})).toEqual(['paymentDate', 'paymentAmount'])
    const itemProps = form.findAllComponents({ name: 'ElFormItem' }).map((i) => i.props('prop'))
    expect(itemProps).toEqual(['paymentDate', 'paymentAmount', undefined, undefined])
  })

  it('金额规则语义: null/0/负数拒绝, 正数放行(与提交守卫拒 0 口径一致)', async () => {
    const wrapper = mountFinanceTab({ dialogs: { invoice: true } })
    for (let i = 0; i < 6; i++) await nextTick()
    await flushPromises()
    const rules = wrapper.findComponent({ name: 'ElForm' }).props('rules')
    const validator = rules.amount.find((r) => typeof r.validator === 'function').validator
    const run = (v) => new Promise((res) => validator(null, v, (err) => res(!!err)))
    expect(await Promise.all([run(null), run(0), run(-1), run(2.5)])).toEqual([true, true, true, false])
  })
})
