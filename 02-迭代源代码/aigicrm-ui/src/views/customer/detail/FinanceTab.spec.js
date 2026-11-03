import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref, nextTick } from 'vue'
import ElementPlus from 'element-plus'
import FinanceTab from './FinanceTab.vue'
// 【2026-11-01 审计修复】组件新增财务写门控(finance:manage∨edit∧view), 默认授权可写视角;
// 【2026-11-02】改为可参数化权限集合(个别用例清空/收窄以验证无权视角), beforeEach 复位
const mockPermCodes = new Set(['finance:manage', 'customer:edit', 'finance:view'])
vi.mock('@/store/user', () => ({ useUserStore: () => ({ hasPermission: (code) => mockPermCodes.has(code) }) }))
import { CustomerDetailKey } from '@/composables/useCustomerDetail'

/**
 * 【裁决 2026-10-10】FinanceTab 阶段总额制重设计后的 UI 契约:
 * - 主体=阶段性开票回款表(周期/两金额/差额/编辑), 判定头展示基准年+三态(真核心/争核心/非核心)
 * - 逐笔录入入口下线: 历史明细收进只读折叠区(发票/回款表无操作列), 作废行仍置灰留痕
 * - 录入/编辑弹窗: 周期(年+上半年/下半年/全年)+两金额, 至少一项>0 的守卫在 useCustomerDetail
 */
function mountFinanceTab({
  stageRows = [], coreType = '', baseYear = 0, annualInvoiceAmount = 0, annualPaymentAmount = 0,
  coreMinorWan = 50, coreMajorWan = 100,
  invoices = [], payments = [], invoiceSummary = { totalCount: 0, totalAmount: 0 },
  paymentSummary = { totalCount: 0, totalAmount: 0 }, readonly = false, handlers = {},
  customer = {},
} = {}) {
  const noop = () => {}
  const stageSummary = ref({ rows: stageRows, coreType, baseYear, annualInvoiceAmount, annualPaymentAmount,
    coreMinorWan, coreMajorWan })
  return {
    stageSummary,
    wrapper: mount(FinanceTab, {
      global: {
        plugins: [ElementPlus],
        provide: {
          [CustomerDetailKey]: {
            // 【2026-11-02 最近开票日页签维护】script 顶层消费的键
            customer: ref(customer),
            isAdmin: ref(false),
            loadAll: async () => {},
            readonly: ref(readonly),
            invoices: ref(invoices),
            invoiceSummary: ref(invoiceSummary),
            payments: ref(payments),
            paymentSummary: ref(paymentSummary),
            stageSummary,
            showStageDialog: ref(false),
            stageForm: ref({ periodYear: 2026, periodType: 'H1', invoiceAmount: null, paymentAmount: null }),
            openStageDialog: handlers.openStageDialog || noop,
            handleSaveStageAmount: handlers.handleSaveStageAmount || noop,
            coreType: ref(coreType),
            coreTypeLabel: ref(coreType === 'core' ? '真核心' : coreType === 'quasi' ? '争核心' : '非核心'),
            // 【2026-11-03 门槛配置化】双门槛随摘要注入(脚注数字不落死值)
            coreMinorWan: ref(coreMinorWan),
            coreMajorWan: ref(coreMajorWan),
          },
        },
      },
    }),
  }
}

const stageRow = (over = {}) => ({
  id: 1, periodYear: 2026, periodType: 'H1', periodLabel: '2026上半年',
  invoiceAmount: 120, paymentAmount: 80, updatedByName: '张三', updatedAt: '2026-07-02T10:30:00', ...over,
})

const invoiceRow = (status) => ({
  id: 1, invoiceDate: '2026-01-10', invoiceNumber: 'F-001', invoiceType: '增值税专票',
  invoiceCompany: '测试单位', amount: 12.5, status, voidedReason: status === 'voided' ? '开错票重开' : null,
})

async function settle(wrapper) {
  for (let i = 0; i < 6; i++) await nextTick()
  await flushPromises()
}

describe('客户详情 FinanceTab 阶段总额制(2026-10-10)', () => {
  beforeEach(() => {
    mockPermCodes.clear()
    mockPermCodes.add('finance:manage')
    mockPermCodes.add('customer:edit')
    mockPermCodes.add('finance:view')
  })

  it('主体为阶段表: 周期/两金额/差额/更新人, 判定头展示基准年与年度金额', async () => {
    const { wrapper } = mountFinanceTab({
      stageRows: [stageRow(), stageRow({ id: 2, periodType: 'FY', periodLabel: '2026全年', invoiceAmount: 150, paymentAmount: 110 })],
      coreType: 'core', baseYear: 2026, annualInvoiceAmount: 150, annualPaymentAmount: 110,
    })
    await settle(wrapper)
    const text = wrapper.text()
    expect(text).toContain('阶段性开票回款')
    expect(text).toContain('按 2026 年度')
    expect(text).toContain('真核心')
    expect(text).toContain('2026上半年')
    expect(text).toContain('2026全年')
    // 阶段差额 = 开票-回款 (formatWan 两位小数)
    expect(text).toContain('40.00万')
  })

  it('录入按钮存在且触发新建弹窗', async () => {
    const openStageDialog = vi.fn()
    const { wrapper } = mountFinanceTab({ handlers: { openStageDialog } })
    await settle(wrapper)
    const btn = wrapper.findAll('button').find((b) => b.text().includes('录入阶段数据'))
    expect(btn.exists()).toBe(true)
    btn.trigger('click')
    expect(openStageDialog).toHaveBeenCalledWith(null)
  })

  // 【裁决 2026-11-02】readonly(公海/共享只读入口)不再锁财务写入口: 持财务写权者(行政/财务专责代录)
  // 从只读入口进详情仍可录开票回款——与后端 2026-10-18 代录口径对齐。防"后端放、前端藏"回退。
  it('readonly(共享/公海只读)下持财务写权仍显示录入与编辑', async () => {
    const { wrapper } = mountFinanceTab({ readonly: true, stageRows: [stageRow()] })
    await settle(wrapper)
    const text = wrapper.text()
    expect(text).toContain('录入阶段数据')
    expect(wrapper.findAll('button').some((b) => b.text().trim() === '编辑')).toBe(true)
  })

  // 防改过头: 无财务写权(只读访客/仅 finance:view)时, readonly 与正常态均不得出现写入口
  it('无财务写权时 readonly 与正常态均不显示录入与编辑操作', async () => {
    mockPermCodes.clear()
    mockPermCodes.add('finance:view')
    for (const readonly of [true, false]) {
      const { wrapper } = mountFinanceTab({ readonly, stageRows: [stageRow()] })
      await settle(wrapper)
      const text = wrapper.text()
      expect(text).not.toContain('+ 录入阶段数据')
      expect(wrapper.findAll('button').every((b) => b.text().trim() !== '编辑')).toBe(true)
    }
  })

  it('行内"编辑"按钮以行参触发 openStageDialog(编辑制 upsert 入口)', async () => {
    const openStageDialog = vi.fn()
    const row = stageRow()
    const { wrapper } = mountFinanceTab({ stageRows: [row], handlers: { openStageDialog } })
    await settle(wrapper)
    wrapper.findAll('button').find((b) => b.text().trim() === '编辑').trigger('click')
    expect(openStageDialog).toHaveBeenCalledWith(row)
  })

  it('无阶段数据时展示空态引导', async () => {
    const { wrapper } = mountFinanceTab({})
    await settle(wrapper)
    expect(wrapper.text()).toContain('暂无阶段数据')
  })

  it('历史逐笔明细收进只读折叠区: 状态三态渲染, 作废行置灰, 无任何操作按钮', async () => {
    const { wrapper } = mountFinanceTab({
      invoices: [invoiceRow('pending'), invoiceRow('voided')],
      invoiceSummary: { totalCount: 2, totalAmount: 12.5 },
    })
    await settle(wrapper)
    expect(wrapper.text()).toContain('历史逐笔明细')
    expect(wrapper.text()).toContain('开票 2 笔')
    // 折叠面板展开(不依赖点击动画, el-collapse-item 内容已渲染)
    expect(wrapper.text()).toContain('待核对')
    expect(wrapper.html()).toContain('ledger-voided-row')
    // 只读历史: 不再有确认/作废/删除/录入开票按钮
    const btns = wrapper.findAll('button').map((b) => b.text().trim())
    expect(btns).not.toContain('确认')
    expect(btns).not.toContain('作废')
    expect(btns).not.toContain('删除')
    expect(btns).not.toContain('录入开票')
    expect(btns).not.toContain('录入回款')
  })

  it('口径脚注: 半年/全年总额制与真核心/争核心判定口径, 门槛数字随配置回传不落死值', async () => {
    const { wrapper } = mountFinanceTab({})
    await settle(wrapper)
    const text = wrapper.text()
    expect(text).toContain('真核心 = 判定年度开票、回款均 ≥ 100 万')
    expect(text).toContain('争核心 = 均达到争核心门槛(50 万)未双达标')
    // 【2026-11-03 门槛配置化】摘要带自定义门槛时脚注数字同步(管理员改配置即改文案)
    const { wrapper: w2 } = mountFinanceTab({ coreMinorWan: 80, coreMajorWan: 200 })
    await settle(w2)
    expect(w2.text()).toContain('均 ≥ 200 万')
    expect(w2.text()).toContain('争核心门槛(80 万)')
    // 【L3 2026-10-12 测试报告】脚注与实现对齐: 起算日=阶段末(未到期按当天,只延长不缩短),
    // 非"按阶段末日期顺延"的旧误述
    expect(text).toContain('刷新保护期起算日')
    expect(text).toContain('只延长不缩短')
  })
})
