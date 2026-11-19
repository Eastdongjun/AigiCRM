<template>
  <div class="fabushi-stats" v-loading="loading">
    <!-- 【UI-STATS-PAGE-001 2026-11-19】按已过审原型 v4 落地: 纵向卷轴三行型(全宽段带/等宽双卡/四卡),
         成员榜前端分页(默认每页 10, 名次章=全榜前三, 占比条=全榜最高值分母), 后端需配套 UI-STATS-PAGE-001 -->
    <!-- ① 页头: 标题回归法布施页头母题, 范围章/数据新鲜度上收(原"统计范围"假筛选移除) -->
    <div class="page-head">
      <div>
        <h1>统计看板</h1>
        <p class="sub">以读为纲 · 阅读推进一页通观</p>
      </div>
      <div class="head-right">
        <span class="fresh">数据截至 <b>{{ freshTime }}</b></span>
        <span class="scope-chip">👁 {{ scopeText }}</span>
        <span class="seal" aria-hidden="true">阅</span>
      </div>
    </div>

    <!-- ② 控制条: 只留真控制; 周期切换即查(无冗余查询钮) -->
    <div class="ctrl-bar">
      <span class="ctrl-label">统计周期</span>
      <div class="seg">
        <button type="button" class="seg-item" :class="{ active: period === 'week' }" @click="switchPeriod('week')">本周</button>
        <button type="button" class="seg-item" :class="{ active: period === 'month' }" @click="switchPeriod('month')">本月</button>
      </div>
      <span class="spacer" />
      <button type="button" class="btn" @click="loadData">↻ 刷新</button>
      <button v-if="userStore.hasPermission('fabushi:stats:export')" type="button" class="btn primary" @click="exportStats">⬇ 导出报表（CSV）</button>
    </div>

    <!-- ③ KPI×4: 阅读四指标, 副行统一口径公式一种性质 -->
    <div class="kpi-row">
      <div class="kpi">
        <div class="label">今日已读人数</div>
        <div class="value">{{ dashboard.todayReadUsers || 0 }}<span class="unit">人</span></div>
        <div class="sub">今日已读 / {{ scopeShort }} {{ dashboard.coveragePct != null ? dashboard.coveragePct + '%' : '—' }}</div>
      </div>
      <div class="kpi">
        <div class="label">周期已读人数</div>
        <div class="value">{{ dashboard.periodReadUsers || 0 }}<span class="unit">人</span></div>
        <div class="sub">{{ dashboard.period === 'month' ? '本月' : '本周' }}内有过阅读的成员</div>
      </div>
      <div class="kpi">
        <div class="label">阅读覆盖率</div>
        <div class="value">
          <template v-if="dashboard.periodCoveragePct != null">{{ dashboard.periodCoveragePct }}<em>%</em></template>
          <template v-else>—</template>
        </div>
        <div class="sub">= 周期已读人数 / {{ scopeShort }}</div>
      </div>
      <div class="kpi">
        <div class="label">每日一读达成率</div>
        <div class="value">
          <template v-if="dashboard.dailyGoalRate != null">{{ dashboard.dailyGoalRate }}<em>%</em></template>
          <template v-else>—</template>
        </div>
        <div class="sub">开提醒应读人日中实读占比</div>
      </div>
    </div>

    <!-- 周期互动条: 发布/敲木鱼/评论在周期口径下的固定归位(口径=谁做的) -->
    <div class="interact-strip">
      <div class="interact-title"><b>周期互动</b><span>{{ dashboard.period === 'month' ? '本月' : '本周' }}</span></div>
      <div class="interact-item"><span class="num">{{ dashboard.periodPosts || 0 }}</span><span class="u">篇</span><span class="lb">发布内容</span></div>
      <div class="interact-item"><span class="num">{{ dashboard.periodLikes || 0 }}</span><span class="u">次</span><span class="lb">🐟 敲木鱼（点赞）</span></div>
      <div class="interact-item"><span class="num">{{ dashboard.periodComments || 0 }}</span><span class="u">条</span><span class="lb">评论</span></div>
      <span class="interact-note">口径：互动条与部门表按"谁做的"计；榜单"获木鱼"按"内容归属"计</span>
    </div>

    <!-- ④ 成员榜(行型 A 全宽 + 前端分页): 名次章=全榜前三, 占比条=指标/全榜最高值(跨页可比) -->
    <section class="panel">
      <div class="panel-head">
        <span class="ttl">成员榜</span>
        <span class="hint">默认按已读人次排序 · 名次章只属全榜前三</span>
        <span class="right">
          <span class="seg-mini">
            <button type="button" :class="{ active: rankSort === 'read' }" @click="switchRankSort('read')">按阅读</button>
            <button type="button" :class="{ active: rankSort === 'post' }" @click="switchRankSort('post')">按发布</button>
          </span>
        </span>
      </div>
      <table class="rank-table">
        <thead>
          <tr>
            <th class="w-no">名次</th><th>成员</th><th>部门</th>
            <th class="w-num r">已读天数</th><th class="w-num r">已读人次</th>
            <th class="w-num r">发布</th><th class="w-num r" title="内容被点赞数，按内容归属">获木鱼</th>
            <th class="w-act">{{ metricLabel }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in pagedRanking" :key="row.userId">
            <td class="w-no"><span class="rank-no" :class="{ top: rankFrom + i < 3 }">{{ rankFrom + i + 1 }}</span></td>
            <td><span class="who-cell"><span class="mini-avatar">{{ (row.userName || '?').slice(0, 1) }}</span><span class="strong">{{ row.userName }}</span></span></td>
            <td class="dim" :title="row.deptName">{{ row.deptName }}</td>
            <td class="num w-num r">{{ row.readDays }}</td>
            <td class="num w-num r">{{ row.readCount }}</td>
            <td class="num w-num r">{{ row.postCount }}</td>
            <td class="num w-num r">{{ row.likesReceived }}</td>
            <td class="w-act"><span class="metric-bar"><span class="tr"><i :style="{ width: metricPct(row) + '%' }" /></span><span class="pc">{{ metricPct(row) }}%</span></span></td>
          </tr>
        </tbody>
      </table>
      <div v-if="!rankedTotal" class="empty">本周期暂无阅读 / 发布数据</div>
      <!-- 分页底栏: 左信息右翻页; URL 直达态暂不做(vue-router 协同留待后续) -->
      <div v-if="rankedTotal" class="rank-pager">
        <span class="info">共 <b>{{ rankedTotal }}</b> 人 · 本周期有阅读 / 发布记录 <b>{{ rankedTotal }} / {{ scopeTotalText }}</b> 人</span>
        <select v-model.number="rankSize" class="pager-size" aria-label="每页人数" @change="rankPage = 1">
          <option :value="10">每页 10 人</option>
          <option :value="20">每页 20 人</option>
        </select>
        <span class="pager-ctrl">
          <button type="button" class="pager-btn" :disabled="rankPage === 1" aria-label="上一页" @click="rankPage = Math.max(1, rankPage - 1)">‹ 上一页</button>
          <button v-for="p in rankTotalPages" :key="p" type="button" class="pager-btn" :class="{ active: p === rankPage }" :aria-current="p === rankPage ? 'page' : undefined" @click="rankPage = p">{{ p }}</button>
          <button type="button" class="pager-btn" :disabled="rankPage === rankTotalPages" aria-label="下一页" @click="rankPage = Math.min(rankTotalPages, rankPage + 1)">下一页 ›</button>
        </span>
      </div>
    </section>

    <!-- ⑤ 等宽双卡行(行型 B): 类别分布 | 近 7 日趋势 -->
    <div class="twin-row">
      <section class="panel">
        <div class="panel-head"><span class="ttl">类别分布</span><span class="hint">已读人次 · 占比</span></div>
        <div v-for="item in dashboard.categoryReads || []" :key="item.categoryCode" class="heat-row">
          <span class="heat-label" :title="item.categoryName">{{ item.categoryName }}</span>
          <div class="heat-track"><i :style="{ width: heatWidth(item.readCount) }" /></div>
          <span class="heat-val">{{ item.readCount }}</span>
          <span class="heat-share">{{ heatShare(item.readCount) }}%</span>
        </div>
        <div v-if="!(dashboard.categoryReads || []).length" class="empty">本周期暂无书目阅读</div>
      </section>
      <section class="panel">
        <div class="panel-head"><span class="ttl">近 7 日 · 每日一读达成</span><span class="hint">每日已读人数</span></div>
        <div class="trend">
          <div v-for="(item, i) in dashboard.trend || []" :key="item.date" class="trend-col" :class="{ today: i === (dashboard.trend || []).length - 1 }">
            <span class="trend-val" :class="{ zero: !item.count }">{{ item.count }}</span>
            <span v-if="!item.count" class="trend-dot" />
            <div v-else class="trend-bar" :style="{ height: trendHeight(item.count) }" />
            <span class="trend-label">{{ i === (dashboard.trend || []).length - 1 ? '今日' : item.label }}</span>
          </div>
        </div>
      </section>
    </div>

    <!-- ⑥ 书目阅读榜(行型 A 全宽) -->
    <section class="panel">
      <div class="panel-head">
        <span class="ttl">书目阅读榜 · {{ dashboard.period === 'month' ? '本月' : '本周' }}</span>
        <span class="hint">按已读人次排序 · 仅章节帖计入 · Top 10</span>
      </div>
      <table class="rank-table">
        <thead><tr><th>书名</th><th>类别</th><th class="w-num r">已读人数</th><th class="w-num r">已读人次</th><th class="w-act">阅读占比</th><th>最新阅读</th></tr></thead>
        <tbody>
          <tr v-for="(row, i) in dashboard.bookRanking || []" :key="row.bookId">
            <td><span class="book-cell"><span class="mini-cover">{{ (row.title || '书').replace(/[《》]/g, '').slice(0, 2) }}</span><span class="strong">{{ row.title }}</span></span></td>
            <td><span class="cat-chip" :class="'c' + (i % 3)">{{ row.categoryName || row.category || '—' }}</span></td>
            <td class="num w-num r">{{ row.readUsers }}</td>
            <td class="num w-num r">{{ row.readCount }}</td>
            <td class="w-act"><span class="metric-bar"><span class="tr"><i :style="{ width: bookPct(row) + '%' }" /></span><span class="pc">{{ bookPct(row) }}%</span></span></td>
            <td class="dim w-time">{{ shortTime(row.latestReadAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="!(dashboard.bookRanking || []).length" class="empty">本周期暂无书目阅读</div>
    </section>

    <!-- ⑦ 分隔带: 月度盘点是独立月份口径, 与上方周期切换切开 -->
    <div class="sec-band">月 度 盘 点<span class="note">独立月份口径 · 与上方本周 / 本月切换无关</span></div>

    <!-- ⑧ 部门进度(行型 A 全宽): 公司直隶/未分配徽标; 互动列=谁做的(后端真实口径) -->
    <section class="panel">
      <div class="panel-head">
        <span class="ttl">部门进度 · {{ period === 'week' ? '本周' : '本月' }}</span>
        <span class="hint">覆盖率 = 已读人数 / 部门成员数 · 木鱼 / 评论按"谁做的"计入本部门成员</span>
      </div>
      <div class="table-scroll">
        <table class="rank-table dept-table">
          <colgroup><col /><col class="w-num" /><col class="w-num" /><col style="width: 168px" /><col class="w-num" /><col class="w-num" /><col class="w-num" /><col class="w-num" /></colgroup>
          <thead><tr><th>部门</th><th class="r">成员数</th><th class="r">已读人数</th><th>覆盖率</th><th class="r">发布</th><th class="r" title="本部门成员敲出的点赞数">木鱼</th><th class="r" title="本部门成员发出的评论数">评论</th><th class="r">已读天数</th></tr></thead>
          <tbody>
            <tr v-for="row in dashboard.deptProgress || []" :key="row.deptId ?? 'none'">
              <td><span class="strong" :title="row.deptName">{{ row.deptName }}</span><span v-if="row.companyLevel" class="dept-badge">公司直隶</span><span v-if="row.deptId == null" class="dept-badge unassigned">无部门成员</span></td>
              <td class="num r">{{ row.memberCount }}</td>
              <td class="num r">{{ row.readUsers }}</td>
              <td>
                <span v-if="row.coveragePct == null" class="dim-c">—</span>
                <span v-else class="cov-wrap"><b class="cov-num">{{ row.coveragePct }}%</b><span class="cov-track"><i :style="{ width: Math.min(100, row.coveragePct || 0) + '%' }" /></span></span>
              </td>
              <td class="num r">{{ row.posts }}</td>
              <td class="num r">{{ row.likes }}</td>
              <td class="num r">{{ row.comments }}</td>
              <td class="num r">{{ row.readDays }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!(dashboard.deptProgress || []).length" class="empty">本周期暂无部门数据</div>
    </section>

    <!-- ⑨ 月度报告(行型 A 全宽): 阅读组/互动组各一行格子; 环比无值时隐藏不露占位符 -->
    <section class="panel">
      <div class="panel-head">
        <span class="ttl">月度报告</span>
        <span class="hint">环比对上月</span>
        <span class="right monthly-tools">
          <el-date-picker v-model="monthlyMonth" type="month" placeholder="选择月份" value-format="YYYY-MM"
            :clearable="false" style="width: 118px" size="small" @change="loadMonthly" />
          <button v-if="userStore.hasPermission('fabushi:stats:export')" type="button" class="btn" @click="exportMonthly">导出</button>
        </span>
      </div>
      <div class="m-sub">阅读组</div>
      <div class="m-grid reads">
        <div class="m-cell">
          <span class="lb">已读人数</span>
          <span class="vrow"><b class="v">{{ monthly.readUsers || 0 }}</b><span v-if="monthly.prev && monthly.prev.readUsersPct != null" class="delta" :class="pctClass(monthly.prev.readUsersPct)">{{ pctText(monthly.prev.readUsersPct) }}</span></span>
        </div>
        <div class="m-cell"><span class="lb">已读人次</span><span class="vrow"><b class="v">{{ monthly.readCount || 0 }}</b></span></div>
        <div class="m-cell"><span class="lb">活跃人数</span><span class="vrow"><b class="v">{{ monthly.activeUsers || 0 }}</b></span></div>
        <div class="m-cell">
          <span class="lb">覆盖率（已读人数 / {{ monthlyScopeText }}）</span>
          <span class="vrow"><b class="v">{{ monthly.coveragePct != null ? monthly.coveragePct + '%' : '—' }}</b><span class="m-progress"><i :style="{ width: Math.min(100, monthly.coveragePct || 0) + '%' }" /></span><span class="m-month">{{ monthly.month }}</span></span>
        </div>
      </div>
      <div class="m-sub">互动组</div>
      <div class="m-grid interacts">
        <div class="m-cell">
          <span class="lb">新增内容</span>
          <span class="vrow"><b class="v">{{ monthly.posts || 0 }}</b><span class="unit">篇</span><span v-if="monthly.prev && monthly.prev.postsPct != null" class="delta" :class="pctClass(monthly.prev.postsPct)">{{ pctText(monthly.prev.postsPct) }}</span></span>
        </div>
        <div class="m-cell">
          <span class="lb">敲木鱼</span>
          <span class="vrow"><b class="v">{{ monthly.likes || 0 }}</b><span class="unit">次</span><span v-if="monthly.prev && monthly.prev.likesPct != null" class="delta" :class="pctClass(monthly.prev.likesPct)">{{ pctText(monthly.prev.likesPct) }}</span></span>
        </div>
        <div class="m-cell">
          <span class="lb">评论</span>
          <span class="vrow"><b class="v">{{ monthly.comments || 0 }}</b><span class="unit">条</span><span v-if="monthly.prev && monthly.prev.commentsPct != null" class="delta" :class="pctClass(monthly.prev.commentsPct)">{{ pctText(monthly.prev.commentsPct) }}</span></span>
        </div>
      </div>
    </section>

    <!-- ⑩ 个人条(墨绿反白, 个人域锚点) -->
    <section class="personal">
      <div class="p-head"><b>我的法布施</b><span>累计以来的个人卷账</span></div>
      <div class="personal-grid">
        <div><span>累计已读天数</span><b>{{ personal.myReadDays || 0 }}</b></div>
        <div><span>连续阅读天数</span><b>{{ personal.myStreakDays || 0 }}</b></div>
        <div><span>最常读类别</span><b class="txt">{{ personal.myTopCategory || '—' }}</b></div>
        <div><span>阅读人次</span><b>{{ personal.myReadCount || 0 }}</b></div>
        <div><span>我的内容</span><b>{{ personal.myPosts || 0 }}</b></div>
        <div><span>获木鱼</span><b>{{ personal.myLikesReceived || 0 }}</b></div>
      </div>
      <a class="p-cta" href="/fabushi/read" @click.prevent="goRead">进入我的阅读 →</a>
    </section>

    <!-- ⑪ 口径说明: 把"哪种数按谁算"一次讲清 -->
    <details class="glossary">
      <summary>口径说明（已读人次 · 获木鱼 · 敲木鱼 · 达成率…）</summary>
      <div class="g-body">
        <div class="g-row"><b>已读人数</b><span>周期内有过阅读记录的去重成员数</span></div>
        <div class="g-row"><b>已读人次</b><span>阅读记录条数，同一人重复阅读累计</span></div>
        <div class="g-row"><b>获木鱼</b><span>内容被点赞数，按内容作者归属（榜单口径）</span></div>
        <div class="g-row"><b>敲木鱼 / 木鱼</b><span>点赞行为次数，按点赞人归属（互动条 / 部门表口径）</span></div>
        <div class="g-row"><b>每日一读达成率</b><span>开提醒成员的应读人日中实读占比</span></div>
        <div class="g-row"><b>近 7 日趋势</b><span>固定滚动近 7 天每日已读人数，不随周期切换变化</span></div>
        <div class="g-row"><b>阅读占比</b><span>个人该指标 / 全榜最高值，跨页可比</span></div>
        <div class="g-row"><b>可见成员</b><span>数据权限范围内的成员集合；全集团时部门表含全部组织</span></div>
      </div>
    </details>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getFabushiDashboard, getFabushiMonthly, getFabushiPersonalStats } from '@/api/fabushi'
import { useUserStore } from '@/store/user'
import { downloadFile } from '@/utils/download'
import { getToken } from '@/utils/token'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const period = ref('week')
const dashboard = ref({
  period: 'week',
  memberCount: 0, todayReadUsers: 0, coveragePct: null,
  periodPosts: 0, periodLikes: 0, periodComments: 0,
  periodReadUsers: 0, periodCoveragePct: null, dailyGoalRate: null,
  ranking: [], deptProgress: [], categoryReads: [], trend: [], bookRanking: [],
})
// 【UI-STATS-REDESIGN-001】personal 扩阅读四格(后端新字段)
const personal = ref({
  myPosts: 0, myComments: 0, myReadCount: 0, myLikesReceived: 0,
  myReadDays: 0, myStreakDays: 0, myTopCategory: null,
})
// 【2026-11-16 P3】月度报告: 默认当月, el-date-picker(type=month) 切换
const monthly = ref({ month: '', periodStart: '', periodEnd: '', memberCount: 0, posts: 0, likes: 0, comments: 0, readCount: 0, readUsers: 0, activeUsers: 0, coveragePct: null, prev: {}, deptProgress: [], ranking: [], trend: [] })
const monthlyMonth = ref(new Date().toISOString().slice(0, 7))

// 【UI-STATS-PAGE-001】成员榜分页态: 默认每页 10; 切排序/每页数回第 1 页
const rankSort = ref('read')
const rankPage = ref(1)
const rankSize = ref(10)

const freshTime = ref('')
const nowLabel = () => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

const scopeText = computed(() => (dashboard.value.memberCount === -1 ? '全集团 · 全部' : `可见成员 ${dashboard.value.memberCount || 0} 人`))
const scopeShort = computed(() => (dashboard.value.memberCount === -1 ? '全集团' : '可见成员'))
const scopeTotalText = computed(() => (dashboard.value.memberCount === -1 ? '全集团' : String(dashboard.value.memberCount || 0)))
const monthlyScopeText = computed(() => (monthly.value.memberCount === -1 ? '全集团' : `可见成员 ${monthly.value.memberCount || 0}`))

// ---- 成员榜: 排序 / 全榜最高值 / 分页 ----
const sortedRanking = computed(() => {
  const key = rankSort.value === 'read' ? 'readCount' : 'postCount'
  return [...(dashboard.value.ranking || [])]
    .sort((a, b) => (Number(b[key]) || 0) - (Number(a[key]) || 0) || (Number(b.readCount) || 0) - (Number(a.readCount) || 0))
})
const rankedTotal = computed(() => sortedRanking.value.length)
const metricLabel = computed(() => (rankSort.value === 'read' ? '阅读占比' : '发布占比'))
const rankGlobalMax = computed(() => {
  const key = rankSort.value === 'read' ? 'readCount' : 'postCount'
  return Math.max(1, ...sortedRanking.value.map((r) => Number(r[key]) || 0))
})
const rankTotalPages = computed(() => Math.max(1, Math.ceil(rankedTotal.value / rankSize.value)))
const rankFrom = computed(() => (rankPage.value - 1) * rankSize.value)
const pagedRanking = computed(() => sortedRanking.value.slice(rankFrom.value, rankFrom.value + rankSize.value))
// 数据刷新后页数收缩时回收越界页码
watch(rankTotalPages, (v) => { if (rankPage.value > v) rankPage.value = v })
const metricPct = (row) => {
  const key = rankSort.value === 'read' ? 'readCount' : 'postCount'
  return Math.round(((Number(row[key]) || 0) * 100) / rankGlobalMax.value)
}
const switchRankSort = (value) => {
  if (rankSort.value === value) return
  rankSort.value = value
  rankPage.value = 1
}

// ---- 类别 / 趋势 / 书目 ----
const maxHeat = computed(() => Math.max(1, ...(dashboard.value.categoryReads || []).map((item) => Number(item.readCount) || 0)))
const totalHeat = computed(() => (dashboard.value.categoryReads || []).reduce((s, i) => s + (Number(i.readCount) || 0), 0) || 1)
const heatWidth = (value) => Math.round(((Number(value) || 0) * 100) / maxHeat.value) + '%'
const heatShare = (value) => Math.round(((Number(value) || 0) * 100) / totalHeat.value)
const maxTrend = computed(() => Math.max(1, ...(dashboard.value.trend || []).map((item) => Number(item.count) || 0)))
const trendHeight = (value) => Math.max(10, Math.round(((Number(value) || 0) * 100) / maxTrend.value)) + '%'
const maxBook = computed(() => Math.max(1, ...(dashboard.value.bookRanking || []).map((r) => Number(r.readCount) || 0)))
const bookPct = (row) => Math.round(((Number(row.readCount) || 0) * 100) / maxBook.value)
// 【UI-STATS-REDESIGN-001】latestReadAt(ISO) → "MM-DD HH:mm" 简显示
const shortTime = (iso) => {
  if (!iso) return '—'
  const m = String(iso).match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/)
  return m ? `${m[2]}-${m[3]} ${m[4]}:${m[5]}` : String(iso)
}
// 【S3358 修复】环比文案: 嵌套三元拆独立语句(无值空串/升箭头/降箭头三分支)
const pctText = (pct) => {
  if (pct == null) return ''
  const arrow = pct > 0 ? '▲ ' : '▼ '
  return arrow + Math.abs(pct) + '%'
}
const pctClass = (pct) => ({ up: pct != null && pct > 0, down: pct != null && pct < 0 })

const goRead = () => router.push('/fabushi/read')

const loadData = async () => {
  loading.value = true
  try {
    const [d, p] = await Promise.all([getFabushiDashboard(period.value), getFabushiPersonalStats()])
    dashboard.value = (d && d.data) || dashboard.value
    personal.value = (p && p.data) || personal.value
    freshTime.value = nowLabel()
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
  rankPage.value = 1
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
  freshTime.value = nowLabel()
  loadData()
  loadMonthly()
})
</script>

<style scoped>
/* ===== 设计系统成文规则(与过审原型 v4 一致) =====
   1. 圆角四档: 卡 12 / 控件 8 / 章签 6 / 胶囊·圆 999
   2. 间距 4px 网格: 区块间隙 12, 卡片水平内边距 16, 纵向 8/12/16/20
   3. 边框两档: 卡外框 1px solid #e8e0d3; 卡内分隔 1px dashed #efe7d8; 卡内单元格无框
   4. 字号七档: 10/11/12/13/15/21/30, 大数字衬线两档 21/30
   5. 对齐: 并排卡标题同行; 数值列右对齐含表头; 条形统一高 6px
   6. 控件高 30px; 面板头迷你件 22px; 分页钮 24px */
.fabushi-stats { display: flex; flex-direction: column; gap: 12px; }

/* ① 页头 */
.page-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-head h1 { margin: 0; color: var(--text-primary); font-family: 'Songti SC', 'STSong', serif; font-size: 21px; font-weight: 800; letter-spacing: 2px; }
.page-head .sub { margin: 4px 0 0; color: #a67c52; font-size: 11px; letter-spacing: 1px; }
.head-right { display: flex; align-items: center; gap: 12px; }
.scope-chip { display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 12px; border-radius: 999px; background: #e4efe6; color: #2d5a4a; font-size: 12px; }
.scope-chip b { font-weight: 700; }
.fresh { color: var(--text-muted); font-size: 11px; }
.fresh b { color: var(--text-secondary); font-weight: 600; }
.seal { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border: 2px solid #b5543a; border-radius: 8px; color: #b5543a; font-family: 'Songti SC', 'STSong', serif; font-size: 18px; font-weight: 700; transform: rotate(-4deg); opacity: .88; user-select: none; }

/* ② 控制条 */
.ctrl-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 12px 16px; border: 1px solid var(--border-light); border-radius: 12px; background: #fffdf8; }
.ctrl-label { color: #a67c52; font-size: 12px; }
.seg { display: flex; gap: 2px; padding: 3px; border-radius: 8px; background: #efe7d8; }
.seg-item { height: 24px; padding: 0 16px; border: 0; border-radius: 6px; color: #6b6257; background: transparent; font-size: 12px; line-height: 24px; cursor: pointer; transition: background .15s, color .15s; }
.seg-item:hover { color: #2d5a4a; }
.seg-item.active { color: #fff7ee; background: #2d5a4a; font-weight: 600; }
.spacer { flex: 1; }
.btn { display: inline-flex; align-items: center; gap: 5px; height: 30px; padding: 0 14px; border: 1px solid var(--border-light); border-radius: 8px; background: #fffdf8; color: #6b6257; font-size: 12px; cursor: pointer; transition: background .15s, border-color .15s, color .15s; }
.btn:hover { border-color: #7fa08e; color: #2d5a4a; background: #faf6ee; }
.btn.primary { border-color: #2d5a4a; color: #fff7ee; background: #2d5a4a; font-weight: 600; }
.btn.primary:hover { border-color: #356350; background: #356350; color: #fff7ee; }

/* ③ KPI 行 */
.kpi-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.kpi { position: relative; padding: 14px 16px 12px; border: 1px solid var(--border-light); border-radius: 12px; background: #fffdf8; }
.kpi::before { content: ''; position: absolute; top: 0; left: 16px; right: 16px; height: 3px; border-radius: 0 0 999px 999px; background: #2d5a4a; }
.kpi .label { color: var(--text-muted); font-size: 12px; }
.kpi .value { margin-top: 8px; color: var(--text-primary); font-family: 'Songti SC', 'STSong', serif; font-size: 30px; font-weight: 800; font-variant-numeric: tabular-nums; line-height: 1.1; }
.kpi .value .unit { margin-left: 4px; color: var(--text-muted); font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; font-size: 12px; font-weight: 500; }
.kpi .value em { font-style: normal; font-size: 21px; }
.kpi .sub { margin-top: 6px; color: #a67c52; font-size: 11px; }

/* 周期互动条 */
.interact-strip { display: flex; align-items: center; padding: 10px 16px; border: 1px solid var(--border-light); border-radius: 12px; background: #faf6ee; }
.interact-title { display: flex; align-items: baseline; gap: 8px; padding-right: 20px; border-right: 1px dashed #efe7d8; }
.interact-title b { color: var(--text-primary); font-family: 'Songti SC', 'STSong', serif; font-size: 15px; font-weight: 700; letter-spacing: 1px; }
.interact-title span { color: var(--text-muted); font-size: 11px; }
.interact-item { display: flex; align-items: baseline; gap: 8px; padding: 0 24px; }
.interact-item + .interact-item { border-left: 1px dashed #efe7d8; }
.interact-item .num { color: #2d5a4a; font-family: 'Songti SC', 'STSong', serif; font-size: 21px; font-weight: 800; font-variant-numeric: tabular-nums; }
.interact-item .u { color: var(--text-muted); font-size: 11px; }
.interact-item .lb { color: var(--text-secondary); font-size: 12px; }
.interact-note { margin-left: auto; color: #a99d8c; font-size: 11px; }

/* 面板通用 */
.panel { padding: 14px 16px; border: 1px solid var(--border-light); border-radius: 12px; background: #fffdf8; }
.panel-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.panel-head .ttl { color: var(--text-primary); font-family: 'Songti SC', 'STSong', serif; font-size: 15px; font-weight: 700; letter-spacing: 1px; }
.panel-head .hint { color: var(--text-muted); font-size: 11px; }
.panel-head .right { margin-left: auto; display: flex; align-items: center; gap: 8px; }

/* 行型 B: 等宽双卡 */
.twin-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 12px; align-items: stretch; }

/* 表格 */
.rank-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.rank-table th { padding: 8px; color: var(--text-muted); font-size: 11px; font-weight: 500; text-align: left; border-bottom: 1px solid var(--border-light); white-space: nowrap; }
.rank-table th.r, .rank-table td.r { text-align: right; }
.rank-table td { padding: 9px 8px; color: var(--text-secondary); border-bottom: 1px dashed #efe7d8; transition: background .15s; }
.rank-table tbody tr:hover td { background: #faf6ee; }
.rank-table tbody tr:last-child td { border-bottom: 0; }
.rank-table .strong { color: var(--text-primary); font-weight: 600; white-space: nowrap; }
.rank-table .dim { color: var(--text-muted); max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-table .num { font-variant-numeric: tabular-nums; }
.w-no { width: 48px; }
.w-num { width: 84px; }
.w-act { width: 140px; }
.book-table .w-time { width: 110px; font-size: 11px; }
.rank-no { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; border-radius: 999px; background: #f4ede0; color: var(--text-muted); font-size: 11px; font-weight: 700; }
.rank-no.top { color: #b5543a; background: #f8e9e0; }
.who-cell { display: flex; align-items: center; gap: 8px; }
.mini-avatar { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 24px; height: 24px; border-radius: 999px; background: linear-gradient(135deg, #2d5a4a, #4a7a62); color: #fff7ee; font-family: 'Songti SC', 'STSong', serif; font-size: 11px; font-weight: 700; }
.metric-bar { display: inline-flex; align-items: center; gap: 8px; }
.metric-bar .tr { display: inline-block; width: 72px; height: 6px; overflow: hidden; border-radius: 999px; background: #efe7d8; }
.metric-bar .tr i { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, #2d5a4a, #4a7a62); }
.metric-bar .pc { color: var(--text-muted); font-size: 11px; font-variant-numeric: tabular-nums; min-width: 32px; }

/* 分页底栏 */
.rank-pager { margin-top: 12px; padding-top: 8px; border-top: 1px dashed #efe7d8; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.rank-pager .info { color: #a99d8c; font-size: 11px; }
.rank-pager .info b { color: var(--text-secondary); font-weight: 600; }
.pager-size { height: 24px; padding: 0 6px; border: 1px solid var(--border-light); border-radius: 6px; background: #fffdf8; color: var(--text-secondary); font-size: 11px; cursor: pointer; }
.pager-ctrl { margin-left: auto; display: flex; align-items: center; gap: 4px; }
.pager-btn { height: 24px; min-width: 24px; padding: 0 8px; border: 1px solid var(--border-light); border-radius: 6px; background: #fffdf8; color: var(--text-secondary); font-size: 11px; font-variant-numeric: tabular-nums; cursor: pointer; transition: background .15s, border-color .15s, color .15s; }
.pager-btn:hover:not(:disabled) { border-color: #7fa08e; color: #2d5a4a; }
.pager-btn:disabled { border-color: #efe7d8; color: #a99d8c; opacity: .6; cursor: default; }
.pager-btn.active { border-color: #2d5a4a; background: #2d5a4a; color: #fff7ee; font-weight: 600; }

/* 类别分布 */
.heat-row { display: flex; align-items: center; gap: 12px; margin: 10px 0; }
.heat-label { flex: 0 0 140px; overflow: hidden; color: var(--text-secondary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.heat-track { flex: 1; height: 6px; overflow: hidden; border-radius: 999px; background: #efe7d8; }
.heat-track i { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, #2d5a4a, #4a7a62); }
.heat-val { flex-shrink: 0; min-width: 24px; color: #2d5a4a; font-size: 12px; font-weight: 700; text-align: right; font-variant-numeric: tabular-nums; }
.heat-share { flex-shrink: 0; width: 44px; color: #a99d8c; font-size: 10px; text-align: right; font-variant-numeric: tabular-nums; }

/* 近 7 日趋势: 0 = 基线空心点 */
.trend { display: flex; align-items: flex-end; gap: 12px; height: 140px; padding-top: 12px; }
.trend-col { display: flex; flex: 1; flex-direction: column; align-items: center; justify-content: flex-end; gap: 4px; height: 100%; }
.trend-val { color: var(--text-muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.trend-val.zero { color: #a99d8c; opacity: .75; }
.trend-bar { width: 100%; max-width: 28px; border-radius: 6px 6px 0 0; background: #a7c0af; }
.trend-col.today .trend-bar { background: #2d5a4a; }
.trend-dot { width: 7px; height: 7px; border: 1.5px solid #b3a58c; border-radius: 999px; background: transparent; }
.trend-label { color: var(--text-muted); font-size: 10px; white-space: nowrap; }
.trend-col.today .trend-label { color: #b5543a; font-weight: 600; }

/* 书目榜 */
.book-cell { display: flex; align-items: center; gap: 8px; }
.mini-cover { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 24px; height: 32px; padding: 2px; border-radius: 3px; background: linear-gradient(135deg, #2d5a4a, #4a7a62); color: #fff7ee; font-family: 'Songti SC', 'STSong', serif; font-size: 9px; line-height: 1.25; text-align: center; letter-spacing: 1px; }
.cat-chip { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 11px; }
.cat-chip.c0 { color: #2d5a4a; background: #e4efe6; }
.cat-chip.c1 { color: #a67c52; background: #f4ead9; }
.cat-chip.c2 { color: #b5543a; background: #f8e9e0; }

/* 分隔带 */
.sec-band { display: flex; align-items: center; gap: 16px; margin: 4px 0; color: #a67c52; font-size: 12px; letter-spacing: 2px; }
.sec-band::before, .sec-band::after { content: ''; flex: 1; height: 1px; background: var(--border-light); }
.sec-band .note { color: #a99d8c; font-size: 11px; letter-spacing: 0; }

/* 部门进度 */
.dept-table .strong { max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cov-wrap { display: inline-flex; align-items: center; gap: 8px; }
.cov-num { min-width: 40px; color: #2d5a4a; font-size: 12px; font-weight: 700; font-variant-numeric: tabular-nums; }
.cov-track { display: inline-block; width: 96px; height: 6px; overflow: hidden; border-radius: 999px; background: #efe7d8; }
.cov-track i { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, #2d5a4a, #4a7a62); }
.dim-c { color: #a99d8c; }
.dept-badge { display: inline-block; margin-left: 8px; padding: 1px 8px; border-radius: 999px; background: #f4ead9; color: #a67c52; font-size: 10px; font-weight: 600; }
.dept-badge.unassigned { background: #f1eee8; color: #a99d8c; }
.table-scroll { max-height: 460px; overflow-y: auto; overscroll-behavior: contain; }

/* 月度报告 */
.monthly-tools { display: flex; align-items: center; gap: 8px; }
.m-sub { margin: 12px 2px 8px; color: #a67c52; font-size: 12px; font-weight: 600; letter-spacing: 1px; }
.m-sub:first-of-type { margin-top: 4px; }
.m-grid { display: grid; gap: 12px; padding-bottom: 4px; }
.m-grid.reads { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.m-grid.interacts { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.m-cell { padding: 10px 14px; border-radius: 8px; background: #faf6ee; }
.m-cell .lb { display: block; color: var(--text-muted); font-size: 11px; }
.m-cell .vrow { display: flex; align-items: center; gap: 8px; margin-top: 4px; }
.m-cell b.v { color: #2d5a4a; font-family: 'Songti SC', 'STSong', serif; font-size: 21px; font-weight: 800; font-variant-numeric: tabular-nums; line-height: 1.2; }
.m-cell .vrow .unit { color: var(--text-muted); font-size: 11px; }
.m-cell .m-month { color: #a99d8c; font-size: 11px; }
.delta { display: inline-flex; align-items: center; padding: 1px 7px; border-radius: 999px; font-size: 10px; line-height: 16px; font-variant-numeric: tabular-nums; }
.delta.up { color: #2d5a4a; background: #e4efe6; }
.delta.down { color: #b5543a; background: #f8e9e0; }
.m-progress { flex: 1; max-width: 160px; height: 6px; overflow: hidden; border-radius: 999px; background: #efe7d8; }
.m-progress i { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, #2d5a4a, #4a7a62); }

/* 个人条 */
.personal { display: flex; align-items: center; gap: 24px; padding: 16px; border-radius: 12px; background: linear-gradient(135deg, #2d5a4a 0%, #4a7a62 100%); color: #fff7ee; }
.personal .p-head { display: flex; flex-direction: column; gap: 4px; padding-right: 24px; border-right: 1px dashed rgba(255, 247, 238, .35); }
.personal .p-head b { font-family: 'Songti SC', 'STSong', serif; font-size: 15px; font-weight: 700; letter-spacing: 1px; }
.personal .p-head span { color: rgba(255, 247, 238, .72); font-size: 11px; }
.personal-grid { display: flex; gap: 32px; flex-wrap: wrap; flex: 1; }
.personal-grid div { display: flex; flex-direction: column; gap: 4px; }
.personal-grid span { color: rgba(255, 247, 238, .72); font-size: 11px; }
.personal-grid b { color: #fff7ee; font-family: 'Songti SC', 'STSong', serif; font-size: 21px; font-weight: 800; font-variant-numeric: tabular-nums; line-height: 1.2; }
.personal-grid b.txt { font-size: 15px; font-weight: 700; }
.personal .p-cta { flex-shrink: 0; display: inline-flex; align-items: center; height: 30px; padding: 0 16px; border: 1px solid rgba(255, 247, 238, .55); border-radius: 999px; color: #fff7ee; font-size: 12px; text-decoration: none; transition: background .15s; }
.personal .p-cta:hover { background: rgba(255, 247, 238, .14); }

/* 口径说明 */
.glossary { border: 1px solid var(--border-light); border-radius: 12px; background: #fffdf8; }
.glossary summary { padding: 10px 16px; color: var(--text-muted); font-size: 11px; cursor: pointer; list-style: none; }
.glossary summary::-webkit-details-marker { display: none; }
.glossary summary::before { content: '▸ '; color: #a67c52; }
.glossary[open] summary::before { content: '▾ '; }
.glossary .g-body { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 4px 32px; padding: 2px 16px 12px; }
.glossary .g-row { display: flex; gap: 8px; font-size: 11px; color: var(--text-muted); line-height: 1.8; }
.glossary .g-row b { flex-shrink: 0; color: var(--text-secondary); font-weight: 600; }

.empty { padding: 16px 0; color: var(--text-muted); font-size: 12px; text-align: center; }

@media (prefers-reduced-motion: reduce) {
  .seg-item, .btn, .pager-btn, .rank-table td, .p-cta { transition: none; }
}
@media (max-width: 1100px) {
  .kpi-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .twin-row { grid-template-columns: 1fr; }
  .m-grid.reads, .m-grid.interacts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .glossary .g-body { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .personal { flex-wrap: wrap; }
}
@media (max-width: 640px) {
  .m-grid.reads, .m-grid.interacts, .glossary .g-body { grid-template-columns: 1fr; }
}
</style>
