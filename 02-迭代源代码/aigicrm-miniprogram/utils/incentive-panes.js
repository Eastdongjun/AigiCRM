// 激励统计页 pane 门禁逻辑（纯函数模块, 零 wx 依赖, 供 pages/incentive 与单元测试共同引用）。
// 【2026-11-04 用户令"逐功能建码"】激励统计页两个 pane 各绑权限中心注册码：
//   统计 pane=incentive:stats:view（激励统计查看）/ 排行 pane=incentive:rank:view（激励排行查看）。
// 入口=注册码并集（任一命中可进），默认 pane=第一个有权 pane，无权 pane 不渲染不发请求。
// 单元测试: scripts/test-miniprogram-incentive-panes.js（dev-verify contracts 链内运行）。

// 注册码映射: 键=pane 标识, 值=权限中心注册码（勿改动值——改动=权限语义变更, 须与菜单/后端守卫三方同步）
const STATS_PANE_PERMISSIONS = {
  stats: 'incentive:stats:view',
  ranking: 'incentive:rank:view'
}

// pane 顺序（默认 pane=该顺序中第一个有权者）
const STATS_PANE_ORDER = ['stats', 'ranking']

// pane 标签（与页面页签文案一致）
const STATS_PANE_LABELS = {
  stats: '统计',
  ranking: '排行'
}

// 按注入的权限判断函数计算各 pane 可见性。hasPermissionFn: (code) => boolean; demoMode 时恒放开。
function computeStatsPanes(hasPermissionFn, demoMode) {
  const can = {}
  for (const key of STATS_PANE_ORDER) {
    can[key] = Boolean(demoMode) || hasPermissionFn(STATS_PANE_PERMISSIONS[key])
  }
  return can
}

// 默认 pane=STATS_PANE_ORDER 中第一个有权者; 全无权返回 null(调用方应弹回首页)。
function firstAllowedStatsPane(canPanes) {
  for (const key of STATS_PANE_ORDER) {
    if (canPanes[key]) return key
  }
  return null
}

// 无权 pane 不渲染。返回 [{key,label}]，保持 STATS_PANE_ORDER 顺序。
function visibleStatsTabs(canPanes) {
  return STATS_PANE_ORDER
    .filter((key) => canPanes[key])
    .map((key) => ({ key, label: STATS_PANE_LABELS[key] }))
}

module.exports = {
  STATS_PANE_PERMISSIONS,
  STATS_PANE_ORDER,
  STATS_PANE_LABELS,
  computeStatsPanes,
  firstAllowedStatsPane,
  visibleStatsTabs
}
