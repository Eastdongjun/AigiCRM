<template>
  <div class="fabushi-stats" v-loading="loading">
    <!-- 【骨架重排+书卷化 2026-11-16】过滤条 / 指标卡 / 主双列(榜+右栏) / 底双列(部门+月度) / 个人条 -->
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

    <!-- 主双列: 成员榜不拉伸(数据少不空荡) -->
    <div class="grid-main">
      <section class="panel">
        <div class="panel-head">
          <span>成员阅读榜 · {{ period === 'week' ? '本周' : '本月' }}</span>
          <span class="panel-hint">发布数 / 获木鱼 / 已读天数</span>
        </div>
        <div class="table-scroll">
          <table class="rank-table">
            <thead><tr><th class="w-no">名次</th><th>成员</th><th>部门</th><th class="w-num">发布</th><th class="w-num">获木鱼</th><th class="w-num">已读天数</th><th class="w-act">活跃度</th></tr></thead>
            <tbody>
              <tr v-for="(row, index) in dashboard.ranking || []" :key="row.userId">
                <td class="w-no"><span class="rank-no" :class="{ top: index < 3 }">{{ index + 1 }}</span></td>
                <td class="strong">{{ row.userName }}</td>
                <td class="dim" :title="row.deptName">{{ row.deptName }}</td>
                <td class="num w-num">{{ row.postCount }}</td>
                <td class="num w-num">{{ row.likesReceived }}</td>
                <td class="num w-num">{{ row.readDays }}</td>
                <td class="w-act"><div class="activity"><i :style="{ width: (row.activityPct || 0) + '%' }" /></div></td>
              </tr>
            </tbody>
          </table>
        </div>
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

    <!-- 底双列: 部门进度表(列宽重排) + 月度报告(纵向格子) -->
    <div class="grid-bottom">
      <section class="panel dept-panel">
        <div class="panel-head">
          <span>部门进度 · {{ period === 'week' ? '本周' : '本月' }}</span>
          <span class="panel-hint">覆盖率 = 已读人数 / 部门成员数</span>
        </div>
        <!-- 【UI-STATS-BORDER-001#1】表体滚动包裹: 长数据在卡内滚动, 末行不再被卡片底边裁切 -->
        <div class="table-scroll">
          <table class="rank-table dept-table">
            <colgroup>
              <col /><col class="c-num" /><col class="c-num" /><col class="c-cov" /><col class="c-num" /><col class="c-num" /><col class="c-num" /><col class="c-num" />
            </colgroup>
            <thead><tr><th>部门</th><th>成员数</th><th>已读人数</th><th>覆盖率</th><th>内容</th><th>木鱼</th><th>评论</th><th>已读天数</th></tr></thead>
            <tbody>
              <tr v-for="row in dashboard.deptProgress || []" :key="row.deptId ?? 'none'">
                <td class="strong" :title="row.deptName">{{ row.deptName }}</td>
                <td class="num">{{ row.memberCount }}</td>
                <td class="num">{{ row.readUsers }}</td>
                <td>
                  <span class="cov-wrap"><b class="cov-num">{{ row.coveragePct }}%</b><span class="cov-track"><i :style="{ width: Math.min(100, row.coveragePct || 0) + '%' }" /></span></span>
                </td>
                <td class="num">{{ row.posts }}</td>
                <td class="num">{{ row.likes }}</td>
                <td class="num">{{ row.comments }}</td>
                <td class="num">{{ row.readDays }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="!(dashboard.deptProgress || []).length" class="empty">本周期暂无部门数据</div>
      </section>

      <section class="panel monthly-panel">
        <div class="panel-head">
          <span>月度报告</span>
          <span class="monthly-tools">
            <el-date-picker v-model="monthlyMonth" type="month" placeholder="选择月份" value-format="YYYY-MM"
              :clearable="false" style="width: 118px" size="small" @change="loadMonthly" />
            <el-button v-if="userStore.hasPermission('fabushi:stats:export')" size="small" @click="exportMonthly">导出</el-button>
          </span>
        </div>
        <div class="m-grid">
          <div class="m-cell"><span>新增内容</span><b>{{ monthly.posts || 0 }}</b><i :class="pctClass(monthly.prev && monthly.prev.postsPct)">{{ pctText(monthly.prev && monthly.prev.postsPct) }}</i></div>
          <div class="m-cell"><span>木鱼</span><b>{{ monthly.likes || 0 }}</b><i :class="pctClass(monthly.prev && monthly.prev.likesPct)">{{ pctText(monthly.prev && monthly.prev.likesPct) }}</i></div>
          <div class="m-cell"><span>评论</span><b>{{ monthly.comments || 0 }}</b><i :class="pctClass(monthly.prev && monthly.prev.commentsPct)">{{ pctText(monthly.prev && monthly.prev.commentsPct) }}</i></div>
          <div class="m-cell"><span>已读人数</span><b>{{ monthly.readUsers || 0 }}</b><i :class="pctClass(monthly.prev && monthly.prev.readUsersPct)">{{ pctText(monthly.prev && monthly.prev.readUsersPct) }}</i></div>
          <div class="m-cell"><span>已读人次</span><b>{{ monthly.readCount || 0 }}</b></div>
          <div class="m-cell"><span>活跃人数</span><b>{{ monthly.activeUsers || 0 }}</b></div>
          <div class="m-cell wide"><span>覆盖率</span><b>{{ monthly.coveragePct != null ? monthly.coveragePct + '%' : '—' }}</b><i>{{ monthly.month }}</i></div>
        </div>
      </section>
    </div>

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
/* 【书卷化 2026-11-16】纸色卡/墨绿主色/朱砂强调/宋体标题, 与 MP 同语言 */
.fabushi-stats { display: flex; flex-direction: column; gap: 14px; }
.filter { display: flex; align-items: center; gap: 10px; row-gap: 8px; flex-wrap: wrap; padding: 10px 14px; border: 1px solid #e8e0d3; border-radius: 12px; background: #fffdf8; }
/* 【UI-STATS-BORDER-001#4】窄幅换行时导出钮与筛选控件基线对齐 */
.export-btn { align-self: center; }
.fl { color: #a67c52; font-size: 12px; }
.scope-pill { padding: 4px 10px; border-radius: 999px; color: #2d5a4a; background: #eef3ee; font-size: 12px; }
.spacer { flex: 1; }
.seg { display: flex; gap: 2px; padding: 3px; border-radius: 10px; background: #efe7d8; }
.seg-item { padding: 4px 14px; border: 0; border-radius: 8px; color: #6b6257; background: transparent; font-size: 12px; cursor: pointer; }
.seg-item.active { color: #fff7ee; background: #2d5a4a; font-weight: 600; }

.stat-cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
/* 【UI-STATS-BORDER-001#2】顶部装饰条改内嵌 ::before 圆角条(直角 border-top 压圆角已除);
   四卡顶色统一墨绿(原绿/红/棕混排) */
.stat-card { position: relative; padding: 15px 18px; border: 1px solid #e8e0d3; border-radius: 12px; background: #fffdf8; }
.stat-card::before { content: ''; position: absolute; top: 0; left: 14px; right: 14px; height: 3px; border-radius: 0 0 999px 999px; background: #2d5a4a; }
.stat-label { color: #9c9184; font-size: 12px; }
.stat-value { margin-top: 6px; color: #2e2a24; font-family: 'Songti SC', 'STSong', serif; font-size: 30px; font-weight: 800; font-variant-numeric: tabular-nums; }
.unit { margin-left: 3px; color: #9c9184; font-size: 12px; font-weight: 500; }
.stat-sub { margin-top: 4px; color: #a67c52; font-size: 11px; }

/* 主双列: 左榜自适应内容高度(不拉伸), 右栏堆叠 */
.grid-main { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(0, 1fr); gap: 14px; align-items: start; }
.panel { padding: 14px 16px; border: 1px solid #e8e0d3; border-radius: 12px; background: #fffdf8; }
.panel-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; color: #2e2a24; font-family: 'Songti SC', 'STSong', serif; font-size: 15px; font-weight: 700; }
.panel-hint { color: #9c9184; font-size: 11px; font-weight: 400; }
.side-stack { display: flex; flex-direction: column; gap: 14px; }

.rank-table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.rank-table th { padding: 7px 8px; color: #9c9184; font-size: 11px; font-weight: 500; text-align: left; border-bottom: 1px solid #e8e0d3; }
.rank-table td { padding: 9px 8px; color: #6b6257; border-bottom: 1px dashed #efe7d8; }
.rank-table tr:last-child td { border-bottom: 0; padding-bottom: 4px; /* 【#5】末行下留白收敛 */ }
.rank-table .strong { color: #2e2a24; font-weight: 600; white-space: nowrap; }
/* 【UI-STATS-BORDER-001#1】两表滚动容器: 卡内滚动替代静默溢出(末行完整可见) */
.table-scroll { max-height: 440px; overflow-y: auto; overscroll-behavior: contain; }
/* 【#5】部门列长名单行截断(hover 看 title 全名), 消除换行导致的行高不均 */
.rank-table .dim { color: #9c9184; max-width: 132px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dept-table .strong { max-width: 132px; overflow: hidden; text-overflow: ellipsis; }
.filter :deep(.el-button--primary) { background: #2d5a4a; border-color: #2d5a4a; }
.filter :deep(.el-button--primary:hover) { background: #356350; border-color: #356350; }
.rank-table .num { font-variant-numeric: tabular-nums; }
.w-no { width: 44px; }
.w-num { width: 72px; }
.w-act { width: 110px; }
.dept-table .c-num { width: 64px; }
.dept-table .c-cov { width: 150px; }
.rank-no { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; color: #9c9184; background: #f4ede0; font-size: 11px; font-weight: 700; }
.rank-no.top { color: #b5543a; background: #f8e9e0; }
.activity { height: 5px; max-width: 96px; overflow: hidden; border-radius: 999px; background: #efe7d8; }
.activity i { display: block; height: 100%; border-radius: 999px; background: #2d5a4a; }

/* 部门覆盖率: 数字+短条紧凑组合 */
.cov-wrap { display: inline-flex; align-items: center; gap: 8px; }
.cov-num { min-width: 38px; color: #2d5a4a; font-size: 12.5px; font-weight: 700; font-variant-numeric: tabular-nums; }
.cov-track { display: inline-block; width: 84px; height: 5px; overflow: hidden; border-radius: 999px; background: #efe7d8; vertical-align: middle; }
.cov-track i { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, #2d5a4a, #5a8a6e); }

.heat-row { display: flex; align-items: center; gap: 8px; margin: 7px 0; }
.heat-label { width: 64px; flex-shrink: 0; color: #6b6257; font-size: 11px; }
.heat-track { flex: 1; height: 6px; overflow: hidden; border-radius: 999px; background: #efe7d8; }
.heat-track i { display: block; height: 100%; border-radius: 999px; background: #5a8a6e; }
.heat-val { width: 28px; color: #9c9184; font-size: 11px; text-align: right; font-variant-numeric: tabular-nums; }
.trend { display: flex; align-items: flex-end; justify-content: space-between; gap: 6px; height: 120px; padding-top: 12px; }
.trend-col { display: flex; flex: 1; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; gap: 3px; }
.trend-val { color: #9c9184; font-size: 10px; }
.trend-bar { width: 100%; max-width: 22px; min-height: 4px; border-radius: 6px 6px 0 0; background: #a7c0af; }
.trend-label { color: #9c9184; font-size: 9.5px; white-space: nowrap; }
.empty { padding: 18px 0; color: #9c9184; font-size: 12px; text-align: center; }

/* 底双列: 部门表 + 月度报告纵向格子 */
.grid-bottom { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr); gap: 14px; align-items: start; }
.monthly-tools { display: flex; align-items: center; gap: 8px; font-weight: 400; }
.m-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px 12px; padding: 4px 0 10px; /* 【#3】底部图例条与卡底边框留白 */ }
.m-cell { padding: 8px 10px; border: 1px solid #efe7d8; border-radius: 10px; background: #faf6ee; }
.m-cell.wide { grid-column: span 3; display: flex; align-items: baseline; gap: 10px; }
.m-cell span { display: block; color: #9c9184; font-size: 11px; }
.m-cell b { display: block; margin-top: 3px; color: #2d5a4a; font-family: 'Songti SC', 'STSong', serif; font-size: 21px; font-weight: 800; font-variant-numeric: tabular-nums; }
.m-cell.wide b { display: inline-block; margin-top: 0; }
.m-cell i { display: block; margin-top: 2px; font-style: normal; font-size: 11px; color: #9c9184; }
.m-cell.wide i { display: inline; }
.m-cell i.up { color: #2d5a4a; }
.m-cell i.down { color: #b5543a; }

.personal { padding: 14px 16px; border: 1px solid #e8e0d3; border-radius: 12px; background: #f4ede0; }
.personal-grid { display: flex; gap: 36px; flex-wrap: wrap; margin-top: 4px; }
.personal-grid div { display: flex; flex-direction: column; gap: 3px; }
.personal-grid span { color: #a67c52; font-size: 11px; }
.personal-grid b { color: #2e2a24; font-family: 'Songti SC', 'STSong', serif; font-size: 20px; font-variant-numeric: tabular-nums; }
@media (max-width: 1100px) {
  .stat-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .grid-main, .grid-bottom { grid-template-columns: 1fr; }
}
</style>
