<template>
  <div class="indicator-page">
    <!-- 标题：与生产菜单(id=108)、router meta.title 统一为「核心客户六项指标」 -->
    <h3 class="page-title">核心客户六项指标</h3>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="sc-label">覆盖客户数</div>
        <div class="sc-num blue">{{ data.coveredCount || 0 }}</div>
        <div class="sc-desc">硬性门槛 + 五项指标评估客户</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">核心客户数</div>
        <div class="sc-num green">{{ data.coreCount || 0 }}</div>
        <div class="sc-desc">五项评分≥12，开票/回款均≥100万</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">指标平均分</div>
        <div class="sc-num green">{{ data.avgScore || 0 }}</div>
        <div class="sc-desc">五项指标总分 / 客户数</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">达标率</div>
        <div class="sc-num yellow">{{ data.qualifyRate || 0 }}%</div>
        <div class="sc-desc">开票/回款均达标客户占比</div>
      </div>
    </div>

    <div class="table-area">
      <div class="rank-header">
        <span class="rank-title">客户排行</span>
        <span class="rank-count">{{ customerRanking.length }}个客户</span>
      </div>
      <el-table :data="customerRanking" stripe style="width:100%" size="small" empty-text="暂无数据，请先在客户详情中录入核心客户六项指标">
        <el-table-column label="排名" min-width="55" align="center">
          <template #default="{ $index }">
            <span v-if="$index === 0" class="medal">1</span>
            <span v-else-if="$index === 1" class="medal">2</span>
            <span v-else-if="$index === 2" class="medal">3</span>
            <span v-else class="rank-num">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="180" />
        <el-table-column prop="ownerName" label="负责人" min-width="90" />
        <el-table-column prop="industry" label="行业" min-width="90" />
        <el-table-column label="高层" min-width="70" align="center">
          <template #default="{ row }"><span class="score-green">{{ row.relationshipScore || 0 }}</span></template>
        </el-table-column>
        <el-table-column label="渠道" min-width="70" align="center">
          <template #default="{ row }">{{ row.indicator02 || 0 }}</template>
        </el-table-column>
        <el-table-column label="基层" min-width="70" align="center">
          <template #default="{ row }">{{ row.indicator03 || 0 }}</template>
        </el-table-column>
        <el-table-column label="产品" min-width="70" align="center">
          <template #default="{ row }">{{ row.indicator04 || 0 }}</template>
        </el-table-column>
        <el-table-column label="项目" min-width="70" align="center">
          <template #default="{ row }">{{ row.indicator05 || 0 }}</template>
        </el-table-column>
        <el-table-column label="总分" min-width="75" align="center">
          <template #default="{ row }"><strong class="score-blue">{{ row.totalScore }}</strong></template>
        </el-table-column>
        <el-table-column label="开票" min-width="90" align="center">
          <template #default="{ row }">{{ row.invoiceTotal || 0 }}万</template>
        </el-table-column>
        <el-table-column label="回款" min-width="90" align="center">
          <template #default="{ row }">{{ row.paymentTotal || 0 }}万</template>
        </el-table-column>
        <el-table-column label="是否核心" min-width="85" align="center">
          <template #default="{ row }">
            <span :class="row.core ? 'core-yes' : 'core-no'">{{ row.core ? '是' : '否' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <!-- 2026-10-04 D15：移除“更新时间: 渲染时刻”展示——/statistics/indicator-stats 不返回
             任何数据时间字段（见后端 StatisticsQueryService.indicatorStats），new Date() 属伪造时间 -->
        <span>按核心五项总分从高到低排序，0分客户不展示</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getIndicatorStats } from '@/api/statistics'

const data = ref({})
const customerRanking = computed(() => data.value.customerRanking || [])

const loadData = async () => {
  try { const r = await getIndicatorStats(); data.value = r.data || {} } catch {}
}
onMounted(loadData)
</script>

<style scoped>
.indicator-page { min-height: calc(100vh - 100px); display: flex; flex-direction: column; }
.page-title { font-size: 18px; font-weight: 600; color: #333; margin: 0 0 12px; }

/* Tab */
.tab-bar { display: flex; gap: 8px; margin-bottom: 12px; }
.tab-item { padding: 8px 20px; border: 1px solid #ebeef5; border-radius: 20px; font-size: 14px; color: #666; cursor: pointer; transition: all .2s; }
.tab-item:hover { color: #409eff; border-color: #409eff; }
.tab-item.active { color: #409eff; border-color: #409eff; background: #ecf5ff; font-weight: 600; }

/* 统计卡片 */
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 12px; }
.stat-card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 20px 24px; }
.sc-label { font-size: 13px; color: #86909c; margin-bottom: 4px; }
.sc-num { font-size: 36px; font-weight: 700; line-height: 1.2; }
.sc-num.blue { color: #409eff; } .sc-num.green { color: #67c23a; } .sc-num.yellow { color: #e6a23c; }
.sc-desc { font-size: 12px; color: #c0c4cc; margin-top: 4px; }

/* 表格区域 */
.table-area {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.table-area :deep(.el-table) { flex: 1; }
.table-area :deep(.el-table td.el-table__cell),
.table-area :deep(.el-table th.el-table__cell) {
  height: 40px;
  padding: 4px 0;
  box-sizing: border-box;
}
.rank-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.rank-title { font-size: 15px; font-weight: 600; }
.rank-count { font-size: 13px; color: #86909c; }

/* 排名 */
.medal { font-size: 20px; line-height: 1; }
.rank-num { color: #86909c; font-weight: 500; }

/* 数据样式 */
.core-yes { color: #f56c6c; font-weight: 600; }
.core-no { color: #c0c4cc; }
.score-green { color: #67c23a; font-weight: 600; }
.score-blue { color: #409eff; }
.level-tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.level-good { color: #f56c6c; background: #fef0f0; }
.level-normal { color: #86909c; background: #f4f4f5; }

/* 页脚 */
.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
  font-size: 12px;
  color: #c0c4cc;
  flex-shrink: 0;
}
</style>
