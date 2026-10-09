<template>
  <!-- 【UI 整改 2026-10-08】加载态：首屏与切年份/部门/刷新期间整页遮罩，慢查询不再无感知停留旧数据 -->
  <div v-loading="pageLoading" class="statistics-page">
    <!-- 数据概览 · 深蓝主视觉带(指标白字统排 + 筛选内嵌) -->
    <div class="ana-hero">
      <div class="ah-top">
        <span class="ah-title">数据概览</span>
        <div class="ah-filters">
          <span class="ah-flbl">年份</span>
          <el-select v-model="filterYear" size="small" class="ah-select narrow" @change="loadAll">
            <el-option v-for="y in yearOptions" :key="y" :label="y+'年'" :value="y" />
          </el-select>
          <span class="ah-flbl">部门</span>
          <el-tree-select
            v-model="filterOffice"
            :data="officeList"
            check-strictly
            clearable
            node-key="id"
            :props="{ label: 'deptName', children: 'children' }"
            placeholder="选择部门（含全部子部门）"
            size="small"
            class="ah-select"
            @change="loadAll"
          />
          <el-button size="small" class="ah-refresh" @click="loadAll">刷新</el-button>
        </div>
      </div>
      <div class="ah-metrics">
        <div class="ah-metric">
          <div class="ah-label">客户总数</div>
          <div class="ah-num">{{ analysis.totalCustomers || 0 }}</div>
          <div class="ah-sub">{{ filterYear }}年新增 <b class="ah-up">↑{{ analysis.yearNew || 0 }}</b></div>
        </div>
        <div class="ah-divider"></div>
        <div class="ah-metric">
          <div class="ah-label">保护期内</div>
          <div class="ah-num">{{ analysis.inProtection || 0 }}</div>
          <div class="ah-track"><div class="ah-fill" :style="{ width: protectionRate + '%' }"></div></div>
          <div class="ah-sub">占比 {{ protectionRate }}%</div>
        </div>
        <div class="ah-divider"></div>
        <div class="ah-metric">
          <div class="ah-label">即将到期</div>
          <div class="ah-num">{{ analysis.expiring || 0 }}<span v-if="analysis.expiring" class="ah-chip chip-amber">30天内</span></div>
          <div class="ah-sub">按当前筛选范围</div>
        </div>
        <div class="ah-divider"></div>
        <div class="ah-metric">
          <div class="ah-label">已超期</div>
          <div class="ah-num">{{ analysis.expired || 0 }}<span v-if="analysis.expired" class="ah-chip chip-red">需关注</span></div>
          <div class="ah-sub">需重点跟进</div>
        </div>
        <div class="ah-divider"></div>
        <div class="ah-metric">
          <div class="ah-label">今日到期</div>
          <div class="ah-num">{{ analysis.todayExpiring || 0 }}</div>
          <div class="ah-sub">当日应处理</div>
        </div>
      </div>
    </div>

    <!-- 趋势 · 双面积图 -->
    <div class="twin-row">
      <div class="card chart-card">
        <div class="card-head">
          <span class="ch-title"><span class="ch-dot d-blue"></span>月度新增客户</span>
          <span class="card-unit">单位：个</span>
        </div>
        <div class="area-wrap">
          <svg viewBox="0 0 640 190" preserveAspectRatio="none" class="area-svg">
            <defs>
              <linearGradient id="gCust" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#6083e4" stop-opacity=".35" />
                <stop offset="100%" stop-color="#6083e4" stop-opacity=".02" />
              </linearGradient>
            </defs>
            <g class="grid-lines">
              <line v-for="i in 4" :key="i" x1="0" :y1="i * 36" x2="640" :y2="i * 36" />
            </g>
            <path :d="areaPath(analysis.monthlyCustomers)" fill="url(#gCust)" />
            <polyline :points="linePoints(analysis.monthlyCustomers)" class="line-blue" />
            <circle v-for="(p, i) in pointCoords(analysis.monthlyCustomers)" :key="'c'+i"
              :cx="p.x" :cy="p.y" r="3.2" :class="['pt', { 'pt-now': isCurrentMonth(i) }]" />
          </svg>
          <div class="x-labels"><span v-for="m in 12" :key="m">{{ m }}月</span></div>
        </div>
        <div class="chart-chips">
          <span class="cc">年累计 <b>{{ formatInt(arrSum(analysis.monthlyCustomers)) }}</b></span>
          <span class="cc">月均 <b>{{ formatInt(arrAvg(analysis.monthlyCustomers)) }}</b></span>
          <span class="cc">峰值 <b>{{ peakMonth(analysis.monthlyCustomers) }}</b></span>
        </div>
      </div>
      <div class="card chart-card">
        <div class="card-head">
          <span class="ch-title"><span class="ch-dot d-amber"></span>月度文件上传</span>
          <span class="card-unit">单位：份</span>
        </div>
        <div class="area-wrap">
          <svg viewBox="0 0 640 190" preserveAspectRatio="none" class="area-svg">
            <defs>
              <linearGradient id="gFile" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#fbbf24" stop-opacity=".32" />
                <stop offset="100%" stop-color="#fbbf24" stop-opacity=".02" />
              </linearGradient>
            </defs>
            <g class="grid-lines">
              <line v-for="i in 4" :key="i" x1="0" :y1="i * 36" x2="640" :y2="i * 36" />
            </g>
            <path :d="areaPath(analysis.monthlyFiles)" fill="url(#gFile)" />
            <polyline :points="linePoints(analysis.monthlyFiles)" class="line-amber" />
            <circle v-for="(p, i) in pointCoords(analysis.monthlyFiles)" :key="'f'+i"
              :cx="p.x" :cy="p.y" r="3.2" :class="['pt pt-amber', { 'pt-now pt-amber-now': isCurrentMonth(i) }]" />
          </svg>
          <div class="x-labels"><span v-for="m in 12" :key="m">{{ m }}月</span></div>
        </div>
        <div class="chart-chips">
          <span class="cc">年累计 <b>{{ formatInt(arrSum(analysis.monthlyFiles)) }}</b></span>
          <span class="cc">月均 <b>{{ formatInt(arrAvg(analysis.monthlyFiles)) }}</b></span>
          <span class="cc">峰值 <b>{{ peakMonth(analysis.monthlyFiles) }}</b></span>
        </div>
      </div>
    </div>

    <!-- 结构 · 环形占比 + 办事处对比 -->
    <div class="twin-row">
      <div class="card chart-card">
        <div class="card-head">
          <span class="ch-title"><span class="ch-dot d-blue"></span>行业分布</span>
          <span class="card-unit">按客户数</span>
        </div>
        <div class="donut-layout">
          <div class="donut-box">
            <div class="donut" :style="donutStyle">
              <div class="donut-center">
                <b>{{ analysis.totalCustomers || 0 }}</b>
                <span>客户总数</span>
              </div>
            </div>
          </div>
          <div class="donut-legend">
            <!-- 【A3 2026-10-08】行业扇区/图例行可下钻：点击落到客户列表同行业筛选，与工作台"点击直达"预期一致 -->
            <div v-for="seg in donutSegs" :key="seg.name" class="dl-row drillable" title="点击查看该行业客户" @click="drillCustomer({ industry: seg.name })">
              <span class="dl-dot" :style="{ background: seg.color }"></span>
              <span class="dl-name">{{ seg.name }}</span>
              <div class="dl-track"><div class="dl-fill" :style="{ width: seg.pct + '%', background: seg.color }"></div></div>
              <span class="dl-count">{{ seg.count }}</span>
              <span class="dl-pct">{{ seg.pctLabel }}%</span>
            </div>
          </div>
        </div>
      </div>

      <div class="card chart-card">
        <div class="card-head">
          <span class="ch-title"><span class="ch-dot d-blue"></span>办事处对比</span>
          <span class="card-unit">近5月新增走势</span>
        </div>
        <table class="office-table">
          <thead>
            <tr>
              <th>办事处</th><th>客户数</th><th>本月新增</th><th>活跃率</th><th>近5月</th><th>趋势</th>
            </tr>
          </thead>
          <tbody>
            <!-- 【A3 2026-10-08】办事处行可下钻：点击落到客户列表同办事处（含子部门）筛选 -->
            <tr v-for="o in analysis.officeComparison || []" :key="o.officeName" class="drillable" title="点击查看该办事处客户" @click="drillCustomer({ office: o.officeName })">
              <td class="office-name"><strong>{{ o.officeName }}</strong></td>
              <td>{{ o.customerCount }}</td>
              <td>{{ o.monthNew }}</td>
              <td>{{ o.activeRate }}%</td>
              <td>
                <div class="spark">
                  <div v-for="(v, i) in (o.recent5 || [])" :key="i" class="spark-bar"
                :style="{ height: Math.max(4, (v / Math.max(...(o.recent5||[1]))) * 20) + 'px' }"></div>
                </div>
              </td>
              <td><span :class="trendCls(o)">{{ trendText(o) }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 总览 · 指标砖 + Top5 -->
    <div class="card biz-overview">
      <div class="card-head">
        <span class="ch-title"><span class="ch-dot d-blue"></span>业务总览</span>
        <span class="card-unit">截至 {{ filterYear }}年{{ currentMonth }}月</span>
      </div>
      <div class="biz-grid">
        <div class="biz-col">
          <div class="biz-title">客户状态分布</div>
          <div class="status-bar-row">
            <span class="sb-label">保护中</span>
            <div class="sb-track"><div class="sb-fill sb-blue" :style="{ width: statusPct('active') + '%' }"></div></div>
            <strong class="sb-count blue">{{ analysis.inProtection || 0 }}</strong>
          </div>
          <div class="status-bar-row">
            <span class="sb-label">预警</span>
            <div class="sb-track"><div class="sb-fill sb-amber" :style="{ width: statusPct('expiring') + '%' }"></div></div>
            <strong class="sb-count amber">{{ analysis.expiring || 0 }}</strong>
          </div>
          <div class="status-bar-row">
            <span class="sb-label">超期</span>
            <div class="sb-track"><div class="sb-fill sb-red" :style="{ width: statusPct('expired') + '%' }"></div></div>
            <strong class="sb-count red">{{ analysis.expired || 0 }}</strong>
          </div>
          <div class="biz-note">保护率 {{ protectionRate }}%，{{ protectionRate >= 60 ? '健康度良好' : '需关注' }}</div>
        </div>

        <div class="biz-col">
          <div class="biz-title">待办 & 预警</div>
          <div class="biz-kv">
            <div class="kv-row"><span>即将到期</span><strong class="amber">{{ analysis.expiring || 0 }} 个</strong></div>
            <div class="kv-sub">30天内到期客户</div>
          </div>
          <div class="biz-kv">
            <div class="kv-row"><span>已超期</span><strong class="red">{{ analysis.expired || 0 }} 个</strong></div>
            <div class="kv-sub">需重点跟进</div>
          </div>
          <div class="biz-kv">
            <div class="kv-row"><span>年度新增</span><strong class="blue">{{ analysis.yearNew || 0 }} 个</strong></div>
            <div class="kv-sub">{{ filterYear }}年新增客户</div>
          </div>
        </div>

        <div class="biz-col">
          <div class="biz-title">文件 & 激励</div>
          <div class="biz-kv">
            <div class="kv-row"><span>年度文件</span><strong class="blue">{{ formatInt(arrSum(analysis.monthlyFiles)) }} 份</strong></div>
            <div class="kv-sub">{{ filterYear }}年累计上传</div>
          </div>
          <div class="biz-kv">
            <div class="kv-row"><span>年度客户</span><strong class="blue">{{ arrSum(analysis.monthlyCustomers) }} 个</strong></div>
            <div class="kv-sub">{{ filterYear }}年累计新增</div>
          </div>
        </div>

        <div class="biz-col">
          <div class="biz-title">人员积分 Top5</div>
          <div v-for="(r, i) in scoreTop5" :key="i" class="top5-row">
            <span class="top5-rank" :class="'r'+(i+1)">{{ i+1 }}</span>
            <span class="top5-name">{{ r.realName }}</span>
            <div class="top5-track"><div class="top5-fill" :style="{ width: top5BarWidth(r.totalScore) + '%' }"></div></div>
            <strong class="top5-score">{{ r.totalScore }}</strong>
          </div>
          <div v-if="!scoreTop5.length" class="biz-note">暂无积分数据</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAnalysis } from '@/api/statistics'
import { getScoreRanking } from '@/api/score'
import { getScopeDepts } from '@/api/system'
// 【B6 三轮 2026-10-09】汇总数(年累计/月均/年度文件)统一千分位口径
import { formatInt } from '@/utils/format'

const router = useRouter()
const currentYear = new Date().getFullYear()
const currentMonth = new Date().getMonth() + 1
const filterYear = ref(currentYear)
const filterOffice = ref(null)
const yearOptions = computed(() => { const a = []; for (let y = 2030; y >= 2024; y--) a.push(y); return a })

// 【A3 2026-10-08】图表下钻：跳客户列表并携带筛选参数（useCustomerList 消费 status/industry/office）；
// tab=all 保持全分类口径，避免落进默认"成熟创新"页签造成数字对不上
const drillCustomer = ({ industry, office }) => {
  const query = { tab: 'all' }
  if (industry) query.industry = industry
  if (office) query.office = office
  router.push({ path: '/customer', query })
}

const analysis = ref({})
const officeList = ref([])
const scoreTop5 = ref([])
// 【UI 整改 2026-10-08】加载态：loadAll（切年份/部门/刷新共用）期间置 true
const pageLoading = ref(false)

const protectionRate = computed(() => {
  const t = analysis.value.totalCustomers || 0
  return t ? ((analysis.value.inProtection || 0) / t * 100).toFixed(1) : 0
})

const DONUT_COLORS = ['#1d4ed8', '#6083e4', '#93b0ec', '#c2d0f5', '#fbbf24', '#cbd5e1']

const donutSegs = computed(() => {
  const d = analysis.value.industryStats || {}
  const entries = Object.entries(d).sort((a, b) => b[1] - a[1])
  const total = entries.reduce((s, e) => s + e[1], 0) || 1
  const top = entries.slice(0, 5).map(([name, count]) => ({
    name, count, pct: (count / total) * 100,
    pctLabel: ((count / total) * 100).toFixed(1)
  }))
  const restCount = entries.slice(5).reduce((s, e) => s + e[1], 0)
  if (restCount > 0) {
    top.push({ name: '其他', count: restCount, pct: (restCount / total) * 100,
      pctLabel: ((restCount / total) * 100).toFixed(1) })
  }
  // 【工作流复核确认修复】行业为空的客户计入"未填行业"段: 原实现既不进扇区也不进"其他",
  // 图例合计与中心数字(totalCustomers)对不上(实测 268 vs 272)
  const noIndustry = Math.max(0, (analysis.value.totalCustomers || 0) - total)
  if (noIndustry > 0) {
    top.push({ name: '未填行业', count: noIndustry, pct: (noIndustry / (total + noIndustry)) * 100,
      pctLabel: ((noIndustry / (total + noIndustry)) * 100).toFixed(1) })
  }
  return top.map((s, i) => ({ ...s, color: DONUT_COLORS[i % DONUT_COLORS.length] }))
})

const donutStyle = computed(() => {
  const segs = donutSegs.value
  if (!segs.length) return { background: '#eef2f8' }
  let acc = 0
  const stops = segs.map((s) => {
    const from = acc
    acc += s.pct
    return `${s.color} ${from.toFixed(2)}% ${acc.toFixed(2)}%`
  })
  return { background: `conic-gradient(${stops.join(', ')})` }
})

const statusPct = (key) => {
  const t = analysis.value.totalCustomers || 1
  const map = { active: analysis.value.inProtection, expiring: analysis.value.expiring, expired: analysis.value.expired }
  return ((map[key] || 0) / t * 100).toFixed(0)
}

// —— 面积图几何(viewBox 640x190, 上留8 下留22 给横轴) ——
const CX = { w: 640, h: 190, padX: 14, top: 10, bottom: 26 }
const yOf = (v, max) => CX.top + (1 - v / max) * (CX.h - CX.top - CX.bottom)
const xOf = (i) => CX.padX + (i / 11) * (CX.w - CX.padX * 2)

const linePoints = (arr) => {
  const a = arr || []
  if (!a.length) return ''
  const max = Math.max(...a, 1)
  return a.map((v, i) => `${xOf(i).toFixed(1)},${yOf(v, max).toFixed(1)}`).join(' ')
}
const areaPath = (arr) => {
  const a = arr || []
  if (!a.length) return ''
  const max = Math.max(...a, 1)
  const pts = a.map((v, i) => `${xOf(i).toFixed(1)},${yOf(v, max).toFixed(1)}`)
  const base = CX.h - CX.bottom
  return `M ${xOf(0).toFixed(1)},${base} L ${pts.join(' L ')} L ${xOf(a.length - 1).toFixed(1)},${base} Z`
}
const pointCoords = (arr) => {
  const a = arr || []
  const max = Math.max(...a, 1)
  return a.map((v, i) => ({ x: +xOf(i).toFixed(1), y: +yOf(v, max).toFixed(1) }))
}

const isCurrentMonth = (i) => filterYear.value === currentYear && i + 1 === currentMonth
const arrSum = (arr) => (arr || []).reduce((s, v) => s + v, 0)
const arrAvg = (arr) => {
  if (!arr || !arr.length) return 0
  // 【M9 修复】仅所选年份为当前年才按 currentMonth 截断（参照 isCurrentMonth 口径）；历史年份统计全年 12 个月
  const months = filterYear.value === currentYear ? currentMonth : 12
  const filled = arr.filter((_, i) => i < months)
  return filled.length ? (filled.reduce((s, v) => s + v, 0) / filled.length).toFixed(1) : 0
}
const peakMonth = (arr) => {
  if (!arr || !arr.length) return '—'
  const max = Math.max(...arr)
  return `${arr.indexOf(max) + 1}月`
}
const top5BarWidth = (score) => {
  const max = Math.max(...scoreTop5.value.map((r) => Number(r.totalScore) || 0), 1)
  return Math.round((Number(score) || 0) / max * 100)
}

// 办事处趋势
const trendText = (o) => {
  const r = o.recent5 || []
  if (r.length < 2) return '—'
  const diff = r[r.length - 1] - r[r.length - 2]
  if (diff > 0) return `↑ ${(diff / Math.max(r[r.length - 2], 1) * 100).toFixed(1)}%`
  if (diff < 0) return `↓ ${(Math.abs(diff) / Math.max(r[r.length - 2], 1) * 100).toFixed(1)}%`
  return '→ 0.0%'
}
const trendCls = (o) => {
  const r = o.recent5 || []
  if (r.length < 2) return ''
  const diff = r[r.length - 1] - r[r.length - 2]
  return diff > 0 ? 'trend-up' : diff < 0 ? 'trend-down' : 'trend-flat'
}

const loadAll = async () => {
  pageLoading.value = true
  try {
    try { const r = await getAnalysis({ year: filterYear.value, deptId: filterOffice.value, officeId: null }); analysis.value = r.data || {} } catch {}
    try { const r = await getScoreRanking({ type: 'personal' }); scoreTop5.value = (r.data || []).slice(0, 5) } catch {}
    try { const r = await getScopeDepts(); officeList.value = r.data || [] } catch {}
  } finally {
    pageLoading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.statistics-page { height: calc(100vh - 80px); display: flex; flex-direction: column; gap: 14px; overflow-y: auto }

/* —— 数据概览 · 深蓝主视觉带（渐变/投影接线 :root 的 --hero-gradient/--hero-shadow 令牌） —— */
.ana-hero {
  background: var(--hero-gradient);
  border-radius: 12px; padding: 16px 24px 18px; flex-shrink: 0;
  box-shadow: var(--hero-shadow);
}
.ah-top { display: flex; align-items: center; margin-bottom: 14px; }
.ah-title { font-size: 15px; font-weight: 700; color: #fff; letter-spacing: .3px }
.ah-filters { margin-left: auto; display: flex; align-items: center; gap: 8px }
.ah-flbl { font-size: 12px; color: rgba(255, 255, 255, .6) }
.ah-select.narrow { width: 96px }
.ah-select { width: 190px }
.ah-select :deep(.el-select__wrapper),
.ah-select :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, .14);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .28) inset;
}
.ah-select :deep(.el-select__placeholder),
.ah-select :deep(.el-select__selected-item),
.ah-select :deep(.el-input__inner) { color: #fff }
.ah-refresh { background: rgba(255, 255, 255, .14); color: #fff; border: 1px solid rgba(255, 255, 255, .3); }
.ah-refresh:hover, .ah-refresh:focus { background: rgba(255, 255, 255, .24); color: #fff }
.ah-metrics { display: grid; grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr auto 1fr; gap: 0 24px; align-items: stretch }
.ah-metric { min-width: 0 }
.ah-divider { width: 1px; background: rgba(255, 255, 255, .18); margin: 4px 0 }
.ah-label { font-size: 12px; color: rgba(255, 255, 255, .72); margin-bottom: 6px; letter-spacing: .3px }
/* 【B6】大数字唯一档 --font-num-hero(30px)，收敛此前 28/30/32 三档并存 */
.ah-num { font-size: var(--font-num-hero); font-weight: 700; line-height: 1.1; color: #fff; display: flex; align-items: center; gap: 8px }
.ah-sub { font-size: 12px; color: rgba(255, 255, 255, .58); margin-top: 6px }
.ah-up { color: #fcd34d; font-weight: 600 }
.ah-chip { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: var(--radius-pill); white-space: nowrap }
.chip-amber { background: rgba(251, 191, 36, .22); color: #fcd34d; border: 1px solid rgba(251, 191, 36, .45) }
.chip-red { background: rgba(248, 113, 113, .2); color: #fca5a5; border: 1px solid rgba(248, 113, 113, .45) }
.ah-track { height: 4px; background: rgba(255, 255, 255, .18); border-radius: 2px; margin: 8px 0 0; overflow: hidden }
.ah-fill { height: 100%; background: #fcd34d; border-radius: 2px; transition: width .4s }

/* —— 通用图表卡 —— */
.twin-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 20px 22px; }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.ch-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 700; color: #1f2329 }
.ch-dot { width: 8px; height: 8px; border-radius: 2px; display: inline-block }
.d-blue { background: #1d4ed8 } .d-amber { background: var(--chart-amber) }
.card-unit { font-size: 12px; color: #6b7280; font-weight: normal; }

/* —— 面积趋势图 —— */
.chart-card { display: flex; flex-direction: column; }
.area-wrap { flex: 1; min-height: 0; }
.area-svg { width: 100%; height: 190px; display: block; }
.grid-lines line { stroke: #f0f3f8; stroke-width: 1; }
.line-blue { fill: none; stroke: #1d4ed8; stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round }
.line-amber { fill: none; stroke: var(--chart-amber); stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round }
.pt { fill: #fff; stroke: #1d4ed8; stroke-width: 2 }
.pt.pt-amber { stroke: var(--chart-amber) }
.pt-now { fill: #1d4ed8 }
.pt-amber-now { fill: var(--chart-amber) }
.x-labels { display: flex; justify-content: space-between; padding: 6px 14px 0; font-size: 12px; color: #6b7280; }
.chart-chips { display: flex; gap: 10px; margin-top: 12px; padding-top: 12px; border-top: 1px solid #f2f4f8; }
.cc { font-size: 12px; color: var(--text-muted); background: #f7f9fc; border-radius: var(--radius-sm); padding: 4px 12px; }
.cc b { color: #1d4ed8; font-size: 13px; margin-left: 4px }

/* —— 环形占比 —— */
.donut-layout { display: flex; gap: 24px; align-items: center; }
.donut-box { flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.donut {
  width: 168px; height: 168px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, .04);
}
.donut-center {
  width: 108px; height: 108px; border-radius: 50%; background: #fff;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  box-shadow: 0 2px 10px rgba(15, 23, 42, .08);
}
.donut-center b { font-size: 26px; font-weight: 700; color: var(--brand-navy); line-height: 1.1 }
.donut-center span { font-size: 12px; color: #6b7280; margin-top: 3px }
.donut-legend { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10px; }
.dl-row { display: flex; align-items: center; gap: 8px; font-size: 13px; }
/* 【A3】可下钻行统一可点击暗示 */
.drillable { cursor: pointer; border-radius: var(--radius-sm); transition: background .15s; }
.dl-row.drillable:hover { background: #f5f8ff; }
.office-table tr.drillable:hover td { background: #f5f8ff; }
.dl-dot { width: 9px; height: 9px; border-radius: 3px; flex-shrink: 0 }
.dl-name { width: 64px; color: #4e5969; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.dl-track { flex: 1; height: 6px; background: #f2f4f8; border-radius: 3px; overflow: hidden }
.dl-fill { height: 100%; border-radius: 3px; }
.dl-count { width: 30px; text-align: right; font-weight: 700; color: #1f2329 }
.dl-pct { width: 44px; text-align: right; color: #6b7280; font-size: 12px }

/* —— 办事处表格 —— */
.office-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.office-table th { text-align: left; padding: 8px 6px; color: var(--text-muted); font-weight: 500; border-bottom: 1px solid #ebeef5; }
.office-table td { padding: 10px 6px; border-bottom: 1px solid #f5f5f5; }
.office-name { font-weight: 600; }
.spark { display: flex; align-items: flex-end; gap: 3px; height: 22px; }
.spark-bar { width: 8px; background: #6083e4; border-radius: 2px; min-height: 3px; }
.trend-up { color: #16a34a; font-weight: 500; } .trend-down { color: var(--el-color-danger); font-weight: 500; } .trend-flat { color: var(--text-muted); }

/* —— 业务总览 —— */
.biz-overview { margin-bottom: 0; }
.biz-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 24px; }
.biz-col { min-width: 0; }
.biz-title { font-size: 13px; font-weight: 700; color: #4e5969; margin-bottom: 14px; }
.biz-note { font-size: 12px; color: #6b7280; margin-top: 10px; }
.status-bar-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.sb-label { width: 32px; font-size: 13px; color: #666; flex-shrink: 0; }
.sb-track { flex: 1; height: 8px; background: #f0f2f5; border-radius: 4px; overflow: hidden; }
.sb-fill { height: 100%; border-radius: 4px; transition: width .4s; }
.sb-blue { background: #1d4ed8; } .sb-amber { background: var(--chart-amber); } .sb-red { background: var(--el-color-danger); }
.sb-count { width: 24px; text-align: right; font-size: 14px; }
.blue { color: #1d4ed8; } .amber { color: #d97706; } .red { color: var(--el-color-danger); }
.biz-kv { margin-bottom: 12px; }
.kv-row { display: flex; justify-content: space-between; font-size: 13px; }
.kv-sub { font-size: 12px; color: #6b7280; margin-top: 2px; }
.top5-row { display: flex; align-items: center; gap: 8px; padding: 5px 0; font-size: 13px; }
.top5-rank { width: 20px; height: 20px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; color: #fff; flex-shrink: 0; }
.r1 { background: #1d4ed8; } .r2 { background: #4a6ee0; } .r3 { background: #7a94e9; }
.r4, .r5 { background: #c0c4cc; }
.top5-name { width: 52px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex-shrink: 0 }
.top5-track { flex: 1; height: 6px; background: #f2f4f8; border-radius: 3px; overflow: hidden }
.top5-fill { height: 100%; border-radius: 3px; background: linear-gradient(90deg, #6083e4, #1d4ed8) }
.top5-score { width: 30px; text-align: right; color: var(--brand-navy) }

@media (max-width: 1280px) {
  .ah-metrics { grid-template-columns: repeat(2, 1fr auto); }
  .ah-metrics .ah-divider:nth-of-type(2n) { display: none }
  .biz-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
