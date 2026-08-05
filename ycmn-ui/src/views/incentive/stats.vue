<template>
  <div class="incentive-stats">
    <!-- 概览卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="sc-top"><span class="sc-label"><el-icon><List /></el-icon> 激励事项数</span></div>
        <div class="sc-num purple">{{ totalCategories }}</div>
        <div class="sc-desc">从激励配置读取</div>
      </div>
      <div class="stat-card">
        <div class="sc-top"><span class="sc-label"><el-icon><Share /></el-icon> 可共享项数</span></div>
        <div class="sc-num green">{{ shareableCount }}</div>
        <div class="sc-desc">名称含"可共享"的项</div>
      </div>
      <div class="stat-card">
        <div class="sc-top"><span class="sc-label"><el-icon><Document /></el-icon> 本周期文件</span></div>
        <div class="sc-num blue">{{ totalRecords }}</div>
        <div class="sc-desc">{{ userStore.realName }} · {{ period }} 共上传 {{ totalRecords }} 个文件</div>
      </div>
      <div class="stat-card">
        <div class="sc-top"><span class="sc-label"><el-icon><Medal /></el-icon> 总学分</span></div>
        <div class="sc-num green">{{ totalScore }}</div>
        <div class="sc-desc">每个文件按子类分值计分</div>
      </div>
    </div>

    <!-- Tab栏 -->
    <div class="tab-bar">
      <div class="tabs">
        <span class="tab-item" :class="{ active: activeTab === 'table' }" @click="activeTab = 'table'">
          <el-icon><Grid /></el-icon> 激励统计表
        </span>
        <span class="tab-item" :class="{ active: activeTab === 'rank' }" @click="activeTab = 'rank'">
          <el-icon><User /></el-icon> 个人排行
        </span>
        <span class="tab-item" :class="{ active: activeTab === 'dept' }" @click="activeTab = 'dept'">
          <el-icon><OfficeBuilding /></el-icon> 部门排行
        </span>
      </div>
      <el-select v-model="period" style="width:150px" size="small" @change="loadData">
        <el-option v-for="p in periodOptions" :key="p" :label="p" :value="p" />
      </el-select>
    </div>

    <!-- 激励统计表 -->
    <div v-if="activeTab === 'table'" class="card">
      <div class="table-header">
        <span>共 <strong>{{ totalCategories }}</strong> 项激励规则</span>
      </div>

      <table class="stats-table">
        <thead>
          <tr>
            <th class="col-cat">激励大类</th>
            <th class="col-sub">激励细分</th>
            <th class="col-score">激励学分</th>
            <th class="col-count">数量</th>
            <th class="col-stage">安装前</th>
            <th class="col-stage">安装中</th>
            <th class="col-stage">安装后</th>
            <th class="col-total">总学分</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="(group, gi) in groupedData" :key="group.parentId">
            <tr v-for="(child, ci) in group.children" :key="child.id">
              <td v-if="ci === 0" :rowspan="group.children.length" class="col-cat">
                <span class="cat-label" :class="catColors[gi % catColors.length]">
                  <el-icon v-if="group.icon === 'location'"><Location /></el-icon>
                  <el-icon v-else-if="group.icon === 'chat'"><ChatDotSquare /></el-icon>
                  <el-icon v-else-if="group.icon === 'document'"><Document /></el-icon>
                  <el-icon v-else><Box /></el-icon>
                  {{ group.parentName }}
                </span>
              </td>
              <td class="col-sub">{{ child.categoryName }}</td>
              <td class="col-score">{{ child.score }}</td>
              <td class="col-count">{{ child.uploadCount }}</td>
              <td class="col-stage">
                <span :class="child.preCount > 0 ? 'stage-active' : 'stage-zero'">{{ child.preCount }}</span>
              </td>
              <td class="col-stage">
                <span :class="child.midCount > 0 ? 'stage-active' : 'stage-zero'">{{ child.midCount }}</span>
              </td>
              <td class="col-stage">
                <span :class="child.postCount > 0 ? 'stage-active' : 'stage-zero'">{{ child.postCount }}</span>
              </td>
              <td class="col-total">
                <span :class="child.totalScore > 0 ? 'score-active' : 'score-zero'">{{ child.totalScore }}</span>
              </td>
            </tr>
          </template>
        </tbody>
        <tfoot>
          <tr class="total-row">
            <td colspan="2"><strong>合计</strong></td>
            <td><strong class="purple">{{ totalCategories }}项</strong></td>
            <td><strong>{{ tableTotalFiles }}</strong></td>
            <td><strong class="blue">{{ tableTotalPre }}</strong></td>
            <td><strong class="blue">{{ tableTotalMid }}</strong></td>
            <td><strong class="blue">{{ tableTotalPost }}</strong></td>
            <td><strong class="blue">{{ tableTotalScore }}</strong></td>
          </tr>
        </tfoot>
      </table>
    </div>

    <!-- 个人排行：团队激励统计矩阵（按模板：基础信息 + 激励政策分列） -->
    <div v-if="activeTab === 'rank'" class="card">
      <div class="table-header">
        <span class="matrix-title">{{ period || '全部周期' }} 团队激励统计</span>
        <span class="rank-count">{{ scoreboard.length }}人</span>
      </div>
      <el-table :data="scoreboard" style="width:100%" empty-text="暂无数据" class="rank-matrix"
        :header-cell-style="{ background: '#f7f9fc', color: '#1f2329', fontWeight: 600, borderBottom: '1px solid #e8ebf0' }"
        :row-style="{ height: '46px' }">
        <el-table-column label="排名" width="64" align="center">
          <template #default="{ row }">
            <span v-if="row.rank <= 3" :class="'rank-medal rank-medal-' + row.rank">{{ ['🥇','🥈','🥉'][row.rank - 1] }}</span>
            <span v-else class="rank-num">{{ row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column label="姓名" width="104">
          <template #default="{ row }">
            <div class="name-wrap">
              <span class="name-avatar" :class="'av-' + (row.rank <= 3 ? row.rank : 4)">{{ row.realName?.charAt(0) }}</span>
              <span :class="row.rank <= 3 ? 'name-top' : 'name-cell'">{{ row.realName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="激励总额" width="92" align="center">
          <template #default="{ row }"><span class="score-pill">{{ row.totalScore }}<em>分</em></span></template>
        </el-table-column>
        <template v-for="(parent, pi) in rankParents" :key="'p' + parent.id">
          <el-table-column :label="parent.categoryName" align="center" :class-name="'cat-group cat-group-' + (pi % 4)">
            <el-table-column v-for="child in parent.children" :key="child.id" :label="child.categoryName" min-width="64" align="center">
              <template #default="{ row }">
                <span v-if="(row.byCategory?.[child.id] || 0) > 0" class="cell-pill">{{ row.byCategory[child.id] }}</span>
                <span v-else class="cell-dash">—</span>
              </template>
            </el-table-column>
          </el-table-column>
        </template>
        <el-table-column label="上传数" min-width="70" align="center">
          <template #default="{ row }"><span class="num-cell">{{ row.recordCount || 0 }}</span></template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 部门排行 -->
    <div v-if="activeTab === 'dept'" class="card">
      <div class="table-header">
        <span>{{ period || '全部' }} 部门排行</span>
        <span class="rank-count">{{ deptList.length }}个部门</span>
      </div>
      <el-table :data="deptList" style="width:100%" empty-text="暂无数据">
        <el-table-column label="排名" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.rank <= 3" class="medal">{{ ['🥇','🥈','🥉'][row.rank - 1] }}</span>
            <span v-else class="rank-num">{{ row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" min-width="160" />
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
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getCategoryTree, getRecords, getCategoryStats, getScoreboard, getIncentiveRanking } from '@/api/incentive'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const period = ref('2026上半年')
const activeTab = ref('table')
const records = ref([])            // 个人记录(当前登录人) — 仅供顶部卡片
const catStat = ref({ byCategory: [], totalFiles: 0, totalScore: 0 })  // 全员聚合 — 供激励统计表
const categories = ref([])
const scoreboard = ref([])
const deptList = ref([])
const periodOptions = (() => {
  const list = []
  for (let y = 2030; y >= 2025; y--) { list.push(y + '下半年', y + '上半年') }
  return list
})()
const catColors = ['cat-blue', 'cat-orange', 'cat-red', 'cat-yellow']
const catIcons = { visit: 'location', exchange: 'chat', plan: 'document', trial: 'box' }

// 统计
const totalCategories = computed(() => categories.value.filter(c => c.parentId !== 0).length)
const shareableCount = computed(() => {
  return categories.value.filter(c => c.parentId !== 0 && c.categoryName?.includes('共享')).length
})
// 顶部卡片：个人口径（当前登录人本周期 文件数 / 学分）
const fileCountOf = (r) => {
  let n = 0
  if (r.filePreId) n++
  if (r.fileMidId) n++
  if (r.filePostId) n++
  if (n === 0 && r.fileId) n = 1
  return n
}
const totalRecords = computed(() => records.value.reduce((s, r) => s + fileCountOf(r), 0))
const totalScore = computed(() => records.value.reduce((s, r) => s + (r.score || 0), 0))

// 激励统计表合计：全员口径
const tableTotalFiles = computed(() => catStat.value.totalFiles || 0)
const tableTotalScore = computed(() => catStat.value.totalScore || 0)
const tableTotalPre = computed(() => catStat.value.totalPre || 0)
const tableTotalMid = computed(() => catStat.value.totalMid || 0)
const tableTotalPost = computed(() => catStat.value.totalPost || 0)

// 激励统计表：全员口径 categoryId -> { fileCount, totalScore, preCount, midCount, postCount }
const catStatMap = computed(() => {
  const m = {}
  for (const s of catStat.value.byCategory || []) m[s.categoryId] = s
  return m
})

// 个人排行矩阵表头：按大类分组的子分类列（模板：激励政策分列）
const rankParents = computed(() => {
  return categories.value
    .filter(c => c.parentId === 0)
    .map(parent => ({ id: parent.id, categoryName: parent.categoryName, children: categories.value.filter(c => c.parentId === parent.id) }))
    .filter(g => g.children.length > 0)
})

// 分组数据：按大类分组，统计每个子类的文件数(=学分数量)、总学分与阶段分布（按数据范围）
const groupedData = computed(() => {
  const parents = categories.value.filter(c => c.parentId === 0)
  return parents.map((parent) => {
    const children = categories.value.filter(c => c.parentId === parent.id).map(child => ({
      ...child,
      uploadCount: catStatMap.value[child.id]?.fileCount || 0,
      totalScore: catStatMap.value[child.id]?.totalScore || 0,
      preCount: catStatMap.value[child.id]?.preCount || 0,
      midCount: catStatMap.value[child.id]?.midCount || 0,
      postCount: catStatMap.value[child.id]?.postCount || 0,
    }))
    return {
      parentId: parent.id,
      parentName: parent.categoryName,
      icon: catIcons[parent.categoryCode] || 'box',
      children,
    }
  }).filter(g => g.children.length > 0)
})

const loadData = async () => {
  try {
    const catRes = await getCategoryTree()
    const flat = []
    const flatten = (list) => {
      for (const item of list) {
        flat.push({ ...item, children: undefined })
        if (item.children) flatten(item.children)
      }
    }
    flatten(catRes.data || [])
    categories.value = flat
  } catch {}

  try {
    // 顶部卡片：当前登录人的个人记录
    const res = await getRecords({ period: period.value })
    records.value = res.data || []
  } catch {}

  try {
    // 激励统计表：全员聚合
    const res = await getCategoryStats({ period: period.value })
    catStat.value = res.data || { byCategory: [], totalFiles: 0, totalScore: 0 }
  } catch {}

  try {
    const res = await getScoreboard({ period: period.value })
    scoreboard.value = res.data || []
  } catch {}

  try {
    const res = await getIncentiveRanking({ type: 'dept', period: period.value })
    deptList.value = res.data || []
  } catch {}
}

watch(period, loadData)
onMounted(loadData)
</script>

<style scoped>
.incentive-stats { }

/* 概览卡片 */
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.stat-card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 20px 24px; }
.sc-top { margin-bottom: 4px; }
.sc-label { font-size: 13px; color: #86909c; display: inline-flex; align-items: center; gap: 4px; }
.sc-num { font-size: 36px; font-weight: 700; line-height: 1.2; }
.sc-num.purple { color: #9b59b6; }
.sc-num.green { color: #67c23a; }
.sc-num.blue { color: #409eff; }
.sc-desc { font-size: 12px; color: #c0c4cc; margin-top: 4px; }

/* Tab */
.tab-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.tabs { display: flex; gap: 0; }
.tab-item { display: inline-flex; align-items: center; gap: 4px; padding: 10px 28px; font-size: 14px; cursor: pointer; color: #666; border: 1px solid #ebeef5; background: #fafafa; margin-left: -1px; }
.tab-item:first-child { border-radius: 8px 0 0 8px; margin-left: 0; }
.tab-item:last-child { border-radius: 0 8px 8px 0; }
.tab-item.active { background: #ecf5ff; color: #409eff; font-weight: 600; border-color: #409eff; z-index: 1; position: relative; }

/* 卡片 */
.card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 24px; }
.table-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; font-size: 14px; color: #86909c; }

/* 统计表 */
.stats-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.stats-table th { text-align: left; padding: 12px 16px; color: #f56c6c; font-weight: 500; border-bottom: 2px solid #f56c6c; background: #fff; }
.stats-table td { padding: 14px 16px; border-bottom: 1px solid #f5f5f5; color: #333; }
.stats-table tbody tr:hover { background: #fafbfc; }

.col-cat { width: 180px; vertical-align: middle; border-right: 1px solid #f0f0f0; }
.col-sub { }
.col-score { width: 100px; text-align: center; }
.col-count { width: 80px; text-align: center; }
.col-stage { width: 80px; text-align: center; }
.col-total { width: 100px; text-align: center; }

/* 大类标签 */
.cat-label { display: inline-flex; align-items: center; gap: 4px; font-size: 14px; font-weight: 600; }
.cat-blue { color: #409eff; }
.cat-orange { color: #e6a23c; }
.cat-red { color: #f56c6c; }
.cat-yellow { color: #e6a23c; }

/* 学分数字 */
.score-active { color: #409eff; font-weight: 600; }
.score-zero { color: #c0c4cc; }
.stage-active { color: #10b981; font-weight: 600; }
.stage-zero { color: #c0c4cc; }
/* 个人排行矩阵（排行榜风格） */
.matrix-title { font-size: 16px; font-weight: 700; color: #1f2329; }
.rank-matrix.el-table { --el-table-border-color: #eceff3; --el-table-header-bg-color: #f7f9fc; border-radius: 10px; overflow: hidden; }
.rank-matrix .el-table__row:hover > td { background: #f5f9ff !important; }
.rank-matrix .el-table__body td { padding: 5px 0; }
/* 排名：前三渐变奖牌底 */
.rank-medal {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  font-size: 15px;
  box-shadow: 0 2px 6px rgba(0,0,0,.12);
}
.rank-medal-1 { background: linear-gradient(135deg, #fbbf24, #f59e0b); }
.rank-medal-2 { background: linear-gradient(135deg, #e2e8f0, #cbd5e1); }
.rank-medal-3 { background: linear-gradient(135deg, #fcd7b6, #d97706); }
.rank-num { color: #94a3b8; font-weight: 600; font-size: 13px; }
/* 姓名：首字头像 + 加粗 */
.name-wrap { display: flex; align-items: center; gap: 8px; }
.name-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}
.av-1 { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.av-2 { background: linear-gradient(135deg, #94a3b8, #cbd5e1); }
.av-3 { background: linear-gradient(135deg, #d97706, #f59e0b); }
.av-4 { background: linear-gradient(135deg, #3b82f6, #6366f1); }
.name-cell { font-weight: 500; color: #1f2329; }
.name-top { font-weight: 700; color: #1f2329; }
/* 大类分组表头：左侧色条区分 */
.cat-group-0 .el-table__header th { background: rgba(64,158,255,.07) !important; box-shadow: inset 3px 0 0 #3b82f6; }
.cat-group-1 .el-table__header th { background: rgba(249,115,22,.06) !important; box-shadow: inset 3px 0 0 #f97316; }
.cat-group-2 .el-table__header th { background: rgba(139,92,246,.06) !important; box-shadow: inset 3px 0 0 #8b5cf6; }
.cat-group-3 .el-table__header th { background: rgba(16,185,129,.06) !important; box-shadow: inset 3px 0 0 #10b981; }
/* 品类单元格：绿色徽章 */
.cell-pill {
  display: inline-block;
  min-width: 26px;
  padding: 2px 8px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(16,185,129,.14), rgba(16,185,129,.08));
  color: #0d9e6e;
  font-weight: 700;
  font-size: 13px;
  text-align: center;
}
.cell-dash { color: #d5dae1; }
.num-cell { color: #5f6672; font-weight: 500; }
/* 总学分：蓝色徽章 */
.score-pill {
  display: inline-block;
  min-width: 42px;
  padding: 3px 10px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(64,158,255,.14), rgba(64,158,255,.06));
  color: #2f7fe0;
  font-weight: 700;
  font-size: 14px;
}
.score-pill em { font-style: normal; font-size: 11px; font-weight: 500; opacity: .7; margin-left: 1px; }

/* 合计行 */
.total-row { background: #fafafa; }
.total-row td { border-top: 2px solid #ebeef5; padding: 14px 16px; text-align: center; }
.total-row td:first-child { text-align: left; }
.purple { color: #9b59b6; }
.blue { color: #409eff; }

/* 奖牌 & 排名 */
.medal { font-size: 20px; }
.rank-num { color: #86909c; font-weight: 500; }
.rank-count { color: #86909c; font-size: 13px; }
</style>
