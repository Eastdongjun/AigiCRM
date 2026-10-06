<template>
  <!-- 概览 · 紧凑深蓝条(与文件管理页同一设计语言;比四张彩卡省一行,表格多一屏) -->
  <div class="cust-strip">
    <div class="cs-stat">
      <div class="cs-num">{{ total }}</div>
      <div class="cs-lbl">客户总数</div>
    </div>
    <div class="cs-divider"></div>
    <div class="cs-stat">
      <div class="cs-num">{{ activeTabCount }}</div>
      <div class="cs-lbl">{{ activeTabLabel }}</div>
    </div>
    <div class="cs-divider"></div>
    <div class="cs-stat">
      <div class="cs-num">{{ warningCount }}</div>
      <div class="cs-lbl">预警中</div>
    </div>
    <div class="cs-divider"></div>
    <div class="cs-stat">
      <div class="cs-num" :class="{ 'cs-alert': expiredCount > 0 }">{{ expiredCount }}</div>
      <div class="cs-lbl">已超期</div>
    </div>
    <div class="cs-tip">点击行查看客户详情 · 到期日列按紧急程度着色</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { injectCustomerList } from '@/composables/useCustomerList'

// 【容量加固·服务端分页 2026-10-05】计数改服务端口径:
// 客户总数=当前筛选下的服务端 total(原为已拉取 500 条的本地过滤长度);
// 预警中/已超期=counts-by-filter 的状态覆盖计数(原为本地过滤当前拉取集)
const { tabs, activeTab, total, tabCounts } = injectCustomerList()

const activeTabCount = computed(() => tabs.value.find((t) => t.type === activeTab.value)?.count || 0)
const activeTabLabel = computed(() => tabs.value.find((t) => t.type === activeTab.value)?.label || '当前分类')
const warningCount = computed(() => tabCounts.value['预警中'] || 0)
const expiredCount = computed(() => tabCounts.value['已超期'] || 0)
</script>

<style scoped>
.cust-strip {
  display: flex; align-items: center; gap: 24px;
  background: linear-gradient(135deg, #16337a 0%, #1d4ed8 58%, #2563eb 100%);
  border-radius: 12px; padding: 14px 24px; margin-bottom: 12px;
  box-shadow: 0 6px 20px rgba(22, 51, 122, .14);
}
.cs-stat { min-width: 76px }
.cs-num { font-size: 24px; font-weight: 700; color: #fff; line-height: 1.2; letter-spacing: .2px }
.cs-num.cs-alert { color: #fca5a5 }
.cs-lbl { font-size: 12px; color: rgba(255, 255, 255, .66); margin-top: 2px }
.cs-divider { width: 1px; height: 34px; background: rgba(255, 255, 255, .18) }
.cs-tip { margin-left: auto; font-size: 12px; color: rgba(255, 255, 255, .55) }
</style>
