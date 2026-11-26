<template>
  <div class="fx-page fabushi-stats" v-loading="loading">
    <!-- 【2026-11-26 按 PC 原型 v8】统计看板 = 原型 P12：主视觉条 → 五张数字卡 → 成员榜 / 类别热度分布 / 每日达成趋势 →
         书目阅读榜 → 月度盘点 → 部门进度 → 月度报告 → 我的法布施 → 口径说明。
         只改外观：数据口径与接口零改动，原有控件一个不少（周期切换、刷新、导出、按阅读 / 按发布、三处翻页、月度导出）。
         原型里给评审看的标注（编号、“已上线”那句）不是界面文案。 -->
    <!-- ① 主视觉条（原型 .stat-hero）：标题 + 口径说明 / 范围章 / 周期开关（切换即查，无冗余查询钮）/ 刷新 / 导出 -->
    <div class="stat-hero">
      <div class="sh-main">
        <div class="sh-t">法布施 · 统计看板</div>
        <div class="sh-sub">口径＝每日诵读 / 每日随施 / 再读一本 三类 · 数据截至 {{ freshTime }}</div>
      </div>
      <span class="sh-chip">{{ scopeText }}</span>
      <span class="spacer" />
      <div class="seg" role="group" aria-label="统计周期">
        <button type="button" class="seg-item" :class="{ active: period === 'week' }" :aria-pressed="period === 'week'" @click="switchPeriod('week')">本周</button>
        <button type="button" class="seg-item" :class="{ active: period === 'month' }" :aria-pressed="period === 'month'" @click="switchPeriod('month')">本月</button>
      </div>
      <div class="sh-acts">
        <el-button class="hero-btn" :icon="Refresh" @click="loadData">刷新</el-button>
        <el-button v-if="userStore.hasPermission('fabushi:stats:export')" class="hero-btn" :icon="Download" @click="exportStats">导出报表（CSV）</el-button>
      </div>
    </div>

    <!-- ② 数字卡 ×5（原型 .kcards / .kcard2）：只放接口真有的数。
         原型的「今日已诵 / 已随施人数」「三类拆分」「人均复诵」接口没有，不放；见口径说明。 -->
    <div class="kcards">
      <div v-for="(card, i) in kpiCards" :key="card.label" class="kcard2">
        <div class="krow">
          <span class="kicon" :class="'ki' + i" aria-hidden="true"><ElIcon><component :is="card.icon" /></ElIcon></span>
          <span class="kv2">
            <template v-if="card.value != null">{{ card.value }}<i v-if="card.unit">{{ card.unit }}</i></template>
            <template v-else>—</template>
          </span>
          <span class="ktrend" :class="kpiTrendClass(i)">{{ kpiTrendText(i) }}</span>
        </div>
        <div class="kl2">{{ card.label }}</div>
        <div class="ksub" :title="card.note || undefined">{{ card.sub }}</div>
        <div class="spark" aria-hidden="true">
          <i v-for="(h, idx) in sparkHeights" :key="idx" :class="{ zero: sparkZeros[idx] }" :style="{ height: h + '%' }" />
        </div>
      </div>
    </div>

    <!-- ③ 成员榜（左）+ 类别热度分布 / 每日达成趋势（右）：宽屏左右排，窄屏上下排 -->
    <div class="dash-grid">
      <!-- 成员榜：名次章 = 全榜前三，占比条 = 指标 / 全榜最高值（跨页可比），前端分页 -->
      <section class="table-card panel-pad rank-card">
        <div class="panel-title">
          成员榜 · {{ periodName }}
          <span class="tt-sub">默认按已读人次排序 · 名次章只属全榜前三</span>
          <span class="spacer" />
          <div class="seg" role="group" aria-label="成员榜排序">
            <button type="button" class="seg-item" :class="{ active: rankSort === 'read' }" :aria-pressed="rankSort === 'read'" @click="switchRankSort('read')">按阅读</button>
            <button type="button" class="seg-item" :class="{ active: rankSort === 'post' }" :aria-pressed="rankSort === 'post'" @click="switchRankSort('post')">按发布</button>
          </div>
        </div>
        <div class="table-scroll">
          <table class="rank-table member-table">
            <thead>
              <tr>
                <th class="c-no">名次</th><th>成员</th><th>部门</th>
                <!-- 【PROTO-AUDIT-20261123 P12】三类型口径落到成员榜（原型列：诵读 / 随施 / 再读） -->
                <th class="num" title="每日诵读发布数">诵读</th>
                <th class="num" title="每日随施发布数">随施</th>
                <th class="num" title="再读一本发布数">再读</th>
                <th class="num">已读天数</th><th class="num">已读人次</th>
                <th class="num">发布</th><th class="num" title="内容被点赞数，按内容归属">获木鱼</th>
                <!-- 【P12 2026-11-23】复诵指标：口径＝他人对本人在本周期内容的复诵行数 -->
                <th class="num" title="本人内容被他人复诵的次数，按内容归属、限本周期">被复诵</th>
                <th class="c-bar" title="个人该指标 / 全榜最高值，跨页可比">{{ metricLabel }}</th>
                <!-- 原型「活跃度」列：条长 = 接口排行项里的 activityPct（发布数 / 全榜最高发布数） -->
                <th class="c-act" title="发布数 / 全榜最高发布数">活跃度</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in pagedRanking" :key="row.userId">
                <td class="c-no"><span class="rk2" :class="medalClass(rankFrom + i)">{{ rankFrom + i + 1 }}</span></td>
                <td><span class="who"><span class="mini-ava">{{ (row.userName || '?').slice(0, 1) }}</span><b>{{ row.userName }}</b></span></td>
                <td><span class="dept" :title="row.deptName">{{ row.deptName }}</span></td>
                <td class="num">{{ row.songduCount || 0 }}</td>
                <td class="num">{{ row.suishiCount || 0 }}</td>
                <td class="num">{{ row.zaiduCount || 0 }}</td>
                <td class="num">{{ row.readDays }}</td>
                <td class="num">{{ row.readCount }}</td>
                <td class="num">{{ row.postCount }}</td>
                <td class="num">{{ row.likesReceived }}</td>
                <td class="num">{{ row.recitationsReceived || 0 }}</td>
                <td class="c-bar"><span class="metric-bar"><span class="tr"><i :style="{ width: metricPct(row) + '%' }" /></span><span class="pc">{{ metricPct(row) }}%</span></span></td>
                <td class="c-act"><div class="rb act-bar" :title="`活跃度 ${activityPct(row)}%`"><i :style="{ width: activityPct(row) + '%' }" /></div></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="!rankedTotal" class="empty">本周期暂无阅读 / 发布数据</div>
        <!-- 翻页底栏：左信息、右翻页 -->
        <div v-if="rankedTotal" class="pager rank-pager">
          <span class="info">共 <b>{{ rankedTotal }}</b> 人上榜（可见范围 {{ scopeTotalText }}{{ dashboard.memberCount === -1 ? '' : ' 人' }}）</span>
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

      <div class="dash-side">
        <!-- 类别热度分布：条长 = 该类别 / 最高的类别，数值 = 已读人次，后附占比 -->
        <section class="table-card panel-pad heat-card">
          <div class="panel-title">类别热度分布<span class="tt-sub">已读人次 · 占比</span></div>
          <div class="heat-list">
            <div v-for="(item, i) in dashboard.categoryReads || []" :key="item.categoryCode" class="hbar-row">
              <span class="hbar-lbl" :title="item.categoryName">{{ item.categoryName }}</span>
              <div class="hbar-track"><i :class="heatHue(i)" :style="{ width: heatWidth(item.readCount) }" /></div>
              <span class="hbar-val">{{ item.readCount }}</span>
              <span class="hbar-share">{{ heatShare(item.readCount) }}%</span>
            </div>
          </div>
          <div v-if="!(dashboard.categoryReads || []).length" class="empty">本周期暂无书目阅读</div>
        </section>

        <!-- 每日达成趋势：固定滚动近几日每日已读人数，不随周期切换变化；最后一根是今天 -->
        <section class="table-card panel-pad trend-card">
          <div class="panel-title">每日达成趋势<span class="tt-sub">近 7 日 · 每日已读人数</span></div>
          <div class="chart">
            <div
              v-for="(item, i) in dashboard.trend || []"
              :key="item.date"
              class="bar"
              :class="{ today: i === (dashboard.trend || []).length - 1, zero: !item.count }"
              :style="{ height: trendHeight(item.count) }"
            >
              <span class="v">{{ item.count }}</span>
            </div>
          </div>
          <div class="chart-x">
            <span v-for="(item, i) in dashboard.trend || []" :key="item.date">{{ i === (dashboard.trend || []).length - 1 ? '今日' : item.label }}</span>
          </div>
        </section>
      </div>
    </div>

    <!-- ④ 书目阅读榜（全宽） -->
    <section class="table-card panel-pad book-card">
      <div class="panel-title">
        书目阅读榜 · {{ dashboard.period === 'month' ? '本月' : '本周' }}
        <span class="tt-sub">按已读人次排序 · 仅章节帖计入 · Top 10</span>
      </div>
      <table class="rank-table">
        <thead><tr><th>书名</th><th>类别</th><th class="num">已读人数</th><th class="num">已读人次</th><th class="c-bar">阅读占比</th><th>最新阅读</th></tr></thead>
        <tbody>
          <tr v-for="(row, i) in pagedBookRanking" :key="row.bookId">
            <td><span class="book-cell"><span class="mini-cover">{{ (row.title || '书').replace(/[《》]/g, '').slice(0, 2) }}</span><b>{{ row.title }}</b></span></td>
            <!-- 【GL-14 2026-11-24】行号先转数字再取模: 类型检查不认这里的循环下标是数字(运行结果不变) -->
            <td><span class="tag" :class="catHue(i)">{{ row.categoryName || row.category || '—' }}</span></td>
            <td class="num">{{ row.readUsers }}</td>
            <td class="num">{{ row.readCount }}</td>
            <td class="c-bar"><span class="metric-bar"><span class="tr"><i :style="{ width: bookPct(row) + '%' }" /></span><span class="pc">{{ bookPct(row) }}%</span></span></td>
            <td class="time">{{ shortTime(row.latestReadAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="!(dashboard.bookRanking || []).length" class="empty">本周期暂无书目阅读</div>
      <div v-if="bookRankTotalPages > 1" class="pager">
        <span>共 {{ (dashboard.bookRanking || []).length }} 本</span>
        <el-pagination v-model:current-page="bookRankPage" :page-size="bookRankSize" :total="(dashboard.bookRanking || []).length" layout="prev, pager, next" size="small" />
      </div>
    </section>

    <!-- ⑤ 分隔带: 月度盘点是独立月份口径, 与上方周期切换切开 -->
    <div class="sec-band">月 度 盘 点<span class="note">独立月份口径 · 与上方本周 / 本月切换无关</span></div>

    <!-- ⑥ 部门进度（全宽）: 公司直属/未分配徽标; 互动列=谁做的(后端真实口径) -->
    <section class="table-card panel-pad dept-card">
      <div class="panel-title">
        部门进度 · {{ period === 'week' ? '本周' : '本月' }}
        <span class="tt-sub">覆盖率 = 已读人数 / 部门成员数 · 木鱼 / 评论按"谁做的"计入本部门成员</span>
      </div>
      <div class="table-scroll">
        <table class="rank-table dept-table">
          <thead><tr><th>部门</th><th class="num">成员数</th><th class="num">已读人数</th><th>覆盖率</th><th class="num">发布</th><th class="num" title="本部门成员敲出的点赞数">木鱼</th><th class="num" title="本部门成员发出的评论数">评论</th><th class="num">已读天数</th></tr></thead>
          <tbody>
            <tr v-for="row in pagedDeptProgress" :key="row.deptId ?? 'none'">
              <td><span class="strong" :title="row.deptName">{{ row.deptName }}</span><span v-if="row.companyLevel" class="tag orange">公司直隶</span><span v-if="row.deptId == null" class="tag gray">无部门成员</span></td>
              <td class="num">{{ row.memberCount }}</td>
              <td class="num">{{ row.readUsers }}</td>
              <td>
                <span v-if="row.coveragePct == null" class="dim-c">—</span>
                <span v-else class="cov-wrap"><b class="cov-num">{{ row.coveragePct }}%</b><span class="cov-track"><i :style="{ width: Math.min(100, row.coveragePct || 0) + '%' }" /></span></span>
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
      <!-- 部门进度每页 8 个: 超过 8 个部门时靠这条翻页条才看得到后面的 -->
      <div v-if="deptTotalPages > 1" class="pager">
        <span>共 {{ (dashboard.deptProgress || []).length }} 个部门</span>
        <el-pagination v-model:current-page="deptPage" :page-size="deptSize" :total="(dashboard.deptProgress || []).length" layout="prev, pager, next" size="small" />
      </div>
    </section>

    <!-- ⑦ 月度报告: 阅读组 / 互动组各一行格子; 环比无值时隐藏不露占位符 -->
    <section class="table-card panel-pad monthly-card">
      <div class="panel-title">
        月度报告
        <span class="tt-sub">环比对上月</span>
        <span class="spacer" />
        <span class="monthly-tools">
          <el-date-picker v-model="monthlyMonth" type="month" placeholder="选择月份" value-format="YYYY-MM"
            :clearable="false" style="width: 118px" size="small" @change="loadMonthly" />
          <el-button v-if="userStore.hasPermission('fabushi:stats:export')" size="small" plain :icon="Download" @click="exportMonthly">导出</el-button>
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

    <!-- ⑧ 个人条: 累计以来的个人卷账 -->
    <section class="table-card panel-pad personal-card">
      <div class="p-head"><b>我的法布施</b><span>累计以来的个人卷账</span></div>
      <div class="personal-grid">
        <div><span>累计已读天数</span><b>{{ personal.myReadDays || 0 }}</b></div>
        <div><span>连续阅读天数</span><b>{{ personal.myStreakDays || 0 }}</b></div>
        <div><span>最常读类别</span><b class="txt">{{ personal.myTopCategory || '—' }}</b></div>
        <div><span>阅读人次</span><b>{{ personal.myReadCount || 0 }}</b></div>
        <div><span>我的内容</span><b>{{ personal.myPosts || 0 }}</b></div>
        <div><span>获木鱼</span><b>{{ personal.myLikesReceived || 0 }}</b></div>
      </div>
      <el-button type="primary" @click="goRead">进入我的觉醒</el-button>
    </section>

    <!-- ⑨ 口径说明: 把“哪种数按谁算”一次讲清 -->
    <details class="glossary">
      <summary>口径说明（已读人次 · 获木鱼 · 敲木鱼 · 达成率…）</summary>
      <div class="g-body">
        <div class="g-row"><b>已读人数</b><span>周期内有过阅读记录的去重成员数</span></div>
        <div class="g-row"><b>已读人次</b><span>阅读记录条数，同一人重复阅读累计</span></div>
        <div class="g-row"><b>获木鱼</b><span>内容被点赞数，按内容作者归属（榜单口径）</span></div>
        <div class="g-row"><b>被复诵</b><span>本人内容被他人复诵的次数，按内容作者归属、限本周期；复诵不计入本人诵读完成度（防刷口径）</span></div>
        <div class="g-row"><b>敲木鱼 / 木鱼</b><span>点赞行为次数，按点赞人归属（数字卡 / 部门表口径）</span></div>
        <div class="g-row"><b>周期互动</b><span>发布 / 木鱼 / 评论按「谁做的」计；榜单「获木鱼」按「内容归属」计，两处的木鱼数口径不同</span></div>
        <div class="g-row"><b>每日一读达成率</b><span>开提醒成员的应读人日中实读占比</span></div>
        <div class="g-row"><b>阅读覆盖率</b><span>周期已读人数 / 可见成员数；全集团范围接口不给人数，显示“—”</span></div>
        <div class="g-row"><b>近 7 日趋势</b><span>固定滚动近 7 天每日已读人数，不随周期切换变化</span></div>
        <div class="g-row"><b>指标卡迷你条</b><span>五张卡共用同一条「近 7 日每日已读人数」趋势，仅作视觉参照，不代表各自指标的日序列</span></div>
        <div class="g-row"><b>阅读占比</b><span>个人该指标 / 全榜最高值，跨页可比</span></div>
        <div class="g-row"><b>活跃度</b><span>成员发布数 / 全榜最高发布数</span></div>
        <div class="g-row"><b>可见成员</b><span>数据权限范围内的成员集合；全集团时部门表含全部组织</span></div>
      </div>
    </details>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { usePagedList } from '@/composables/usePagedList'
import { useRouter } from 'vue-router'
import { ElIcon } from 'element-plus'
import { ChatDotRound, Download, PieChart, Reading, Refresh, TrendCharts, User } from '@element-plus/icons-vue'
import { getFabushiDashboard, getFabushiMonthly, getFabushiPersonalStats } from '@/api/fabushi'
import { useUserStore } from '@/store/user'
import { downloadFile } from '@/utils/download'
import { getToken } from '@/utils/token'
import '@/assets/fabushi-v8.css'

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
const { current: bookRankPage, size: bookRankSize, totalPages: bookRankTotalPages, paged: pagedBookRanking } = usePagedList(computed(() => dashboard.value.bookRanking || []), { pageSize: 8 })
const { current: deptPage, size: deptSize, totalPages: deptTotalPages, paged: pagedDeptProgress } = usePagedList(computed(() => dashboard.value.deptProgress || []), { pageSize: 8 })
// 默认月份按本机日期算（toISOString 是 UTC：月初的深夜、月末的傍晚会和本机差一个月）
const localMonthOf = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
const monthlyMonth = ref(localMonthOf(new Date()))

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
const monthlyScopeText = computed(() => (monthly.value.memberCount === -1 ? '全集团' : `可见成员 ${monthly.value.memberCount || 0} 人`))
// 周期名跟着接口回来的 period 走(本周 / 本月)
const periodName = computed(() => (dashboard.value.period === 'month' ? '本月' : '本周'))

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
// 活跃度条: 直接用接口排行项里的 activityPct, 不在前端另算
const activityPct = (row) => Math.min(100, Math.max(0, Math.round(Number(row.activityPct) || 0)))
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
// 横条 / 类别章的颜色按行号轮换(纯装饰, 不代表类别含义)
const heatHue = (i) => 'h' + (Number(i) % 5)
const catHue = (i) => ['blue', 'cyan', 'green'][Number(i) % 3]
const maxTrend = computed(() => Math.max(1, ...(dashboard.value.trend || []).map((item) => Number(item.count) || 0)))
// 没人读的日子是空柱(高度 0, 由样式留一道细线); 其余最高的一天占满
const trendHeight = (value) => {
  const count = Number(value) || 0
  return count ? Math.max(10, Math.round((count * 100) / maxTrend.value)) + '%' : '0%'
}
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

// 五张数字卡(原型 P12): 图标 / 大数字 / 右上角的章 / 小字说明 / 底部迷你条。
// 数字全部来自现有 dashboard 字段, 零接口改动。接口给 null 的(全集团的覆盖率、无人开提醒的达成率)显示“—”, 不在前端另算。
// 原型的「今日已诵 / 已随施人数」「内容三类拆分」「本月木鱼」「人均复诵」后端没有对应字段, 不做。
const kpiCards = computed(() => [
  {
    label: '今日已读人数', icon: Reading,
    value: dashboard.value.todayReadUsers || 0, unit: '人',
    sub: `今日已读 / ${scopeShort.value} ${dashboard.value.coveragePct != null ? dashboard.value.coveragePct + '%' : '—'}`,
    note: '',
  },
  {
    label: '周期已读人数', icon: User,
    value: dashboard.value.periodReadUsers || 0, unit: '人',
    sub: `${periodName.value}内有过阅读的成员`,
    note: '',
  },
  {
    label: '阅读覆盖率', icon: PieChart,
    value: dashboard.value.periodCoveragePct, unit: '%',
    sub: `= 周期已读人数 / ${scopeShort.value}`,
    note: '',
  },
  {
    label: '每日一读达成率', icon: TrendCharts,
    value: dashboard.value.dailyGoalRate, unit: '%',
    sub: '提醒应读人日中实读占比',
    note: '',
  },
  {
    label: `${periodName.value}发布内容`, icon: ChatDotRound,
    value: dashboard.value.periodPosts || 0, unit: '篇',
    sub: `木鱼 ${dashboard.value.periodLikes || 0} 次 · 评论 ${dashboard.value.periodComments || 0} 条`,
    note: '口径：发布 / 木鱼 / 评论按「谁做的」计；榜单「获木鱼」按「内容归属」计',
  },
])
const trendValues = computed(() => (dashboard.value.trend || []).map((item) => Number(item.count) || 0))
// 右上角的章: 只有「今日已读人数」有环比(今天比昨天, 由 trend 算出); 其余是周期名, 不编别的环比
const trendDelta = computed(() => {
  const values = trendValues.value
  if (values.length < 2 || !values[values.length - 2]) return null
  return Math.round(((values[values.length - 1] - values[values.length - 2]) * 100) / values[values.length - 2])
})
const kpiTrendText = (i) => {
  if (i !== 0 || trendDelta.value == null) return periodName.value
  return (trendDelta.value > 0 ? '↑ ' : trendDelta.value < 0 ? '↓ ' : '') + Math.abs(trendDelta.value) + '%'
}
// 与昨天持平是中性的灰章, 不算上涨
const kpiTrendClass = (i) => (i === 0 && trendDelta.value != null ? (trendDelta.value > 0 ? 'up' : trendDelta.value < 0 ? 'down' : 'flat') : 'flat')
// 迷你条五卡共用「近几日每日已读人数」同口径, 口径说明里已标注, 不伪造其他指标的日序列
const sparkHeights = computed(() => {
  const values = trendValues.value
  const max = Math.max(1, ...values)
  return values.map((v) => Math.max(8, Math.round((v * 100) / max)))
})
// 没人读的日子: 迷你条里是一道灰线
const sparkZeros = computed(() => trendValues.value.map((v) => !v))
const medalClass = (index) => (index === 0 ? 'gold' : index === 1 ? 'silver' : index === 2 ? 'bronze' : '')

// 【2026-11-26】按钮写的是“进入我的觉醒”, 就去“我的觉醒”那一页(与认知广场右栏的同名按钮同一个去处)。
// 此前这里跳的是书目浏览: 11-23 的一次修复把 /fabushi/read(没有书目编号, 路由不匹配)改成了 /fabushi/library, 文案没跟着改。
const goRead = () => router.push('/fabushi/shelf')

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
// pagination-audit: no-pager 月度报告按月整体返回、不带页码，也不喂本页任何分页列表（本页的分页器都是对本地数据分页）
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
/* ===== 按 PC 原型 v8（P12 统计）=====
   通用件（数字卡 .kcards/.kcard2/.spark、横条 .hbar-*、名次章 .rk2、进度条 .rb、柱状图 .chart、分段开关 .seg、
   表格卡 .table-card/.panel-title、标签 .tag）在 assets/fabushi-v8.css；这里只写这一屏自己的版式，
   数值取自原型的样式表，颜色一律用令牌。 */

/* ① 主视觉条 */
.stat-hero {
  display: flex; flex-wrap: wrap; flex-shrink: 0; align-items: center; gap: 16px;
  padding: 14px 18px; border-radius: var(--fx-r-md); background: var(--hero-gradient);
  box-shadow: 0 8px 20px rgba(22, 51, 122, .25); color: #fff;
}
.sh-t { font-size: 16px; font-weight: 800; letter-spacing: 1px; }
.sh-sub { margin-top: 3px; font-size: 11px; opacity: .75; }
.sh-chip {
  flex-shrink: 0; padding: 4px 12px; border: 1px solid rgba(255, 255, 255, .35); border-radius: var(--fx-r-pill);
  background: rgba(255, 255, 255, .16); font-size: 11px; white-space: nowrap;
}
.stat-hero .seg { background: rgba(255, 255, 255, .16); }
.stat-hero .seg-item { color: rgba(255, 255, 255, .85); }
.stat-hero .seg-item:hover { color: #fff; }
.stat-hero .seg-item.active { background: #fff; color: var(--primary-color); }
.sh-acts { display: flex; flex-shrink: 0; gap: 8px; }
.sh-acts .el-button + .el-button { margin-left: 0; }
/* 主视觉条上的按钮：半透明白底（原型导出按钮的写法） */
.hero-btn.el-button {
  --el-button-text-color: #fff;
  --el-button-bg-color: rgba(255, 255, 255, .18);
  --el-button-border-color: rgba(255, 255, 255, .4);
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: rgba(255, 255, 255, .28);
  --el-button-hover-border-color: rgba(255, 255, 255, .6);
  --el-button-active-text-color: #fff;
  --el-button-active-bg-color: rgba(255, 255, 255, .32);
  --el-button-active-border-color: rgba(255, 255, 255, .7);
  --el-button-outline-color: rgba(255, 255, 255, .7);
}

/* ② 数字卡 ×5 */
.kcards .kcard2 { display: flex; flex-direction: column; }
.kcards .kcard2 .krow { flex-wrap: wrap; }
.kcards .kcard2 .kv2 { min-width: 0; white-space: nowrap; }
.kcards .kcard2 .ktrend { flex-shrink: 0; white-space: nowrap; }
.kcards .kcard2 .ktrend.down { background: var(--fx-red-bg); color: var(--fx-red); }
.kcards .kcard2 .ktrend.flat { background: var(--fx-pill-gray-bg); color: var(--fx-pill-gray); }
.kicon { font-size: 17px; }
.kicon.ki0 { background: var(--hero-gradient); }
.kicon.ki1 { background: linear-gradient(135deg, var(--fx-amber-deep), var(--fx-amber)); }
.kicon.ki2 { background: var(--fx-green); }
.kicon.ki3 { background: var(--fx-orange); }
.kicon.ki4 { background: var(--fx-cyan); }
.ksub {
  margin-bottom: 9px; overflow: hidden; color: var(--text-muted); font-size: 10.5px; line-height: 1.45;
  text-overflow: ellipsis; white-space: nowrap;
}
.kcards .kcard2 .spark { margin-top: auto; }
.kcards .spark i.zero { background: var(--fx-line); opacity: 1; }

/* ③ 成员榜 + 两张图 */
.dash-grid { display: flex; flex-direction: column; gap: 10px; }
.dash-side { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; align-items: stretch; }
.heat-card { display: flex; flex-direction: column; }
.heat-list { display: flex; flex: 1; flex-direction: column; justify-content: center; }
.panel-title .tt-sub { font-weight: 400; }
/* 宽屏：成员榜在左，两张图上下叠在右（原型的排法）。断点取 1680px —— 这时左边不窄于 1280 宽下通栏的成员榜 */
@media (min-width: 1680px) {
  .dash-grid { display: grid; grid-template-columns: minmax(0, 1fr) 400px; gap: 14px; align-items: stretch; }
  .rank-card { min-width: 0; }
  .dash-side { display: flex; flex-direction: column; gap: 14px; }
  .heat-card { flex: 1; }
}

/* 表格 */
.table-scroll { max-width: 100%; overflow-x: auto; }
.rank-table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.rank-table th {
  padding: 9px 12px; border-bottom: 1px solid var(--border-light); background: var(--fx-table-head);
  color: var(--fx-pill-gray); font-size: 12.5px; font-weight: 600; text-align: left; white-space: nowrap;
}
.rank-table td { padding: 9px 12px; border-bottom: 1px solid var(--border-light); color: var(--text-primary); white-space: nowrap; }
.rank-table tbody tr:hover td { background: var(--fx-table-head); }
.rank-table tbody tr:last-child td { border-bottom: 0; }
.rank-table .num { text-align: right; font-variant-numeric: tabular-nums; }
.rank-table .c-bar { width: 116px; text-align: left; }

/* 成员榜：原有各列都保留，列多，单元格左右内边距收到 9px */
.member-table th, .member-table td { padding: 9px; }
.member-table .c-no { width: 44px; }
.member-table .c-act { width: 90px; text-align: left; }
.who { display: inline-flex; align-items: center; gap: 7px; vertical-align: middle; }
.who b { max-width: 120px; overflow: hidden; font-weight: 700; text-overflow: ellipsis; }
.mini-ava {
  display: inline-flex; flex-shrink: 0; align-items: center; justify-content: center; width: 22px; height: 22px;
  border-radius: 50%; background: var(--primary-color); color: #fff; font-size: 11px; font-weight: 700;
}
.dept { display: inline-block; max-width: 200px; overflow: hidden; vertical-align: bottom; text-overflow: ellipsis; white-space: nowrap; }
.metric-bar { display: inline-flex; align-items: center; gap: 6px; vertical-align: middle; }
.metric-bar .tr { display: inline-block; width: 56px; height: 7px; overflow: hidden; border-radius: var(--fx-r-pill); background: var(--fx-track); }
.metric-bar .tr i, .cov-track i, .m-progress i {
  display: block; height: 100%; border-radius: var(--fx-r-pill); background: linear-gradient(90deg, #3e8df3, var(--fx-accent));
}
.metric-bar .pc { min-width: 30px; color: var(--text-muted); font-size: 11px; font-variant-numeric: tabular-nums; }
.member-table .act-bar { width: 64px; }

/* 翻页行（原型 .pager / .pg） */
.panel-pad .pager {
  display: flex; flex-wrap: wrap; flex-shrink: 0; align-items: center; justify-content: space-between; gap: 8px;
  margin-top: 12px; padding: 9px 0 0; border-top: 1px solid var(--fx-line-soft); color: var(--text-secondary); font-size: 12px;
}
.panel-pad .pager b { color: var(--text-primary); font-weight: 700; }
.rank-pager .pager-ctrl { display: inline-flex; align-items: center; gap: 4px; margin-left: auto; }
.pager-size {
  height: 26px; padding: 0 6px; border: 1px solid var(--border-light); border-radius: 6px; background: #fff;
  color: var(--text-secondary); font-size: 12px; cursor: pointer;
}
.pager-btn {
  min-width: 26px; height: 26px; padding: 0 6px; border: 1px solid var(--border-light); border-radius: 6px; background: #fff;
  color: var(--text-secondary); font-size: 12px; font-variant-numeric: tabular-nums; font-family: inherit; cursor: pointer;
  transition: color .15s, border-color .15s, background-color .15s;
}
.pager-btn:hover:not(:disabled, .active) { border-color: var(--primary-color); color: var(--primary-color); }
.pager-btn:disabled { color: var(--text-placeholder); cursor: default; }
.pager-btn.active { border-color: var(--primary-color); background: var(--primary-color); color: #fff; font-weight: 700; }

/* 类别热度分布：标签按真实类名放宽，条后带占比 */
.heat-card .hbar-lbl {
  width: auto; min-width: 76px; max-width: 96px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis;
}
.heat-card .hbar-val { text-align: right; }
.hbar-share {
  flex-shrink: 0; min-width: 34px; color: var(--text-placeholder); font-size: 10.5px; font-variant-numeric: tabular-nums;
  text-align: right;
}
.hbar-track i.h0 { background: linear-gradient(90deg, var(--fx-accent), #12b8cf); }
.hbar-track i.h1 { background: linear-gradient(90deg, var(--fx-amber-deep), var(--fx-amber)); }
.hbar-track i.h2 { background: linear-gradient(90deg, var(--fx-green), var(--fx-cyan)); }
.hbar-track i.h3 { background: linear-gradient(90deg, var(--fx-red), var(--fx-amber)); }
.hbar-track i.h4 { background: linear-gradient(90deg, var(--brand-navy), var(--el-color-primary-light-5)); }

/* 每日达成趋势：每根柱顶上标数值（今天那根是黄色，没人读的日子是空柱）；日期与柱子逐根对齐 */
.trend-card .chart { margin-top: 26px; }
.trend-card .bar.zero { background: var(--fx-line); }
.trend-card .bar:not(.today) .v { color: var(--fx-pill-gray); font-weight: 600; }
.trend-card .chart-x { justify-content: flex-start; gap: 6px; }
.trend-card .chart-x span { flex: 1; white-space: nowrap; text-align: center; }

/* 书目阅读榜 */
.book-cell { display: inline-flex; align-items: center; gap: 8px; max-width: 100%; vertical-align: middle; }
.book-cell b { min-width: 0; overflow: hidden; font-weight: 700; text-overflow: ellipsis; }
.mini-cover {
  display: inline-flex; flex-shrink: 0; align-items: center; justify-content: center; width: 24px; height: 32px; padding: 2px;
  border-radius: 4px; background: linear-gradient(150deg, var(--fx-navy), var(--primary-color));
  box-shadow: 0 3px 6px rgba(35, 57, 96, .2); color: #fff; font-size: 9px; line-height: 1.25; text-align: center;
}
.book-card .time { color: var(--text-muted); font-size: 11.5px; }

/* 分隔带 */
.sec-band {
  display: flex; flex-shrink: 0; align-items: center; gap: 16px; margin: 6px 0 2px;
  color: var(--text-muted); font-size: 12.5px; font-weight: 700; letter-spacing: 2px;
}
.sec-band::before, .sec-band::after { content: ''; flex: 1; height: 1px; background: var(--border-light); }
.sec-band .note { color: var(--text-placeholder); font-size: 11.5px; font-weight: 400; letter-spacing: 0; }

/* 部门进度 */
.dept-card .table-scroll { max-height: 460px; overflow-y: auto; overscroll-behavior: contain; }
.dept-table .strong { display: inline-block; max-width: 280px; overflow: hidden; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom; }
.dept-card .tag { margin-left: 8px; }
.cov-wrap { display: inline-flex; align-items: center; gap: 8px; vertical-align: middle; }
.cov-num { min-width: 40px; color: var(--text-primary); font-size: 12.5px; font-weight: 700; font-variant-numeric: tabular-nums; }
.cov-track { display: inline-block; width: 96px; height: 7px; overflow: hidden; border-radius: var(--fx-r-pill); background: var(--fx-track); }
.dim-c { color: var(--text-placeholder); }

/* 月度报告 */
.monthly-tools { display: inline-flex; align-items: center; gap: 8px; }
.monthly-card .panel-title + .m-sub { margin-top: 4px; }
.m-sub { margin: 12px 0 8px; color: var(--text-secondary); font-size: 12.5px; font-weight: 700; }
.m-grid { display: grid; gap: 12px; padding-bottom: 4px; }
.m-grid.reads { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.m-grid.interacts { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.m-cell { padding: 10px 14px; border-radius: var(--fx-r-sm); background: var(--fx-table-head); }
.m-cell .lb { display: block; color: var(--text-muted); font-size: 11.5px; }
.m-cell .vrow { display: flex; align-items: center; gap: 8px; margin-top: 4px; }
.m-cell b.v { color: var(--brand-navy); font-size: 21px; font-weight: 800; font-variant-numeric: tabular-nums; line-height: 1.2; }
.m-cell .vrow .unit { color: var(--text-muted); font-size: 11.5px; }
.m-cell .m-month { color: var(--text-placeholder); font-size: 11.5px; }
.delta { display: inline-flex; align-items: center; padding: 1px 7px; border-radius: var(--fx-r-pill); font-size: 10.5px; line-height: 16px; font-variant-numeric: tabular-nums; }
.delta.up { color: var(--fx-green); background: var(--fx-green-bg); }
.delta.down { color: var(--fx-red); background: var(--fx-red-bg); }
.m-progress { flex: 1; max-width: 160px; height: 7px; overflow: hidden; border-radius: var(--fx-r-pill); background: var(--fx-line); }

/* 我的法布施 */
.personal-card { flex-direction: row; flex-wrap: wrap; align-items: center; gap: 16px 24px; }
.personal-card > .el-button { flex-shrink: 0; margin-left: auto; }
.p-head { display: flex; flex-direction: column; gap: 4px; padding-right: 24px; border-right: 1px solid var(--fx-line-soft); }
.p-head b { font-size: 14px; font-weight: 700; }
.p-head span { color: var(--text-muted); font-size: 11.5px; }
.personal-grid { display: flex; flex: 1; flex-wrap: wrap; gap: 12px 32px; }
.personal-grid div { display: flex; flex-direction: column; gap: 4px; }
.personal-grid span { color: var(--text-muted); font-size: 11.5px; }
.personal-grid b { color: var(--text-primary); font-size: 20px; font-weight: 800; font-variant-numeric: tabular-nums; line-height: 1.2; }
.personal-grid b.txt { font-size: 14px; }

/* 口径说明 */
.glossary { border-radius: var(--fx-r-md); background: #fff; box-shadow: var(--shadow-card); }
.glossary summary { padding: 10px 16px; color: var(--text-muted); font-size: 12px; cursor: pointer; list-style: none; }
.glossary summary::-webkit-details-marker { display: none; }
.glossary summary::before { content: '▸'; margin-right: 6px; color: var(--fx-pill-gray); }
.glossary[open] summary::before { content: '▾'; }
.glossary .g-body { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 4px 32px; padding: 2px 16px 12px; }
.glossary .g-row { display: flex; gap: 8px; color: var(--text-muted); font-size: 11.5px; line-height: 1.8; }
.glossary .g-row b { flex-shrink: 0; color: var(--text-secondary); font-weight: 600; }

.empty { padding: 16px 0; color: var(--text-placeholder); font-size: 12px; text-align: center; }

@media (prefers-reduced-motion: reduce) {
  .pager-btn, .rank-table td { transition: none; }
}
@media (max-width: 1100px) {
  .fabushi-stats .kcards { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .dash-side { grid-template-columns: 1fr; }
  .m-grid.reads, .m-grid.interacts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .glossary .g-body { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .m-grid.reads, .m-grid.interacts, .glossary .g-body { grid-template-columns: 1fr; }
}
</style>
