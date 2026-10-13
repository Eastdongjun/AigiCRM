/** 客户模块组内共享常量与工具（2026-10-04 评审 D24/D25/D27 抽取）。
 *  此前五项指标配置、客户状态文案、表单返回兜底、核心客户阈值、筛选草稿
 *  字段清单在组内 2-5 个页面各有一份完全相同副本，漏改一处即口径漂移，
 *  统一收敛到本文件作为单一出处；各页面只引用，不再各自维护副本。 */

// ===== 客户状态文案（列表页与详情页共用同一份映射，口径勿在页面内另写） =====

function normalizeStatusKey(status) {
  return status === undefined || status === null ? '' : String(status).trim().toLowerCase()
}

function customerStatusLabel(status) {
  const labels = {
    normal: '正常',
    warning: '预警',
    protected: '保护期',
    expired: '超期',
    sea: '公海',
    active: '正常',
    available: '公海'
  }
  return labels[normalizeStatusKey(status)] || status || '正常'
}

function customerStatusTone(status) {
  const label = customerStatusLabel(status)
  if (label === '保护期' || label === '预警') return 'blue-accent'
  if (label === '超期') return 'red-accent'
  return 'green-accent'
}

// ===== 客情五项指标配置（客户表单、指标维护页、客户详情共用；与 PC utils/customer-fields.js 同口径） =====

const INDICATOR_FIELDS = ['relationshipScore', 'indicator02', 'indicator03', 'indicator04', 'indicator05']

// 【#10 2026-10-12】客情·商务关系深度选项: 新文案为兜底(与后台字典 relationship_depth 同口径),
// 页面可用 fetchRelationshipDictOptions 用字典数据实时覆盖 INDICATOR_ITEMS[0].options;
// 【F9 2026-10-13】分值口径对齐 PC(utils/customer-fields.js): 档位下标必须来自 dictValue,
// 后台调 sort_order 或停用中间档不再造成 MP 静默存成另一档分值。
const RELATIONSHIP_OPTIONS = ['商务少', '纯商务', '感情商务', '多样性']

/** 字典行 → 客情选项数组; 空返回 null(调用方保持兜底文案)。
 * 下标=dictValue(0-3): 行缺失的档位用兜底文案补位(防下标前移), 脏值(非0-3整数)丢弃。 */
function relationshipOptionsFromDict(rows) {
  const valid = (rows || []).filter((r) => r && r.dictValue !== null && r.dictValue !== undefined && r.status !== 0)
  if (!valid.length) return null
  const byValue = {}
  let maxValue = -1
  valid.forEach((r) => {
    const v = Number(r.dictValue)
    if (!Number.isInteger(v) || v < 0 || v > 3) return
    if (r.dictLabel) byValue[v] = r.dictLabel
    if (v > maxValue) maxValue = v
  })
  if (maxValue < 0) return null
  const labels = []
  for (let v = 0; v <= maxValue; v++) labels.push(byValue[v] || RELATIONSHIP_OPTIONS[v] || `档位${v}`)
  return labels
}

/** 拉后台字典覆盖客情选项(传入 api.getDictData; 失败静默保持兜底) */
function fetchRelationshipDictOptions(getDictData) {
  return getDictData('relationship_depth')
    .then((rows) => {
      const labels = relationshipOptionsFromDict(rows)
      if (labels) INDICATOR_ITEMS[0].options = labels
      return labels
    })
    .catch(() => null)
}

const INDICATOR_ITEMS = [
  { field: 'relationshipScore', name: '高层', desc: '客情程度', options: RELATIONSHIP_OPTIONS.slice() },
  { field: 'indicator02', name: '渠道', desc: '编码数量', options: ['一两个码', '有效编码', '多种编码', '全部畅通'] },
  { field: 'indicator03', name: '基层', desc: '分厂车间', options: ['一两个', '1/3分厂', '2/3分厂', '全部部门'] },
  { field: 'indicator04', name: '产品', desc: '密封润滑防腐', options: ['一两个', '一大类', '两大类', '三大类'] },
  { field: 'indicator05', name: '项目', desc: '项目资金', options: ['日常计划', '改造大修', '安全环保', '新建配套'] }
]

const sumIndicatorScore = (form) => INDICATOR_FIELDS.reduce((sum, field) => sum + (Number(form && form[field]) || 0), 0)

/** 指标选项视图（表单/维护页共用：value = 当前分值，optionsView 带 active 类名） */
const buildIndicatorItems = (form) => INDICATOR_ITEMS.map((item) => {
  const value = Number(form && form[item.field]) || 0
  return {
    ...item,
    value,
    optionsView: item.options.map((label, score) => ({
      label,
      score,
      className: value === score ? 'score-option active' : 'score-option'
    }))
  }
})

// ===== 核心客户判定阈值（单一出处；与 PC 端 aigicrm-ui/src/utils/customer-fields.js 同口径） =====

/** 【裁决 2026-10-10】核心客户三态=纯金额门槛(与后端 CustomerService/StatisticsQueryService 同口径):
 *  真核心 core   = 判定年度(最近有数据年, 全年行优先否则上半年+下半年合计)开票、回款均 >= 100 万;
 *  争核心 quasi  = 有阶段数据但未双达标;
 *  非核心 ''     = 无任何有数据阶段行。
 *  五项评分不再参与判定(保留为客情参考, 12 分线仅作"良好/参考"提示);
 *  判定结果由后端按阶段总额(biz_customer_stage_amount)一处算好回传, 前端只消费 coreType。 */
const CORE_INVOICE_AMOUNT_THRESHOLD = 100
const CORE_PAYMENT_AMOUNT_THRESHOLD = 100
const CORE_INDICATOR_SCORE_THRESHOLD = 12

/** 五项评分满分（5 项 × 3 分），详情页「x / 15」展示用 */
const INDICATOR_MAX_SCORE = 15

/** 核心三态展示标签(与后端 core_type 枚举一一对应) */
function coreTypeLabel(coreType) {
  return coreType === 'core' ? '真核心' : coreType === 'quasi' ? '争核心' : '非核心'
}

/** 表单/指标维护页的门槛提示文案：由常量拼装，wxml 不再硬编码数字。
 *  【2026-10-10】核心判定已改纯金额三态, 此处文案同步(五项为客情参考)。 */
const CORE_THRESHOLD_TIP = `核心判定（2026-10-10 起）：年度开票、回款均 >= ${CORE_INVOICE_AMOUNT_THRESHOLD} 万为真核心，有阶段数据未双达标为争核心。五项评分为客情参考，不参与判定。`

// ===== 表单页返回兜底（各表单 goBack 共用；栈内多页正常返回，直达页跳回 fallback） =====

/** pages 栈仅一页（分享/直达进入）时 wx.navigateBack 无处可退，跳回 fallbackUrl 兜底 */
function navigateBackWithFallback(fallbackUrl) {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  if (pages.length > 1) return wx.navigateBack()
  wx.navigateTo({ url: fallbackUrl })
}

// ===== 客户高级筛选草稿（筛选页写入 ↔ 客户列表页读取共用同一 key 与字段清单） =====

/** 此前 'customerFilterDraft' 魔法串与字段清单在筛选页/列表页共三处各写一份，
 *  漏改一个字段即静默丢筛选条件；storage key 与字段清单统一由此处提供。 */
const CUSTOMER_FILTER_DRAFT_KEY = 'customerFilterDraft'

/** 草稿字段清单：筛选页保存按此挑选落盘，列表页恢复按此读取 */
const CUSTOMER_FILTER_DRAFT_FIELDS = ['keyword', 'devType', 'industry', 'status', 'protect', 'province', 'city', 'deptId', 'ownerId']

/** 空的高级筛选状态（列表页初始值与「清空」共用，字段与草稿清单保持一致） */
function emptyAdvancedFilter() {
  return { industry: '', status: '', deptId: '', ownerId: '', protect: '', province: '', city: '' }
}

/** 筛选页保存草稿：按字段清单挑值落存储（清单外字段不落盘） */
function saveFilterDraft(draft) {
  const source = draft || {}
  const picked = {}
  CUSTOMER_FILTER_DRAFT_FIELDS.forEach((field) => {
    picked[field] = source[field] === undefined || source[field] === null ? '' : source[field]
  })
  wx.setStorageSync(CUSTOMER_FILTER_DRAFT_KEY, picked)
}

/** 列表页读取草稿（无草稿返回 null，由调用方决定是否刷新） */
function readFilterDraft() {
  const draft = wx.getStorageSync(CUSTOMER_FILTER_DRAFT_KEY)
  return draft || null
}

function clearFilterDraft() {
  wx.removeStorageSync(CUSTOMER_FILTER_DRAFT_KEY)
}

/** 草稿 → 列表页筛选状态（含 hasAdvancedFilter 判定；字段缺失按空处理） */
function parseFilterDraft(draft) {
  const source = draft || {}
  const advancedFilter = emptyAdvancedFilter()
  Object.keys(advancedFilter).forEach((key) => { advancedFilter[key] = source[key] || '' })
  return {
    keyword: source.keyword || '',
    filter: source.devType || '全部',
    advancedFilter,
    hasAdvancedFilter: Object.keys(advancedFilter).some((key) => advancedFilter[key])
  }
}

/** 列表请求参数拼装（与筛选字段清单同源，列表页 buildCustomerParams 调用）：
 *  keyword/devType 为基础筛选；advancedFilter 为高级筛选；
 *  快捷保护筛选 statusFilter 与高级筛选 protect 同属保护状态域，合并传 protectionStatus。 */
function buildCustomerFilterParams({ keyword, filter, statusFilter, advancedFilter }) {
  const params = {
    keyword: (keyword || '').trim() || undefined,
    devType: filter === '全部' ? undefined : filter
  }
  const advanced = advancedFilter || emptyAdvancedFilter()
  if (advanced.industry) params.industry = advanced.industry
  // 【对齐 PC 2026-09-21】status 域(normal/warning)与 protectionStatus 域(active/expiring/expired)是两个不同字段,
  // 此前快捷保护筛选误传 status=active → 后端按 status 等值匹配恒空; 现分开传参
  if (advanced.status) params.status = advanced.status
  if (advanced.deptId) params.deptId = advanced.deptId
  if (advanced.ownerId) params.ownerId = advanced.ownerId
  const protects = [statusFilter, advanced.protect].filter(Boolean)
  if (protects.length) params.protectionStatus = [...new Set(protects)].join(',')
  if (advanced.province) params.province = advanced.province.trim()
  if (advanced.city) params.city = advanced.city.trim()
  return params
}

module.exports = {
  customerStatusLabel,
  customerStatusTone,
  INDICATOR_FIELDS,
  INDICATOR_ITEMS,
  RELATIONSHIP_OPTIONS,
  relationshipOptionsFromDict,
  fetchRelationshipDictOptions,
  sumIndicatorScore,
  buildIndicatorItems,
  CORE_INVOICE_AMOUNT_THRESHOLD,
  CORE_PAYMENT_AMOUNT_THRESHOLD,
  CORE_INDICATOR_SCORE_THRESHOLD,
  INDICATOR_MAX_SCORE,
  CORE_THRESHOLD_TIP,
  coreTypeLabel,
  navigateBackWithFallback,
  CUSTOMER_FILTER_DRAFT_KEY,
  CUSTOMER_FILTER_DRAFT_FIELDS,
  emptyAdvancedFilter,
  saveFilterDraft,
  readFilterDraft,
  clearFilterDraft,
  parseFilterDraft,
  buildCustomerFilterParams
}
