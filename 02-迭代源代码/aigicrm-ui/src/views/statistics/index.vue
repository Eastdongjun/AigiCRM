<template>
  <div class="statistics-page">
    <!-- 数据概览 · 深蓝主视觉带(指标白字统排 + 筛选内嵌,与工作台/文件页同语言) -->
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

    <!-- 行业分布 + 办事处对比 -->
    <div class="twin-row">
      <div class="card">
        <div class="card-head"><span>行业分布</span><span class="card-unit">按客户数排序</span></div>
        <div class="industry-bars">
          <div v-for="item in industryData" :key="item.name" class="ind-row">
            <span class="ind-name">{{ item.name }}</span>
            <div class="ind-track">
              <div class="ind-fill" :style="{ width: item.pct + '%' }">
                <span class="ind-pct">{{ item.pctLabel }}%</span>
              </div>
            </div>
            <span class="ind-count"><strong>{{ item.count }}</strong></span>
            <span class="ind-pct-r">{{ item.pctLabel }}%</span>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-head"><span>办事处对比</span><span class="card-unit">近5月新增走势</span></div>
        <table class="office-table">
          <thead>
            <tr>
              <th>办事处</th><th>客户数</th><th>本月新增</th><th>活跃率</th><th>近5月</th><th>趋势</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in analysis.officeComparison || []" :key="o.officeName">
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

    <!-- 月度图表 -->
    <div class="twin-row">
      <div class="card">
        <div class="card-head"><span>月度新增客户</span><span class="card-unit">单位：个</span></div>
        <div class="month-chart">
          <div v-for="(v, i) in (analysis.monthlyCustomers || [])" :key="'c'+i" class="mc-col">
            <span class="mc-val">{{ v }}</span>
            <div class="mc-bar" :class="{ 'mc-now': isCurrentMonth(i) }" :style="{ height: barH(v, analysis.monthlyCustomers) + 'px' }"></div>
            <span class="mc-lbl">{{ i+1 }}月</span>
          </div>
        </div>
        <div class="chart-summary">
          全年新增 <strong>{{ arrSum(analysis.monthlyCustomers) }}</strong> 个客户，月均 <strong>{{ arrAvg(analysis.monthlyCustomers) }}</strong> 个，峰值 <strong>{{ peakMonth(analysis.monthlyCustomers) }}</strong>
        </div>
      </div>
      <div class="card">
        <div class="card-head"><span>月度文件上传</span><span class="card-unit">单位：份</span></div>
        <div class="month-chart">
          <div v-for="(v, i) in (analysis.monthlyFiles || [])" :key="'f'+i" class="mc-col">
            <span class="mc-val">{{ v }}</span>
            <div class="mc-bar" :class="{ 'mc-now': isCurrentMonth(i) }" :style="{ height: barH(v, analysis.monthlyFiles) + 'px' }"></div>
            <span class="mc-lbl">{{ i+1 }}月</span>
          </div>
        </div>
        <div class="chart-summary">
          全年上传 <strong>{{ arrSum(analysis.monthlyFiles) }}</strong> 份文件，月均 <strong>{{ arrAvg(analysis.monthlyFiles) }}</strong> 份，峰值 <strong>{{ peakMonth(analysis.monthlyFiles) }}</strong>
        </div>
      </div>
    </div>

    <!-- 业务总览 -->
    <div class="card biz-overview">
      <div class="card-head"><span>业务总览</span><span class="card-unit">截至 {{ filterYear }}年{{ currentMonth }}月</span></div>
      <div class="biz-grid">
        <!-- 客户状态分布 -->
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

        <!-- 待办 & 预警 -->
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

        <!-- 文件 & 激励 -->
        <div class="biz-col">
          <div class="biz-title">文件 & 激励</div>
          <div class="biz-kv">
            <div class="kv-row"><span>年度文件</span><strong class="blue">{{ arrSum(analysis.monthlyFiles) }} 份</strong></div>
            <div class="kv-sub">{{ filterYear }}年累计上传</div>
          </div>
          <div class="biz-kv">
            <div class="kv-row"><span>年度客户</span><strong class="blue">{{ arrSum(analysis.monthlyCustomers) }} 个</strong></div>
            <div class="kv-sub">{{ filterYear }}年累计新增</div>
          </div>
        </div>

        <!-- 人员积分 Top5 -->
        <div class="biz-col">
          <div class="biz-title">人员积分 Top5</div>
          <div v-for="(r, i) in scoreTop5" :key="i" class="top5-row">
            <span class="top5-rank" :class="'r'+(i+1)">{{ i+1 }}</span>
            <span class="top5-name">{{ r.realName }}</span>
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
import { getAnalysis } from '@/api/statistics'
import { getScoreRanking } from '@/api/score'
import { getScopeDepts } from '@/api/system'

const currentYear = new Date().getFullYear()
const currentMonth = new Date().getMonth() + 1
const filterYear = ref(currentYear)
const filterOffice = ref(null)
const yearOptions = computed(() => { const a = []; for (let y = 2030; y >= 2024; y--) a.push(y); return a })

const analysis = ref({})
const officeList = ref([])
const scoreTop5 = ref([])

const protectionRate = computed(() => {
  const t = analysis.value.totalCustomers || 0
  return t ? ((analysis.value.inProtection || 0) / t * 100).toFixed(1) : 0
})

const industryData = computed(() => {
  const d = analysis.value.industryStats || {}
  const entries = Object.entries(d).sort((a, b) => b[1] - a[1])
  const total = entries.reduce((s, e) => s + e[1], 0) || 1
  return entries.map(([name, count]) => ({
    name, count,
    pct: (count / total) * 100,
    pctLabel: ((count / total) * 100).toFixed(1)
  }))
})

const statusPct = (key) => {
  const t = analysis.value.totalCustomers || 1
  const map = { active: analysis.value.inProtection, expiring: analysis.value.expiring, expired: analysis.value.expired }
  return ((map[key] || 0) / t * 100).toFixed(0)
}

// 图表工具
const barH = (v, arr) => {
  if (!arr || !arr.length) return 0
  const max = Math.max(...arr, 1)
  return Math.max(Math.round((v / max) * 100), v > 0 ? 4 : 0)
}
const arrSum = (arr) => (arr || []).reduce((s, v) => s + v, 0)
const arrAvg = (arr) => {
  if (!arr || !arr.length) return 0
  const filled = arr.filter((_, i) => i < currentMonth)
  return filled.length ? (filled.reduce((s, v) => s + v, 0) / filled.length).toFixed(1) : 0
}
const peakMonth = (arr) => {
  if (!arr || !arr.length) return '—'
  const max = Math.max(...arr)
  const idx = arr.indexOf(max)
  return `${idx + 1}月`
}
// 当前月高亮(仅查看今年数据时)
const isCurrentMonth = (i) => filterYear.value === currentYear && i + 1 === currentMonth

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
  try { const r = await getAnalysis({ year: filterYear.value, deptId: filterOffice.value, officeId: null }); analysis.value = r.data || {} } catch {}
  try { const r = await getScoreRanking({ type: 'personal' }); scoreTop5.value = (r.data || []).slice(0, 5) } catch {}
  try { const r = await getScopeDepts(); officeList.value = r.data || [] } catch {}
}

onMounted(loadAll)
</script>

<style scoped>
.statistics-page { height: calc(100vh - 80px); display: flex; flex-direction: column; gap: 10px; overflow-y: auto }

/* —— 数据概览 · 深蓝主视觉带 —— */
.ana-hero {
  background: linear-gradient(135deg, #16337a 0%, #1d4ed8 58%, #2563eb 100%);
  border-radius: 12px; padding: 16px 24px 18px; flex-shrink: 0;
  box-shadow: 0 6px 20px rgba(22, 51, 122, .14);
}
.ah-top { display: flex; align-items: center; margin-bottom: 14px; }
.ah-title { font-size: 15px; font-weight: 700; color: #fff; letter-spacing: .3px }
.ah-filters { margin-left: auto; display: flex; align-items: center; gap: 8px }
.ah-flbl { font-size: 12px; color: rgba(255, 255, 255, .6) }
.ah-select.narrow { width: 96px }
.ah-select { width: 190px }
.ah-select :deep(.el-select__wrapper),
.ah-select :deep(.el-tree-select .el-select__wrapper),
.ah-select :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, .14);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .28) inset;
}
.ah-select :deep(.el-select__placeholder),
.ah-select :deep(.el-select__selected-item),
.ah-select :deep(.el-input__inner) { color: #fff }
.ah-refresh {
  background: rgba(255, 255, 255, .14); color: #fff; border: 1px solid rgba(255, 255, 255, .3);
}
.ah-refresh:hover, .ah-refresh:focus { background: rgba(255, 255, 255, .24); color: #fff }
.ah-metrics { display: grid; grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr auto 1fr; gap: 0 24px; align-items: stretch }
.ah-metric { min-width: 0 }
.ah-divider { width: 1px; background: rgba(255, 255, 255, .18); margin: 4px 0 }
.ah-label { font-size: 12px; color: rgba(255, 255, 255, .72); margin-bottom: 6px; letter-spacing: .3px }
.ah-num { font-size: 28px; font-weight: 700; line-height: 1.1; color: #fff; display: flex; align-items: center; gap: 8px }
.ah-sub { font-size: 12px; color: rgba(255, 255, 255, .58); margin-top: 6px }
.ah-up { color: #fcd34d; font-weight: 600 }
.ah-chip { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 999px; white-space: nowrap }
.chip-amber { background: rgba(251, 191, 36, .22); color: #fcd34d; border: 1px solid rgba(251, 191, 36, .45) }
.chip-red { background: rgba(248, 113, 113, .2); color: #fca5a5; border: 1px solid rgba(248, 113, 113, .45) }
.ah-track { height: 4px; background: rgba(255, 255, 255, .18); border-radius: 2px; margin: 8px 0 0; overflow: hidden }
.ah-fill { height: 100%; background: #fcd34d; border-radius: 2px; transition: width .4s }

/* 通用卡片 */
.twin-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 24px; }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; font-size: 15px; font-weight: 600; }
.card-unit { font-size: 12px; color: #c0c4cc; font-weight: normal; }

/* 行业分布(单色系蓝阶,粗细=占比) */
.ind-row { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.ind-name { width: 80px; text-align: right; font-size: 13px; color: #333; flex-shrink: 0; white-space: nowrap }
.ind-track { flex: 1; height: 28px; background: #f5f7fa; border-radius: 6px; overflow: hidden; }
.ind-fill { height: 100%; border-radius: 6px; display: flex; align-items: center; justify-content: flex-end; padding-right: 10px; transition: width .5s; min-width: 40px;
  background: linear-gradient(90deg, #6083e4, #1d4ed8); }
.ind-pct { color: #fff; font-size: 11px; font-weight: 600; white-space: nowrap }
.ind-count { width: 32px; font-size: 16px; font-weight: 700; color: #333; text-align: center; flex-shrink: 0 }
.ind-pct-r { width: 40px; font-size: 11px; color: #999; text-align: right; flex-shrink: 0 }

/* 办事处表格 */
.office-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.office-table th { text-align: left; padding: 8px 6px; color: #86909c; font-weight: 500; border-bottom: 1px solid #ebeef5; }
.office-table td { padding: 10px 6px; border-bottom: 1px solid #f5f5f5; }
.office-name { font-weight: 600; }
.spark { display: flex; align-items: flex-end; gap: 3px; height: 22px; }
.spark-bar { width: 8px; background: #6083e4; border-radius: 2px; min-height: 3px; }
.trend-up { color: #16a34a; font-weight: 500; } .trend-down { color: #f56c6c; font-weight: 500; } .trend-flat { color: #999; }

/* 月度图表(蓝阶柱,当月琥珀高亮) */
.month-chart { display: flex; align-items: flex-end; justify-content: space-between; height: 140px; padding: 10px 0; }
.mc-col { display: flex; flex-direction: column; align-items: center; gap: 4px; flex: 1; }
.mc-val { font-size: 11px; font-weight: 600; color: #333; }
.mc-bar { width: 28px; background: linear-gradient(180deg, #6083e4, #1d4ed8); border-radius: 4px 4px 0 0; transition: height .4s; }
.mc-bar.mc-now { background: linear-gradient(180deg, #fcd34d, #f59e0b); }
.mc-lbl { font-size: 11px; color: #86909c; }
.chart-summary { text-align: right; font-size: 12px; color: #86909c; margin-top: 8px; padding-top: 8px; border-top: 1px solid #f0f0f0; }

/* 业务总览 */
.biz-overview { margin-bottom: 0; }
.biz-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 24px; }
.biz-col { min-width: 0; }
.biz-title { font-size: 14px; font-weight: 600; color: #333; margin-bottom: 16px; }
.biz-note { font-size: 12px; color: #c0c4cc; margin-top: 10px; }

/* 状态条(语义色: 蓝=保护, 琥珀=预警, 红=超期) */
.status-bar-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.sb-label { width: 32px; font-size: 13px; color: #666; flex-shrink: 0; }
.sb-track { flex: 1; height: 8px; background: #f0f2f5; border-radius: 4px; overflow: hidden; }
.sb-fill { height: 100%; border-radius: 4px; transition: width .4s; }
.sb-blue { background: #1d4ed8; } .sb-amber { background: #f59e0b; } .sb-red { background: #f56c6c; }
.sb-count { width: 24px; text-align: right; font-size: 14px; }
.blue { color: #1d4ed8; } .amber { color: #d97706; } .red { color: #f56c6c; }

/* 待办KV */
.biz-kv { margin-bottom: 12px; }
.kv-row { display: flex; justify-content: space-between; font-size: 13px; }
.kv-sub { font-size: 11px; color: #c0c4cc; margin-top: 2px; }

/* Top5(深蓝阶名次,非彩虹奖牌) */
.top5-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 13px; }
.top5-rank { width: 20px; height: 20px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 600; color: #fff; flex-shrink: 0; }
.r1 { background: #1d4ed8; } .r2 { background: #4a6ee0; } .r3 { background: #7a94e9; }
.r4, .r5 { background: #c0c4cc; }
.top5-name { flex: 1; }
.top5-score { color: #333; }

@media (max-width: 1280px) {
  .ah-metrics { grid-template-columns: repeat(2, 1fr auto); }
  .ah-metrics .ah-divider:nth-of-type(2n) { display: none }
}
</style>
