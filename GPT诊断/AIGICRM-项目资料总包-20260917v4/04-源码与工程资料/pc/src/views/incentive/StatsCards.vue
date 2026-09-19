<template>
  <!-- 概览卡片 -->
  <div class="stat-row">
    <div class="stat-card">
      <div class="sc-top"><span class="sc-label"><el-icon><List /></el-icon> 激励事项数</span></div>
      <div class="sc-num purple">{{ totalCategories }}</div>
      <div class="sc-desc">从激励配置读取</div>
    </div>
    <div class="stat-card">
      <div class="sc-top"><span class="sc-label"><el-icon><Share /></el-icon> 可共享项数</span></div>
      <div class="sc-num green">{{ shareableCount }}</div>
      <div class="sc-desc">名称含"可共享"的项</div>
    </div>
    <div class="stat-card">
      <div class="sc-top"><span class="sc-label"><el-icon><Document /></el-icon> 本周期文件</span></div>
      <div class="sc-num blue">{{ totalRecords }}</div>
      <div class="sc-desc">{{ userStore.realName }} · {{ period }} 共上传 {{ totalRecords }} 个文件</div>
    </div>
    <div class="stat-card">
      <div class="sc-top"><span class="sc-label"><el-icon><Medal /></el-icon> 总积分</span></div>
      <div class="sc-num green">{{ totalScore }}</div>
      <div class="sc-desc">每个文件按子类分值计分</div>
    </div>
  </div>
</template>

<script setup>
import { injectIncentiveStats } from '@/composables/useIncentiveStats'

const { userStore, period, totalCategories, shareableCount, totalRecords, totalScore } = injectIncentiveStats()
</script>

<style scoped>
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.stat-card { background: #fff; border: 1px solid #ebeef5; border-radius: 12px; padding: 20px 24px; }
.sc-top { margin-bottom: 4px; }
.sc-label { font-size: 13px; color: #86909c; display: inline-flex; align-items: center; gap: 4px; }
.sc-num { font-size: 36px; font-weight: 700; line-height: 1.2; }
.sc-num.purple { color: #9b59b6; }
.sc-num.green { color: #67c23a; }
.sc-num.blue { color: #409eff; }
.sc-desc { font-size: 12px; color: #c0c4cc; margin-top: 4px; }
</style>
