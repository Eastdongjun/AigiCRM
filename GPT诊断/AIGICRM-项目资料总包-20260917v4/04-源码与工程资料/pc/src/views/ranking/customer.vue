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
        <div class="sc-label">核心客户</div>
        <div class="sc-num yellow">{{ coreCount }}</div>
        <div class="sc-desc">五项评分≥12，开票/回款均≥100万</div>
      </div>
    </div>

    <div class="tab-bar">
      <span class="tab-item" :class="{ active: activeTab === 'incentive' }" @click="activeTab = 'incentive'">四项激励客户排行</span>
      <span class="tab-item" :class="{ active: activeTab === 'relationship' }" @click="activeTab = 'relationship'">客情商务关系深度排行</span>
      <span class="tab-item" :class="{ active: activeTab === 'indicator' }" @click="activeTab = 'indicator'">核心客户六项指标排行</span>
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

      <el-table v-else :data="indicatorRanking" stripe size="small" style="width:100%" empty-text="暂无核心客户六项指标排行数据">
        <el-table-column label="排名" width="62" align="center">
          <template #default="{ $index }"><span class="rank-num">{{ $index + 1 }}</span></template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="180" />
        <el-table-column prop="ownerName" label="负责人" min-width="90" />
        <el-table-column prop="industry" label="行业" min-width="95" />
        <el-table-column label="高层" min-width="72" align="center"><template #default="{ row }">{{ row.relationshipScore || 0 }}</template></el-table-column>
        <el-table-column label="渠道" min-width="72" align="center"><template #default="{ row }">{{ row.indicator02 || 0 }}</template></el-table-column>
        <el-table-column label="基层" min-width="72" align="center"><template #default="{ row }">{{ row.indicator03 || 0 }}</template></el-table-column>
        <el-table-column label="产品" min-width="72" align="center"><template #default="{ row }">{{ row.indicator04 || 0 }}</template></el-table-column>
        <el-table-column label="项目" min-width="72" align="center"><template #default="{ row }">{{ row.indicator05 || 0 }}</template></el-table-column>
        <el-table-column label="总分" min-width="82" align="center">
          <template #default="{ row }"><span class="score-pill">{{ row.totalScore }}分</span></template>
        </el-table-column>
        <el-table-column label="开票" min-width="95" align="center"><template #default="{ row }">{{ moneyText(row.invoiceTotal) }}</template></el-table-column>
        <el-table-column label="回款" min-width="95" align="center"><template #default="{ row }">{{ moneyText(row.paymentTotal) }}</template></el-table-column>
        <el-table-column label="是否核心" min-width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="row.core ? 'success' : 'info'" size="small">{{ row.core ? '是' : '否' }}</el-tag>
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

const coreCount = computed(() => indicatorRanking.value.filter(row => row.core).length)

function relationshipLabel(score) {
  return ['陌生', '知道', '熟悉', '支持'][score] || '陌生'
}

function moneyText(value) {
  const n = Number(value || 0)
  return `${Number.isInteger(n) ? n : n.toFixed(2)}万`
}

async function loadData() {
  loading.value = true
  try {
    const res = await getCustomerRankings({ period: periodQueryValue(period.value) })
    rankingData.value = res.data || {}
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.customer-ranking-page { min-height: calc(100vh - 100px); display: flex; flex-direction: column; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 12px; }
.page-title { font-size: 18px; font-weight: 600; color: #303133; margin: 0; }
.page-desc { font-size: 13px; color: #86909c; margin: 6px 0 0; }
.period-select { width: 150px; flex-shrink: 0; }
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 12px; }
.stat-card { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 16px 18px; }
.sc-label { font-size: 13px; color: #86909c; margin-bottom: 4px; }
.sc-num { font-size: 30px; font-weight: 700; line-height: 1.2; }
.sc-num.blue { color: #409eff; }
.sc-num.green { color: #67c23a; }
.sc-num.yellow { color: #e6a23c; }
.sc-desc { font-size: 12px; color: #a8abb2; margin-top: 4px; }
.tab-bar { display: flex; gap: 0; margin-bottom: 12px; }
.tab-item { display: inline-flex; align-items: center; padding: 9px 22px; border: 1px solid #ebeef5; margin-left: -1px; background: #fafafa; color: #606266; cursor: pointer; font-size: 14px; }
.tab-item:first-child { margin-left: 0; border-radius: 8px 0 0 8px; }
.tab-item:last-child { border-radius: 0 8px 8px 0; }
.tab-item.active { color: #409eff; background: #ecf5ff; border-color: #409eff; font-weight: 600; z-index: 1; }
.table-area { flex: 1; min-height: 0; background: #fff; }
.table-area :deep(.el-table td.el-table__cell),
.table-area :deep(.el-table th.el-table__cell) { height: 40px; padding: 4px 0; box-sizing: border-box; }
.rank-num { color: #606266; font-weight: 600; }
.score-pill { display: inline-flex; align-items: center; justify-content: center; min-width: 56px; padding: 2px 8px; border-radius: 12px; background: #ecf5ff; color: #409eff; font-weight: 600; }
.score-pill.green { background: #f0f9eb; color: #67c23a; }
.completion { color: #303133; font-size: 12px; }
.cell-dash { color: #c0c4cc; }
</style>
