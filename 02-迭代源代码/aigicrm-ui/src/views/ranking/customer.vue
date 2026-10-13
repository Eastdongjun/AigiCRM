<template>
  <div class="customer-ranking-page" v-loading="loading" element-loading-text="正在加载客户排行...">
    <div class="page-head">
      <div>
        <h3 class="page-title">客户排行</h3>
        <p class="page-desc">按客户统一查看四项激励、客情商务关系深度、核心客户六项指标</p>
      </div>
      <el-select v-model="period" class="period-select" size="small" @change="loadData">
        <el-option v-for="p in periodOptions" :key="p.value" :label="p.label" :value="p.value" />
      </el-select>
    </div>

    <div class="stat-row">
      <div class="stat-card">
        <div class="sc-label">四项激励客户</div>
        <div class="sc-num blue">{{ incentiveRanking.length }}</div>
        <div class="sc-desc">本周期有激励积分的客户</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">客情有分客户</div>
        <div class="sc-num green">{{ relationshipRanking.length }}</div>
        <div class="sc-desc">商务关系深度大于 0 分</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">核心指标客户</div>
        <div class="sc-num green">{{ indicatorRanking.length }}</div>
        <div class="sc-desc">五项指标总分大于 0 分</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">真核心</div>
        <div class="sc-num green">{{ coreCount }}</div>
        <div class="sc-desc">年度开票、回款均≥100万</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">争核心</div>
        <div class="sc-num yellow">{{ quasiCount }}</div>
        <div class="sc-desc">有阶段数据，门槛未双达标</div>
      </div>
    </div>

    <div class="tab-bar">
      <span class="tab-item" :class="{ active: activeTab === 'incentive' }" @click="activeTab = 'incentive'">四项激励客户排行</span>
      <span class="tab-item" :class="{ active: activeTab === 'relationship' }" @click="activeTab = 'relationship'">客情商务关系深度排行</span>
      <span class="tab-item" :class="{ active: activeTab === 'indicator' }" @click="activeTab = 'indicator'">核心客户六项指标排行</span>
      <!-- 【裁决 2026-10-10】核心类型筛选: 区分真核心/争核心 -->
      <el-select v-if="activeTab === 'indicator'" v-model="coreFilter" size="small" class="core-filter">
        <el-option label="全部核心类型" value="" />
        <el-option label="真核心" value="core" />
        <el-option label="争核心" value="quasi" />
      </el-select>
    </div>

    <div class="table-area">
      <el-table v-if="activeTab === 'incentive'" :data="incentiveRanking" stripe size="small" style="width:100%" empty-text="暂无激励排行数据">
        <el-table-column label="排名" width="62" align="center">
          <template #default="{ $index }"><span class="rank-num">{{ $index + 1 }}</span></template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="180" />
        <el-table-column prop="ownerName" label="负责人" min-width="90" />
        <el-table-column prop="industry" label="行业" min-width="100" />
        <el-table-column label="激励总额" min-width="95" align="center">
          <template #default="{ row }"><span class="score-pill">{{ row.totalScore }}分</span></template>
        </el-table-column>
        <el-table-column label="上传数" min-width="80" align="center">
          <template #default="{ row }">{{ row.recordCount }}</template>
        </el-table-column>
        <el-table-column v-for="parent in incentiveParents" :key="parent.id" :label="parent.categoryName" min-width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.byParent[parent.id]" class="completion">
              {{ row.byParent[parent.id].count }}条 / {{ row.byParent[parent.id].score }}分
            </span>
            <span v-else class="cell-dash">-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else-if="activeTab === 'relationship'" :data="relationshipRanking" stripe size="small" style="width:100%" empty-text="暂无客情排行数据">
        <el-table-column label="排名" width="62" align="center">
          <template #default="{ $index }"><span class="rank-num">{{ $index + 1 }}</span></template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="190" />
        <el-table-column prop="ownerName" label="负责人" min-width="90" />
        <el-table-column prop="industry" label="行业" min-width="110" />
        <el-table-column label="客情程度" min-width="110" align="center">
          <template #default="{ row }">{{ relationshipLabel(row.relationshipScore) }}</template>
        </el-table-column>
        <el-table-column label="分值" min-width="90" align="center">
          <template #default="{ row }"><span class="score-pill green">{{ row.relationshipScore }}分</span></template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="filteredIndicatorRanking" stripe size="small" style="width:100%" empty-text="暂无核心客户六项指标排行数据" :header-cell-class-name="indHeaderClass">
        <el-table-column label="排名" width="62" align="center">
          <template #default="{ $index }"><span class="rank-num">{{ $index + 1 }}</span></template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="180" />
        <el-table-column prop="ownerName" label="负责人" min-width="90" />
        <el-table-column prop="industry" label="行业" min-width="95" />
        <!-- 两级表头: 一级=六项指标分类, 二级=具体指标(一级包含二级, 口径同 customer-fields INDICATOR_FIELDS) -->
        <el-table-column label="年度门槛 ≥¥100万/年" align="center">
          <el-table-column label="年度开票" min-width="95" align="center">
            <template #default="{ row }">
              <span :class="{ 'threshold-miss': (Number(row.invoiceTotal) || 0) < 100 }">{{ moneyText(row.invoiceTotal) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="年度回款" min-width="95" align="center">
            <template #default="{ row }">
              <span :class="{ 'threshold-miss': (Number(row.paymentTotal) || 0) < 100 }">{{ moneyText(row.paymentTotal) }}</span>
            </template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="客情商务指标" align="center">
          <el-table-column label="高层（客情程度）" min-width="86" align="center"><template #default="{ row }">{{ row.relationshipScore || 0 }}</template></el-table-column>
          <el-table-column label="渠道（编码数量）" min-width="86" align="center"><template #default="{ row }">{{ row.indicator02 || 0 }}</template></el-table-column>
          <el-table-column label="基层（分厂车间）" min-width="86" align="center"><template #default="{ row }">{{ row.indicator03 || 0 }}</template></el-table-column>
          <el-table-column label="产品（密封润滑防腐）" min-width="100" align="center"><template #default="{ row }">{{ row.indicator04 || 0 }}</template></el-table-column>
          <el-table-column label="项目（项目资金）" min-width="86" align="center"><template #default="{ row }">{{ row.indicator05 || 0 }}</template></el-table-column>
          <el-table-column label="总分 /15" min-width="82" align="center">
            <template #default="{ row }"><span class="score-pill">{{ row.totalScore }}分</span></template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="核心类型" min-width="85" align="center" fixed="right">
          <template #default="{ row }">
            <el-tag :type="row.coreType === 'core' ? 'success' : row.coreType === 'quasi' ? 'warning' : 'info'" size="small">
              {{ row.coreType === 'core' ? '真核心' : row.coreType === 'quasi' ? '争核心' : '—' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getCustomerRankings } from '@/api/statistics'
import { buildPeriodOptions, periodQueryValue } from '@/utils/incentive-period'
import { getDictData } from '@/api/system'
import { RELATIONSHIP_OPTIONS_FALLBACK, fetchRelationshipOptions } from '@/utils/customer-fields'

const loading = ref(false)
const activeTab = ref('incentive')
// 【2026-09-12】默认「全部」：原来默认 '2026上半年' 而库内数据是 '2026全年' ⇒ 打开页面 0 条
const period = ref('')
const rankingData = ref({})

const periodOptions = buildPeriodOptions()

const incentiveParents = computed(() => rankingData.value.incentiveParents || [])

const incentiveRanking = computed(() => rankingData.value.incentiveRanking || [])
const indicatorRanking = computed(() => rankingData.value.indicatorRanking || [])
const relationshipRanking = computed(() => rankingData.value.relationshipRanking || [])

const coreCount = computed(() => indicatorRanking.value.filter(row => row.coreType === 'core').length)
const quasiCount = computed(() => indicatorRanking.value.filter(row => row.coreType === 'quasi').length)
// 【裁决 2026-10-10】核心类型筛选(真核心/争核心); 筛选后排名序号仍按原榜次呈现
const coreFilter = ref('')
const filteredIndicatorRanking = computed(() =>
  !coreFilter.value ? indicatorRanking.value : indicatorRanking.value.filter(row => row.coreType === coreFilter.value))

function relationshipLabel(score) {
  // 【#10 2026-10-12】客情文案单一出处(customer-fields), 不再本地硬编码数组
  // 【F4 复审 2026-10-13】字典缺行/停用档位时未命中不再回落第 0 档文案(会把存量分值错显为
  // 第一档), 显 '—' 如实表达"该档字典行不存在"
  const hit = RELATIONSHIP_OPTIONS_FALLBACK.find((o) => Number(o.dictValue) === Number(score))
  return (hit && hit.dictLabel) || '—'
}

function moneyText(value) {
  const n = Number(value || 0)
  return `${Number.isInteger(n) ? n : n.toFixed(2)}万`
}

// 两级表头: 仅第一行(一级组行)着组头样式,二级明细行保持常规表头
function indHeaderClass({ rowIndex }) {
  return rowIndex === 0 ? 'ind-group-head' : ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await getCustomerRankings({ period: periodQueryValue(period.value) })
    rankingData.value = res.data || {}
  } catch {
    // 【D13 2026-10-04】失败由 request 拦截器统一提示；此处补 catch 兜底
    // （原 try/finally 无 catch 会产生 unhandled rejection），失败时保留旧数据
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
  // 【F4 复审 2026-10-13】排行页自身触发客情字典拉取(幂等, 原地同步兜底数组): 此前全仓
  // 唯一触发点在客户详情页, 未开过详情的会话里排行标签始终是硬编码文案。
  // 局限: FALLBACK 非响应式, fetch 晚于首次渲染时标签随下一次重渲染(切tab/翻页)更新。
  fetchRelationshipOptions(getDictData)
})
</script>

<style scoped>
.customer-ranking-page { min-height: calc(100vh - 100px); display: flex; flex-direction: column; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 12px; }
.page-title { font-size: 18px; font-weight: 600; color: #303133; margin: 0; }
.page-desc { font-size: 13px; color: var(--text-muted); margin: 6px 0 0; }
.period-select { width: 150px; flex-shrink: 0; }
.stat-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; margin-bottom: 12px; }
.stat-card { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 16px 18px; }
.sc-label { font-size: 13px; color: var(--text-muted); margin-bottom: 4px; }
.sc-num { font-size: 30px; font-weight: 700; line-height: 1.2; }
.sc-num.blue { color: var(--el-color-primary); }
.sc-num.green { color: var(--el-color-success); }
.sc-num.yellow { color: var(--el-color-warning); }
.sc-desc { font-size: 12px; color: #a8abb2; margin-top: 4px; }
.tab-bar { display: flex; gap: 0; margin-bottom: 12px; align-items: center; }
.core-filter { width: 140px; margin-left: 14px; flex-shrink: 0; }
.tab-item { display: inline-flex; align-items: center; padding: 9px 22px; border: 1px solid #ebeef5; margin-left: -1px; background: #fafafa; color: #606266; cursor: pointer; font-size: 14px; }
.tab-item:first-child { margin-left: 0; border-radius: 8px 0 0 8px; }
.tab-item:last-child { border-radius: 0 8px 8px 0; }
.tab-item.active { color: #1d4ed8; background: #eef4ff; border-color: #1d4ed8; font-weight: 600; z-index: 1; }
.table-area { flex: 1; min-height: 0; background: #fff; }
.table-area :deep(.el-table td.el-table__cell),
.table-area :deep(.el-table th.el-table__cell) { height: 40px; padding: 4px 0; box-sizing: border-box; }
.rank-num { color: #606266; font-weight: 600; }
.score-pill { display: inline-flex; align-items: center; justify-content: center; min-width: 56px; padding: 2px 8px; border-radius: 12px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 600; }
.score-pill.green { background: #f0f9eb; color: var(--el-color-success); }
.completion { color: #303133; font-size: 12px; }
.cell-dash { color: var(--text-placeholder); }
/* 两级表头: 第一行(一级组)整行浅底深蓝加粗,与二级明细区分; 门槛未达(万<100)红色弱标 */
.table-area :deep(.ind-group-head) { background: #f7f9fc !important; color: #1d4ed8; font-weight: 700; }
.threshold-miss { color: var(--el-color-danger); font-weight: 600; }
</style>
