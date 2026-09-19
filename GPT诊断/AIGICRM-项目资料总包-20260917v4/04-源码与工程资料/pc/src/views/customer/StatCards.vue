<template>
  <el-row :gutter="16" style="margin-bottom:12px">
    <el-col :span="6"><el-card shadow="hover" class="stat-card stat-blue"><div class="stat-row"><div class="stat-icon-box blue"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="5"/><path d="M3 21v-2a7 7 0 0 1 7-7h4a7 7 0 0 1 7 7v2"/></svg></div><div class="stat-info"><div class="stat-num">{{ filteredAllData.length }}</div><div class="stat-label">客户总数</div></div></div></el-card></el-col>
    <el-col :span="6"><el-card shadow="hover" class="stat-card stat-orange"><div class="stat-row"><div class="stat-icon-box orange"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg></div><div class="stat-info"><div class="stat-num">{{ tabs.find(t=>t.type===activeTab)?.count||0 }}</div><div class="stat-label">{{ tabs.find(t=>t.type===activeTab)?.label||'' }}</div></div></div></el-card></el-col>
    <el-col :span="6"><el-card shadow="hover" class="stat-card stat-red"><div class="stat-row"><div class="stat-icon-box red"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div><div class="stat-info"><div class="stat-num">{{ tableData.filter(c=>c.status==='warning'||c.status==='expired').length }}</div><div class="stat-label">预警/超期</div></div></div></el-card></el-col>
    <el-col :span="6"><el-card shadow="hover" class="stat-card stat-green"><div class="stat-row"><div class="stat-icon-box green"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg></div><div class="stat-info"><div class="stat-num">{{ tableData.filter(c=>c.status==='expired').length }}</div><div class="stat-label">已过期</div></div></div></el-card></el-col>
  </el-row>
</template>

<script setup>
import { injectCustomerList } from '@/composables/useCustomerList'

const { filteredAllData, tabs, activeTab, tableData } = injectCustomerList()
</script>

<style scoped>
.stat-card { border-radius: 10px; transition: transform .2s; }
.stat-card:hover { transform: translateY(-2px); }
.stat-row { display: flex; align-items: center; gap: 14px; }
.stat-icon-box { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.stat-icon-box.blue { background: #e8f4fd; color: #409eff; }
.stat-icon-box.orange { background: #fef5e7; color: #e6a23c; }
.stat-icon-box.green { background: #eaf7ea; color: #67c23a; }
.stat-icon-box.red { background: #fef0f0; color: #f56c6c; }
.stat-num { font-size: 24px; font-weight: 700; color: #1f2329; line-height: 1.2; }
.stat-label { font-size: 12px; color: #909399; margin-top: 2px; }
</style>
