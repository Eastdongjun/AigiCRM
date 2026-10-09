<template>
  <!-- 【C9 三轮 2026-10-09】复用全局 MetricStrip（与客户列表/公海池/审批管理顶部统计条同一视觉语言）。
       原实现是 4 张白卡 + 36px 紫/绿/蓝彩色大数字，与全站统计卡三轨色并存（B5-3）且为第 5 处自写统计卡；
       现收口为深蓝条带白字数字，desc 说明随指标下钻小字展示。 -->
  <MetricStrip :metrics="metrics" />
</template>

<script setup>
import { computed } from 'vue'
import MetricStrip from '@/components/MetricStrip.vue'
import { injectIncentiveStats } from '@/composables/useIncentiveStats'

const { userStore, period, totalCategories, shareableCount, totalRecords, totalScore } = injectIncentiveStats()

const metrics = computed(() => [
  { num: totalCategories.value, label: '激励事项数', desc: '从激励配置读取' },
  { num: shareableCount.value, label: '可共享项数', desc: '名称含"可共享"的项' },
  { num: totalRecords.value, label: '本周期文件', desc: `${userStore.realName || '—'} · ${period.value || '全部周期'} 共上传 ${totalRecords.value} 个文件` },
  { num: totalScore.value, label: '总积分', desc: '每个文件按子类分值计分' },
])
</script>
