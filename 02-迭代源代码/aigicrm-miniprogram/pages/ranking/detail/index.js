const { requireLogin, hasPermission } = require('../../../utils/auth')
const { getScoreGrid, getScoreDeptMembers } = require('../../../api/score')
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

function buildItemsView(rows, emptyText) {
  return { rows: Array.isArray(rows) ? rows : [], emptyText: emptyText || '本期暂无得分记录' }
}

Page({
  data: {
    mode: 'personal',
    userId: '',
    period: '',
    loading: false,
    header: buildDetailView({}),
    itemsView: buildItemsView([]),
    membersView: { rows: [], summaryText: '' }
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
        { key: 'demo-1', catName: '机封类', name: '机封维修、机封备件', score: 8 },
        { key: 'demo-2', catName: '涂层类', name: '全固体聚合涂层', score: 5 },
        { key: 'demo-3', catName: '涂层类', name: '防滑板', score: 2 },
        { key: 'demo-4', catName: '润滑类', name: '工业润滑油脂类（脂、油）', score: 0 }
      ]
      this.setData({ itemsView: buildItemsView(rows) })
      return
    }
    if (this.data.mode !== 'personal' || !this.data.userId) {
      // 【2026-11-07 用户令"部门排行做成明细, PC端也一样"】dept 模式=部门成员明细:
      // 摘要(人数/合计/人均)+成员行(姓名+分值+公司名次), 点成员行钻到该成员的学分明细
      this.setData({ loading: true })
      getScoreDeptMembers({ period: this.data.period, deptName: this.data.header.nameText }).then((res) => {
        const data = res && res.deptName ? res : (res && res.data) || res || {}
        const rows = (data.members || []).map((m) => ({
          key: String(m.userId),
          name: m.realName || '未命名成员',
          meta: `${m.itemCount || 0} 个文件 · 公司第 ${m.companyRank || '-'} 名`,
          scoreText: `${m.totalScore || 0} 分`,
          userId: m.userId,
          score: m.totalScore || 0
        }))
        const summaryText = data.participantCount !== undefined
          ? `共 ${data.participantCount} 人 · 合计 ${data.totalScore || 0} 分 · 人均 ${data.avgScore || 0} 分`
          : ''
        this.setData({ loading: false, membersView: { rows, summaryText } })
      }).catch((error) => {
        this.setData({ loading: false, membersView: { rows: [], summaryText: '' } })
        showError(error, '部门明细加载失败')
      })
      return
    }
    this.setData({ loading: true })
    getScoreGrid({ period: this.data.period }).then((grid) => {
      const items = (grid && grid.items) || []
      const scores = (grid && grid.scores) || {}
      const uid = String(this.data.userId)
      // 【2026-11-07 用户令"学分排行榜分值点进去看明细表格"】表格化(与 PC 明细弹窗/录入网格同构):
      // 全部学分项按录入顺序, 列=#/大类/产品/得分(>0 蓝字, 0 显"—"); 大类中文名取 score_category
      // 字典回填的 categoryName(拼音码不入 UI); 无名占位行大类归"未分类"
      const rows = items.map((item) => ({
        key: `${item.id}_${uid}`,
        catName: item.categoryName || '未分类',
        name: item.product || '学分项',
        scoreNum: Number(scores[`${item.id}_${uid}`] || 0)
      })).map((row) => ({ ...row, scoreText: row.scoreNum > 0 ? `${row.scoreNum} 分` : '—' }))
      this.setData({ loading: false, itemsView: buildItemsView(rows) })
    }).catch((error) => {
      this.setData({ loading: false })
      showError(error, '明细加载失败')
    })
  },

  applyState(patch) {
    this.setData(patch)
  },

  // 【2026-11-07 部门明细】点成员行 → 钻到该成员的学分明细(本页 personal 模式复用)
  onMemberTap(event) {
    const dataset = event.currentTarget.dataset || {}
    if (!dataset.userId) return
    const query = [
      `name=${encodeURIComponent(dataset.name || '')}`,
      `score=${encodeURIComponent(dataset.score || 0)}`,
      `dept=${encodeURIComponent(this.data.header.nameText || '')}`,
      `period=${encodeURIComponent(this.data.period || '')}`,
      `userId=${encodeURIComponent(dataset.userId)}`,
      `rank=${encodeURIComponent('')}`
    ].join('&')
    wx.navigateTo({ url: `/pages/ranking/detail/index?${query}` })
  },

  goBack() { wx.navigateBack() }
})
