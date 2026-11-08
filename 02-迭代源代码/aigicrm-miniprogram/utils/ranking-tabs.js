// 排行页注册码并集逻辑（纯函数模块, 零 wx 依赖, 供 pages/ranking 与单元测试共同引用）。
// 【2026-11-04 注册表对齐】页内三榜各绑权限中心注册码（与 PC 菜单同源，不发明不特判）：
//   学分榜=score:view / 客户榜=statistics:view。
// 【11-08 审计项4】激励榜=incentive:stats:view∨incentive:rank:view(任一)——与后端数据面守卫
// 对齐(IncentiveController /scoreboard hasAnyAuthority('incentive:stats:view','incentive:rank:view'));
// 原挂 incentive:view 与后端不一致: 持 incentive:view 而无 stats/rank 双码的角色看得见页签但
// 数据必 403, 反向持 stats/rank 而无 incentive:view 的角色整页签被误藏。数组=any-of 语义。
// 入口=注册码并集（任一命中可进），默认页签=第一个有权页签，各页签数据按各自码加载。
// 取代 2026-10-17 mp-3 的 statistics:view 单码补丁（该补丁使 score:view 持有者如行政
// 在手机端丢失整个排行入口，与 PC 形成双端漂移——详见提交 2b129496）。
// 单元测试: scripts/test-miniprogram-ranking-tabs.js（dev-verify contracts 链内运行）。

// 注册码映射: 键=页签标识, 值=权限中心注册码或码数组(数组=any-of 任一命中;
// 勿改动值——改动=权限语义变更, 须与 PC 菜单/后端守卫三方同步)
const MAIN_TAB_PERMISSIONS = {
  score: 'score:view',
  customer: 'statistics:view',
  incentive: ['incentive:stats:view', 'incentive:rank:view']
}

// 页签顺序（默认页签=该顺序中第一个有权者）
// 【2026-11-06 整体检查 X2 已核】入口拆分后 incentive 页签无独立入口卡, 仅存于无参全榜模式
// (应用中心激励排行的家=四项激励·激励统计卡 rank:view pane)。
// 【11-08 审计项4】11-06 注释预告的"给本页签改挂 rank:view 三方同步"由本批落地:
// 页签码=后端数据面双码 any-of(stats:view∨rank:view), 勿再挂回 incentive:view。
const MAIN_TAB_ORDER = ['score', 'customer', 'incentive']

// 页签标题（与 PC 菜单名对齐: 学分排行/客户排行; 激励排行=MP 排行页对激励个人榜的页面级命名）
const MAIN_TAB_TITLES = {
  score: '学分排行',
  customer: '客户排行',
  incentive: '激励排行'
}

// 【2026-11-06 用户令"客户排行归客户模块 学分排行归学分模块"】页签归属 PC 菜单组 + 页签副题:
// 应用中心入口拆两卡(客户经营·客户排行 / 学分管理·学分排行)带 ?tab= 直达, 页标题/副标题随页签动态对齐。
// 值=PC 菜单组名(勿改动——改动=菜单归属语义变更, 须与 PC 菜单/后端守卫三方同步)
const MAIN_TAB_PARENTS = {
  score: '学分管理',
  customer: '客户管理',
  incentive: '四项激励'
}

const MAIN_TAB_SUBS = {
  score: '个人与部门学分榜',
  customer: '客户四项排行',
  incentive: '四项服务个人排行'
}

// 单码或码数组(any-of)统一判定: 数组任一命中即放行(11-08 审计项4 激励榜=stats:view∨rank:view)
function satisfiesPermission(hasPermissionFn, codes) {
  return Array.isArray(codes) ? codes.some((code) => hasPermissionFn(code)) : hasPermissionFn(codes)
}

// 按注入的权限判断函数计算各页签可见性。hasPermissionFn: (code) => boolean; demoMode 时恒放开。
function computeCanTabs(hasPermissionFn, demoMode) {
  const can = {}
  for (const key of MAIN_TAB_ORDER) {
    can[key] = Boolean(demoMode) || satisfiesPermission(hasPermissionFn, MAIN_TAB_PERMISSIONS[key])
  }
  return can
}

// 默认页签=MAIN_TAB_ORDER 中第一个有权者; 全无权返回 null(调用方应弹回首页)。
function firstAllowedTab(canTabs) {
  for (const key of MAIN_TAB_ORDER) {
    if (canTabs[key]) return key
  }
  return null
}

// 无权页签不渲染。返回 [{key,title}]，保持 MAIN_TAB_ORDER 顺序。
function visibleMainTabs(canTabs) {
  return MAIN_TAB_ORDER
    .filter((key) => canTabs[key])
    .map((key) => ({ key, title: MAIN_TAB_TITLES[key] }))
}

module.exports = {
  MAIN_TAB_PERMISSIONS,
  MAIN_TAB_ORDER,
  MAIN_TAB_TITLES,
  MAIN_TAB_PARENTS,
  MAIN_TAB_SUBS,
  satisfiesPermission,
  computeCanTabs,
  firstAllowedTab,
  visibleMainTabs
}
