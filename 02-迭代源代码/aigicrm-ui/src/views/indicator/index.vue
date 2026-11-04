<template>
  <div v-loading="loading" class="indicator-page">
    <!-- 标题：与生产菜单(id=108)、router meta.title 统一为「核心客户六项指标」 -->
    <h3 class="page-title">核心客户六项指标</h3>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="sc-label">覆盖客户数</div>
        <div class="sc-num blue">{{ data.coveredCount || 0 }}</div>
        <div class="sc-desc">有五项指标评估的客户</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">真核心</div>
        <div class="sc-num green">{{ data.coreCount || 0 }}</div>
        <!-- 【2026-11-03 门槛配置化】门槛数字随接口回传(管理员可改), 不落死值 -->
        <div class="sc-desc">年度开票、回款均≥{{ data.coreMajorWan || 100 }}万</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">争核心</div>
        <div class="sc-num yellow">{{ data.quasiCount || 0 }}</div>
        <div class="sc-desc">均≥{{ data.coreMinorWan || 50 }}万，未双达真核心门槛</div>
      </div>
      <div class="stat-card">
        <div class="sc-label">指标平均分</div>
        <div class="sc-num green">{{ data.avgScore || 0 }}</div>
        <div class="sc-desc">五项指标总分 / 客户数</div>
      </div>
    </div>

    <div class="table-area">
      <div class="rank-header">
        <span class="rank-title">客户排行</span>
        <div class="rank-tools">
          <!-- 【裁决 2026-10-10】核心类型筛选: 真核心/争核心 -->
          <el-select v-model="coreFilter" size="small" style="width:140px">
            <el-option label="全部核心类型" value="" />
            <el-option label="真核心" value="core" />
            <el-option label="争核心" value="quasi" />
          </el-select>
          <span class="rank-count">{{ filteredRanking.length }}个客户</span>
        </div>
      </div>
      <el-table :data="pagedRanking" stripe style="width:100%" size="small" empty-text="暂无数据，请先在客户详情中录入核心客户六项指标">
        <el-table-column label="排名" min-width="55" align="center">
          <template #default="{ $index }">
            <!-- 【2026-11-05 用户令补翻页】分页后排名=绝对榜次(跨页连续), 非页内序号 -->
            <span v-if="rankOf($index) === 1" class="medal">1</span>
            <span v-else-if="rankOf($index) === 2" class="medal">2</span>
            <span v-else-if="rankOf($index) === 3" class="medal">3</span>
            <span v-else class="rank-num">{{ rankOf($index) }}</span>
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
        <el-table-column label="年度开票" min-width="90" align="center">
          <template #default="{ row }">{{ row.invoiceTotal || 0 }}万</template>
        </el-table-column>
        <el-table-column label="年度回款" min-width="90" align="center">
          <template #default="{ row }">{{ row.paymentTotal || 0 }}万</template>
        </el-table-column>
        <el-table-column label="核心类型" min-width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="row.coreType === 'core' ? 'success' : row.coreType === 'quasi' ? 'warning' : 'info'" size="small">
              {{ row.coreType === 'core' ? '真核心' : row.coreType === 'quasi' ? '争核心' : '—' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <!-- 【2026-11-05 用户令】补翻页: 一次拉全量+前端切片(与激励查询/案例库同口径), 页大小可切 -->
      <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="filteredRanking.length"
        :page-sizes="pageSizes(pageSize)" layout="sizes, prev, pager, next, total" size="small" background
        style="margin-top:10px;justify-content:flex-end" @size-change="onSizeChange" />
      <div class="table-footer">
        <!-- 2026-10-04 D15：移除“更新时间: 渲染时刻”展示——/statistics/indicator-stats 不返回
             任何数据时间字段（见后端 StatisticsQueryService.indicatorStats），new Date() 属伪造时间 -->
        <span>按核心五项总分从高到低排序，0分客户不展示；核心类型按阶段开票回款年度门槛判定（2026-10-10 起，五项评分不参与）</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getIndicatorStats } from '@/api/statistics'
import { paginate, pageSizes } from '@/utils/pagination'

const data = ref({})
const customerRanking = computed(() => data.value.customerRanking || [])
// 【裁决 2026-10-10】核心类型筛选(真核心/争核心); 筛选后排名序号仍按原榜次呈现
const coreFilter = ref('')
const filteredRanking = computed(() =>
  !coreFilter.value ? customerRanking.value : customerRanking.value.filter(row => row.coreType === coreFilter.value))

// 【2026-11-05 用户令补翻页】前端切片分页; 筛选变化回第一页; 排名=绝对榜次(跨页连续)
const pageNum = ref(1)
const pageSize = ref(20)
const pagedRanking = computed(() => paginate(filteredRanking.value, pageNum.value, pageSize.value))
const rankOf = (localIndex) => (pageNum.value - 1) * pageSize.value + localIndex + 1
const onSizeChange = (size) => { pageSize.value = size; pageNum.value = 1 }
watch(coreFilter, () => { pageNum.value = 1 })

const loading = ref(false)
const loadData = async () => {
  loading.value = true
  try { const r = await getIndicatorStats(); data.value = r.data || {} } catch {}
  finally { loading.value = false }
}
onMounted(loadData)
</script>

<style scoped>
.indicator-page { min-height: calc(100vh - 100px); display: flex; flex-direction: column; }
.page-title { font-size: 18px; font-weight: 600; color: #333; margin: 0 0 12px; }


/* 统计卡片 */
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 12px; }
/* 达标率卡换成争核心卡后仍是 4 列; 排行工具行 */
.rank-tools { display: flex; align-items: center; gap: 12px; }
.stat-card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 20px 24px; }
.sc-label { font-size: 13px; color: var(--text-muted); margin-bottom: 4px; }
/* 【B6 三轮 2026-10-09】大数字唯一档 --font-num-hero(30px)，收敛 36px 游离档 */
.sc-num { font-size: var(--font-num-hero); font-weight: 700; line-height: 1.2; }
.sc-num.blue { color: var(--el-color-primary); } .sc-num.green { color: var(--el-color-success); } .sc-num.yellow { color: var(--el-color-warning); }
.sc-desc { font-size: 12px; color: var(--text-placeholder); margin-top: 4px; }

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
.rank-count { font-size: 13px; color: var(--text-muted); }

/* 排名 */
.medal { font-size: 20px; line-height: 1; }
.rank-num { color: var(--text-muted); font-weight: 500; }

/* 数据样式 */
.core-yes { color: var(--el-color-danger); font-weight: 600; }
.core-no { color: var(--text-placeholder); }
.score-green { color: var(--el-color-success); font-weight: 600; }
.score-blue { color: var(--el-color-primary); }
.level-tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.level-good { color: var(--el-color-danger); background: #fef0f0; }
.level-normal { color: var(--text-muted); background: #f4f4f5; }

/* 页脚 */
.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
  font-size: 12px;
  color: var(--text-placeholder);
  flex-shrink: 0;
}
</style>
