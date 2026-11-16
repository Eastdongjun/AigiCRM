<template>
  <div class="fabushi-stats" v-loading="loading">
    <div class="filter">
      <span class="fl">统计范围</span>
      <span class="scope-pill">{{ dashboard.memberCount === -1 ? '全集团 · 全部' : `可见成员 · ${dashboard.memberCount || 0} 人` }}</span>
      <span class="fl">周期</span>
      <div class="seg">
        <button type="button" class="seg-item" :class="{ active: period === 'week' }" @click="switchPeriod('week')">本周</button>
        <button type="button" class="seg-item" :class="{ active: period === 'month' }" @click="switchPeriod('month')">本月</button>
      </div>
      <el-button type="primary" @click="loadData">查询</el-button>
      <span class="spacer" />
      <el-button v-if="userStore.hasPermission('fabushi:stats:export')" class="export-btn" @click="exportStats">导出报表（CSV）</el-button>
    </div>

    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-label">今日已读人数</div>
        <div class="stat-value">{{ dashboard.todayReadUsers || 0 }}<span class="unit">人</span></div>
        <div class="stat-sub">{{ dashboard.coveragePct != null ? dashboard.coveragePct + '% 覆盖率' : '全部范围' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本周期新增内容</div>
        <div class="stat-value">{{ dashboard.periodPosts || 0 }}<span class="unit">篇</span></div>
        <div class="stat-sub">心得 + 诵读 + 正能量</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本周期木鱼</div>
        <div class="stat-value">{{ dashboard.periodLikes || 0 }}</div>
        <div class="stat-sub">点赞 = 敲木鱼</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本周期评论</div>
        <div class="stat-value">{{ dashboard.periodComments || 0 }}</div>
        <div class="stat-sub">已发布评论</div>
      </div>
    </div>

    <div class="stats-grid">
      <section class="panel ranking-panel">
        <div class="panel-head">
          <span>成员阅读榜 · {{ period === 'week' ? '本周' : '本月' }}</span>
          <span class="panel-hint">发布数 / 获木鱼 / 已读天数</span>
        </div>
        <table class="rank-table">
          <thead><tr><th>名次</th><th>成员</th><th>部门</th><th>发布</th><th>获木鱼</th><th>已读天数</th><th>活跃度</th></tr></thead>
          <tbody>
            <tr v-for="(row, index) in dashboard.ranking || []" :key="row.userId">
              <td><span class="rank-no" :class="{ top: index < 3 }">{{ index + 1 }}</span></td>
              <td class="strong">{{ row.userName }}</td>
              <td>{{ row.deptName }}</td>
              <td class="num">{{ row.postCount }}</td>
              <td class="num">{{ row.likesReceived }}</td>
              <td class="num">{{ row.readDays }}</td>
              <td><div class="activity"><i :style="{ width: (row.activityPct || 0) + '%' }" /></div></td>
            </tr>
          </tbody>
        </table>
        <div v-if="!(dashboard.ranking || []).length" class="empty">本周期暂无阅读数据</div>
      </section>

      <div class="side-stack">
        <section class="panel">
          <div class="panel-head"><span>类别热度分布</span></div>
          <div v-for="item in dashboard.categoryHeat || []" :key="item.categoryCode" class="heat-row">
            <span class="heat-label">{{ item.categoryName }}</span>
            <div class="heat-track"><i :style="{ width: heatWidth(item.count) }" /></div>
            <span class="heat-val">{{ item.count }}</span>
          </div>
          <div v-if="!(dashboard.categoryHeat || []).length" class="empty">本周期暂无内容</div>
        </section>

        <section class="panel">
          <div class="panel-head"><span>每日一读 · 达成趋势</span><span class="panel-hint">人数</span></div>
          <div class="trend">
            <div v-for="item in dashboard.trend || []" :key="item.date" class="trend-col">
              <span class="trend-val">{{ item.count }}</span>
              <div class="trend-bar" :style="{ height: trendHeight(item.count) }" />
              <span class="trend-label">{{ item.label }}</span>
            </div>
          </div>
        </section>
      </div>
    </div>

    <!-- 【2026-11-16 P3】部门进度: 与成员榜同周期口径, 按 sys_user.dept_id → sys_dept 分组 -->
    <section class="panel dept-panel">
      <div class="panel-head">
        <span>部门进度 · {{ period === 'week' ? '本周' : '本月' }}</span>
        <span class="panel-hint">覆盖率 = 已读人数 / 部门成员数</span>
      </div>
      <table class="rank-table">
        <thead><tr><th>部门</th><th>成员数</th><th>已读人数</th><th>覆盖率</th><th>新增内容</th><th>木鱼</th><th>评论</th><th>已读天数</th></tr></thead>
        <tbody>
          <tr v-for="row in dashboard.deptProgress || []" :key="row.deptId ?? 'none'">
            <td class="strong">{{ row.deptName }}</td>
            <td class="num">{{ row.memberCount }}</td>
            <td class="num">{{ row.readUsers }}</td>
            <td>
              <span class="cov-num">{{ row.coveragePct }}%</span>
              <div class="activity"><i :style="{ width: Math.min(100, row.coveragePct || 0) + '%' }" /></div>
            </td>
            <td class="num">{{ row.posts }}</td>
            <td class="num">{{ row.likes }}</td>
            <td class="num">{{ row.comments }}</td>
            <td class="num">{{ row.readDays }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="!(dashboard.deptProgress || []).length" class="empty">本周期暂无部门数据</div>
    </section>

    <!-- 【2026-11-16 P3】月度报告: 自然月聚合 + 环比上月 + 月度 CSV 导出 -->
    <section class="panel monthly-panel">
      <div class="panel-head">
        <span>月度报告</span>
        <span class="monthly-tools">
          <el-date-picker v-model="monthlyMonth" type="month" placeholder="选择月份" value-format="YYYY-MM"
            :clearable="false" style="width: 130px" size="small" @change="loadMonthly" />
          <el-button v-if="userStore.hasPermission('fabushi:stats:export')" size="small" @click="exportMonthly">导出月度报告（CSV）</el-button>
        </span>
      </div>
      <div class="monthly-summary">
        <div class="m-cell"><span>新增内容</span><b>{{ monthly.posts || 0 }}</b><i :class="pctClass(monthly.prev && monthly.prev.postsPct)">{{ pctText(monthly.prev && monthly.prev.postsPct) }}</i></div>
        <div class="m-cell"><span>木鱼</span><b>{{ monthly.likes || 0 }}</b><i :class="pctClass(monthly.prev && monthly.prev.likesPct)">{{ pctText(monthly.prev && monthly.prev.likesPct) }}</i></div>
        <div class="m-cell"><span>评论</span><b>{{ monthly.comments || 0 }}</b><i :class="pctClass(monthly.prev && monthly.prev.commentsPct)">{{ pctText(monthly.prev && monthly.prev.commentsPct) }}</i></div>
        <div class="m-cell"><span>已读人数</span><b>{{ monthly.readUsers || 0 }}</b><i :class="pctClass(monthly.prev && monthly.prev.readUsersPct)">{{ pctText(monthly.prev && monthly.prev.readUsersPct) }}</i></div>
        <div class="m-cell"><span>已读人次</span><b>{{ monthly.readCount || 0 }}</b></div>
        <div class="m-cell"><span>活跃人数</span><b>{{ monthly.activeUsers || 0 }}</b></div>
        <div class="m-cell"><span>覆盖率</span><b>{{ monthly.coveragePct != null ? monthly.coveragePct + '%' : '—' }}</b></div>
      </div>
    </section>

    <section class="personal">
      <div class="panel-head"><span>我的法布施</span></div>
      <div class="personal-grid">
        <div><span>我的内容</span><b>{{ personal.myPosts || 0 }}</b></div>
        <div><span>我的评论</span><b>{{ personal.myComments || 0 }}</b></div>
        <div><span>我的阅读</span><b>{{ personal.myReadCount || 0 }}</b></div>
        <div><span>获木鱼</span><b>{{ personal.myLikesReceived || 0 }}</b></div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getFabushiDashboard, getFabushiMonthly, getFabushiPersonalStats } from '@/api/fabushi'
import { useUserStore } from '@/store/user'
import { downloadFile } from '@/utils/download'
import { getToken } from '@/utils/token'

const userStore = useUserStore()
const loading = ref(false)
const period = ref('week')
const dashboard = ref({
  memberCount: 0, todayReadUsers: 0, coveragePct: null,
  periodPosts: 0, periodLikes: 0, periodComments: 0,
  ranking: [], deptProgress: [], categoryHeat: [], trend: [],
})
const personal = ref({ myPosts: 0, myComments: 0, myReadCount: 0, myLikesReceived: 0 })
// 【2026-11-16 P3】月度报告: 默认当月, el-date-picker(type=month) 切换
const monthly = ref({ month: '', periodStart: '', periodEnd: '', memberCount: 0, posts: 0, likes: 0, comments: 0, readCount: 0, readUsers: 0, activeUsers: 0, coveragePct: null, prev: {}, deptProgress: [], ranking: [], trend: [] })
const monthlyMonth = ref(new Date().toISOString().slice(0, 7))
const maxHeat = computed(() => Math.max(1, ...(dashboard.value.categoryHeat || []).map((item) => Number(item.count) || 0)))
const maxTrend = computed(() => Math.max(1, ...(dashboard.value.trend || []).map((item) => Number(item.count) || 0)))
const heatWidth = (value) => Math.round((Number(value) || 0) * 100 / maxHeat.value) + '%'
const trendHeight = (value) => Math.max(8, Math.round((Number(value) || 0) * 100 / maxTrend.value)) + '%'
const pctText = (pct) => (pct == null ? '—' : (pct > 0 ? '+' : '') + pct + '%')
const pctClass = (pct) => ({ up: pct != null && pct > 0, down: pct != null && pct < 0 })

const loadData = async () => {
  loading.value = true
  try {
    const [d, p] = await Promise.all([getFabushiDashboard(period.value), getFabushiPersonalStats()])
    dashboard.value = (d && d.data) || dashboard.value
    personal.value = (p && p.data) || personal.value
  } finally {
    loading.value = false
  }
}

const exportStats = async () => {
  await downloadFile(`/api/fabushi/stats/export?period=${period.value}`, {
    name: `法布施统计-${period.value}.csv`,
    headers: { Authorization: `Bearer ${getToken() || ''}` },
  })
}

const switchPeriod = (value) => {
  period.value = value
  loadData()
}

// 【2026-11-16 P3】月度报告加载与导出
const loadMonthly = async () => {
  const r = await getFabushiMonthly(monthlyMonth.value).catch(() => null)
  monthly.value = (r && r.data) || monthly.value
}

const exportMonthly = async () => {
  await downloadFile(`/api/fabushi/stats/monthly/export?month=${monthlyMonth.value}`, {
    name: `法布施月度报告-${monthlyMonth.value}.csv`,
    headers: { Authorization: `Bearer ${getToken() || ''}` },
  })
}

onMounted(() => {
  loadData()
  loadMonthly()
})
</script>

<style scoped>
.fabushi-stats { display: flex; flex-direction: column; gap: 12px; }
.filter { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 9px 12px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.fl { color: var(--text-secondary); font-size: 12px; }
.scope-pill { padding: 4px 9px; border-radius: var(--radius-sm); color: var(--text-secondary); background: var(--el-fill-color-light); font-size: 12px; }
.spacer { flex: 1; }
.seg { display: flex; gap: 2px; padding: 3px; border-radius: var(--radius-sm); background: var(--el-fill-color-light); }
.seg-item { padding: 4px 12px; border: 0; border-radius: var(--radius-sm); color: var(--text-secondary); background: transparent; font-size: 12px; cursor: pointer; }
.seg-item.active { color: var(--el-color-white); background: var(--primary-color); }
.phase-chip { margin-left: 4px; padding: 1px 5px; border-radius: var(--radius-sm); color: var(--text-placeholder); background: var(--el-fill-color-light); font-size: 10px; }
.stat-cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.stat-card { padding: 14px 16px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.stat-label { color: var(--text-muted); font-size: 12px; }
.stat-value { margin-top: 5px; color: var(--text-primary); font-size: 28px; font-weight: 800; font-variant-numeric: tabular-nums; }
.unit { margin-left: 3px; color: var(--text-muted); font-size: 12px; font-weight: 500; }
.stat-sub { margin-top: 3px; color: var(--text-placeholder); font-size: 11px; }
.stats-grid { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr); gap: 12px; align-items: start; }
.panel { padding: 13px 16px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.panel-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 8px; color: var(--text-primary); font-size: 13px; font-weight: 700; }
.panel-hint { color: var(--text-placeholder); font-size: 10.5px; font-weight: 400; }
.rank-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.rank-table th { padding: 7px 8px; color: var(--text-placeholder); font-size: 10.5px; font-weight: 500; text-align: left; border-bottom: 1px solid var(--border-light); }
.rank-table td { padding: 8px; color: var(--text-secondary); border-bottom: 1px solid var(--border-light); }
.rank-table tr:last-child td { border-bottom: 0; }
.rank-table .strong { color: var(--text-primary); font-weight: 600; }
.rank-table .num { font-variant-numeric: tabular-nums; }
.rank-no { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 50%; color: var(--text-muted); background: var(--el-fill-color-light); font-size: 10px; font-weight: 700; }
.rank-no.top { color: var(--primary-color); background: var(--el-color-primary-light-9); }
.activity { height: 5px; overflow: hidden; border-radius: var(--radius-pill); background: var(--el-fill-color-light); }
.activity i { display: block; height: 100%; border-radius: var(--radius-pill); background: var(--primary-color); }
.side-stack { display: flex; flex-direction: column; gap: 12px; }
.heat-row { display: flex; align-items: center; gap: 8px; margin: 7px 0; }
.heat-label { width: 64px; flex-shrink: 0; color: var(--text-secondary); font-size: 11px; }
.heat-track { flex: 1; height: 6px; overflow: hidden; border-radius: var(--radius-pill); background: var(--el-fill-color-light); }
.heat-track i { display: block; height: 100%; border-radius: var(--radius-pill); background: var(--el-color-primary-light-3); }
.heat-val { width: 28px; color: var(--text-muted); font-size: 11px; text-align: right; font-variant-numeric: tabular-nums; }
.trend { display: flex; align-items: flex-end; justify-content: space-between; gap: 6px; height: 120px; padding-top: 12px; }
.trend-col { display: flex; flex: 1; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; gap: 3px; }
.trend-val { color: var(--text-muted); font-size: 10px; }
.trend-bar { width: 100%; max-width: 22px; min-height: 4px; border-radius: var(--radius-sm) var(--radius-sm) 0 0; background: var(--el-color-primary-light-7); }
.trend-label { color: var(--text-placeholder); font-size: 9.5px; white-space: nowrap; }
.empty { padding: 14px 0; color: var(--text-placeholder); font-size: 12px; }
/* 【2026-11-16 P3】部门进度 */
.dept-panel .activity { width: 90px; }
.cov-num { margin-right: 6px; color: var(--text-primary); font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums; }
/* 【2026-11-16 P3】月度报告 */
.monthly-tools { display: flex; align-items: center; gap: 8px; font-weight: 400; }
.monthly-summary { display: flex; gap: 24px; flex-wrap: wrap; padding: 6px 0 2px; }
.m-cell { display: flex; flex-direction: column; gap: 2px; min-width: 72px; }
.m-cell span { color: var(--text-muted); font-size: 11px; }
.m-cell b { color: var(--text-primary); font-size: 20px; font-variant-numeric: tabular-nums; }
.m-cell i { font-style: normal; font-size: 11px; color: var(--text-placeholder); }
.m-cell i.up { color: var(--el-color-success); }
.m-cell i.down { color: var(--el-color-danger); }
.personal { padding: 13px 16px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-fill-color-lighter); }
.personal-grid { display: flex; gap: 32px; flex-wrap: wrap; }
.personal-grid div { display: flex; flex-direction: column; gap: 3px; }
.personal-grid span { color: var(--text-muted); font-size: 11px; }
.personal-grid b { color: var(--text-primary); font-size: 18px; font-variant-numeric: tabular-nums; }
@media (max-width: 1100px) { .stat-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } .stats-grid { grid-template-columns: 1fr; } }
</style>
