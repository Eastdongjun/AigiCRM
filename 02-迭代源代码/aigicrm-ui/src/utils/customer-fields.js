// 客户业务字段字典（2026-09-13）
//
// 需求：新增/编辑表单此前缺「终端」，只有 Excel 导入能写；列表与详情也没有展示位。
// 这里集中存放该字段的取值与中文口径，供表单（选项）、列表与详情（展示）共用，
// 避免在三个组件里各写一份 1/0 判断。

/** 终端客户选项（与导入模板一致：1=是 0=否） */
export const TERMINAL_OPTIONS = [
  { label: '否', value: 0 },
  { label: '是', value: 1 }
]

/** 终端字段中文标签；null/undefined/其它脏值统一显示 '—' */
export function terminalLabel(value) {
  if (value === 1 || value === '1' || value === true) return '是'
  if (value === 0 || value === '0' || value === false) return '否'
  return '—'
}

// ===== 客情五项评分（2026-09-13）=====
//
// 需求：核心客户判定 = 开票/回款各 ≥100 万（硬门槛）+ 五项评分合计 ≥12 分。
// 五项评分此前只能在「客户详情」页录入，新建/编辑客户时无入口，
// 导致建档后综合得分与核心客户判定长期为 0，须有人事后进详情页补录。
// 口径唯一来源：这里集中定义字段名与中文标签，客户详情（展示/评分）与
// 新增编辑表单（录入）共用，避免两处各写一份标签导致口径漂移。

/** 五项评分满分（5 项 × 3 分） */
export const INDICATOR_MAX_SCORE = 15

/** 核心客户五项评分门槛（合计 ≥12 分，且开票/回款各 ≥100 万） */
export const INDICATOR_CORE_THRESHOLD = 12

/** 客情·商务关系深度选项(高层/客情程度 0-3 分)。
 * 【#10 2026-10-12】文案单一出处 + 后台字典 relationship_depth 优先: 页面不再各自硬编码,
 * 运行时 fetchRelationshipOptions() 拉字典(登录即可读)覆盖, 本表为字典缺行/接口失败的兜底。
 * 分值 value(0-3) 与库内存量数字不变, 切文案不影响统计与排行分值。 */
export const RELATIONSHIP_OPTIONS_FALLBACK = [
  { dictValue: '0', dictLabel: '商务少' },
  { dictValue: '1', dictLabel: '纯商务' },
  { dictValue: '2', dictLabel: '感情商务' },
  { dictValue: '3', dictLabel: '多样性' }
]

/** 拉取后台客情字典(按 sort_order 排序;空/异常回退兜底文案) */
export async function fetchRelationshipOptions(getDictData) {
  try {
    const rows = await getDictData('relationship_depth')
    const options = (rows || [])
      .filter((r) => r && r.dictValue !== null && r.dictValue !== undefined && r.status !== 0)
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
      .map((r) => ({ dictValue: String(r.dictValue), dictLabel: r.dictLabel }))
      .filter((r) => r.dictLabel)
    return options.length ? options : RELATIONSHIP_OPTIONS_FALLBACK
  } catch (e) {
    return RELATIONSHIP_OPTIONS_FALLBACK
  }
}

/** 五项评分字段定义：options 的 dictValue 为字符串（与后端字典返回口径一致），分值 0-3 */
export const INDICATOR_FIELDS = [
  {
    code: '02',
    field: 'relationshipScore',
    label: '高层（客情程度）',
    options: RELATIONSHIP_OPTIONS_FALLBACK
  },
  {
    code: '03',
    field: 'indicator02',
    label: '渠道（编码数量）',
    options: [
      { dictValue: '0', dictLabel: '一两个码' },
      { dictValue: '1', dictLabel: '有效编码' },
      { dictValue: '2', dictLabel: '多种编码' },
      { dictValue: '3', dictLabel: '全部畅通' }
    ]
  },
  {
    code: '04',
    field: 'indicator03',
    label: '基层（分厂车间）',
    options: [
      { dictValue: '0', dictLabel: '一两个' },
      { dictValue: '1', dictLabel: '1/3分厂' },
      { dictValue: '2', dictLabel: '2/3分厂' },
      { dictValue: '3', dictLabel: '全部部门' }
    ]
  },
  {
    code: '05',
    field: 'indicator04',
    label: '产品（密封润滑防腐）',
    options: [
      { dictValue: '0', dictLabel: '一两个' },
      { dictValue: '1', dictLabel: '一大类' },
      { dictValue: '2', dictLabel: '两大类' },
      { dictValue: '3', dictLabel: '三大类' }
    ]
  },
  {
    code: '06',
    field: 'indicator05',
    label: '项目（项目资金）',
    options: [
      { dictValue: '0', dictLabel: '日常计划' },
      { dictValue: '1', dictLabel: '改造大修' },
      { dictValue: '2', dictLabel: '安全环保' },
      { dictValue: '3', dictLabel: '新建配套' }
    ]
  }
]

/** 五项评分合计（0~15）；缺失/脏值按 0 计，供详情页与新增编辑表单共用 */
export function sumIndicatorScore(source) {
  return INDICATOR_FIELDS.reduce((sum, f) => sum + (Number(source?.[f.field]) || 0), 0)
}
