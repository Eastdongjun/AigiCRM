<template>
  <!-- 激励统计表 -->
  <div class="card">
    <div class="table-header">
      <span>共 <strong>{{ totalCategories }}</strong> 项激励规则</span>
    </div>

    <table class="stats-table">
      <thead>
        <tr>
          <th class="col-cat">激励大类</th>
          <th class="col-sub">激励细分</th>
          <th class="col-score">激励积分</th>
          <th class="col-count">数量</th>
          <th class="col-stage">安装前</th>
          <th class="col-stage">安装中</th>
          <th class="col-stage">安装后</th>
          <th class="col-total">总积分</th>
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
</template>

<script setup>
import { injectIncentiveStats } from '@/composables/useIncentiveStats'

const {
  totalCategories, groupedData, catColors,
  tableTotalFiles, tableTotalPre, tableTotalMid, tableTotalPost, tableTotalScore,
} = injectIncentiveStats()
</script>

<style scoped>
.card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 24px; }
.table-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; font-size: 14px; color: #86909c; }
.stats-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.stats-table th { text-align: left; padding: 12px 16px; color: #f56c6c; font-weight: 500; border-bottom: 2px solid #f56c6c; background: #fff; }
.stats-table td { padding: 14px 16px; border-bottom: 1px solid #f5f5f5; color: #333; }
.stats-table tbody tr:hover { background: #fafbfc; }

.col-cat { width: 180px; vertical-align: middle; border-right: 1px solid #f0f0f0; }
.col-score { width: 100px; text-align: center; }
.col-count { width: 80px; text-align: center; }
.col-stage { width: 80px; text-align: center; }
.col-total { width: 100px; text-align: center; }

.cat-label { display: inline-flex; align-items: center; gap: 4px; font-size: 14px; font-weight: 600; }
.cat-blue { color: #409eff; }
.cat-orange { color: #e6a23c; }
.cat-red { color: #f56c6c; }
.cat-yellow { color: #e6a23c; }

.score-active { color: #409eff; font-weight: 600; }
.score-zero { color: #c0c4cc; }
.stage-active { color: #10b981; font-weight: 600; }
.stage-zero { color: #c0c4cc; }

.total-row { background: #fafafa; }
.total-row td { border-top: 2px solid #ebeef5; padding: 14px 16px; text-align: center; }
.total-row td:first-child { text-align: left; }
.purple { color: #9b59b6; }
.blue { color: #409eff; }
</style>
