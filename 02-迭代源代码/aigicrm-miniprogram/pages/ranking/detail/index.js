const { requireLogin, hasPermission } = require('../../../utils/auth')
const { getScoreGrid } = require('../../../api/score')
const { showError } = require('../../../utils/feedback')

// 【2026-11-06 用户令"学分明细点进去要能看到具体明细,不是一个名字一个总分"+重设计 UI 注意骨架】
// 此前本页=URL 传参空壳。数据面复用 /api/score/grid(评分网格: items=学分项全集,
// scores="itemId_userId"→得分), 个人模式拉本周期网格拼单人逐项得分; 零后端改动。
// 骨架: 统一深蓝 hero 细条 → 紧凑头卡(头像首字/姓名/名次徽标/总分右对齐/周期组织一行)
// → 「得分明细」区块卡(逐项行 分值右对齐) → 空态文案。
function buildDetailView(state = {}) {
  const name = state.name || '未命名用户'
  return {
    nameInitialText: name.slice(0, 1) || '排',
    nameText: name,
    scoreText: String(state.score || 0),
    metaText: state.metaText || '',
    rankText: state.rank || '',
    subText: state.mode === 'dept' ? '部门四项服务学分表现' : '个人四项服务学分表现'
  }
}

function buildItemsView(groups, emptyText) {
  return { groups: Array.isArray(groups) ? groups : [], emptyText: emptyText || '本期暂无得分记录' }
}

// 演示模式分组装配(与真实链同形)
function groupDemoRows(rows) {
  const byCat = new Map()
  for (const row of rows) {
    if (!byCat.has(row.cat)) byCat.set(row.cat, { key: row.cat, name: row.cat, rows: [], total: 0 })
    const g = byCat.get(row.cat)
    g.rows.push(row)
    g.total += Number(String(row.scoreText).replace(' 分', '')) || 0
  }
  return Array.from(byCat.values()).map((g) => ({ ...g, totalText: `${g.total} 分` }))
}

Page({
  data: {
    mode: 'personal',
    userId: '',
    period: '',
    loading: false,
    header: buildDetailView({}),
    itemsView: buildItemsView([])
  },

  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('score:view')) {
      wx.showToast({ title: '没有排行查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    const name = decodeURIComponent(options.name || '')
    const userId = options.userId || ''
    const rank = options.rank || ''
    const period = decodeURIComponent(options.period || '')
    const dept = decodeURIComponent(options.dept || '')
    const score = decodeURIComponent(options.score || '0')
    // 无 userId(部门聚合行)=部门模式: 只出摘要卡+提示, 逐项明细需到个人榜下钻
    const mode = userId ? 'personal' : 'dept'
    const metaText = [period ? `周期 ${period}` : '', dept].filter(Boolean).join(' · ')
    this.setData({
      mode,
      userId,
      period,
      header: buildDetailView({ name, score, metaText, rank, mode })
    })
    this.loadBreakdown()
  },

  loadBreakdown() {
    if (getApp().globalData.demoMode) {
      const rows = [
        { key: 'demo-1', cat: '机封类', name: '机封维修、机封备件', scoreText: '8 分' },
        { key: 'demo-2', cat: '涂层类', name: '全固体聚合涂层', scoreText: '5 分' },
        { key: 'demo-3', cat: '涂层类', name: '防滑板', scoreText: '2 分' }
      ]
      this.setData({ itemsView: buildItemsView(groupDemoRows(rows)) })
      return
    }
    if (this.data.mode !== 'personal' || !this.data.userId) {
      this.setData({ itemsView: buildItemsView([], '部门聚合行请回个人榜查看成员明细') })
      return
    }
    this.setData({ loading: true })
    getScoreGrid({ period: this.data.period }).then((grid) => {
      const items = (grid && grid.items) || []
      const scores = (grid && grid.scores) || {}
      const uid = String(this.data.userId)
      // 【2026-11-07 用户令 A=理顺码系】按大类分组展示: categoryName 已由后端从 score_category
      // 字典回填(与 PC 录入页同源), 组头=大类名+组内合计, 组内行=product+分值(降序);
      // 无名占位行(PC 录入网格空行)归"未分类"组——数据侧补名后自动归位
      const byCat = new Map()
      for (const item of items) {
        const scoreNum = Number(scores[`${item.id}_${uid}`] || 0)
        if (!scoreNum) continue
        const cat = item.categoryName || '未分类'
        if (!byCat.has(cat)) byCat.set(cat, { key: cat, name: cat, rows: [], total: 0 })
        const g = byCat.get(cat)
        g.rows.push({ key: `${item.id}_${uid}`, name: item.product || '学分项', scoreNum })
        g.total += scoreNum
      }
      const groups = Array.from(byCat.values()).map((g) => ({
        key: g.key,
        name: g.name,
        totalText: `${g.total} 分`,
        rows: g.rows
          .sort((a, b) => b.scoreNum - a.scoreNum)
          .map((row) => ({ key: row.key, name: row.name, scoreText: `${row.scoreNum} 分` }))
      }))
      this.setData({ loading: false, itemsView: buildItemsView(groups) })
    }).catch((error) => {
      this.setData({ loading: false })
      showError(error, '明细加载失败')
    })
  },

  applyState(patch) {
    this.setData(patch)
  },

  goBack() { wx.navigateBack() }
})
