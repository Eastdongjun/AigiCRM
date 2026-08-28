<template>
  <div class="customer-list">
    <!-- 顶部工具栏 -->
    <TopBar />

    <!-- 统计卡片 -->
    <StatCards />

    <!-- Tab + 筛选 + 表格 -->
    <MainTable />

    <!-- 分页 -->
    <div :style="{ borderTopColor: activeTab==='innovate'?'#409eff':activeTab==='break'?'#e6a23c':'#67c23a', borderTopWidth:'2px' }" style="display:flex;justify-content:flex-end;padding:4px 16px;background:#fff;border-top-style:solid;flex-shrink:0">
      <!-- 【列表页真分页 2026-08-28】翻页/改每页条数触发重新请求 -->
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[30, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        size="small"
        @current-change="loadData"
        @size-change="page = 1; loadData()"
      />
    </div>

    <!-- 弹窗 -->
    <ColPickerDialog />
    <AdvFilterDialog />
    <ImportDialog />
    <ReleaseDialog />
  </div>
</template>

<script setup>
import { CustomerListKey, useCustomerList } from '@/composables/useCustomerList'
import { onMounted, provide } from 'vue'
import AdvFilterDialog from './AdvFilterDialog.vue'
import ColPickerDialog from './ColPickerDialog.vue'
import ImportDialog from './ImportDialog.vue'
import MainTable from './MainTable.vue'
import ReleaseDialog from './ReleaseDialog.vue'
import StatCards from './StatCards.vue'
import TopBar from './TopBar.vue'

// 共享上下文: 父级实例化一次并 provide,各区块/弹窗 inject
const list = useCustomerList()
provide(CustomerListKey, list)

const {
  activeTab, page, pageSize, total, loadData,
  loadBizConfig, loadStats, loadScopeCounts,
  loadRegions, loadIndustryOptions, loadFilterData,
} = list

onMounted(() => {
  loadBizConfig()
  loadData()
  loadStats()
  loadScopeCounts()
  loadRegions()
  loadIndustryOptions()
  loadFilterData()
})
</script>

<style scoped>
.customer-list { padding: 16px; }
</style>
