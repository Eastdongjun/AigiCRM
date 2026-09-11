<template>
  <div class="score-ranking">
    <div class="header-card">
      <div class="header-left">
        <h2 class="header-title">积分排行</h2>
        <p class="header-desc">按评分汇总展示个人与部门排行</p>
        <p class="header-period">数据周期：{{ selectedPeriod || '全部' }}</p>
      </div>
      <div class="header-right">
        <el-button @click="showRules = true">积分规则</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-tabs v-model="activeTab" class="rank-tabs">
        <el-tab-pane name="personal" label="个人排行" />
        <el-tab-pane name="dept" label="部门排行" />
      </el-tabs>
      <div class="period-select">
        <span>评分周期</span>
        <el-select v-model="selectedPeriod" style="width:140px" @change="loadData">
          <el-option label="全部" value="" />
          <el-option v-for="p in periodOptions" :key="p" :label="p" :value="p" />
        </el-select>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">参与人数</div>
        <div class="stat-num">{{ stats.participantCount || 0 }}</div>
        <div class="stat-desc">本期有积分记录的人员</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">参与部门</div>
        <div class="stat-num">{{ stats.deptCount || 0 }}</div>
        <div class="stat-desc">本期有积分记录的部门</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">最高积分</div>
        <div class="stat-num">{{ stats.topScore || 0 }}</div>
        <div class="stat-desc">{{ stats.topScoreName || '-' }} · {{ stats.topItemCount || 0 }}个文件</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">文件总数</div>
        <div class="stat-num">{{ stats.totalItemCount || 0 }}</div>
        <div class="stat-desc">当前周期累计积分文件数</div>
      </div>
    </div>

    <div v-if="activeTab === 'personal'" class="table-area">
      <div class="rank-header">
        <span class="rank-title">个人排行</span>
        <span class="rank-count">{{ personalList.length }}人</span>
      </div>
      <el-table :data="pagedPersonal" stripe style="width:100%" size="small" empty-text="暂无数据">
        <el-table-column label="排名" min-width="60" align="center">
          <template #default="{ row }"><span class="rank-num">{{ row.rank }}</span></template>
        </el-table-column>
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="officeName" label="办事处" min-width="100" />
        <el-table-column label="总积分" min-width="100" align="center">
          <template #default="{ row }"><strong>{{ row.totalScore }}分</strong></template>
        </el-table-column>
        <el-table-column label="文件数" min-width="80" align="center">
          <template #default="{ row }">{{ row.itemCount }}个</template>
        </el-table-column>
        <el-table-column label="趋势" min-width="80" align="center">
          <template #default="{ row }">
            <span :class="'trend trend-' + trendClass(row.trend)">{{ trendIcon(row.trend) }} {{ row.trend }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="personalList.length > pageSize" v-model:current-page="pageNum" :page-size="pageSize" :total="personalList.length" layout="prev, pager, next" size="small" background style="justify-content:flex-end;margin-top:12px" />
      <div class="rank-footer">
        <span class="footer-note">同分时按文件数排序，文件数相同则按姓名排序</span>
        <span class="footer-time">最近更新：{{ new Date().toLocaleString() }}</span>
      </div>
    </div>

    <div v-if="activeTab === 'dept'" class="table-area">
      <div class="rank-header">
        <span class="rank-title">部门排行</span>
      </div>
      <el-table :data="pagedDept" stripe style="width:100%" size="small" empty-text="暂无数据">
        <el-table-column label="排名" min-width="60" align="center">
          <template #default="{ row }"><span class="rank-num">{{ row.rank }}</span></template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" min-width="140" />
        <el-table-column label="总积分" min-width="100" align="center">
          <template #default="{ row }"><strong>{{ row.totalScore }}分</strong></template>
        </el-table-column>
        <el-table-column label="参与人数" min-width="100" align="center">
          <template #default="{ row }">{{ row.participantCount }}人</template>
        </el-table-column>
        <el-table-column label="人均积分" min-width="100" align="center">
          <template #default="{ row }">{{ row.avgScore }}分</template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="deptList.length > pageSize" v-model:current-page="pageNum" :page-size="pageSize" :total="deptList.length" layout="prev, pager, next" size="small" background style="justify-content:flex-end;margin-top:12px" />
    </div>

    <el-dialog v-model="showRules" title="积分规则" width="500px">
      <p>1. 每半年对所有同事进行产品知识评分</p>
      <p>2. 每项评分不超出该行设定的区间范围</p>
      <p>3. 总积分 = 所有评分项得分之和</p>
      <p>4. 排名按总积分降序，同分按评分项数降序</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getScoreRanking, getScoreRankingStats } from '@/api/score'

const activeTab = ref('personal')
const periodOptions = (() => {
  const list = []
  for (let y = 2030; y >= 2025; y--) list.push(y + '下半年', y + '上半年')
  return list
})()
const selectedPeriod = ref('')
const personalList = ref([])
const deptList = ref([])
const stats = ref({})
const pageNum = ref(1)
const pageSize = ref(15)
const pagedPersonal = computed(() => personalList.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value))
const pagedDept = computed(() => deptList.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value))
const showRules = ref(false)

async function loadData() {
  pageNum.value = 1
  try {
    const params = { period: selectedPeriod.value }
    const [personal, dept, stat] = await Promise.all([
      getScoreRanking({ ...params, type: 'personal' }),
      getScoreRanking({ ...params, type: 'dept' }),
      getScoreRankingStats(params),
    ])
    personalList.value = personal.data || []
    deptList.value = dept.data || []
    stats.value = stat.data || {}
  } catch {}
}

function trendClass(value) {
  if (value === '领先') return 'lead'
  if (value === '上升') return 'up'
  return 'chase'
}

function trendIcon(value) {
  return value === '领先' || value === '上升' ? '^' : 'v'
}

watch(activeTab, loadData)
onMounted(loadData)
</script>

<style scoped>
.score-ranking { min-height: calc(100vh - 100px); display: flex; flex-direction: column; }
.header-card { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 24px 28px; display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.header-title { font-size: 18px; font-weight: 600; color: #333; margin: 0 0 4px; }
.header-desc { color: #86909c; font-size: 14px; margin: 0 0 2px; }
.header-period { color: #c0c4cc; font-size: 13px; margin: 0; }
.filter-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.rank-tabs { flex: 1; }
.rank-tabs :deep(.el-tabs__header) { margin: 0; }
.period-select { display: flex; align-items: center; gap: 8px; color: #666; font-size: 14px; }
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 12px; }
.stat-card { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 18px 22px; }
.stat-label { font-size: 13px; color: #86909c; margin-bottom: 8px; }
.stat-num { font-size: 32px; font-weight: 700; color: #1d2129; line-height: 1.1; }
.stat-desc { font-size: 12px; color: #c0c4cc; margin-top: 6px; }
.table-area { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.table-area :deep(.el-table) { flex: 1; }
.table-area :deep(.el-table td.el-table__cell), .table-area :deep(.el-table th.el-table__cell) { height: 40px; padding: 4px 0; box-sizing: border-box; }
.rank-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.rank-title { font-size: 15px; font-weight: 600; }
.rank-count { color: #86909c; font-size: 13px; }
.rank-num { color: #86909c; font-weight: 500; }
.trend { font-size: 13px; font-weight: 500; }
.trend-lead { color: #f56c6c; }
.trend-up { color: #409eff; }
.trend-chase { color: #e6a23c; }
.rank-footer { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-top: 1px solid #ebeef5; flex-shrink: 0; }
.footer-note, .footer-time { color: #c0c4cc; font-size: 12px; }
</style>
