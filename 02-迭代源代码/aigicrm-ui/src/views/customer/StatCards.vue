<template>
  <!-- 概览 · 紧凑深蓝条(与文件管理页同一设计语言;比四张彩卡省一行,表格多一屏) -->
  <!-- 【C9 二轮 2026-10-08】条带收口为全局 MetricStrip 组件（与公海池/审批管理同源） -->
  <!-- 【A2 二轮 2026-10-08】首位改「全部」页签口径并标注：此前"客户总数"实为当前页签筛选口径，
       与第二位页签计数同值（截图曾出现 20|20），现首位=全部分类计数(274 口径)，语义不再重复 -->
  <MetricStrip
    :metrics="metrics"
    tip="点击行查看客户详情 · 到期日列按紧急程度着色"
  />
</template>

<script setup>
import { computed } from 'vue'
import MetricStrip from '@/components/MetricStrip.vue'
import { injectCustomerList } from '@/composables/useCustomerList'

// 【容量加固·服务端分页 2026-10-05】计数改服务端口径:
// 首位客户总数=「全部」页签口径(不随 devType 页签变化,仍随搜索/筛选联动,无该计数时回退当前 total);
// 预警中/已超期=counts-by-filter 的状态覆盖计数
const { tabs, activeTab, total, tabCounts } = injectCustomerList()

const currentTab = computed(() => tabs.value.find((t) => t.type === activeTab.value))
const allCount = computed(() => tabs.value.find((t) => t.type === 'all')?.count ?? total.value)
const warningCount = computed(() => tabCounts.value['预警中'] || 0)
const expiredCount = computed(() => tabCounts.value['已超期'] || 0)

// 【ui-5 2026-10-17】默认「全部」页签下 currentTab 就是「全部」，第二位与首位同值同义(截图 20|20)。
// 仅当切到具体分类页签时才追加该分类计数；不新增数据口径，切换页签后指标仍各自正确。
const metrics = computed(() => {
  const list = [{ num: allCount.value, label: '客户总数(全部)' }]
  if (activeTab.value !== 'all') {
    list.push({ num: currentTab.value?.count || 0, label: currentTab.value?.label || '当前分类' })
  }
  list.push({ num: warningCount.value, label: '预警中' })
  list.push({ num: expiredCount.value, label: '已超期', alert: expiredCount.value > 0 })
  return list
})
</script>
