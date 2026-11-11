<template>
  <div class="dashboard">

    <!-- 核心指标 · 深蓝主视觉带(企业级:白字统排,语义差异用小徽标表达) -->
    <!-- 【2026-10-08 UI 整改 D13】六路聚合接口加载中先渲染骨架屏,慢接口期间不再显示误导性 0 值 -->
    <div class="hero-band" v-if="!pageLoading">
      <!-- 【工作流复核确认修复】钻取统一带 tab=all(与已超期卡同口径): 否则落默认"成熟创新"页签
           叠加 devType 过滤, 数字与落地列表严重不对应(实测 264→20) -->
      <div class="hero-metric drill" role="button" tabindex="0"
        @click="$router.push({ path: '/customer', query: { tab: 'all' } })"
        @keydown="onCardKeydown($event, () => $router.push({ path: '/customer', query: { tab: 'all' } }))">
        <div class="hm-label">客户总数</div>
        <div class="hm-num">{{ stats.myCustomers || 0 }}</div>
        <div class="hm-sub">本月新增 {{ stats.monthNew || 0 }} · 公海 {{ stats.seaStats?.total || 0 }}</div>
      </div>
      <div class="hero-divider"></div>
      <div class="hero-metric drill" role="button" tabindex="0"
        @click="$router.push({ path: '/customer', query: { status: 'normal', tab: 'all' } })"
        @keydown="onCardKeydown($event, () => $router.push({ path: '/customer', query: { status: 'normal', tab: 'all' } }))">
        <div class="hm-label">在保护期</div>
        <div class="hm-num">{{ stats.inProtection || 0 }}</div>
        <div class="hm-sub">占比 {{ protectionRate }}%</div>
        <div class="hero-track"><div class="hero-fill" :style="{ width: protectionRate + '%' }"></div></div>
      </div>
      <div class="hero-divider"></div>
      <div class="hero-metric drill" role="button" tabindex="0"
        @click="$router.push('/reminder')"
        @keydown="onCardKeydown($event, () => $router.push('/reminder'))">
        <div class="hm-label">即将到期</div>
        <div class="hm-num">
          {{ stats.expiring || 0 }}<span class="hm-chip chip-amber" v-if="stats.expiring">30天内</span>
        </div>
        <div class="hm-tags" v-if="stats.expiringDetails?.length">
          <span v-for="d in stats.expiringDetails.slice(0, 2)" :key="d.customerName"
            class="hm-tag" :class="d.daysLeft <= 7 ? 'ht-strong' : 'ht-plain'">
            {{ d.officeName || d.customerName }} {{ d.daysLeft }}天
          </span>
        </div>
        <div class="hm-sub" v-else>暂无 30 天内到期客户</div>
      </div>
      <div class="hero-divider"></div>
      <!-- 【A3 2026-10-08】点击落到客户列表「超期」筛选（tab=all 全分类口径），数字与落地内容对应 -->
      <!-- 【口径统一 2026-10-09 用户裁决】已超期=expireDate<今日且不含公海/归档，与统计页/列表钻取同判据，「（含公海）」标注移除 -->
      <div class="hero-metric drill" role="button" tabindex="0"
        @click="$router.push({ path: '/customer', query: { status: 'expired', tab: 'all' } })"
        @keydown="onCardKeydown($event, () => $router.push({ path: '/customer', query: { status: 'expired', tab: 'all' } }))">
        <div class="hm-label">已超期</div>
        <div class="hm-num">
          {{ stats.expired || 0 }}<span class="hm-chip chip-red" v-if="stats.expired">需处理</span>
        </div>
        <div class="hm-sub">点击查看超期客户 · 今日到期 {{ stats.todayExpiring || 0 }}</div>
      </div>
    </div>
    <!-- 骨架屏：与上方 hero-band 同形，避免首屏跳变（已超期口径 2026-10-09 统一为不含公海，与统计页一致） -->
    <div class="hero-band hero-skeleton" v-else aria-busy="true">
      <el-skeleton :rows="3" animated />
      <!-- 【D13 二轮】聚合超 4s 未完成时给出进行中文案，不再无限期静默停留 -->
      <div v-if="slowLoading" class="slow-tip">数据加载较慢，正在努力获取中…</div>
    </div>

    <!-- 运营指标 · 白色定义卡四联(任何角色都是完整四列) -->
    <!-- 【PR-10 前端 2026-09-19】待办任务; 【PR-11 后续批次】销售=self激励/阅读, 管理=审批概览/范围 -->
    <div class="stat-row secondary" v-if="!pageLoading">
      <!-- 【工作流复核确认修复】待办任务卡加 task:view 门控(后端 /api/task/* 均要求该权限, 无权限角色点击即 403) -->
      <div class="stat-card drill" v-if="userStore.hasPermission(PERMISSION_CODES.TASK_VIEW)" role="button" tabindex="0"
        @click="$router.push('/task')" @keydown="onCardKeydown($event, () => $router.push('/task'))">
        <div class="stat-info">
          <div class="stat-label">待办任务</div>
          <div class="stat-num">{{ taskStats.pending || 0 }}</div>
          <div class="stat-sub">今日到期 {{ taskStats.todayDue || 0 }} · 逾期 {{ taskStats.overdue || 0 }}</div>
        </div>
        <el-icon class="stat-icon" :size="22"><List /></el-icon>
      </div>
      <!-- 【体检 P1 2026-10-12】本月完成任务卡同步 task:view 门控(数据源同 /api/task/statistics, 无权限恒 0 且请求弹假错) -->
      <div class="stat-card" v-if="userStore.hasPermission(PERMISSION_CODES.TASK_VIEW)">
        <div class="stat-info">
          <div class="stat-label">本月完成任务</div>
          <div class="stat-num">{{ taskStats.completedThisMonth || 0 }}</div>
          <div class="stat-sub">按自然月统计</div>
        </div>
        <el-icon class="stat-icon" :size="22"><CircleCheck /></el-icon>
      </div>
      <div class="stat-card drill" v-if="isSalesRole" role="button" tabindex="0"
        @click="$router.push('/score/ranking')" @keydown="onCardKeydown($event, () => $router.push('/score/ranking'))">
        <div class="stat-info">
          <div class="stat-label">我的激励学分</div>
          <div class="stat-num">{{ myScore || 0 }}</div>
          <!-- 【体检 P1】口径随数据源改为全年周期(scoreboard 按年聚合), 原文案"本月上传"与实际口径不符 -->
          <div class="stat-sub">四项服务 · 本年上传 {{ myUploadCount || 0 }} 次</div>
        </div>
        <el-icon class="stat-icon" :size="22"><Trophy /></el-icon>
      </div>
      <div class="stat-card" v-if="isSalesRole && canViewReadStats">
        <div class="stat-info">
          <div class="stat-label">我的阅读完成</div>
          <div class="stat-num">{{ myReadCount || 0 }}<span class="stat-unit">/{{ myReadTotal || 0 }}</span></div>
          <div class="stat-sub">本月激励资料阅读(重复打开计一次)</div>
        </div>
        <el-icon class="stat-icon" :size="22"><Reading /></el-icon>
      </div>
      <div class="stat-card drill" v-if="canViewApprovals" role="button" tabindex="0"
        @click="$router.push('/approval')" @keydown="onCardKeydown($event, () => $router.push('/approval'))">
        <div class="stat-info">
          <div class="stat-label">待我审批</div>
          <div class="stat-num">{{ stats.approvalStats?.pending || 0 }}</div>
          <div class="stat-sub">本月通过 {{ stats.approvalStats?.approvedThisMonth || 0 }} · 驳回 {{ stats.approvalStats?.rejectedThisMonth || 0 }}</div>
        </div>
        <el-icon class="stat-icon is-amber" :size="22"><Checked /></el-icon>
      </div>
      <div class="stat-card" v-if="!isSalesRole">
        <div class="stat-info">
          <div class="stat-label">数据范围</div>
          <div class="stat-scope-value">{{ scopeLabel }}</div>
          <div class="stat-sub">{{ stats.myCustomers || 0 }} 客户 · 公海 {{ stats.seaStats?.total || 0 }}</div>
        </div>
        <el-icon class="stat-icon" :size="22"><DataAnalysis /></el-icon>
      </div>
    </div>
    <!-- 骨架屏：与上方运营指标卡同形 -->
    <div class="stat-row secondary" v-else>
      <div v-for="n in 4" :key="'sk-card-' + n" class="stat-card" aria-busy="true">
        <el-skeleton :rows="2" animated />
      </div>
    </div>

    <!-- 【PR-11 首批】统计卡片口径标注:范围=本人数据范围(后端按权限过滤),进页面/点刷新时更新 -->
    <div class="stat-scope-note">
      <span>统计范围：我的客户（按账号数据范围过滤） · 周期：本月 / 30 天到期窗口</span>
      <span class="scope-right">刷新于 {{ lastRefreshedAt || '—' }} <a @click="loadData">立即刷新</a></span>
    </div>

    <!-- 快捷入口 -->
    <div class="quick-row">
      <div class="quick-item" v-for="q in quickActions" :key="q.label" role="button" tabindex="0"
        @click="$router.push(q.path)" @keydown="onCardKeydown($event, () => $router.push(q.path))">
        <el-icon :size="26"><component :is="q.icon" /></el-icon>
        <span>{{ q.label }}</span>
      </div>
    </div>

    <!-- 每周统计 -->
    <div class="weekchart-section" v-if="!pageLoading">
      <div class="wchart-card">
        <div class="wchart-head"><span class="wch-dot c-blue"></span>本月新增客户 <span class="wchart-total">{{ weeklyCustomerTotal }}</span></div>
        <div class="hbar-list">
          <div v-for="(b,i) in weeklyCustomer" :key="'c'+i" class="hbar-row">
            <span class="hbar-lbl" :class="{ 'hb-now': b.isCurrent }">{{ b.label }}</span>
            <div class="hbar-track">
              <div class="hbar-fill hbar-blue" :class="{ 'hb-now': b.isCurrent }"
                :style="{ width: maxWC > 0 ? (b.value / maxWC * 100).toFixed(1) + '%' : '0%' }"></div>
            </div>
            <span class="hbar-val" :class="{ 'hb-now': b.isCurrent }">{{ b.value }}</span>
          </div>
        </div>
      </div>
      <div class="wchart-card">
        <div class="wchart-head"><span class="wch-dot c-green"></span>本月文件上传 <span class="wchart-total">{{ weeklyFileTotal }}</span></div>
        <div class="hbar-list">
          <div v-for="(b,i) in weeklyFile" :key="'f'+i" class="hbar-row">
            <span class="hbar-lbl" :class="{ 'hb-now': b.isCurrent }">{{ b.label }}</span>
            <div class="hbar-track">
              <div class="hbar-fill hbar-green" :class="{ 'hb-now': b.isCurrent }"
                :style="{ width: maxWF > 0 ? (b.value / maxWF * 100).toFixed(1) + '%' : '0%' }"></div>
            </div>
            <span class="hbar-val" :class="{ 'hb-now': b.isCurrent }">{{ b.value }}</span>
          </div>
        </div>
      </div>
    </div>
    <!-- 骨架屏：与上方周统计卡同形 -->
    <div class="weekchart-section" v-else>
      <div v-for="n in 2" :key="'sk-week-' + n" class="wchart-card" aria-busy="true">
        <el-skeleton :rows="5" animated />
      </div>
    </div>

    <!-- 提醒 + 动态 -->
    <div class="twin-row">
      <div class="panel-card">
        <div class="panel-head">
          <span class="panel-title"><el-icon class="pt-icon pt-red"><AlarmClock /></el-icon>待处理提醒</span>
          <a class="panel-link" @click="$router.push('/reminder')">全部 →</a>
        </div>
        <div v-if="!pageLoading && reminders.length === 0" class="empty-tip">暂无待处理提醒</div>
        <div class="remind-list">
        <div v-for="item in reminders" :key="item.id" class="remind-row">
          <span class="r-dot" :class="item.dotCls"></span>
          <div class="r-body">
            <!-- 【2026-10-09】customerName 为空(如死提醒)时不再渲染前导「·」 -->
            <div class="r-main">{{ [item.customerName, item.desc].filter(Boolean).join(' · ') }}</div>
            <div class="r-sub">
              <span v-if="item.daysTag" class="days-tag" :class="item.daysTagCls">{{ item.daysTag }}</span>
              <span class="r-extra">{{ item.extra || '' }}</span>
            </div>
          </div>
        </div>
        </div>
      </div>

      <div class="panel-card">
        <div class="panel-head">
          <span class="panel-title"><el-icon class="pt-icon pt-blue"><ChatDotSquare /></el-icon>团队动态</span>
        </div>
        <div v-if="!pageLoading && activities.length === 0" class="empty-tip">暂无团队动态</div>
        <div class="act-list">
        <div v-for="(a, i) in activities" :key="i" class="act-row">
          <span class="a-dot"></span>
          <div class="a-body">
            <div class="a-main">{{ a.title }}</div>
            <!-- 【2026-10-09】空部门时不再出现双分隔点(「· · 」),空段全部静默折叠 -->
            <div class="a-meta">{{ [a.user, a.dept, formatRelativeTime(a.time)].filter(Boolean).join(' · ') }}</div>
          </div>
        </div>
        </div>
      </div>
    </div>

    <!-- 待审批 -->
    <div class="panel-card" v-if="approvals.length">
      <div class="panel-head">
        <span class="panel-title"><el-icon class="pt-icon pt-gray"><Document /></el-icon>待审批</span>
        <a class="panel-link" @click="$router.push('/approval')">全部 →</a>
      </div>
      <div v-for="item in approvals" :key="item.id" class="appr-row">
        <div class="appr-body">
          <div class="appr-main">{{ typeLabel(item.type) }} · {{ item.customerName || '—' }}</div>
          <div class="appr-meta">{{ item.applicantName || '—' }} · {{ formatRelativeTime(item.createdAt) }}</div>
        </div>
        <!-- 【2026-11-08 审计修复】通过/驳回按 approval:approve / approval:reject 码渲染(后端硬校验, 前端原缺门控) -->
        <div class="appr-btns" v-if="canApprove || canReject">
          <el-button v-if="canApprove" type="primary" size="small" @click="doApprove(item.id)">通过</el-button>
          <el-button v-if="canReject" size="small" @click="doReject(item.id)">驳回</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
// 【字面量收敛第一批 2026-11-12】权限码走生成物单源, 禁止手写字面量
import { PERMISSION_CODES } from '@/generated/permission-codes'
import { getDashboardData, getTeamActivities, getWeeklyStats, getDailyStats } from '@/api/statistics'
import { getReminderList, getExpiringList } from '@/api/reminder'
import { getApprovalList, approve as apiApprove, reject as apiReject } from '@/api/approval'
import { defaultRecordPeriod } from '@/utils/incentive-period'

const router = useRouter()
const userStore = useUserStore()

// 【可访问性 2026-10-17】指标/快捷入口卡为可点击 div，补 role="button" 后需自行响应
// Enter/Space（原生 button 才内置该行为）；Space 阻止页面滚动
const onCardKeydown = (event, action) => {
  if (event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar') {
    event.preventDefault()
    action()
  }
}
const stats = ref({})
const reminders = ref([])
const activities = ref([])
const approvals = ref([])

// ——— 快捷入口 ———
const quickActions = [
  { label: '新增客户', icon: 'Avatar', path: '/customer/add' },
  { label: '上传文件', icon: 'Upload', path: '/file' },
  { label: '录入激励', icon: 'Star', path: '/incentive' },
  { label: '待审批', icon: 'CircleCheck', path: '/approval' },
  { label: '公海捞取', icon: 'Promotion', path: '/sea' },
  { label: '数据统计', icon: 'DataAnalysis', path: '/statistics' },
]

// ——— 保护期占比 ———
const protectionRate = computed(() => {
  const total = stats.value.myCustomers || 0
  if (!total) return 0
  return ((stats.value.inProtection || 0) / total * 100).toFixed(1)
})

// ——— 每周柱状图 ———
const dailyCustomer = ref([])
const dailyFile = ref([])
const today2 = new Date().getDate()

const toWeeks = (days) => {
  if (!days || !days.length) return []
  const weeks = []
  let week = []
  for (const d of days) {
    week.push(d)
    const dow = new Date(d.date + 'T00:00:00').getDay() || 7
    if (dow === 7 || d.day === today2) {
      const total = week.reduce((s, x) => s + x.value, 0)
      const isCurrent = week.some(x => x.day === today2)
      weeks.push({ label: 'W' + (weeks.length + 1), value: total, isCurrent })
      week = []
    }
  }
  if (week.length) {
    const total = week.reduce((s, x) => s + x.value, 0)
    const isCurrent = week.some(x => x.day === today2)
    weeks.push({ label: '本周', value: total, isCurrent })
  }
  // 去重标记：只有最后一个isCurrent为true
  let found = false
  for (let i = weeks.length - 1; i >= 0; i--) {
    if (weeks[i].isCurrent) {
      if (found) weeks[i].isCurrent = false
      else found = true
    }
  }
  return weeks
}

const weeklyCustomer = computed(() => toWeeks(dailyCustomer.value))
const weeklyFile = computed(() => toWeeks(dailyFile.value))
const maxWC = computed(() => Math.max(...weeklyCustomer.value.map(d => d.value), 1))
const maxWF = computed(() => Math.max(...weeklyFile.value.map(d => d.value), 1))
const weeklyCustomerTotal = computed(() => weeklyCustomer.value.reduce((s, d) => s + d.value, 0))
const weeklyFileTotal = computed(() => weeklyFile.value.reduce((s, d) => s + d.value, 0))

// ——— 审批类型标签 ———
const typeLabel = (t) => ({ claim: '客户捞取申请', transfer: '客户转让申请', extend: '保护期延长申请', storage: '存储扩容申请' }[t] || t)

// ——— 数据加载 ———
const lastRefreshedAt = ref('')
// 【PR-10 前端】任务统计
const taskStats = ref({})
const loadTaskStats = async () => {
  try {
    const res = await import('@/api/task').then(m => m.getTaskStatistics())
    taskStats.value = res.data || {}
  } catch { taskStats.value = {} }
}
// 【PR-11 后续批次】岗位差异化(议题三裁决后):self=销售卡片;非self=管理卡片
// 【D17 修复】userStore2 与上方 userStore 重复获取，统一用 userStore
const isSalesRole = computed(() => userStore.dataScope === 'self')
const scopeLabel = computed(() => {
    const s = userStore.dataScope
    if (s === 'all') return '全量'
    if (s === 'custom' || s === 'dept') return '部门范围'
    if (s === 'team') return '团队'
    return '个人'
})
const myScore = ref(0)
const myUploadCount = ref(0)
const myReadCount = ref(0)
const myReadTotal = ref(0)
// 【B3】阅读统计卡权限门控: incentive:read-stats 默认仅 admin, 无权限隐藏卡片且不发起请求
const canViewReadStats = computed(() => userStore.hasPermission(PERMISSION_CODES.INCENTIVE_READ_STATS))
// 【2026-11-01 审计修复】待我审批卡改按权限码门控(原只判非销售角色: 无 approval:view 者可见可点、跳转即 403)
const canViewApprovals = computed(() => userStore.hasPermission(PERMISSION_CODES.APPROVAL_VIEW))
// 【2026-11-08 审计修复】待审批面板行内"通过/驳回"补码门控: 后端 ApprovalController 分别硬校验
// approval:approve / approval:reject, 此前面板仅 approval:view 即渲染两钮, 无码用户一点必 403。
// 与审批中心页 canApprove/canReject 同口径, 无码不渲染。
const canApprove = computed(() => userStore.hasPermission(PERMISSION_CODES.APPROVAL_APPROVE))
const canReject = computed(() => userStore.hasPermission(PERMISSION_CODES.APPROVAL_REJECT))
const canViewReminders = computed(() => userStore.hasPermission(PERMISSION_CODES.REMINDER_VIEW))

const loadRoleData = async () => {
  if (!isSalesRole.value) return
  try {
    // 【体检 P1 2026-10-12】原调 getMyScore 全库不存在(api/score.js 无此导出, 后端亦无 /score/me
    // 端点)——TypeError 被 catch 静默吞, Promise.all 首路必败连累阅读卡, 双卡恒 0。
    // 改接真实数据源 /incentive/scoreboard(按人聚合含 totalScore/recordCount), 周期取当前年
    // 「全年」(defaultRecordPeriod 与写入口同口径)
    const [boardRes, readRes] = await Promise.all([
      import('@/api/incentive').then(m => m.getScoreboard({ period: defaultRecordPeriod() })),
      // 【B3 2026-10-20】原写法引用 api 模块不存在的 getReadStats 导出(条件恒 false), 阅读卡恒 0/0——
      // 改真实导出 getIncentiveReadStats(无参=本月口径, 与卡片文案一致); incentive:read-stats
      // 默认仅 admin(其他角色需勾选), 无权限不发请求防 403 弹错, 卡片由模板 v-if 隐藏
      canViewReadStats.value
        ? import('@/api/incentive').then(m => m.getIncentiveReadStats())
        : Promise.resolve({ data: [] }),
    ])
    const mine = (boardRes?.data || []).find(r => r.userId === userStore.userId)
    myScore.value = mine?.totalScore || 0
    myUploadCount.value = mine?.recordCount || 0
    const readList = readRes?.data || []
    // 【L5 修复】只取本人记录；找不到本人时显示 0/0 占位，不再拿列表第一人冒充；myReadTotal 直写已发布数
    const mineRead = readList.find(r => r.userId === userStore.userId)
    myReadCount.value = mineRead?.readCount || 0
    myReadTotal.value = mineRead?.publishedCount || 0
  } catch { /* 数据缺失时卡片显示 0 */ }
}
// 【2026-10-08 UI 整改 D13】首屏六路聚合接口统一 pageLoading：加载中渲染 el-skeleton
// （统计卡 + 周统计图），慢接口期间不再显示误导性的 0 值；
// 首次加载完成后，手动刷新/审批回执触发的 loadData 不再整屏闪骨架屏
const pageLoading = ref(true)
const hasLoadedOnce = ref(false)
// 【D13 二轮 2026-10-08】慢载兜底文案：聚合超过 4s 仍未完成时在骨架区给出进行中提示，
// 不再无限期静默停留（配合下方五路并发，骨架停留时间已大幅缩短）
const slowLoading = ref(false)
let slowTimer = null

// 五路聚合彼此独立，拆成并行任务（原为串行 await，总耗时=各接口之和；并发后=最慢一路）
const loadStatsData = async () => {
  try { const r = await getDashboardData(); stats.value = r.data || {} } catch {}
  lastRefreshedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}
const loadReminders = async () => {
  if (!canViewReminders.value) return
  try {
    const r = await getExpiringList({ current: 1, size: 5 })
    const list = r.data?.records || []
    reminders.value = list.map(item => ({
      // 【工作流复核确认修复】字段契约对齐: /reminder/expiring-list 实际返回 remainingDays(无 daysLeft/overdueDays),
      // 原读错字段名致天数标签/到期文案/红橙分级全部失效(>300天无开票提示为死代码, 已移除)
      id: item.customerId || item.id,
      customerName: item.customerName,
      desc: item.remainingDays != null ? (item.remainingDays <= 7 ? '保护期即将到期' : '') : '',
      daysTag: item.remainingDays != null ? item.remainingDays + '天' : '',
      daysTagCls: item.remainingDays <= 7 ? 'dt-orange' : 'dt-red',
      extra: '',
      dotCls: item.remainingDays != null && item.remainingDays <= 7 ? 'dot-red' : 'dot-orange',
      isShare: false,
    }))
    // 追加分享提醒（三类并发拉取）
    // 【D18】三类分享提醒互不依赖，串行 for 改为 Promise.all 并发（对齐 reminder/index.vue 2026-08-08 改法）；单路失败降级为空不阻塞其他路
    try {
      const types = ['share_to', 'share_by', 'share_owner']
      const labelMap = { share_to: '客户已分享给你', share_by: '你分享了客户', share_owner: '你的客户被分享' }
      const srs = await Promise.all(types.map(t =>
        getReminderList({ current: 1, size: 5, type: t, status: 'pending' }).catch(() => null)))
      types.forEach((t, i) => {
        for (const item of (srs[i]?.data?.records || [])) {
          reminders.value.unshift({
            id: item.id,
            customerName: item.customerName,
            desc: labelMap[item.reminderType] || '分享提醒',
            daysTag: '分享',
            daysTagCls: 'dt-blue',
            extra: '',
            dotCls: 'dot-blue',
            isShare: true,
          })
        }
      })
    } catch {}
  } catch {}
}
const loadActivities = async () => {
  try { const r = await getTeamActivities(); activities.value = r.data || [] } catch {}
}
const loadApprovals = async () => {
  if (!canViewApprovals.value) return
  try {
    const r = await getApprovalList({ current: 1, size: 3, tab: 'pending' })
    approvals.value = r.data?.records || []
  } catch {}
}
const loadDaily = async () => {
  try {
    const r = await getDailyStats()
    const fmt = (arr) => (arr || []).map(d => ({ ...d, isToday: d.day === today2 }))
    dailyCustomer.value = fmt(r.data?.customerDays)
    dailyFile.value = fmt(r.data?.fileDays)
  } catch {}
}

const loadData = async () => {
  if (!hasLoadedOnce.value) pageLoading.value = true
  clearTimeout(slowTimer)
  slowTimer = setTimeout(() => { slowLoading.value = true }, 4000)
  try {
    // 【D13 二轮】五路并发：首屏耗时从"之和"降为"最慢一路"
    await Promise.all([loadStatsData(), loadReminders(), loadActivities(), loadApprovals(), loadDaily()])
  } finally {
    clearTimeout(slowTimer)
    slowLoading.value = false
    pageLoading.value = false
    hasLoadedOnce.value = true
  }
}

// ——— 审批操作 ———
const doApprove = async (id) => {
  try { await apiApprove(id, {}); ElMessage.success('已通过'); loadData(); userStore.refreshBadges() } catch {}
}
const doReject = async (id) => {
  try { await apiReject(id, {}); ElMessage.success('已驳回'); loadData(); userStore.refreshBadges() } catch {}
}

// ——— 相对时间 ———
const formatRelativeTime = (time) => {
  if (!time) return ''
  // 后端返回的LocalDateTime没有时区信息，MySQL(Docker)用UTC，需要补上Z
  const timeStr = String(time).endsWith('Z') ? time : time + 'Z'
  const diff = Date.now() - new Date(timeStr).getTime()
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return m + '分钟前'
  const h = Math.floor(m / 60)
  if (h < 24) return h + '小时前'
  const d = Math.floor(h / 24)
  if (d === 1) return '昨天'
  if (d < 7) return d + '天前'
  // 【工作流复核确认修复】7 天以上分支与 diff 同用补 Z 的 UTC 口径解析(原用原始串本地时区解析, 同一串两种口径差 8 小时, 落尾日期可能差一天)
  return new Date(timeStr).toLocaleDateString()
}

onMounted(() => {
  loadData(); loadRoleData()
  // 【体检 P1 2026-10-12】/api/task/statistics 要求 task:view, 无权限角色(如行政05)此前
  // 每次进工作台必弹"没有访问权限"假错误——数据请求与待办卡同步门控
  if (userStore.hasPermission(PERMISSION_CODES.TASK_VIEW)) loadTaskStats()
})
</script>

<style scoped>
/* 【PR-11 首批】统计口径标注条（2026-10-08 UI 整改④：灰阶/主色接线 :root 令牌） */
.stat-scope-note { display:flex; justify-content:space-between; flex-wrap:wrap; gap:8px; color:var(--text-muted); font-size:12px; margin:2px 2px 16px; }
.stat-scope-note a { color:var(--el-color-primary); cursor:pointer; margin-left:8px }

.dashboard { }

/* —— 核心指标 · 深蓝主视觉带（渐变/投影接线 :root 的 --hero-gradient/--hero-shadow 令牌） —— */
.hero-band {
  display: grid; grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr; gap: 0 28px;
  align-items: stretch;
  background: var(--hero-gradient);
  border-radius: var(--radius-lg);
  padding: 24px 28px; margin-bottom: 16px;
  box-shadow: var(--hero-shadow);
}
/* 骨架屏形态：深蓝底上用半透明白条，避免灰条压蓝底的脏色；display 覆盖为块级，让骨架条占满整带 */
.hero-skeleton { display: block; --el-skeleton-color: rgba(255, 255, 255, .14); --el-skeleton-to-color: rgba(255, 255, 255, .26); }
/* 深蓝底小字统一 ≥.92：最亮渐变色 #2563eb 上 .92 白字约 4.65:1，达 AA；数字仍为纯白保持层级 */
.slow-tip { margin-top: 10px; font-size: 12px; color: rgba(255, 255, 255, .92); }
.hero-metric { min-width: 0; padding: 2px 0; }
.hero-metric.drill { cursor: pointer; transition: opacity .2s; border-radius: 8px; }
.hero-metric.drill:hover { opacity: .82; }
.hero-metric.drill:focus-visible { outline: 2px solid #fff; outline-offset: 3px; }
.hero-divider { width: 1px; background: rgba(255, 255, 255, .18); margin: 4px 0; }
/* 【对比度 2026-10-17】.72→.95：最亮端点 #2563eb 上 3.44→4.82:1，13px 辅助标签达 AA */
.hm-label { font-size: 13px; color: rgba(255, 255, 255, .95); margin-bottom: 10px; letter-spacing: .3px; }
/* 【B6】大数字唯一档 --font-num-hero(30px)，收敛此前 32/30/28 三档并存 */
.hm-num { font-size: var(--font-num-hero); font-weight: 700; line-height: 1.1; color: #fff; display: flex; align-items: center; gap: 10px; }
/* 【E17】12px 白字压深蓝底；【对比度 2026-10-17】.8 在 #2563eb 端点仅 3.89:1，提到 .92（4.65:1）达 AA */
.hm-sub { font-size: 12px; color: rgba(255, 255, 255, .92); margin-top: 8px; }
.hm-chip { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: var(--radius-pill); white-space: nowrap; }
/* chip-amber 保留唯一语义：警告(即将到期)。进度条改白色半透明，黄色不再一色两义 */
/* 【对比度 2026-10-17】原浅黄/浅红字压同色半透明底在 #2563eb 端点仅 3.05/2.65:1；
   改实心浅色底+深色字：琥珀 8.97:1、红 6.93:1，12px 达 AA（与全站 effect=light 标签同质感） */
.chip-amber { background: #fbbf24; color: #451a03; border: 1px solid #fbbf24; }
.chip-red { background: #fecaca; color: #7f1d1d; border: 1px solid #fecaca; }
.hero-track { height: 4px; background: rgba(255, 255, 255, .18); border-radius: 2px; margin-top: 10px; overflow: hidden; }
.hero-fill { height: 100%; background: rgba(255, 255, 255, .85); border-radius: 2px; transition: width .4s; }
.hm-tags { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
.hm-tag { font-size: 12px; padding: 2px 8px; border-radius: 4px; white-space: nowrap; }
.ht-strong { color: #fff; background: rgba(248, 113, 113, .32); }
/* 【对比度 2026-10-17】半白底会把底色调亮，白字 .85 仅约 3.5:1；改为纯白+更淡的底(.06)，
   最亮端点 #2563eb 上约 4.65:1，12px 达 AA 且保留轻量胶囊感 */
.ht-plain { color: #fff; background: rgba(255, 255, 255, .06); }

/* —— 运营指标 · 白色定义卡(统排:数字同字号同色,语义只用图标色点) —— */
/* 【ui-1 2026-10-17】固定四列改为 auto-fit：有 task:view 时 4 卡、无权限时 2 卡都不留空列；
   1200px 以下 4 卡会落成 3+1，故收为两列（1024/768 为 2×2），卡片最小 220px 不挤压 */
.stat-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 16px; }
@media (max-width: 1200px) { .stat-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.stat-card {
  background: #fff; border: 1px solid var(--border-light);
  border-radius: var(--radius-lg); padding: 20px 24px;
  display: flex; justify-content: space-between; align-items: flex-start;
  position: relative; overflow: hidden; transition: all .25s;
  box-shadow: var(--shadow-card);
}
.stat-card:hover { box-shadow: var(--shadow-card-hover); transform: translateY(-2px); }
.stat-card.drill { cursor: pointer; }
.stat-card.drill:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.stat-label { font-size: 14px; color: var(--text-muted); margin-bottom: 8px; font-weight: 500; }
/* 【B6】大数字唯一档 --font-num-hero；【B8】深蓝大数字色接线 --brand-navy 令牌 */
.stat-num { font-size: var(--font-num-hero); font-weight: 700; line-height: 1.15; color: var(--brand-navy); letter-spacing: .2px; }
.stat-scope-value { font-size: 16px; font-weight: 600; line-height: 38px; color: var(--text-primary); }
.stat-sub { font-size: 12px; color: var(--text-muted); margin-top: 6px; }
.stat-unit { font-size: 14px; color: var(--text-muted); margin-left: 4px; font-weight: 500; }
.stat-icon {
  flex-shrink: 0; width: 40px; height: 40px; border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
  background: #eef4ff; color: #2563eb;
}
.stat-icon.is-amber { background: #fff7e6; color: #d97706; }

/* —— 快捷入口 —— */
.quick-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 16px; margin-bottom: 16px; }
.quick-item {
  background: #fff; border: 1px solid var(--border-light);
  border-radius: var(--radius-lg); padding: 20px 0;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  cursor: pointer; transition: all .2s; color: var(--text-secondary);
  box-shadow: var(--shadow-card);
}
.quick-item:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); box-shadow: var(--shadow-card-hover); transform: translateY(-2px); }
.quick-item:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.quick-item span { font-size: 13px; }

/* —— 每周横向进度条 —— */
.weekchart-section { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.wchart-card { background: #fff; border: 1px solid var(--border-light); border-radius: var(--radius-lg); padding: 18px 20px 16px; box-shadow: var(--shadow-card); }
.wchart-head { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 700; color: var(--text-primary); margin-bottom: 16px; }
.wchart-total { font-weight: 700; font-size: 18px; color: var(--text-primary); margin-left: auto }
.wch-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0 }
.c-blue { background: var(--el-color-primary); }
.c-green { background: var(--el-color-success); }
.hbar-list { display: flex; flex-direction: column; gap: 10px }
.hbar-row { display: flex; align-items: center; gap: 10px }
.hbar-lbl { width: 32px; font-size: 12px; color: var(--text-muted); font-weight: 500; text-align: right; flex-shrink: 0 }
.hbar-track { flex: 1; height: 20px; background: #f5f6f7; border-radius: var(--radius-md); overflow: hidden }
/* 【E19 回退修复 2026-10-09】0.6s 超全站 0.15-0.4s 锚点且与同页 hero 填充 0.4s 双节奏 → 对齐 0.4s */
.hbar-fill { height: 100%; border-radius: var(--radius-md); transition: width .4s ease }
.hbar-blue { background: linear-gradient(90deg, var(--el-color-primary-light-3), var(--el-color-primary)) }
.hbar-green { background: linear-gradient(90deg, #95d475, var(--el-color-success)) }
.hbar-fill.hb-now { background: linear-gradient(90deg, #ff9a76, #e8684a) }
.hbar-val { width: 36px; font-size: 13px; font-weight: 700; color: var(--text-primary); flex-shrink: 0 }
.hbar-lbl.hb-now { color: #e8684a; font-weight: 700 }
.hbar-val.hb-now { color: #e8684a }

/* —— 双列面板 —— */
.twin-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.panel-card { background: #fff; border: 1px solid var(--border-light); border-radius: var(--radius-md); padding: 24px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.panel-title { display: flex; align-items: center; gap: 6px; font-size: 15px; font-weight: 600; color: var(--text-primary); }
.pt-icon { font-size: 18px; }
.pt-red { color: var(--el-color-danger); }
.pt-blue { color: var(--el-color-primary); }
.pt-gray { color: var(--text-muted); }
.panel-link { font-size: 13px; color: var(--el-color-primary); cursor: pointer; }
.panel-link:hover { text-decoration: underline; }
/* 【对比度 2026-10-17】空态提示属于可读信息，--text-placeholder(#94a3b8) 仅 2.56:1，改用 --text-muted(4.83:1) */
.empty-tip { text-align: center; color: var(--text-muted); padding: 32px 0; font-size: 14px; }

.remind-list { max-height: 320px; overflow-y: auto }
.remind-row { display: flex; align-items: flex-start; gap: 12px; padding: 14px 0; border-bottom: 1px solid #f5f5f5; }
.remind-row:last-child { border-bottom: none; }
.r-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 6px; flex-shrink: 0; }
.dot-red { background: var(--el-color-danger); }
.dot-orange { background: var(--el-color-warning); }
.dot-gray { background: #c0c4cc; }
.dot-blue { background: var(--el-color-primary); }
.r-body { flex: 1; min-width: 0; }
.r-main { font-size: 14px; color: var(--text-primary); font-weight: 500; }
.r-sub { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.days-tag { font-size: 12px; padding: 1px 6px; border-radius: 4px; font-weight: 600; }
.dt-orange { color: var(--el-color-warning); background: #fdf6ec; }
.dt-red { color: var(--el-color-danger); background: #fef0f0; }
.dt-blue { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.r-extra { font-size: 12px; color: var(--text-muted); }

/* —— 团队动态 —— */
.act-list { max-height: 320px; overflow-y: auto }
.act-row { display: flex; align-items: flex-start; gap: 12px; padding: 14px 0; border-bottom: 1px solid #f5f5f5; }
.act-row:last-child { border-bottom: none; }
.a-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--el-color-primary); margin-top: 6px; flex-shrink: 0; }
.a-body { flex: 1; }
.a-main { font-size: 14px; color: var(--text-primary); font-weight: 500; }
.a-meta { font-size: 12px; color: var(--text-muted); margin-top: 4px; }

/* —— 待审批 —— */
.appr-row { display: flex; justify-content: space-between; align-items: center; padding: 14px 0; border-bottom: 1px solid #f5f5f5; }
.appr-row:last-child { border-bottom: none; }
.appr-main { font-size: 14px; color: var(--text-primary); font-weight: 500; }
.appr-meta { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
.appr-btns { display: flex; gap: 8px; flex-shrink: 0; }
</style>
