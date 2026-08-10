<template>
  <div class="incentive-stats" v-loading="loading" element-loading-text="正在加载激励统计…">
    <!-- M5 2026-08-10: 未获公司级统计可见授权 → 空态 -->
    <div v-if="noPermission" class="no-perm">
      <el-empty description="无权限查看全公司激励统计，请联系管理员授权" />
    </div>
    <template v-else>
    <!-- 概览卡片 -->
    <StatsCards />

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
    <StatsTableView v-if="activeTab === 'table'" />

    <!-- 个人排行 -->
    <RankTableView v-if="activeTab === 'rank'" />

    <!-- 部门排行 -->
    <DeptRankTableView v-if="activeTab === 'dept'" />
    </template>
  </div>
</template>

<script setup>
import { provide, onMounted, watch } from 'vue'
import { useIncentiveStats, IncentiveStatsKey } from '@/composables/useIncentiveStats'
import StatsCards from './StatsCards.vue'
import StatsTableView from './StatsTableView.vue'
import RankTableView from './RankTableView.vue'
import DeptRankTableView from './DeptRankTableView.vue'

// 共享上下文: 父级实例化一次并 provide,各视图子组件 inject
const stats = useIncentiveStats()
provide(IncentiveStatsKey, stats)

const { loading, noPermission, activeTab, period, periodOptions, loadData } = stats

watch(period, loadData)
onMounted(loadData)
</script>

<style scoped>
.no-perm { display: flex; align-items: center; justify-content: center; min-height: calc(100vh - 200px); }
.incentive-stats { }
.tab-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.tabs { display: flex; gap: 0; }
.tab-item { display: inline-flex; align-items: center; gap: 4px; padding: 10px 28px; font-size: 14px; cursor: pointer; color: #666; border: 1px solid #ebeef5; background: #fafafa; margin-left: -1px; }
.tab-item:first-child { border-radius: 8px 0 0 8px; margin-left: 0; }
.tab-item:last-child { border-radius: 0 8px 8px 0; }
.tab-item.active { background: #ecf5ff; color: #409eff; font-weight: 600; border-color: #409eff; z-index: 1; position: relative; }
</style>
