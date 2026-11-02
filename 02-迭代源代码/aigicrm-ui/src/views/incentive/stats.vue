<template>
  <div class="incentive-stats" v-loading="loading" element-loading-text="正在加载激励统计…">
    <!-- 概览卡片 -->
    <StatsCards />

    <!-- Tab栏 -->
    <div class="tab-bar">
      <div class="tabs">
        <span class="tab-item" :class="{ active: activeTab === 'table' }" @click="activeTab = 'table'">
          <el-icon><Grid /></el-icon> 激励统计表
        </span>
        <span class="tab-item" :class="{ active: activeTab === 'rank' }" @click="activeTab = 'rank'">
          <el-icon><User /></el-icon> 客户排行
        </span>
        <!-- 【2026-09-23 需求】个人排行: 按人员统计四项服务数据 -->
        <span class="tab-item" :class="{ active: activeTab === 'person' }" @click="activeTab = 'person'">
          <el-icon><Trophy /></el-icon> 个人排行
        </span>
      </div>
      <!-- 【A2 三轮 2026-10-09】period 默认空串=「全部周期」，此前未设 placeholder 时 el-select 渲染裸「请选择」，
           用户无法得知当前筛选语义；placeholder 与 PERIOD_ALL 选项 label 对齐 -->
      <el-select v-model="period" placeholder="全部周期" style="width:150px" size="small" @change="loadData">
        <el-option v-for="p in periodOptions" :key="p.value" :label="p.label" :value="p.value" />
      </el-select>
    </div>

    <!-- 激励统计表 -->
    <StatsTableView v-if="activeTab === 'table'" />

    <!-- 客户排行 -->
    <RankTableView v-if="activeTab === 'rank'" />

    <!-- 个人排行(按人员统计四项服务) -->
    <PersonRankTableView v-if="activeTab === 'person'" />
  </div>
</template>

<script setup>
import { provide, onMounted, watch } from 'vue'
import { useIncentiveStats, IncentiveStatsKey } from '@/composables/useIncentiveStats'
import { useLiveRefresh } from '@/composables/useLiveRefresh'
import StatsCards from './StatsCards.vue'
import StatsTableView from './StatsTableView.vue'
import RankTableView from './RankTableView.vue'
import PersonRankTableView from './PersonRankTableView.vue'

// 共享上下文: 父级实例化一次并 provide,各视图子组件 inject
const stats = useIncentiveStats()
provide(IncentiveStatsKey, stats)

const { loading, activeTab, period, periodOptions, loadData } = stats

watch(period, loadData)
onMounted(loadData)
// 【非实时 A 方案 2026-11-02】激励统计聚焦重拉+30s 轮询(页面可见时; 后端 scoreboard 30s 缓存命中为主)
useLiveRefresh(loadData)
</script>

<style scoped>
.incentive-stats { }
.tab-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.tabs { display: flex; gap: 0; }
.tab-item { display: inline-flex; align-items: center; gap: 4px; padding: 10px 28px; font-size: 14px; cursor: pointer; color: #666; border: 1px solid #ebeef5; background: #fafafa; margin-left: -1px; }
.tab-item:first-child { border-radius: 8px 0 0 8px; margin-left: 0; }
.tab-item:last-child { border-radius: 0 8px 8px 0; }
.tab-item.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 600; border-color: var(--el-color-primary); z-index: 1; position: relative; }
</style>
