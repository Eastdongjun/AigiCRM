// 【D10 2026-10-04】五大类产品目录（大类 → 小类）的唯一来源。
// 原先 views/incentive/search.vue（查询筛选）与 composables/useIncentiveUpload.js（上传弹窗）
// 各自完整硬编码一份同样的映射，两处口径一旦调整极易漂移（一处理了一处漏）。
// 注：放在 views/incentive/ 而非 src/utils/ 是本批次改动文件权限边界所致——
// utils 目录不在授权清单内，两个消费方（incentive 视图 / useIncentiveUpload 组合式）均可引用本文件。
export const PRODUCT_CATS = {
  '涂层类': ['防腐', '加固', '钢结构', '煤气管道', '其他'],
  '防滑板类': ['公共区', '装置区'],
  '机封类': ['水泵', '渣泵', '介质泵'],
  '密封类': ['静密封', '动密封', '橡胶密封'],
  '润滑类': ['润滑油', '润滑脂', '润滑剂'],
}

// 大类键序列（下拉顺序），与 PRODUCT_CATS 保持同源
export const PRODUCT_CAT_KEYS = Object.keys(PRODUCT_CATS)
