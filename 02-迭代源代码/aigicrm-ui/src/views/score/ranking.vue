<template>
  <div class="score-ranking">
    <!-- 深蓝主视觉带(对齐 statistics/ana-hero 统一语言: 标题+周期筛选+操作内嵌) -->
    <div class="rank-hero">
      <div class="rh-top">
        <div class="rh-left">
          <h2 class="rh-title">积分排行</h2>
          <p class="rh-sub">按评分汇总展示个人与部门排行 · 数据周期 {{ selectedPeriod || '全部' }}</p>
        </div>
        <div class="rh-actions">
          <span class="rh-flbl">评分周期</span>
          <el-select v-model="selectedPeriod" size="small" class="rh-select" @change="loadData">
            <!-- 【2026-09-12】移除硬编码的"全部"：得分接口按周期等值过滤，空周期必然 0 条（避免给出无效选项） -->
            <el-option v-for="p in periodOptions" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
          <el-button size="small" class="rh-ghost" @click="showRules = true">积分规则</el-button>
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <el-tabs v-model="activeTab" class="rank-tabs">
        <el-tab-pane name="personal" label="个人排行" />
        <el-tab-pane name="dept" label="部门排行" />
      </el-tabs>
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
      <el-table :data="pagedPersonal" stripe style="width:100%" size="small" empty-text="本周期暂无个人积分记录，可在「积分录入」提交评分">
        <el-table-column label="排名" min-width="60" align="center">
          <template #default="{ row }"><span class="rank-num">{{ row.rank }}</span></template>
        </el-table-column>
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="officeName" label="办事处" min-width="100" />
        <el-table-column label="总积分" min-width="100" align="right" header-align="right">
          <template #default="{ row }"><strong>{{ row.totalScore }}分</strong></template>
        </el-table-column>
        <el-table-column label="文件数" min-width="80" align="right" header-align="right">
          <template #default="{ row }">{{ row.itemCount }}个</template>
        </el-table-column>
        <el-table-column label="趋势" min-width="80" align="center">
          <template #default="{ row }">
            <span :class="'trend trend-' + trendClass(row.trend)">{{ trendIcon(row.trend) }} {{ row.trend }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="personalList.length > pageSize" v-model:current-page="pageNum" :page-size="pageSize" :total="personalList.length" :page-sizes="pageSizes(pageSize)" layout="sizes, prev, pager, next, jumper" size="small" background @size-change="onSizeChange" style="justify-content:flex-end;margin-top:12px" />
      <div class="rank-footer">
        <span class="footer-note">同分时按文件数排序，文件数相同则按姓名排序</span>
        <!-- 2026-10-04 D15：移除“最近更新：渲染时刻”展示——/score/ranking 与 /score/ranking/stats
             均不返回数据时间字段，用 new Date() 属伪造时间（接口返回字段见后端 ScoreService.ranking/rankingStats） -->
      </div>
    </div>

    <div v-if="activeTab === 'dept'" class="table-area">
      <div class="rank-header">
        <span class="rank-title">部门排行</span>
      </div>
      <el-table :data="pagedDept" stripe style="width:100%" size="small" empty-text="本周期暂无部门积分记录，可在「积分录入」提交评分">
        <el-table-column label="排名" min-width="60" align="center">
          <template #default="{ row }"><span class="rank-num">{{ row.rank }}</span></template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" min-width="140" />
        <el-table-column label="总积分" min-width="100" align="right" header-align="right">
          <template #default="{ row }"><strong>{{ row.totalScore }}分</strong></template>
        </el-table-column>
        <el-table-column label="参与人数" min-width="100" align="right" header-align="right">
          <template #default="{ row }">{{ row.participantCount }}人</template>
        </el-table-column>
        <el-table-column label="人均积分" min-width="100" align="right" header-align="right">
          <template #default="{ row }">{{ row.avgScore }}分</template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="deptList.length > pageSize" v-model:current-page="pageNum" :page-size="pageSize" :total="deptList.length" :page-sizes="pageSizes(pageSize)" layout="sizes, prev, pager, next, jumper" size="small" background @size-change="onSizeChange" style="justify-content:flex-end;margin-top:12px" />
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
import { buildPeriodOptions, defaultRecordPeriod } from '@/utils/incentive-period'
import { pageSizes } from '@/utils/pagination'

const activeTab = ref('personal')
// 【2026-09-12】选项统一含「全年」（库内得分数据是 2026全年）；得分接口按周期等值过滤，
// 不支持"全部"，故这里去掉「全部」选项，并把默认周期设为当年「全年」（原来默认空串 ⇒ 必然 0 条）。
const periodOptions = buildPeriodOptions().filter((option) => option.value)
const selectedPeriod = ref(defaultRecordPeriod())
const personalList = ref([])
const deptList = ref([])
const stats = ref({})
const pageNum = ref(1)
const pageSize = ref(15)
// 切换每页条数后回到第 1 页
const onSizeChange = (size) => { pageSize.value = size; pageNum.value = 1 }
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
  } catch {
    // 【D13 2026-10-04】失败由 request 拦截器统一提示（Promise.all 任一失败即整体失败），
    // 本地保留旧数据不再叠加笼统文案
  }
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
/* —— 深蓝主视觉带(统一语言, 对齐 statistics/.ana-hero) —— */
.rank-hero {
  background: var(--hero-gradient);
  border-radius: 12px; padding: 16px 24px 18px; margin-bottom: 12px; flex-shrink: 0;
  box-shadow: var(--hero-shadow);
}
.rh-top { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.rh-title { font-size: 20px; font-weight: 700; color: #fff; letter-spacing: .3px; margin: 0 0 4px; }
.rh-sub { font-size: 13px; color: rgba(255, 255, 255, .72); margin: 0; }
.rh-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.rh-flbl { font-size: 12px; color: rgba(255, 255, 255, .6); }
.rh-select { width: 140px }
.rh-select :deep(.el-select__wrapper) {
  background: rgba(255, 255, 255, .14);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .28) inset;
}
.rh-select :deep(.el-select__placeholder),
.rh-select :deep(.el-select__selected-item) { color: #fff }
.rh-ghost { background: rgba(255, 255, 255, .14); color: #fff; border: 1px solid rgba(255, 255, 255, .3); }
.rh-ghost:hover, .rh-ghost:focus { background: rgba(255, 255, 255, .24); color: #fff }
.filter-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.rank-tabs { flex: 1; }
.rank-tabs :deep(.el-tabs__header) { margin: 0; }
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 12px; }
.stat-card { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 18px 22px; }
.stat-label { font-size: 13px; color: var(--text-muted); margin-bottom: 8px; }
/* 【B6 三轮 2026-10-09】大数字唯一档 --font-num-hero(30px)；【B5/B6】色统一 --brand-navy（原 #1d2129 近黑，与工作台/统计页深蓝三轨并存） */
.stat-num { font-size: var(--font-num-hero); font-weight: 700; color: var(--brand-navy); line-height: 1.1; }
.stat-desc { font-size: 12px; color: #6b7280; margin-top: 6px; }
.table-area { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.table-area :deep(.el-table) { flex: 1; }
.table-area :deep(.el-table td.el-table__cell), .table-area :deep(.el-table th.el-table__cell) { height: 40px; padding: 4px 0; box-sizing: border-box; }
.rank-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.rank-title { font-size: 15px; font-weight: 600; }
.rank-count { color: var(--text-muted); font-size: 13px; }
.rank-num { color: var(--text-muted); font-weight: 500; }
.trend { font-size: 13px; font-weight: 500; }
/* 【UI 整改 2026-10-08】趋势色重排：「领先」改金系（var(--chart-amber-deep)，白底 5.3:1），红色仅保留危险语义 */
.trend-lead { color: var(--chart-amber-deep); }
.trend-up { color: #1d4ed8; }
.trend-chase { color: var(--el-color-warning); }
.rank-footer { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-top: 1px solid #ebeef5; flex-shrink: 0; }
/* 2026-10-04 D15：移除"最近更新"假时间后，.footer-time 选择器一并清理（模板已无该 class） */
.footer-note { color: #6b7280; font-size: 12px; }
</style>
