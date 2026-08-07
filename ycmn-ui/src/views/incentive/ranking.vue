<template>
  <div class="incentive-ranking">
    <!-- 顶部卡片 -->
    <div class="header-card">
      <div class="header-left">
        <h2 class="header-title">激励排行</h2>
        <p class="header-desc">按激励上传汇总展示个人与部门排行</p>
        <p class="header-period">数据周期：{{ selectedPeriod || '全部' }}</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="handleExport">导出当前排行</el-button>
      </div>
    </div>

    <!-- Tab + 周期选择 -->
    <div class="filter-bar">
      <el-tabs v-model="activeTab" class="rank-tabs">
        <el-tab-pane name="personal">
          <template #label><span>&#x1F3C6; 个人排行</span></template>
        </el-tab-pane>
        <el-tab-pane name="dept">
          <template #label><span>&#x1F3C5; 部门排行</span></template>
        </el-tab-pane>
      </el-tabs>
      <div class="period-select">
        <span>激励周期</span>
        <el-select v-model="selectedPeriod" style="width:140px" @change="loadData">
          <el-option label="全部" value="" />
          <el-option label="2026上半年" value="2026上半年" />
          <el-option label="2026下半年" value="2026下半年" />
          <el-option label="2025上半年" value="2025上半年" />
          <el-option label="2025下半年" value="2025下半年" />
        </el-select>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label"><el-icon><User /></el-icon> 参与人数</div>
        <div class="stat-num">{{ stats.participantCount || 0 }}</div>
        <div class="stat-desc">本期有激励记录的人员</div>
      </div>
      <div class="stat-card">
        <div class="stat-label"><el-icon><OfficeBuilding /></el-icon> 参与部门</div>
        <div class="stat-num">{{ stats.deptCount || 0 }}</div>
        <div class="stat-desc">本期有激励记录的部门</div>
      </div>
      <div class="stat-card">
        <div class="stat-label"><el-icon><Trophy /></el-icon> 最高学分</div>
        <div class="stat-num">{{ stats.topScore || 0 }}</div>
        <div class="stat-desc">{{ stats.topScoreName || '—' }} · {{ stats.topRecordCount || 0 }}个文件</div>
      </div>
      <div class="stat-card">
        <div class="stat-label"><el-icon><Document /></el-icon> 文件总数</div>
        <div class="stat-num">{{ stats.totalRecordCount || 0 }}</div>
        <div class="stat-desc">当前周期累计激励文件数</div>
      </div>
    </div>

    <!-- 个人排行 -->
    <div v-if="activeTab === 'personal'" class="rank-section">
      <div class="rank-header">
        <span class="rank-title"><el-icon><User /></el-icon> 个人排行</span>
        <span class="rank-count">{{ personalList.length }}人</span>
      </div>
      <el-table :data="pagedPersonal" style="width:100%" empty-text="暂无数据">
        <el-table-column label="排名" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.rank === 1" class="medal gold">&#x1F947;</span>
            <span v-else-if="row.rank === 2" class="medal silver">&#x1F948;</span>
            <span v-else-if="row.rank === 3" class="medal bronze">&#x1F949;</span>
            <span v-else class="rank-num">{{ row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="realName" label="姓名" width="140" />
        <el-table-column prop="officeName" label="办事处" width="120" />
        <el-table-column label="总学分" width="120" align="center">
          <template #default="{ row }"><strong>{{ row.totalScore }}分</strong></template>
        </el-table-column>
        <el-table-column label="上传数" width="100" align="center">
          <template #default="{ row }">{{ row.recordCount || 0 }}个</template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="personalList.length > pageSize" v-model:current-page="pageNum" :page-size="pageSize" :total="personalList.length" layout="prev, pager, next" small background style="justify-content:flex-end;margin-top:12px" />
      <div class="rank-footer">
        <span class="footer-note">按总学分降序排列，学分相同按上传数排序</span>
        <span class="footer-time">最近更新：{{ new Date().toLocaleString() }}</span>
      </div>
    </div>

    <!-- 部门排行 -->
    <div v-if="activeTab === 'dept'" class="rank-section">
      <div class="rank-header">
        <span class="rank-title"><el-icon><OfficeBuilding /></el-icon> 部门排行</span>
        <span class="rank-count">{{ deptList.length }}个部门</span>
      </div>
      <el-table :data="pagedDept" style="width:100%" empty-text="暂无数据">
        <el-table-column label="排名" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.rank <= 3" class="medal" :class="['gold','silver','bronze'][row.rank-1]">
              {{ ['&#x1F947;','&#x1F948;','&#x1F949;'][row.rank-1] }}
            </span>
            <span v-else class="rank-num">{{ row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" width="160" />
        <el-table-column label="总学分" width="120" align="center">
          <template #default="{ row }"><strong>{{ row.totalScore }}分</strong></template>
        </el-table-column>
        <el-table-column label="参与人数" width="120" align="center">
          <template #default="{ row }">{{ row.participantCount }}人</template>
        </el-table-column>
        <el-table-column label="人均学分" width="120" align="center">
          <template #default="{ row }">{{ row.avgScore }}分</template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="deptList.length > pageSize" v-model:current-page="pageNum" :page-size="pageSize" :total="deptList.length" layout="prev, pager, next" small background style="justify-content:flex-end;margin-top:12px" />
      <div class="rank-footer">
        <span class="footer-note">按部门总学分降序排列，人均学分 = 总学分 / 参与人数</span>
        <span class="footer-time">最近更新：{{ new Date().toLocaleString() }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getIncentiveRanking, getIncentiveRankingStats } from '@/api/incentive'

const activeTab = ref('personal')
const selectedPeriod = ref('')
const personalList = ref([])
const deptList = ref([])
const stats = ref({})
// 排行分页（前端切片，团队增长后仍可浏览）
const pageNum = ref(1)
const pageSize = ref(15)
const pagedPersonal = computed(() => personalList.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value))
const pagedDept = computed(() => deptList.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value))

const loadData = async () => {
  pageNum.value = 1
  try {
    const params = { period: selectedPeriod.value }
    const [r1, r2, r3] = await Promise.all([
      getIncentiveRanking({ ...params, type: 'personal' }),
      getIncentiveRanking({ ...params, type: 'dept' }),
      getIncentiveRankingStats(params),
    ])
    personalList.value = r1.data || []
    deptList.value = r2.data || []
    stats.value = r3.data || {}
  } catch {}
}

const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

onMounted(loadData)
</script>

<style scoped>
.incentive-ranking { padding: 0; max-width: 1440px; }

/* 顶部卡片 */
.header-card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 28px 32px;
  display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px;
}
.header-title { font-size: 24px; font-weight: 700; margin: 0 0 4px; }
.header-desc { color: #86909c; font-size: 14px; margin: 0 0 2px; }
.header-period { color: #c0c4cc; font-size: 13px; margin: 0; }
.header-right { display: flex; gap: 12px; }

/* Tab + 周期 */
.filter-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.rank-tabs { flex: 1; }
.rank-tabs :deep(.el-tabs__header) { margin: 0; }
.period-select { display: flex; align-items: center; gap: 8px; color: #666; font-size: 14px; }

/* 统计卡片 */
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.stat-card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 20px 24px; }
.stat-label { font-size: 13px; color: #86909c; display: flex; align-items: center; gap: 4px; margin-bottom: 8px; }
.stat-num { font-size: 32px; font-weight: 700; color: #1d2129; line-height: 1.1; }
.stat-desc { font-size: 12px; color: #c0c4cc; margin-top: 6px; }

/* 排行区 */
.rank-section { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 24px; }
.rank-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.rank-title { font-size: 15px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
.rank-count { color: #86909c; font-size: 13px; }

/* 排名 */
.medal { font-size: 20px; }
.rank-num { color: #86909c; font-weight: 500; }

/* 页脚 */
.rank-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 16px; padding-top: 12px; border-top: 1px solid #f0f0f0; }
.footer-note { color: #c0c4cc; font-size: 12px; }
.footer-time { color: #c0c4cc; font-size: 12px; }
</style>
