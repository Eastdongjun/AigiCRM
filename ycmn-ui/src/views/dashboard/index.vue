<template>
  <div class="dashboard">
    <!-- 标题 & 问候 -->
    <div class="page-header">
      <h2 class="page-title">工作台</h2>
      <p class="greeting">{{ greetingText }}</p>
    </div>

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

    <!-- 图表行 -->
    <div class="chart-row">
      <div class="chart-card">
        <div class="chart-head"><el-icon class="ch-icon"><TrendCharts /></el-icon><span>本月新增客户</span></div>
        <div class="bars-wrap">
          <div v-for="b in customerBars" :key="b.label" class="bar-col">
            <span class="bar-val">{{ b.value }}</span>
            <div class="bar" :class="b.cls" :style="{ height: b.h + 'px' }"></div>
            <span class="bar-lbl">{{ b.label }}</span>
          </div>
        </div>
      </div>
      <div class="chart-card">
        <div class="chart-head"><el-icon class="ch-icon"><Document /></el-icon><span>本月文件上传</span></div>
        <div class="bars-wrap">
          <div v-for="b in fileBars" :key="b.label" class="bar-col">
            <span class="bar-val">{{ b.value }}</span>
            <div class="bar" :class="b.cls" :style="{ height: b.h + 'px' }"></div>
            <span class="bar-lbl">{{ b.label }}</span>
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

      <div class="panel-card">
        <div class="panel-head">
          <span class="panel-title"><el-icon class="pt-icon pt-blue"><ChatDotSquare /></el-icon>团队动态</span>
        </div>
        <div v-if="activities.length === 0" class="empty-tip">暂无团队动态</div>
        <div v-for="(a, i) in activities" :key="i" class="act-row">
          <span class="a-dot"></span>
          <div class="a-body">
            <div class="a-main">{{ a.title }}</div>
            <div class="a-meta">{{ a.user }} · {{ a.dept }} · {{ formatRelativeTime(a.time) }}</div>
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
import { getDashboardData, getTeamActivities, getWeeklyStats } from '@/api/statistics'
import { getReminderList, getExpiringList } from '@/api/reminder'
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

// ——— 柱状图 ———
const weeklyData = ref({ customerWeeks: [], fileWeeks: [] })

const makeBars = (data) => {
  if (!data || !data.length) return []
  const max = Math.max(...data.map(d => d.value), 1)
  return data.map((d, i) => ({
    ...d,
    h: Math.max(Math.round((d.value / max) * 80), 4),
    cls: i === data.length - 1 ? 'bar-green' : 'bar-blue',
  }))
}
const customerBars = computed(() => makeBars(weeklyData.value.customerWeeks))
const fileBars = computed(() => makeBars(weeklyData.value.fileWeeks))

// ——— 审批类型标签 ———
const typeLabel = (t) => ({ claim: '客户捞取申请', transfer: '客户转让申请', extend: '保护期延长申请', storage: '入库申请' }[t] || t)

// ——— 数据加载 ———
const loadData = async () => {
  try { const r = await getDashboardData(); stats.value = r.data || {} } catch {}
  try {
    const r = await getExpiringList({ current: 1, size: 5 })
    const list = r.data?.records || []
    reminders.value = list.map(item => ({
      id: item.id || item.customerName,
      customerName: item.customerName,
      desc: item.daysLeft <= 7 ? '保护期即将到期' : '',
      daysTag: item.daysLeft != null ? item.daysLeft + '天' : '',
      daysTagCls: item.daysLeft <= 7 ? 'dt-orange' : 'dt-red',
      extra: item.overdueDays > 300 ? item.overdueDays + '天无开票' : '',
      dotCls: item.daysLeft <= 7 ? 'dot-red' : 'dot-orange',
    }))
    pendingReminderCount.value = r.data?.total || list.length
  } catch {}
  try { const r = await getTeamActivities(); activities.value = r.data || [] } catch {}
  try {
    const r = await getApprovalList({ current: 1, size: 3, tab: 'pending' })
    approvals.value = r.data?.records || []
  } catch {}
  try { const r = await getWeeklyStats(); weeklyData.value = r.data || { customerWeeks: [], fileWeeks: [] } } catch {}
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
}
.stat-label { font-size: 14px; color: #86909c; margin-bottom: 8px; }
.stat-num { font-size: 36px; font-weight: 700; line-height: 1.1; }
.stat-num.orange { color: #e8684a; }
.stat-num.blue { color: #409eff; }
.stat-num.red { color: #f56c6c; }
.stat-sub { font-size: 12px; color: #999; margin-top: 6px; }
.stat-icon { flex-shrink: 0; opacity: 0.35; }
.icon-blue { color: #409eff; }
.icon-orange { color: #e6a23c; }
.icon-red { color: #f56c6c; }

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

/* —— 图表 —— */
.chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.chart-card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 24px; }
.chart-head { display: flex; align-items: center; gap: 6px; font-size: 15px; font-weight: 600; color: #1d2129; margin-bottom: 16px; }
.ch-icon { color: #86909c; }
.bars-wrap { display: flex; align-items: flex-end; justify-content: center; gap: 48px; height: 120px; padding-top: 10px; }
.bar-col { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.bar-val { font-size: 12px; font-weight: 600; color: #1d2129; }
.bar { width: 44px; border-radius: 6px 6px 0 0; transition: height .4s; }
.bar-blue { background: #409eff; }
.bar-green { background: #67c23a; }
.bar-lbl { font-size: 12px; color: #86909c; }

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

/* —— 提醒列表 —— */
.remind-row { display: flex; align-items: flex-start; gap: 12px; padding: 14px 0; border-bottom: 1px solid #f5f5f5; }
.remind-row:last-child { border-bottom: none; }
.r-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 6px; flex-shrink: 0; }
.dot-red { background: #f56c6c; }
.dot-orange { background: #e6a23c; }
.dot-gray { background: #c0c4cc; }
.r-body { flex: 1; min-width: 0; }
.r-main { font-size: 14px; color: #1d2129; font-weight: 500; }
.r-sub { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.days-tag { font-size: 12px; padding: 1px 6px; border-radius: 4px; font-weight: 600; }
.dt-orange { color: #e6a23c; background: #fdf6ec; }
.dt-red { color: #f56c6c; background: #fef0f0; }
.r-extra { font-size: 12px; color: #999; }

/* —— 团队动态 —— */
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
