const { deleteCustomer, getCustomer, getCustomerContacts, getCustomerLogs, getCustomerIndicator, getCustomerStageAmounts, saveCustomerRelationship, releaseCustomer, archiveCustomer } = require('../../../api/customer')
const { submitApproval } = require('../../../api/approval')
const { hasPermission, requireLogin, isAdmin } = require('../../../utils/auth')
const { showError, showErrorWithRetry } = require('../../../utils/feedback')
const { readDemoCustomers } = require('../../../utils/demo-customers')
// 【D24/D25 2026-10-04】状态文案、五项指标配置、核心客户阈值统一引用组内 shared(单一出处)
// 【#10 2026-10-12】客情选项同步走 shared 新文案 + 后台字典 relationship_depth 覆盖
const { customerStatusLabel, INDICATOR_FIELDS, INDICATOR_ITEMS, CORE_INVOICE_AMOUNT_THRESHOLD, CORE_PAYMENT_AMOUNT_THRESHOLD, CORE_INDICATOR_SCORE_THRESHOLD, INDICATOR_MAX_SCORE, RELATIONSHIP_OPTIONS, fetchRelationshipDictOptions, fetchIndicatorDictOptions } = require('../shared')
const { getDictData } = require('../../../api/system')

const INITIAL_RELATIONSHIP_OPTIONS = RELATIONSHIP_OPTIONS.map((label, value) => ({ label, value }))

function normalizeDetail(item) {
  const terminalValue = item.hasTerminal
  const status = item.status || '正常'
  return {
    id: item.id,
    customerCode: item.customerCode || '',
    customerName: item.customerName || item.name || '未命名客户',
    invoiceName: item.invoiceName || '',
    industry: item.industry || '其他',
    devType: item.devType || '未分类',
    capacity: item.capacity || '',
    hasTerminal: terminalValue,
    terminalLabel: terminalValue === 1 || terminalValue === true || terminalValue === '1' ? '终端客户' : (terminalValue === 0 || terminalValue === false || terminalValue === '0' ? '非终端客户' : '-'),
    ownerName: item.ownerName || item.salesperson || '未分配',
    managerName: item.managerName || '',
    deptName: item.deptName || '',
    officeName: item.officeName || item.deptName || '未设置办事处',
    regionName: item.regionName || '',
    groupName: item.groupName || '',
    branchCompany: item.branchCompany || '',
    unitName: item.unitName || '',
    province: item.province || '',
    city: item.city || '',
    district: item.district || '',
    areaText: `${item.province || ''}${item.city || ''}${item.district || ''}` || '',
    address: item.address || '',
    status,
    statusLabel: customerStatusLabel(status),
    registerDate: item.registerDate || '',
    lastInvoiceDate: item.lastInvoiceDate || '',
    expireDate: item.expireDate || '',
    remark: item.remark || ''
  }
}

function toNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

/** 【C6】单路请求结果收敛为 { ok, value|error }：任一分支失败不外抛，
 *  由调用方按区块降级，避免 Promise.all 一处 reject 导致联系人/日志/阶段/指标整块不落稿 */
function settleRequest(promise) {
  return promise.then(
    (value) => ({ ok: true, value }),
    (error) => ({ ok: false, error })
  )
}

function statusView(met, okText, warnText) {
  return {
    statusClass: met ? 'ok' : 'warn',
    statusText: met ? okText : warnText
  }
}

function buildIndicatorSummary(stageSummary, indicator) {
  const stage = stageSummary || {}
  const invoiceAmount = toNumber(stage.annualInvoiceAmount)
  const paymentAmount = toNumber(stage.annualPaymentAmount)
  const scoreTotal = INDICATOR_FIELDS
    .reduce((sum, key) => sum + toNumber((indicator || {})[key]), 0)
  // 【裁决 2026-10-10】核心三态=纯金额门槛(年度开票/回款双>=100万=真核心, 有数据未双达标=争核心,
  // 无数据=非核心)。五项评分不再参与判定, 保留为客情参考; 判定结果由后端按阶段总额一处算好回传,
  // 前端只消费 coreType, 不复刻口径(防两端漂移)。阈值常量仍引用 shared 单一出处。
  const coreType = stage.coreType || ''
  const invoiceMet = invoiceAmount >= CORE_INVOICE_AMOUNT_THRESHOLD
  const paymentMet = paymentAmount >= CORE_PAYMENT_AMOUNT_THRESHOLD
  const invoiceView = statusView(invoiceMet, '达标', '未达标')
  const paymentView = statusView(paymentMet, '达标', '未达标')
  // 五项评分=客情参考: 12 分线仅作"良好/参考"提示, 不再卡核心身份
  const scoreView = statusView(scoreTotal >= CORE_INDICATOR_SCORE_THRESHOLD, '良好', '参考')
  const coreView = coreType === 'core'
    ? { statusClass: 'ok', statusText: '已满足' }
    : coreType === 'quasi'
      ? { statusClass: 'warn', statusText: '争取中' }
      : { statusClass: 'warn', statusText: '无数据' }
  return {
    baseYear: toNumber(stage.baseYear),
    coreType,
    invoiceAmount,
    paymentAmount,
    invoiceMet,
    invoiceStatusClass: invoiceView.statusClass,
    invoiceStatusText: invoiceView.statusText,
    paymentMet,
    paymentStatusClass: paymentView.statusClass,
    paymentStatusText: paymentView.statusText,
    bothMet: invoiceMet && paymentMet,
    scoreTotal,
    scoreStatusClass: scoreView.statusClass,
    scoreStatusText: scoreView.statusText,
    coreStatusClass: coreView.statusClass,
    coreStatusText: coreView.statusText,
    coreSummaryText: coreType === 'core' ? '真核心' : coreType === 'quasi' ? '争核心' : '非核心'
  }
}

function buildRelationshipOptions(options, indicator, canEdit) {
  const score = toNumber((indicator || {}).relationshipScore)
  return options.map((item) => ({
    ...item,
    className: `relationship-option${score === item.value ? ' active' : ''}${canEdit ? '' : ' disabled'}`
  }))
}

function textOrDash(value) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function buildContactView(item) {
  return {
    ...item,
    nameText: item.name || item.contactName || '未命名联系人',
    phoneText: item.mobile || item.phone || '-'
  }
}

function buildLogView(item) {
  return {
    ...item,
    contentText: item.content || item.remark || '跟进记录',
    userText: `记录人：${item.userName || item.creatorName || item.createByName || '-'}`,
    timeText: item.createdAt || item.createTime || '-'
  }
}

function buildDetailView(data) {
  const detail = data.detail || null
  const loading = Boolean(data.loading)
  const indicator = data.indicator || {}
  const contacts = (data.contacts || []).map(buildContactView)
  const logs = (data.logs || []).map(buildLogView)
  // 【裁决 2026-10-10】判定金额源=阶段总额摘要(后端一处算好), 逐笔 invoiceSummary 不再参与判定
  const coreSummary = buildIndicatorSummary(data.stageSummary, indicator)
  const hasDetail = Boolean(detail)
  // 【C6】阶段总额/指标单路失败时对应区块显式置错误态，不再用 0/非核心伪装成真实数据
  const stageError = Boolean(data.stageError)
  const indicatorError = Boolean(data.indicatorError)
  const canEdit = Boolean(data.canEdit)
  const canDelete = Boolean(data.canDelete)
  const canViewContacts = Boolean(data.canViewContacts)
  const canViewFinance = Boolean(data.canViewFinance)
  const fromSea = data.from === 'sea'
  // 【mp-2 2026-10-17】公海客户不允许经客户删除接口删除（后端 delete() 会以
  // "该客户仍在公海池中" 拒绝）。入口侧按来源/状态双重识别，隐藏删除按钮并给出正确出口。
  const isSeaCustomer = fromSea || customerStatusLabel(detail && detail.status) === '公海'
  // 【D24 2026-10-04】指标行由 shared 的五项配置生成(此前字段清单在表单/维护页/详情三处各写一份)
  const indicatorRows = indicatorError
    ? [{ key: '__error__', label: '六项指标加载失败', valueText: '请稍后重试' }]
    : INDICATOR_ITEMS.map((item) => ({
      key: item.field,
      label: `${item.name}（${item.desc}）`,
      valueText: `${toNumber(indicator[item.field])} / 3`
    }))
  return {
    showLoading: loading,
    loadingText: '加载中...',
    hasDetail,
    showEmpty: !loading && !detail,
    showContactsSection: hasDetail && canViewContacts,
    showFinanceSection: hasDetail && canViewFinance,
    showCoreVerdictSection: hasDetail,
    // 【体检 P1 2026-10-12】延期按钮补 !fromSea 守卫(与释放/归档同款): 公海客户发起延期,
    // 审批通过会把客户拉回私海而池行残留——双归属; 后端提交侧已同步加守卫, 此处前端纵深
    showExtendAction: hasDetail && !fromSea,
    showRelationshipSection: hasDetail,
    showIndicatorSection: hasDetail,
    showIndicatorEditAction: hasDetail && canEdit,
    showEditAction: hasDetail && canEdit,
    showReleaseSection: hasDetail && !fromSea && canEdit,
    // 【闭环 2026-10-09 用户反馈】归档入口: 有开票/回款的客户不允许删除(议题五.3),停止跟进走归档;
    // 与释放同权限口径(canEdit),已归档客户不再显示(MP 默认口径列表也不会拉到归档客户,防御性判 status)
    showArchiveSection: hasDetail && !fromSea && canEdit && detail && detail.status !== 'archived',
    showDeleteSection: hasDetail && canDelete,
    // 删除按钮对公海客户隐藏；危险区保留说明，指向公海管理/捞取回私海（普通客户不受影响）
    showDeleteButton: hasDetail && canDelete && !isSeaCustomer,
    deleteCopyText: isSeaCustomer
      ? '该客户仍在公海池中，请先在公海管理删除，或捞取回私海后再操作。'
      : '删除客户会移除该客户资料，操作不可恢复。有开票/回款记录的客户不允许删除，停止跟进请使用「归档」。',
    isSeaCustomer,
    // 【2026-10-26 用户令】跟进时间线页移除后, 操作卡须至少剩一个可见入口才渲染(防空卡)
    showActionCard: hasDetail && (canViewContacts || !isAdmin() || canEdit),
    showContactsAction: hasDetail && canViewContacts,
    // 【L4 2026-10-12 测试报告】平台 admin 无集团归属, 分享接口必 403「无有效集团归属，禁止共享」
    // ——入口隐藏防误导(与 PC detail.vue canShareCustomer 同口径)
    showSharesAction: hasDetail && !isAdmin(),
    showLogAction: hasDetail && canEdit,
    titleText: detail ? detail.customerName : '',
    statusText: detail ? (detail.statusLabel || customerStatusLabel(detail.status)) : '',
    industryText: detail ? textOrDash(detail.industry) : '-',
    devTypeText: detail ? textOrDash(detail.devType) : '-',
    baseRows: detail ? [
      ['客户编号', detail.customerCode],
      ['开票名称', detail.invoiceName],
      ['产能(万吨)', detail.capacity],
      ['终端', detail.terminalLabel],
      ['集团', detail.groupName],
      ['分公司', detail.branchCompany],
      ['所属单位', detail.unitName],
      ['负责人', detail.ownerName],
      ['管理上级', detail.managerName],
      ['大区', detail.regionName],
      ['办事处', detail.officeName],
      ['所属部门', detail.deptName],
      ['省市县', detail.areaText],
      ['注册日', detail.registerDate],
      ['最近开票', detail.lastInvoiceDate],
      ['保护到期', detail.expireDate],
      ['地址', detail.address]
    ].map(([label, value]) => ({ label, valueText: textOrDash(value), isExpireRow: label === '保护到期' })) : [],
    remarkText: detail ? textOrDash(detail.remark) : '-',
    contacts,
    showNoContacts: contacts.length === 0,
    logs,
    showNoLogs: logs.length === 0,
    financeRows: stageError
      ? [{ key: '__error__', label: '开票回款数据加载失败', valueText: '请稍后重试' }]
      : (function () {
        // 【裁决 2026-10-10】开票回款卡改阶段口径: 年度金额取后端判定摘要;
        // 待回款 = 全部阶段开票合计 − 回款合计(历史逐笔已按年汇入阶段行, 口径连续)
        const rows = (data.stageSummary && data.stageSummary.rows) || []
        const totalInvoice = rows.reduce((sum, r) => sum + toNumber(r.invoiceAmount), 0)
        const totalPayment = rows.reduce((sum, r) => sum + toNumber(r.paymentAmount), 0)
        const yearTag = coreSummary.baseYear ? `(${coreSummary.baseYear})` : ''
        return [
          { key: 'invoice', label: `年度开票${yearTag}`, valueText: textOrDash(coreSummary.invoiceAmount || '') },
          { key: 'payment', label: `年度回款${yearTag}`, valueText: textOrDash(coreSummary.paymentAmount || '') },
          { key: 'unpaid', label: '待回款', valueText: textOrDash(Math.round((totalInvoice - totalPayment) * 100) / 100 || '') }
        ]
      })(),
    coreVerdictText: stageError ? '无法判定' : coreSummary.coreSummaryText,
    coreVerdictStatusText: stageError ? '加载失败' : coreSummary.coreStatusText,
    coreVerdictStatusClass: stageError ? 'warn' : coreSummary.coreStatusClass,
    coreVerdictRows: stageError
      ? [{ key: 'stage', label: '开票回款', valueText: '加载失败', thresholdText: '-', statusText: '暂时无法判定', statusClass: 'warn' }]
      : [
        { key: 'invoice', label: '年度开票', valueText: `${coreSummary.invoiceAmount} 万`, thresholdText: '≥100万/年', statusText: coreSummary.invoiceStatusText, statusClass: coreSummary.invoiceStatusClass },
        { key: 'payment', label: '年度回款', valueText: `${coreSummary.paymentAmount} 万`, thresholdText: '≥100万/年', statusText: coreSummary.paymentStatusText, statusClass: coreSummary.paymentStatusClass },
        { key: 'relationship', label: '客情评分', valueText: `${toNumber(indicator.relationshipScore)} / 3`, thresholdText: '0-3分·参考', statusText: `${toNumber(indicator.relationshipScore)}分`, statusClass: toNumber(indicator.relationshipScore) >= 2 ? 'ok' : 'warn' },
        { key: 'score', label: '五项评分', valueText: `${coreSummary.scoreTotal} / ${INDICATOR_MAX_SCORE}`, thresholdText: `满分${INDICATOR_MAX_SCORE}·不计判定`, statusText: coreSummary.scoreStatusText, statusClass: coreSummary.scoreStatusClass }
      ],
    relationshipScoreText: String(toNumber(indicator.relationshipScore)),
    indicatorRows
  }
}

Page({
  data: {
    id: '',
    from: '',
    detail: null,
    loading: false,
    contacts: [],
    logs: [],
    invoiceSummary: null,
    paymentSummary: null,
    stageSummary: null,
    stageError: false,
    indicator: {},
    indicatorError: false,
    indicatorSummary: buildIndicatorSummary({}, {}),
    // 【D25 2026-10-04】五项满分单一出处(shared)，wxml「x / 15」统一引用
    indicatorMaxScore: INDICATOR_MAX_SCORE,
    relationshipOptions: INITIAL_RELATIONSHIP_OPTIONS,
    relationshipOptionsView: buildRelationshipOptions(INITIAL_RELATIONSHIP_OPTIONS, {}, false),
    canEdit: false,
    canDelete: false,
    canViewFinance: false,
    canViewContacts: false,
    detailView: buildDetailView({ detail: null, contacts: [], logs: [], stageSummary: {}, indicator: {} })
  },

  setDetailData(patch) {
    const nextData = { ...this.data, ...patch }
    this.setData({ ...patch, detailView: buildDetailView(nextData) })
  },

  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    const demoMode = getApp().globalData.demoMode
    this.setDetailData({
      id: options.id || '',
      from: options.from || '',
      canEdit: demoMode || hasPermission('customer:edit'),
      canDelete: !demoMode && hasPermission('customer:delete'),
      canViewFinance: demoMode || hasPermission('finance:view'),
      canViewContacts: demoMode || hasPermission('contact:view'),
      relationshipOptionsView: buildRelationshipOptions(this.data.relationshipOptions, this.data.indicator, demoMode || hasPermission('customer:edit'))
    })
    this.loadRelationshipDict()
    this.loadDetail()
  },

  /** 【#10 2026-10-12】后台字典覆盖客情选项(幂等一次): 成功后按当前指标重建选中态, 失败静默保持兜底文案 */
  loadRelationshipDict() {
    if (this._relationshipDictLoaded || getApp().globalData.demoMode) return
    this._relationshipDictLoaded = true
    fetchRelationshipDictOptions(getDictData).then((labels) => {
      if (!labels) return
      const options = labels.map((label, value) => ({ label, value }))
      this.setData({
        relationshipOptions: options,
        relationshipOptionsView: buildRelationshipOptions(options, this.data.indicator, this.data.canEdit)
      })
    })
    // 【2026-10-29 字典接线】指标 03~06 选项随 core_indicator_03..06 字典覆盖(shared 原地改 INDICATOR_ITEMS),
    // 成功后用当前页面态重建详情视图(指标行文案即时跟随字典); 失败静默保持静态表
    fetchIndicatorDictOptions(getDictData).then(() => {
      this.setData({ detailView: buildDetailView(this.data) })
    })
  },

  /** 【M14 2026-10-04】从子页(编辑表单/联系人/财务/指标/跟进)返回时刷新详情。
   *  此前全模块仅客户列表页有 onShow，子表单保存返回后详情不刷新；
   *  对齐 customer/index.js 既有模式：首次 onShow 跳过(onLoad 已加载)，加载中不重复请求。 */
  onShow() {
    if (!this.didInitialShow) {
      this.didInitialShow = true
      return
    }
    if (!this.data.loading) this.loadDetail()
  },

  loadDetail() {
    if (!this.data.id) {
      wx.showToast({ title: '缺少客户ID', icon: 'none' })
      return
    }
    if (getApp().globalData.demoMode) {
      const saved = readDemoCustomers().find((item) => String(item.id) === String(this.data.id))
      const indicator = { relationshipScore: 0, indicator02: 0, indicator03: 0, indicator04: 0, indicator05: 0 }
      this.setDetailData({
        detail: normalizeDetail(saved || {
          id: this.data.id,
          customerCode: 'DEMO-' + this.data.id,
          customerName: this.data.from === 'sea' ? '公海客户样例' : '山西晋能集团中阳煤业',
          industry: '煤矿',
          capacity: '600',
          hasTerminal: 1,
          ownerName: this.data.from === 'sea' ? '原负责人' : '张三',
          managerName: '王总',
          deptName: '销售一部',
          officeName: '晋蒙业务部',
          devType: '成熟创新',
          status: this.data.from === 'sea' ? '公海只读' : '保护期',
          registerDate: '2026-01-01',
          lastInvoiceDate: '2026-08-12',
          expireDate: '2026-12-31',
          invoiceName: '山西晋能集团有限公司',
          groupName: '晋能集团',
          branchCompany: '晋能控股装备制造集团',
          unitName: '中阳煤业',
          province: '山西省',
          city: '吕梁市',
          district: '中阳县',
          address: '中阳县煤化工园区',
          remark: '移动端展示 PC 基础信息备注'
        }),
        invoiceSummary: { totalAmount: 120 },
        paymentSummary: { totalAmount: 105, unpaidAmount: 15 },
        stageSummary: {
          rows: [
            { periodLabel: '2026上半年', invoiceAmount: 120, paymentAmount: 105 }
          ],
          baseYear: 2026,
          annualInvoiceAmount: 120,
          annualPaymentAmount: 105,
          coreType: 'core'
        },
        indicator,
        relationshipOptionsView: buildRelationshipOptions(this.data.relationshipOptions, indicator, this.data.canEdit),
        indicatorSummary: buildIndicatorSummary(
          {
            rows: [{ periodLabel: '2026上半年', invoiceAmount: 120, paymentAmount: 105 }],
            baseYear: 2026,
            annualInvoiceAmount: 120,
            annualPaymentAmount: 105,
            coreType: 'core'
          },
          indicator
        )
      })
      return
    }

    this.setDetailData({ loading: true })
    getCustomer(this.data.id).then((detail) => {
      this.setDetailData({ detail: normalizeDetail(detail || {}) })
      // 【C6】四路并发各自 settle：任一失败只降级对应区块，其余成功数据照常落稿
      const contactsRequest = this.data.canViewContacts
        ? settleRequest(getCustomerContacts(this.data.id))
        : Promise.resolve({ ok: true, value: [] })
      // 【裁决 2026-10-10】财务摘要改阶段总额判定口径(后端含三态), 逐笔 summary 请求下线
      const stageRequest = this.data.canViewFinance
        ? settleRequest(getCustomerStageAmounts(this.data.id))
        : Promise.resolve({ ok: true, value: {} })
      return Promise.all([
        contactsRequest,
        settleRequest(getCustomerLogs(this.data.id)),
        stageRequest,
        settleRequest(getCustomerIndicator(this.data.id))
      ])
    }).then(([contactsResult, logsResult, stageResult, indicatorResult]) => {
      const emptyStage = { rows: [], coreType: '', baseYear: 0, annualInvoiceAmount: 0, annualPaymentAmount: 0 }
      const failedSections = []
      // 失败区块用一条错误行占位：wxml 无独立错误态字段，复用现有行渲染且不再误显"暂无"
      const contacts = contactsResult.ok
        ? (contactsResult.value || [])
        : [{ id: '__error__', contactName: '联系人加载失败，请稍后重试', mobile: '-' }]
      if (!contactsResult.ok) failedSections.push('联系人')
      const logs = logsResult.ok
        ? (logsResult.value || [])
        : [{ id: '__error__', content: '跟进日志加载失败，请稍后重试', createdAt: '-' }]
      if (!logsResult.ok) failedSections.push('跟进日志')
      // 阶段失败保留上一次成功摘要(避免用 0 冒充判定), 并置 stageError 让相关卡片显式报错
      const stageSummary = stageResult.ok ? (stageResult.value || emptyStage) : (this.data.stageSummary || emptyStage)
      if (!stageResult.ok) failedSections.push('开票回款')
      // 指标失败同样保留上次成功指标, 不用空对象把五项评分显示成 0
      const normalizedIndicator = indicatorResult.ok ? (indicatorResult.value || {}) : (this.data.indicator || {})
      if (!indicatorResult.ok) failedSections.push('六项指标')
      this.setDetailData({
        contacts,
        logs,
        stageSummary,
        stageError: !stageResult.ok,
        indicator: normalizedIndicator,
        indicatorError: !indicatorResult.ok,
        relationshipOptionsView: buildRelationshipOptions(this.data.relationshipOptions, normalizedIndicator, this.data.canEdit),
        indicatorSummary: buildIndicatorSummary(stageSummary, normalizedIndicator)
      })
      if (failedSections.length) {
        showErrorWithRetry({ message: `${failedSections.join('、')}加载失败` }, '部分数据加载失败', () => this.loadDetail())
      }
    }).catch((error) => showError(error, '无权查看或客户不存在')).finally(() => {
      this.setDetailData({ loading: false })
    })
  },

  editCustomer() {
    if (!hasPermission('customer:edit') && !getApp().globalData.demoMode) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/form/index?id=${this.data.id}` })
  },


  submitExtendApproval() {
    if (!this.data.id) return
    if (!getApp().globalData.demoMode && !hasPermission('approval:view')) return wx.showToast({ title: '没有审批提交权限', icon: 'none' })
    if (getApp().globalData.demoMode) return wx.showToast({ title: '延期申请已提交', icon: 'success' })
    submitApproval({ type: 'extend', customerId: String(this.data.id), actionAfterApprove: '保护期+360天' }).then(() => {
      wx.showToast({ title: '延期申请已提交', icon: 'success' })
    }).catch((error) => showError(error, '提交失败'))
  },

  releaseToSea() {
    if (!this.data.id || !hasPermission('customer:edit')) return wx.showToast({ title: '没有释放权限', icon: 'none' })
    wx.showModal({
      title: '释放到公海',
      content: '确认将该客户释放到公海池？',
      success: (res) => {
        if (!res.confirm) return
        if (getApp().globalData.demoMode) return wx.showToast({ title: '释放申请已提交', icon: 'success' })
        releaseCustomer(this.data.id, '移动端释放').then(() => {
          wx.showToast({ title: '已释放到公海', icon: 'success' })
          this.goBack()
        }).catch((error) => showError(error, '释放失败'))
      }
    })
  },

  // 【闭环 2026-10-09 用户反馈】有开票/回款的客户拒删,停止跟进的正确出口=归档
  archiveCurrentCustomer() {
    if (!this.data.id || !hasPermission('customer:edit')) return wx.showToast({ title: '没有归档权限', icon: 'none' })
    wx.showModal({
      title: '归档客户',
      content: `归档后“${(this.data.detail && this.data.detail.customerName) || '该客户'}”默认隐藏、不参与统计与提醒；如需恢复请在电脑端客户列表勾选“显示已归档”操作。确认归档？`,
      success: (res) => {
        if (!res.confirm) return
        if (getApp().globalData.demoMode) return wx.showToast({ title: '归档已提交', icon: 'success' })
        archiveCustomer(this.data.id).then(() => {
          wx.showToast({ title: '客户已归档', icon: 'success' })
          setTimeout(() => this.goBack(), 300)
        }).catch((error) => showError(error, '归档失败'))
      }
    })
  },

  deleteCurrentCustomer() {
    if (!this.data.id || !this.data.canDelete) return wx.showToast({ title: '没有客户删除权限', icon: 'none' })
    if (this.data.detailView && this.data.detailView.isSeaCustomer) return wx.showToast({ title: '请先在公海管理删除/捞取回私海', icon: 'none' })
    wx.showModal({
      title: '删除客户',
      content: `确认删除“${(this.data.detail && this.data.detail.customerName) || '该客户'}”？删除后不可恢复。`,
      success: (res) => {
        if (!res.confirm) return
        deleteCustomer(this.data.id).then(() => {
          wx.showToast({ title: '客户已删除', icon: 'success' })
          // 【PR-12 滚动位置保持】返回原列表实例(onShow 自刷新): redirectTo 会复制一个新列表页
          // 垫在栈里,返回手势回到旧列表(已删客户仍在), 且滚动位置丢失
          setTimeout(() => this.goBack(), 300)
        }).catch((error) => showError(error, '删除客户失败'))
      }
    })
  },

  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack()
    } else if (this.data.from === 'sea') {
      wx.navigateTo({ url: '/pages/sea/index' })
    } else {
      wx.navigateTo({ url: '/pages/customer/index' })
    }
  }
  ,openFinance() {
    if (!this.data.canViewFinance) return wx.showToast({ title: '没有财务查看权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/finance/index?id=${this.data.id}` })
  },
  openContacts() {
    if (!this.data.canViewContacts) return wx.showToast({ title: '没有联系人查看权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/contact/index?id=${this.data.id}` })
  },
  openShares() {
    if (!this.data.detail) return
    wx.navigateTo({ url: `/pages/customer/share/index?id=${this.data.id}` })
  },
  openLogForm() {
    if (!this.data.canEdit) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/log/index?id=${this.data.id}` })
  },
  openIndicatorForm() {
    if (!this.data.canEdit) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/indicator/index?id=${this.data.id}` })
  },
  selectRelationship(event) {
    if (!this.data.canEdit) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    const score = Number(event.currentTarget.dataset.score) || 0
    if (getApp().globalData.demoMode) {
      const indicator = { ...(this.data.indicator || {}), relationshipScore: score }
      this.setDetailData({
        indicator,
        relationshipOptionsView: buildRelationshipOptions(this.data.relationshipOptions, indicator, this.data.canEdit),
        // 【C5】签名是 (stageSummary, indicator)：判定金额/核心状态一律取阶段总额摘要，与同页核心判定卡同源
        indicatorSummary: buildIndicatorSummary(this.data.stageSummary || {}, indicator)
      })
      return wx.showToast({ title: '客情已保存', icon: 'success' })
    }
    saveCustomerRelationship(this.data.id, score)
      .then(() => getCustomerIndicator(this.data.id))
      .then((indicator) => {
        const normalizedIndicator = indicator || {}
        this.setDetailData({
          indicator: normalizedIndicator,
          relationshipOptionsView: buildRelationshipOptions(this.data.relationshipOptions, normalizedIndicator, this.data.canEdit),
          // 【C5】同上：传阶段摘要而非已下线的逐笔 summary，否则金额/核心判定归零
          indicatorSummary: buildIndicatorSummary(this.data.stageSummary || {}, normalizedIndicator)
        })
        wx.showToast({ title: '客情已保存', icon: 'success' })
      })
      .catch((error) => showError(error, '客情关系保存失败'))
  }
})
