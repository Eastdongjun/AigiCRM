<template>
  <!-- 个人排行: 按人员统计四项激励(与客户排行同构: 排名+姓名/办事处+总积分+分类矩阵+三阶段+记录数) -->
  <div class="card">
    <div class="table-header">
      <span class="matrix-title">{{ period || '全部周期' }} 四项激励个人排行</span>
      <span class="header-right-actions">
        <span class="rank-count">{{ personBoard.length }}人</span>
      </span>
    </div>
    <el-table :data="pagedPersonBoard" style="width:100%" empty-text="暂无数据" class="rank-matrix"
      :header-cell-style="{ background: '#f7f9fc', color: '#1f2329', fontWeight: 600, borderBottom: '1px solid #e8ebf0' }"
      :row-style="{ height: '46px' }">
      <el-table-column label="排名" width="64" align="center">
        <template #default="{ row }">
          <span v-if="row.rank <= 3" :class="'rank-medal rank-medal-' + row.rank">{{ ['🥇','🥈','🥉'][row.rank - 1] }}</span>
          <span v-else class="rank-num">{{ row.rank }}</span>
        </template>
      </el-table-column>
      <el-table-column label="人员" min-width="170">
        <template #default="{ row }">
          <div class="name-wrap">
            <span class="name-avatar" :class="'av-' + (row.rank <= 3 ? row.rank : 4)">{{ (row.realName || '?').charAt(0) }}</span>
            <div>
              <div :class="row.rank <= 3 ? 'name-top' : 'name-cell'">{{ row.realName || '未知' }}</div>
              <div class="name-office">{{ row.officeName || '—' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="激励总额" width="92" align="center" sortable :sort-by="'totalScore'">
        <template #default="{ row }"><span class="score-pill">{{ row.totalScore || 0 }}<em>分</em></span></template>
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
      <el-table-column label="安装前" min-width="66" align="center">
        <template #default="{ row }"><span class="num-cell">{{ row.preCount || 0 }}</span></template>
      </el-table-column>
      <el-table-column label="安装中" min-width="66" align="center">
        <template #default="{ row }"><span class="num-cell">{{ row.midCount || 0 }}</span></template>
      </el-table-column>
      <el-table-column label="安装后" min-width="66" align="center">
        <template #default="{ row }"><span class="num-cell">{{ row.postCount || 0 }}</span></template>
      </el-table-column>
      <el-table-column label="记录数" min-width="70" align="center">
        <template #default="{ row }"><span class="num-cell">{{ row.recordCount || 0 }}</span></template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="personBoard.length > personPageSize" v-model:current-page="personPageNum" :page-size="personPageSize" :total="personBoard.length" :page-sizes="pageSizes(personPageSize)" layout="sizes, prev, pager, next, jumper" size="small" background style="justify-content:flex-end;margin-top:12px" @size-change="onSizeChange" />
  </div>
</template>

<script setup>
import { injectIncentiveStats } from '@/composables/useIncentiveStats'
import { pageSizes } from '@/utils/pagination'

const { period, personBoard, personPageNum, personPageSize, pagedPersonBoard, rankParents } = injectIncentiveStats()

const onSizeChange = (size) => {
  personPageSize.value = size
  personPageNum.value = 1
}
</script>

<!-- 共享排行样式: scoped 引入(与 RankTableView 同一来源) -->
<style scoped src="./rank-common.css"></style>

<style scoped>
.name-office { font-size: 12px; color: #94a3b8; margin-top: 2px }
</style>
