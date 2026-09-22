<template>
  <div class="dashboard">

    <!-- 核心指标 · 深蓝主视觉带(企业级:白字统排,语义差异用小徽标表达) -->
    <div class="hero-band">
      <div class="hero-metric drill" @click="$router.push('/customer')">
        <div class="hm-label">客户总数</div>
        <div class="hm-num">{{ stats.myCustomers || 0 }}</div>
        <div class="hm-sub">本月新增 {{ stats.monthNew || 0 }} · 公海 {{ stats.seaStats?.total || 0 }}</div>
      </div>
      <div class="hero-divider"></div>
      <div class="hero-metric drill" @click="$router.push({ path: '/customer', query: { status: 'normal' } })">
        <div class="hm-label">在保护期</div>
        <div class="hm-num">{{ stats.inProtection || 0 }}</div>
        <div class="hm-sub">占比 {{ protectionRate }}%</div>
        <div class="hero-track"><div class="hero-fill" :style="{ width: protectionRate + '%' }"></div></div>
      </div>
      <div class="hero-divider"></div>
      <div class="hero-metric drill" @click="$router.push('/reminder')">
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
      <div class="hero-metric drill" @click="$router.push('/reminder')">
        <div class="hm-label">已超期</div>
        <div class="hm-num">
          {{ stats.expired || 0 }}<span class="hm-chip chip-red" v-if="stats.expired">需处理</span>
        </div>
        <div class="hm-sub">今日到期 {{ stats.todayExpiring || 0 }} · 公海 {{ stats.seaStats?.total || 0 }}</div>
      </div>
    </div>

    <!-- 运营指标 · 白色定义卡四联(任何角色都是完整四列) -->
    <!-- 【PR-10 前端 2026-09-19】待办任务; 【PR-11 后续批次】销售=self激励/阅读, 管理=审批概览/范围 -->
    <div class="stat-row secondary">
      <div class="stat-card drill" @click="$router.push('/task')">
        <div class="stat-info">
          <div class="stat-label">待办任务</div>
          <div class="stat-num">{{ taskStats.pending || 0 }}</div>
          <div class="stat-sub">今日到期 {{ taskStats.todayDue || 0 }} · 逾期 {{ taskStats.overdue || 0 }}</div>
        </div>
        <el-icon class="stat-icon" :size="22"><List /></el-icon>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">本月完成任务</div>
          <div class="stat-num">{{ taskStats.completedThisMonth || 0 }}</div>
          <div class="stat-sub">按自然月统计</div>
        </div>
        <el-icon class="stat-icon" :size="22"><CircleCheck /></el-icon>
      </div>
      <div class="stat-card drill" v-if="isSalesRole" @click="$router.push('/score/ranking')">
        <div class="stat-info">
          <div class="stat-label">我的激励积分</div>
          <div class="stat-num">{{ myScore || 0 }}</div>
          <div class="stat-sub">四项激励 · 本月上传 {{ myUploadCount || 0 }} 次</div>
        </div>
        <el-icon class="stat-icon" :size="22"><Trophy /></el-icon>
      </div>
      <div class="stat-card" v-if="isSalesRole">
        <div class="stat-info">
          <div class="stat-label">我的阅读完成</div>
          <div class="stat-num">{{ myReadCount || 0 }}<span class="stat-unit">/{{ myReadTotal || 0 }}</span></div>
          <div class="stat-sub">本月激励资料阅读(重复打开计一次)</div>
        </div>
        <el-icon class="stat-icon" :size="22"><Reading /></el-icon>
      </div>
      <div class="stat-card drill" v-if="!isSalesRole" @click="$router.push('/approval')">
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

    <!-- 【PR-11 首批】统计卡片口径标注:范围=本人数据范围(后端按权限过滤),进页面/点刷新时更新 -->
    <div class="stat-scope-note">
      <span>统计范围：我的客户（按账号数据范围过滤） · 周期：本月 / 30 天到期窗口</span>
      <span class="scope-right">刷新于 {{ lastRefreshedAt || '—' }} <a @click="loadData">立即刷新</a></span>
    </div>

    <!-- 快捷入口 -->
    <div class="quick-row">
      <div class="quick-item" v-for="q in quickActions" :key="q.label" @click="$router.push(q.path)">
        <el-icon :size="26"><component :is="q.icon" /></el-icon>
        <span>{{ q.label }}</span>
      </div>
    </div>

    <!-- 每周统计 -->
    <div class="weekchart-section">
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

    <!-- 提醒 + 动态 -->
    <div class="twin-row">
      <div class="panel-card">
        <div class="panel-head">
          <span class="panel-title"><el-icon class="pt-icon pt-red"><AlarmClock /></el-icon>待处理提醒</span>
          <a class="panel-link" @click="$router.push('/reminder')">全部 →</a>
        </div>
        <div v-if="reminders.length === 0" class="empty-tip">暂无待处理提醒</div>
        <div class="remind-list">
        <div v-for="item in reminders" :key="item.id" class="remind-row">
          <span class="r-dot" :class="item.dotCls"></span>
          <div class="r-body">
            <div class="r-main">{{ item.customerName }}{{ item.desc ? ' · ' + item.desc : '' }}</div>
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
        <div v-if="activities.length === 0" class="empty-tip">暂无团队动态</div>
        <div class="act-list">
        <div v-for="(a, i) in activities" :key="i" class="act-row">
          <span class="a-dot"></span>
          <div class="a-body">
            <div class="a-main">{{ a.title }}</div>
            <div class="a-meta">{{ a.user }} · {{ a.dept }} · {{ formatRelativeTime(a.time) }}</div>
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
        <div class="appr-btns">
          <el-button type="primary" size="small" @click="doApprove(item.id)">通过</el-button>
          <el-button size="small" @click="doReject(item.id)">驳回</el-button>
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
import { getDashboardData, getTeamActivities, getWeeklyStats, getDailyStats } from '@/api/statistics'
import { getReminderList, getExpiringList, getPendingCount } from '@/api/reminder'
import { getApprovalList, approve as apiApprove, reject as apiReject } from '@/api/approval'

const router = useRouter()
const userStore = useUserStore()
const stats = ref({})
const reminders = ref([])
const activities = ref([])
const approvals = ref([])
const pendingReminderCount = ref(0)

// ——— 快捷入口 ———
const quickActions = [
  { label: '新增客户', icon: 'Avatar', path: '/customer/add' },
  { label: '上传文件', icon: 'Upload', path: '/file' },
  { label: '录入激励', icon: 'Star', path: '/incentive' },
  { label: '待审批', icon: 'CircleCheck', path: '/approval' },
  { label: '公海捞取', icon: 'Promotion', path: '/sea' },
  { label: '数据统计', icon: 'DataAnalysis', path: '/statistics' },
]

// ——— 问候语 ———
const greetingText = computed(() => {
  const h = new Date().getHours()
  const g = h < 12 ? '早上好' : h < 18 ? '下午好' : '晚上好'
  return `${g}，${userStore.realName}。今日待处理 ${pendingReminderCount.value} 项提醒，${approvals.value.length} 条审批。`
})

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
const typeLabel = (t) => ({ claim: '客户捞取申请', transfer: '客户转让申请', extend: '保护期延长申请', storage: '入库申请' }[t] || t)

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
const userStore2 = useUserStore()
const isSalesRole = computed(() => userStore2.dataScope === 'self')
const scopeLabel = computed(() => {
    const s = userStore2.dataScope
    if (s === 'all') return '全量'
    if (s === 'custom' || s === 'dept') return '部门范围'
    if (s === 'team') return '团队'
    return '个人'
})
const myScore = ref(0)
const myUploadCount = ref(0)
const myReadCount = ref(0)
const myReadTotal = ref(0)

const loadRoleData = async () => {
  if (!isSalesRole.value) return
  try {
    const [scoreRes, readRes] = await Promise.all([
      import('@/api/score').then(m => m.getMyScore()),
      import('@/api/incentive').then(m => m.getReadStats ? m.getReadStats() : Promise.resolve({ data: [] })),
    ])
    if (scoreRes?.data) {
      myScore.value = scoreRes.data.totalScore || 0
      myUploadCount.value = scoreRes.data.itemCount || 0
    }
    const readList = readRes?.data || []
    const mine = readList.find(r => r.userId === userStore2.userId) || readList[0]
    if (mine) {
      myReadCount.value = mine.readCount || 0
      myReadTotal.value = (mine.publishedCount || 0) + (mine.readCount || 0) > 0
        ? mine.publishedCount || 0 : 0
    }
  } catch { /* 数据缺失时卡片显示 0 */ }
}
const loadData = async () => {
  try { const r = await getDashboardData(); stats.value = r.data || {} } catch {}
  lastRefreshedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  try {
    const r = await getExpiringList({ current: 1, size: 5 })
    const list = r.data?.records || []
    reminders.value = list.map(item => ({
      id: item.customerId || item.id,
      customerName: item.customerName,
      desc: item.daysLeft != null ? (item.daysLeft <= 7 ? '保护期即将到期' : '') : '',
      daysTag: item.daysLeft != null ? item.daysLeft + '天' : '',
      daysTagCls: item.daysLeft <= 7 ? 'dt-orange' : 'dt-red',
      extra: item.overdueDays > 300 ? item.overdueDays + '天无开票' : '',
      dotCls: item.daysLeft <= 7 ? 'dot-red' : 'dot-orange',
      isShare: false,
    }))
    // 追加分享提醒（分开查三种类型）
    try {
      const types = ['share_to', 'share_by', 'share_owner']
      const labelMap = { share_to: '客户已分享给你', share_by: '你分享了客户', share_owner: '你的客户被分享' }
      for (const t of types) {
        try {
          const sr = await getReminderList({ current: 1, size: 5, type: t, status: 'pending' })
          for (const item of (sr.data?.records || [])) {
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
        } catch {}
      }
    } catch {}
    try { const pc = await getPendingCount(); pendingReminderCount.value = pc.data || 0 } catch {}
  } catch {}
  try { const r = await getTeamActivities(); activities.value = r.data || [] } catch {}
  try {
    const r = await getApprovalList({ current: 1, size: 3, tab: 'pending' })
    approvals.value = r.data?.records || []
  } catch {}
  try {
    const r = await getDailyStats()
    const fmt = (arr) => (arr || []).map(d => ({ ...d, isToday: d.day === today2 }))
    dailyCustomer.value = fmt(r.data?.customerDays)
    dailyFile.value = fmt(r.data?.fileDays)
  } catch {}
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
  return new Date(time).toLocaleDateString()
}

onMounted(() => { loadData(); loadRoleData(); loadTaskStats() })
</script>

<style scoped>
/* 【PR-11 首批】统计口径标注条 */
.stat-scope-note { display:flex; justify-content:space-between; flex-wrap:wrap; gap:8px; color:#94a3b8; font-size:12px; margin:2px 2px 16px; }
.stat-scope-note a { color:#409eff; cursor:pointer; margin-left:8px }

.dashboard { }

/* —— 头部 —— */
.page-header { margin-bottom: 24px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--ds-text-1, #1f2329); margin: 0 0 4px; letter-spacing: .2px; }
.greeting { font-size: 14px; color: var(--ds-text-3, #86909c); margin: 0; }

/* —— 核心指标 · 深蓝主视觉带 —— */
.hero-band {
  display: grid; grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr; gap: 0 28px;
  align-items: stretch;
  background: linear-gradient(135deg, #16337a 0%, #1d4ed8 58%, #2563eb 100%);
  border-radius: var(--ds-radius-lg, 14px);
  padding: 24px 28px; margin-bottom: 16px;
  box-shadow: 0 6px 20px rgba(22, 51, 122, .18);
}
.hero-metric { min-width: 0; padding: 2px 0; }
.hero-metric.drill { cursor: pointer; transition: opacity .2s; border-radius: 8px; }
.hero-metric.drill:hover { opacity: .82; }
.hero-divider { width: 1px; background: rgba(255, 255, 255, .18); margin: 4px 0; }
.hm-label { font-size: 13px; color: rgba(255, 255, 255, .72); margin-bottom: 10px; letter-spacing: .3px; }
.hm-num { font-size: 32px; font-weight: 700; line-height: 1.1; color: #fff; display: flex; align-items: center; gap: 10px; }
.hm-sub { font-size: 12px; color: rgba(255, 255, 255, .6); margin-top: 8px; }
.hm-chip { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 999px; white-space: nowrap; }
.chip-amber { background: rgba(251, 191, 36, .22); color: #fcd34d; border: 1px solid rgba(251, 191, 36, .45); }
.chip-red { background: rgba(248, 113, 113, .2); color: #fca5a5; border: 1px solid rgba(248, 113, 113, .45); }
.hero-track { height: 4px; background: rgba(255, 255, 255, .18); border-radius: 2px; margin-top: 10px; overflow: hidden; }
.hero-fill { height: 100%; background: #fcd34d; border-radius: 2px; transition: width .4s; }
.hm-tags { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
.hm-tag { font-size: 11px; padding: 2px 8px; border-radius: 4px; white-space: nowrap; }
.ht-strong { color: #fff; background: rgba(248, 113, 113, .32); }
.ht-plain { color: rgba(255, 255, 255, .85); background: rgba(255, 255, 255, .14); }

/* —— 运营指标 · 白色定义卡(统排:数字同字号同色,语义只用图标色点) —— */
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card {
  background: var(--ds-surface, #fff); border: 1px solid var(--ds-border-light, #eef1f5);
  border-radius: var(--ds-radius-lg, 14px); padding: 20px 24px;
  display: flex; justify-content: space-between; align-items: flex-start;
  position: relative; overflow: hidden; transition: all .25s;
  box-shadow: var(--ds-shadow-1, 0 1px 2px rgba(15,23,42,.04));
}
.stat-card:hover { box-shadow: var(--ds-shadow-2, 0 4px 16px rgba(15,23,42,.06)); transform: translateY(-2px); }
.stat-card.drill { cursor: pointer; }
.stat-label { font-size: 14px; color: var(--ds-text-3, #86909c); margin-bottom: 8px; font-weight: 500; }
.stat-num { font-size: 30px; font-weight: 700; line-height: 1.15; color: #1d337a; letter-spacing: .2px; }
.stat-scope-value { font-size: 16px; font-weight: 600; line-height: 38px; color: #1d2129; }
.stat-sub { font-size: 12px; color: var(--ds-text-3, #86909c); margin-top: 6px; }
.stat-unit { font-size: 14px; color: #94a3b8; margin-left: 4px; font-weight: 500; }
.stat-icon {
  flex-shrink: 0; width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  background: #eef4ff; color: #2563eb;
}
.stat-icon.is-amber { background: #fff7e6; color: #d97706; }

/* —— 快捷入口 —— */
.quick-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 16px; margin-bottom: 16px; }
.quick-item {
  background: var(--ds-surface, #fff); border: 1px solid var(--ds-border-light, #eef1f5);
  border-radius: var(--ds-radius-lg, 14px); padding: 20px 0;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  cursor: pointer; transition: all .2s; color: var(--ds-text-2, #4e5969);
  box-shadow: var(--ds-shadow-1, 0 1px 2px rgba(15,23,42,.04));
}
.quick-item:hover { border-color: var(--ds-primary, #409eff); color: var(--ds-primary, #409eff); box-shadow: var(--ds-shadow-2, 0 4px 16px rgba(15,23,42,.06)); transform: translateY(-2px); }
.quick-item span { font-size: 13px; }

/* —— 每周横向进度条 —— */
.weekchart-section { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.wchart-card { background: var(--ds-surface, #fff); border: 1px solid var(--ds-border-light, #eef1f5); border-radius: var(--ds-radius-lg, 14px); padding: 18px 20px 16px; box-shadow: var(--ds-shadow-1, 0 1px 2px rgba(15,23,42,.04)); }
.wchart-head { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 700; color: #1d2129; margin-bottom: 16px; }
.wchart-total { font-weight: 700; font-size: 18px; color: #1d2129; margin-left: auto }
.wch-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0 }
.c-blue { background: #409eff; }
.c-green { background: #67c23a; }
.hbar-list { display: flex; flex-direction: column; gap: 10px }
.hbar-row { display: flex; align-items: center; gap: 10px }
.hbar-lbl { width: 32px; font-size: 12px; color: #86909c; font-weight: 500; text-align: right; flex-shrink: 0 }
.hbar-track { flex: 1; height: 20px; background: #f5f6f7; border-radius: 10px; overflow: hidden }
.hbar-fill { height: 100%; border-radius: 10px; transition: width .6s ease }
.hbar-blue { background: linear-gradient(90deg, #66b1ff, #409eff) }
.hbar-green { background: linear-gradient(90deg, #95d475, #67c23a) }
.hbar-fill.hb-now { background: linear-gradient(90deg, #ff9a76, #e8684a) }
.hbar-val { width: 36px; font-size: 13px; font-weight: 700; color: #1d2129; flex-shrink: 0 }
.hbar-lbl.hb-now { color: #e8684a; font-weight: 700 }
.hbar-val.hb-now { color: #e8684a }

/* —— 双列面板 —— */
.twin-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.panel-card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 24px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.panel-title { display: flex; align-items: center; gap: 6px; font-size: 15px; font-weight: 600; color: #1d2129; }
.pt-icon { font-size: 18px; }
.pt-red { color: #f56c6c; }
.pt-blue { color: #409eff; }
.pt-gray { color: #86909c; }
.panel-link { font-size: 13px; color: #409eff; cursor: pointer; }
.panel-link:hover { text-decoration: underline; }
.empty-tip { text-align: center; color: #c0c4cc; padding: 32px 0; font-size: 14px; }

.remind-list { max-height: 320px; overflow-y: auto }
.remind-row { display: flex; align-items: flex-start; gap: 12px; padding: 14px 0; border-bottom: 1px solid #f5f5f5; }
.remind-row:last-child { border-bottom: none; }
.r-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 6px; flex-shrink: 0; }
.dot-red { background: #f56c6c; }
.dot-orange { background: #e6a23c; }
.dot-gray { background: #c0c4cc; }
.dot-blue { background: #409eff; }
.r-body { flex: 1; min-width: 0; }
.r-main { font-size: 14px; color: #1d2129; font-weight: 500; }
.r-sub { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.days-tag { font-size: 12px; padding: 1px 6px; border-radius: 4px; font-weight: 600; }
.dt-orange { color: #e6a23c; background: #fdf6ec; }
.dt-red { color: #f56c6c; background: #fef0f0; }
.dt-blue { color: #409eff; background: #ecf5ff; }
.r-extra { font-size: 12px; color: #999; }

/* —— 团队动态 —— */
.act-list { max-height: 320px; overflow-y: auto }
.act-row { display: flex; align-items: flex-start; gap: 12px; padding: 14px 0; border-bottom: 1px solid #f5f5f5; }
.act-row:last-child { border-bottom: none; }
.a-dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; margin-top: 6px; flex-shrink: 0; }
.a-body { flex: 1; }
.a-main { font-size: 14px; color: #1d2129; font-weight: 500; }
.a-meta { font-size: 12px; color: #999; margin-top: 4px; }

/* —— 待审批 —— */
.appr-row { display: flex; justify-content: space-between; align-items: center; padding: 14px 0; border-bottom: 1px solid #f5f5f5; }
.appr-row:last-child { border-bottom: none; }
.appr-main { font-size: 14px; color: #1d2129; font-weight: 500; }
.appr-meta { font-size: 12px; color: #999; margin-top: 4px; }
.appr-btns { display: flex; gap: 8px; flex-shrink: 0; }
</style>
