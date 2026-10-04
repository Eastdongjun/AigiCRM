// 字典接口失败时的前端兜底选项（唯一来源，2026-10-04）
//
// 此前「行业」兜底列表在三处各内联一份完整副本（useCustomerForm/useCustomerList/
// useIncentiveUpload 的 getDictData('industry') 失败分支），口径易漂移；抽出共享常量。
// 注：views/sea/index.vue 还有一份 5 项（无「其他」）的公海页私有兜底，属页面私有口径，
// 不在本共享范围内，如后续统一应先核对公海页业务口径。

/** 行业字典(getDictData('industry'))加载失败时的兜底选项 */
export const INDUSTRY_FALLBACK_OPTIONS = ['煤矿', '化工', '电力', '洗煤厂', '有色', '其他']
