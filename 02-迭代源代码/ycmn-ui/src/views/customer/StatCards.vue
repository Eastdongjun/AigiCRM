<template>
  <el-row :gutter="16" style="margin-bottom:12px">
    <el-col :span="6"><el-card shadow="hover" class="stat-card stat-blue"><div class="stat-row"><div class="stat-icon-box blue"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="5"/><path d="M3 21v-2a7 7 0 0 1 7-7h4a7 7 0 0 1 7 7v2"/></svg></div><div class="stat-info"><div class="stat-num">{{ total }}</div><div class="stat-label">客户总数</div></div></div></el-card></el-col>
    <el-col :span="6"><el-card shadow="hover" class="stat-card stat-orange"><div class="stat-row"><div class="stat-icon-box orange"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg></div><div class="stat-info"><div class="stat-num">{{ tabs.find(t=>t.type===activeTab)?.count||0 }}</div><div class="stat-label">{{ tabs.find(t=>t.type===activeTab)?.label||'' }}</div></div></div></el-card></el-col>
    <el-col :span="6"><el-card shadow="hover" class="stat-card stat-red"><div class="stat-row"><div class="stat-icon-box red"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div><div class="stat-info"><div class="stat-num">{{ (scopeCounts.warning||0)+(scopeCounts.expired||0) }}</div><div class="stat-label">预警/超期</div></div></div></el-card></el-col>
    <el-col :span="6"><el-card shadow="hover" class="stat-card stat-green"><div class="stat-row"><div class="stat-icon-box green"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg></div><div class="stat-info"><div class="stat-num">{{ scopeCounts.expired||0 }}</div><div class="stat-label">已过期</div></div></div></el-card></el-col>
  </el-row>
</template>

<script setup>
/**
 * 客户列表统计卡片组件
 *
 * 【需求】列表页顶部四张统计卡片：客户总数 / 当前Tab客户数 / 预警超期数 / 已过期数
 * 【方法】口径全部取服务端统计，禁止用当前页 tableData 前端过滤——
 *         真分页（2026-08-28）后 tableData 只含当前页 records，过滤统计会随翻页漂移；
 *         且 filteredAllData 已随改造移除，引用它会渲染报错（本组件此前两处回归均源于此）
 * 【业务规则】总数=分页接口 total（当前筛选+Tab 口径）；
 *         预警/超期=(scopeCounts.warning||0)+(scopeCounts.expired||0)，
 *         已过期=scopeCounts.expired||0，两键由后端 listScopeCounts 按 status
 *         字段计数（warning/expired 由 CustomerExpireTask 定时任务维护）并受数据范围控制
 * 【参数】无 props；经 injectCustomerList() 注入共享上下文
 * 【注意】scopeCounts 在接口返回前为空对象，此时卡片显示 0；依赖父组件 list.vue
 *         onMounted 中的 loadScopeCounts() 拉取
 */
import { injectCustomerList } from '@/composables/useCustomerList';

const { tabs, activeTab, total, scopeCounts } = injectCustomerList()
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
