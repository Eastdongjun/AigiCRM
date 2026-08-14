<template>
  <div class="dashboard">

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">客户总数</div>
          <div class="stat-num orange">{{ stats.myCustomers || 0 }}</div>
          <div class="stat-sub">本月新增 {{ stats.monthNew || 0 }} · 公海 {{ stats.seaStats?.total || 0 }}</div>
        </div>
        <el-icon class="stat-icon icon-blue" :size="28"><User /></el-icon>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">在保护期</div>
          <div class="stat-num blue">{{ stats.inProtection || 0 }}</div>
          <div class="stat-sub">占比 {{ protectionRate }}%</div>
          <div class="progress-track"><div class="progress-fill" :style="{ width: protectionRate + '%' }"></div></div>
        </div>
        <el-icon class="stat-icon icon-blue" :size="28"><Timer /></el-icon>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">即将到期</div>
          <div class="stat-num orange">{{ stats.expiring || 0 }}</div>
          <div class="stat-sub">30天内</div>
          <div class="stat-tags" v-if="stats.expiringDetails?.length">
            <span v-for="d in stats.expiringDetails.slice(0, 2)" :key="d.customerName"
              class="mini-tag" :class="d.daysLeft <= 7 ? 'mt-blue' : 'mt-red'">
              {{ d.officeName || d.customerName }} {{ d.daysLeft }}天
            </span>
          </div>
        </div>
        <el-icon class="stat-icon icon-orange" :size="28"><Warning /></el-icon>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">已超期</div>
          <div class="stat-num red">{{ stats.expired || 0 }}</div>
          <div class="stat-sub">含公海 {{ stats.seaStats?.total || 0 }} 个</div>
          <div class="stat-links">
            <a @click="$router.push('/reminder')">今日到期 {{ stats.todayExpiring || 0 }}</a>
            <a @click="$router.push('/reminder')" class="lk-red">待处理</a>
          </div>
        </div>
        <el-icon class="stat-icon icon-red" :size="28"><CircleClose /></el-icon>
      </div>
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
const loadData = async () => {
  try { const r = await getDashboardData(); stats.value = r.data || {} } catch {}
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

onMounted(loadData)
</script>

<style scoped>
.dashboard { }

/* —— 头部 —— */
.page-header { margin-bottom: 24px; }
.page-title { font-size: 22px; font-weight: 700; color: #1d2129; margin: 0 0 4px; }
.greeting { font-size: 14px; color: #999; margin: 0; }

/* —— 统计卡片 —— */
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 20px 24px;
  display: flex; justify-content: space-between; align-items: flex-start;
  position: relative; overflow: hidden; transition: all .25s;
}
.stat-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; }
.stat-card:nth-child(1)::before { background: linear-gradient(90deg, #e8684a, #f59e0b); }
.stat-card:nth-child(2)::before { background: linear-gradient(90deg, #409eff, #66b1ff); }
.stat-card:nth-child(3)::before { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.stat-card:nth-child(4)::before { background: linear-gradient(90deg, #f56c6c, #fc8181); }
.stat-card:hover { box-shadow: 0 6px 20px rgba(0,0,0,.08); transform: translateY(-2px); border-color: #e0e4eb; }
.stat-label { font-size: 14px; color: #86909c; margin-bottom: 8px; font-weight: 500; }
.stat-num { font-size: 36px; font-weight: 700; line-height: 1.1; }
.stat-num.orange { color: #e8684a; }
.stat-num.blue { color: #409eff; }
.stat-num.red { color: #f56c6c; }
.stat-sub { font-size: 12px; color: #999; margin-top: 6px; }
.stat-icon { flex-shrink: 0; width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; }
.icon-blue { background: #ecf5ff; color: #409eff; }
.icon-orange { background: #fdf6ec; color: #e6a23c; }
.icon-red { background: #fef0f0; color: #f56c6c; }

/* 进度条 */
.progress-track { height: 6px; background: #f0f2f5; border-radius: 3px; margin-top: 8px; overflow: hidden; }
.progress-fill { height: 100%; background: #409eff; border-radius: 3px; transition: width .4s; }

/* 到期标签 */
.stat-tags { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.mini-tag { font-size: 12px; padding: 2px 8px; border-radius: 4px; }
.mt-blue { color: #409eff; background: #ecf5ff; }
.mt-red { color: #f56c6c; background: #fef0f0; }

/* 超期链接 */
.stat-links { display: flex; gap: 12px; margin-top: 8px; }
.stat-links a { font-size: 12px; color: #e6a23c; cursor: pointer; }
.stat-links a:hover { text-decoration: underline; }
.stat-links .lk-red { color: #f56c6c; }

/* —— 快捷入口 —— */
.quick-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 16px; margin-bottom: 16px; }
.quick-item {
  background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 20px 0;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  cursor: pointer; transition: all .2s; color: #4e5969;
}
.quick-item:hover { border-color: #409eff; color: #409eff; box-shadow: 0 2px 8px rgba(64,158,255,.12); }
.quick-item span { font-size: 13px; }

/* —— 每周横向进度条 —— */
.weekchart-section { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.wchart-card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 18px 20px 16px; }
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
