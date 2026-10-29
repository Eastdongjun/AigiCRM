// 【D10 2026-10-04】五大类产品目录（大类 → 小类）的兜底来源。
// 原先 views/incentive/search.vue（查询筛选）与 composables/useIncentiveUpload.js（上传弹窗）
// 各自完整硬编码一份同样的映射，两处口径一旦调整极易漂移（一处理了一处漏）。
// 注：放在 views/incentive/ 而非 src/utils/ 是本批次改动文件权限边界所致——
// utils 目录不在授权清单内，两个消费方（incentive 视图 / useIncentiveUpload 组合式）均可引用本文件。
//
// 【2026-10-29 产品目录归树批(用户裁决: 按树规划+唯一入口)】运行时权威来源改为分类树
// (getCategoryTree 的"产品试点/trial"分支: 二级=五大类、三级=小类, 系统设置→激励类别管理维护);
// 本常量降级为树缺行/接口失败的兜底。消费方统一经 buildProductCatsFromTree() 取目录。
export const PRODUCT_CATS = {
  '涂层类': ['防腐', '加固', '钢结构', '煤气管道', '其他'],
  '防滑板类': ['公共区', '装置区'],
  '机封类': ['水泵', '渣泵', '介质泵'],
  '密封类': ['静密封', '动密封', '橡胶密封'],
  '润滑类': ['润滑油', '润滑脂', '润滑剂'],
}

// 大类键序列（下拉顺序），与 PRODUCT_CATS 保持同源
export const PRODUCT_CAT_KEYS = Object.keys(PRODUCT_CATS)

// 产品试点分支定位: 编码优先(trial), 名称兜底(防手工改码)
const TRIAL_CODES = ['trial']
const TRIAL_NAMES = ['产品试点']

/**
 * 从分类树派生产品目录(大类→小类)。树形: [{categoryName/categoryCode, children:[...]}]。
 * 取"产品试点"分支: 二级(有子节点者)为大类, 其 children 为小类。
 * 返回 { cats, keys }；树缺失/无三级结构(如旧库未跑 seed 迁移)返回 null——调用方保持兜底常量。
 */
export function buildProductCatsFromTree(tree) {
  const trial = (tree || []).find((node) => node && (
    TRIAL_CODES.includes(node.categoryCode) || TRIAL_NAMES.includes(node.categoryName || node.name)
  ))
  const cats = {}
  const keys = []
  ;(((trial && trial.children) || [])).forEach((child) => {
    const name = (child && (child.categoryName || child.name)) || ''
    if (!name) return
    const subs = ((child && child.children) || [])
      .map((g) => (g && (g.categoryName || g.name)) || '')
      .filter(Boolean)
    if (!subs.length) return
    keys.push(name)
    cats[name] = subs
  })
  return keys.length ? { cats, keys } : null
}
