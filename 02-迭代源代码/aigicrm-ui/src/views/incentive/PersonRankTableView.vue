<template>
  <!-- 个人排行: 按人员统计四项激励(与客户排行同构: 排名+姓名/办事处+总积分+分类矩阵+三阶段+记录数) -->
  <div class="card">
    <div class="table-header">
      <span class="matrix-title">{{ period || '全部周期' }} 四项激励个人排行</span>
      <span class="header-right-actions">
        <span class="rank-count">{{ personBoard.length }}人</span>
        <!-- 【2026-09-23 需求】导出受独立权限点控制, 管理员在角色管理权限树勾选授予(V2026.09.23.7) -->
        <el-button v-if="userStore.hasPermission('incentive:person-rank:export')" size="small" type="primary" plain :icon="Download" @click="exportPersonRank">导出</el-button>
      </span>
    </div>
    <el-table :data="pagedPersonBoard" style="width:100%" empty-text="当前周期暂无个人激励记录，上传激励文件后在此排行" class="rank-matrix"
      :header-cell-style="{ background: '#f7f9fc', color: '#1f2329', fontWeight: 600, borderBottom: '1px solid #e8ebf0' }"
      :row-style="{ height: '46px' }">
      <!-- 【A2/B7 三轮 2026-10-09】12 类目矩阵总宽必然超主视口：排名/人员固定左侧，横向滚动时身份列常驻（列优先级）；
               类目子列 72→80 + 表头 12px 紧凑内边距，五~六字类目名不再断字换行（如「价值服务手册」） -->
      <el-table-column label="排名" width="64" align="center" fixed="left">
        <template #default="{ row }">
          <span v-if="row.rank <= 3" :class="'rank-medal rank-medal-' + row.rank">{{ ['🥇','🥈','🥉'][row.rank - 1] }}</span>
          <span v-else class="rank-num">{{ row.rank }}</span>
        </template>
      </el-table-column>
      <el-table-column label="人员" min-width="150" fixed="left">
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
      <!-- 2026-10-04 M7：去掉本列 sortable——表格数据是当前页 15 条的前端切片，列排序只会重排本页、
           与"排名/全榜"语义冲突（客户排行表 RankTableView 同列本就无 sortable）；两表口径统一 -->
      <el-table-column label="激励总额" width="92" align="center">
        <template #default="{ row }"><span class="score-pill">{{ row.totalScore || 0 }}<em>分</em></span></template>
      </el-table-column>
      <!-- 【决策7 2026-10-18 用户裁决·"三列+合计"】恢复 2026-09-25 隐藏的安装前/中/后三列, 记录数改标"合计"
           紧随其后(数据 preCount/midCount/postCount 一直随行返回,后端零改动);导出同步恢复 -->
      <el-table-column label="安装前" min-width="70" align="center">
        <template #default="{ row }"><span class="num-cell">{{ row.preCount || 0 }}</span></template>
      </el-table-column>
      <el-table-column label="安装中" min-width="70" align="center">
        <template #default="{ row }"><span class="num-cell">{{ row.midCount || 0 }}</span></template>
      </el-table-column>
      <el-table-column label="安装后" min-width="70" align="center">
        <template #default="{ row }"><span class="num-cell">{{ row.postCount || 0 }}</span></template>
      </el-table-column>
      <el-table-column label="合计" min-width="70" align="center">
        <template #default="{ row }"><span class="num-cell">{{ row.recordCount || 0 }}</span></template>
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
      <!-- 【2026-09-25】安装前/中/后三列曾被要求隐藏 → 【决策7 2026-10-18】恢复(上方"三列+合计"),此处的记录数列已上移改标"合计" -->
    </el-table>
    <el-pagination v-if="personBoard.length > personPageSize" v-model:current-page="personPageNum" :page-size="personPageSize" :total="personBoard.length" :page-sizes="pageSizes(personPageSize)" layout="sizes, prev, pager, next, jumper" size="small" background style="justify-content:flex-end;margin-top:12px" @size-change="onSizeChange" />
  </div>
</template>

<script setup>
import { Download } from '@element-plus/icons-vue'
import { injectIncentiveStats } from '@/composables/useIncentiveStats'
import { useUserStore } from '@/store/user'
import { pageSizes } from '@/utils/pagination'
import { exportCsv } from '@/utils/csv'

const { period, personBoard, personPageNum, personPageSize, pagedPersonBoard, rankParents } = injectIncentiveStats()
const userStore = useUserStore()

const onSizeChange = (size) => {
  personPageSize.value = size
  personPageNum.value = 1
}

// 【2026-09-23】个人排行导出(前端 CSV, 与客户排行 exportRank 同构; 按钮由 incentive:person-rank:export 控制)
// 【2026-10-04 E4 去重】esc/BOM/Blob 下载逻辑提取到 utils/csv.js（与 useIncentiveStats.exportRank 共用），
// 此处只保留表头/行数据组装与文件名，导出内容不变
const exportPersonRank = () => {
  const periodLabel = period.value || '全部周期'
  const cats = rankParents.value.flatMap(g => g.children.map(c => c.categoryName))
  // 【决策7 2026-10-18】导出与表格同构: 恢复安装前/中/后+合计(记录数)列
  const header = ['排名', '姓名', '办事处', '激励总额', '安装前', '安装中', '安装后', '合计', ...cats]
  const rows = personBoard.value.map(r => [
    r.rank, r.realName || '未知', r.officeName || '—', r.totalScore || 0,
    r.preCount || 0, r.midCount || 0, r.postCount || 0, r.recordCount || 0,
    ...rankParents.value.flatMap(g => g.children.map(c => r.byCategory?.[c.id]?.[0] || 0)),
  ])
  exportCsv([header, ...rows], `激励个人排行_${periodLabel}.csv`)
}
</script>

<!-- 共享排行样式: scoped 引入(与 RankTableView 同一来源) -->
<style scoped src="./rank-common.css"></style>

<style scoped>
.name-office { font-size: 12px; color: #94a3b8; margin-top: 2px }
</style>
