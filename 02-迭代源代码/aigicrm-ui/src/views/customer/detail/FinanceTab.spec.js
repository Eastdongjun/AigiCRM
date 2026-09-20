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
function mountFinanceTab({ invoices = [], payments = [], readonly = false, handlers = {} }) {
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
          showInvoiceDialog: ref(false),
          showPaymentDialog: ref(false),
          invoiceForm: ref({}),
          paymentForm: ref({}),
          handleAddInvoice: noop,
          handleDeleteInvoice: handlers.handleDeleteInvoice || noop,
          handleAddPayment: noop,
          handleDeletePayment: noop,
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

  it('操作门控: pending 行有确认/作废/删除, confirmed 行仅作废, voided 行无操作', async () => {
    const wrapper = mountFinanceTab({
      invoices: [invoiceRow('pending'), invoiceRow('confirmed'), invoiceRow('voided')],
    })
    for (let i = 0; i < 6; i++) await nextTick() // el-table 列注册/单元格渲染跨帧完成(双表时更多)
    await flushPromises()
    const rows = wrapper.findAll('.el-table__row')
    const btnText = (row) => row.findAll('button').map((b) => b.text()).join(',')
    expect(btnText(rows[0])).toContain('确认')
    expect(btnText(rows[0])).toContain('删除')
    expect(btnText(rows[1])).not.toContain('确认')
    expect(btnText(rows[1])).not.toContain('删除')
    expect(btnText(rows[1])).toContain('作废')
    expect(btnText(rows[2])).toBe('')
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
})
