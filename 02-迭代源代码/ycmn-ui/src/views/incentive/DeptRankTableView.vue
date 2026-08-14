<template>
  <!-- 部门排行 -->
  <div class="card">
    <div class="table-header">
      <span>{{ period || '全部' }} 部门排行</span>
      <span class="header-right-actions">
        <span class="rank-count">{{ deptList.length }}个部门</span>
        <el-button size="small" type="primary" plain :icon="Download" @click="exportRank('dept')">导出</el-button>
      </span>
    </div>
    <el-table :data="deptList" style="width:100%" empty-text="暂无数据" class="rank-matrix"
      :header-cell-style="{ background: '#f7f9fc', color: '#1f2329', fontWeight: 600, borderBottom: '1px solid #e8ebf0' }"
      :row-style="{ height: '46px' }">
      <el-table-column label="排名" width="64" align="center">
        <template #default="{ row }">
          <span v-if="row.rank <= 3" :class="'rank-medal rank-medal-' + row.rank">{{ ['🥇','🥈','🥉'][row.rank - 1] }}</span>
          <span v-else class="rank-num">{{ row.rank }}</span>
        </template>
      </el-table-column>
      <el-table-column label="部门" min-width="140">
        <template #default="{ row }">
          <div class="name-wrap">
            <span class="name-avatar" :class="'av-' + (row.rank <= 3 ? row.rank : 4)">{{ row.deptName?.charAt(0) }}</span>
            <span :class="row.rank <= 3 ? 'name-top' : 'name-cell'">{{ row.deptName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="激励总额" width="92" align="center">
        <template #default="{ row }"><span class="score-pill">{{ row.totalScore }}<em>分</em></span></template>
      </el-table-column>
      <template v-for="(parent, pi) in rankParents" :key="'p' + parent.id">
        <el-table-column :label="parent.categoryName" align="center" :class-name="'cat-group cat-group-' + (pi % 4)">
          <el-table-column v-for="child in parent.children" :key="child.id" :label="child.categoryName" min-width="72" align="center">
            <template #default="{ row }">
              <span v-if="row.byCategory?.[child.id]" class="cell-pill">
                <b class="cell-cnt">{{ row.byCategory[child.id][0] }}</b><span class="cell-unit">条</span>
                <span class="cell-score" :class="{ zero: row.byCategory[child.id][1] === 0 }">{{ row.byCategory[child.id][1] }}分</span>
              </span>
              <span v-else class="cell-dash">—</span>
            </template>
          </el-table-column>
        </el-table-column>
      </template>
      <el-table-column label="参与人数" width="84" align="center">
        <template #default="{ row }"><span class="num-cell">{{ row.participantCount }}人</span></template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { Download } from '@element-plus/icons-vue'
import { injectIncentiveStats } from '@/composables/useIncentiveStats'

const { period, deptList, rankParents, exportRank } = injectIncentiveStats()
</script>

<!-- 共享排行样式: scoped 引入(避免非 scoped 泄漏到其它页面),各组件各自 scoped 副本 -->
<style scoped src="./rank-common.css"></style>
