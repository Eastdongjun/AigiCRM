<template>
  <div class="customer-list">
    <!-- 顶部工具栏 -->
    <TopBar />

    <!-- 统计卡片 -->
    <StatCards />

    <!-- Tab + 筛选 + 表格 -->
    <MainTable />

    <!-- 分页 -->
    <div style="display:flex;justify-content:flex-end;padding:4px 16px;background:#fff;border-top:1px solid #ebeef5;flex-shrink:0">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[30, 50, 100]"
        :total="currentTabData.length"
        layout="total, sizes, prev, pager, next, jumper"
        background
        size="small"
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
import { provide, onMounted } from 'vue'
import { useCustomerList, CustomerListKey } from '@/composables/useCustomerList'
import TopBar from './TopBar.vue'
import StatCards from './StatCards.vue'
import MainTable from './MainTable.vue'
import ColPickerDialog from './ColPickerDialog.vue'
import AdvFilterDialog from './AdvFilterDialog.vue'
import ImportDialog from './ImportDialog.vue'
import ReleaseDialog from './ReleaseDialog.vue'

// 共享上下文: 父级实例化一次并 provide,各区块/弹窗 inject
const list = useCustomerList()
provide(CustomerListKey, list)

const {
  activeTab, page, pageSize, currentTabData,
  loadBizConfig, loadData, loadStats, loadScopeCounts,
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
