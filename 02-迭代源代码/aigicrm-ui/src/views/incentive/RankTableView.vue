<template>
  <!-- 客户排行：客户激励统计矩阵（按模板：基础信息 + 激励政策分列） -->
  <div class="card">
    <div class="table-header">
      <span class="matrix-title">{{ period || '全部周期' }} 四项激励客户排行</span>
      <span class="header-right-actions">
        <span class="rank-count">{{ scoreboard.length }}个客户</span>
        <!-- 2026-10-04 D11：exportRank 清理死掉的部门排行分支后不再需要参数 -->
        <el-button size="small" type="primary" plain :icon="Download" @click="exportRank()">导出</el-button>
      </span>
    </div>
    <el-table :data="pagedScoreboard" style="width:100%" empty-text="当前周期暂无客户激励记录，上传激励文件后在此排行" class="rank-matrix"
      :header-cell-style="{ background: '#f7f9fc', color: '#1f2329', fontWeight: 600, borderBottom: '1px solid #e8ebf0' }"
      :row-style="{ height: '46px' }">
      <el-table-column label="排名" width="64" align="center">
        <template #default="{ row }">
          <span v-if="row.rank <= 3" :class="'rank-medal rank-medal-' + row.rank">{{ ['🥇','🥈','🥉'][row.rank - 1] }}</span>
          <span v-else class="rank-num">{{ row.rank }}</span>
        </template>
      </el-table-column>
      <el-table-column label="客户" min-width="180">
        <template #default="{ row }">
          <div class="name-wrap">
            <span class="name-avatar" :class="'av-' + (row.rank <= 3 ? row.rank : 4)">{{ row.customerName?.charAt(0) }}</span>
            <span :class="row.rank <= 3 ? 'name-top' : 'name-cell'">{{ row.customerName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="激励总额" width="92" align="center">
        <template #default="{ row }"><span class="score-pill">{{ row.totalScore }}<em>分</em></span></template>
      </el-table-column>
      <template v-for="(parent, pi) in rankParents" :key="'p' + parent.id">
        <el-table-column :label="parent.categoryName" align="center" :class-name="'cat-group cat-group-' + (pi % 4)">
          <el-table-column v-for="child in parent.children" :key="child.id" :label="child.categoryName" min-width="80" align="center">
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
      <el-table-column label="上传数" min-width="70" align="center">
        <template #default="{ row }"><span class="num-cell">{{ row.recordCount || 0 }}</span></template>
      </el-table-column>
    </el-table>
    <!-- 2026-09-13：补「每页条数」下拉（30/50/100） -->
    <el-pagination v-if="scoreboard.length > pageSize" v-model:current-page="pageNum" :page-size="pageSize" :total="scoreboard.length" :page-sizes="pageSizes(pageSize)" layout="sizes, prev, pager, next, jumper" size="small" background style="justify-content:flex-end;margin-top:12px" @size-change="onSizeChange" />
  </div>
</template>

<script setup>
import { Download } from '@element-plus/icons-vue'
import { injectIncentiveStats } from '@/composables/useIncentiveStats'
import { pageSizes } from '@/utils/pagination'

const {
  period, scoreboard, pagedScoreboard, rankParents, pageNum, pageSize, exportRank,
} = injectIncentiveStats()

// 切换每页条数后回到第 1 页
const onSizeChange = (size) => {
  pageSize.value = size
  pageNum.value = 1
}
</script>

<!-- 共享排行样式: scoped 引入(避免非 scoped 泄漏到其它页面),各组件各自 scoped 副本 -->
<style scoped src="./rank-common.css"></style>
