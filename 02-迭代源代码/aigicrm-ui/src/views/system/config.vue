<template>
  <div class="config-page">

    <div class="section-box">
      <!-- 2026-08-08 布局重构：左侧导航 + 右侧内容 -->
      <!-- 【2026-11-03 权限中心收口 → 2026-11-05 用户令"权限中心收敛到系统设置里面"】
           权限中心不再是独立侧栏菜单(库节点已删 V2026.11.05.1), 整页收敛为下方「权限中心」页签
           (唯一可写入口不变, 只是入口位置; embedded 形态隐藏页头避免与页签双重标题)。 -->
      <el-tabs v-model="activeTab" tab-position="left" class="settings-tabs">
        <!-- 基本设置 -->
        <el-tab-pane name="basic">
          <template #label><span class="tab-label"><el-icon><Setting /></el-icon>基本设置</span></template>
          <BasicSettings />
        </el-tab-pane>

        <!-- 字典管理 -->
        <el-tab-pane name="dict">
          <template #label><span class="tab-label"><el-icon><Notebook /></el-icon>字典管理</span></template>
          <DictManager />
        </el-tab-pane>
        <!-- 审批配置 -->
        <el-tab-pane name="approvalConfig"><template #label><span class="tab-label"><el-icon><Select /></el-icon>审批配置</span></template><ApprovalConfig /></el-tab-pane>
        <!-- 激励类别管理(2026-11-03 起"与权限"三字移除: 专项授权已迁权限中心) -->
        <el-tab-pane name="incentiveCategory">
          <template #label><span class="tab-label"><el-icon><Coin /></el-icon>激励类别管理</span></template>
          <CategoryManager />
        </el-tab-pane>
        <!-- 日志管理(2026-11-05 用户令"日志管理也放到系统设置里面": 独立侧栏菜单已删 V2026.11.05.2,
             页签按注册码 system:log:view 显隐, 无码者不见此页签) -->
        <el-tab-pane v-if="canViewLog" name="log" lazy>
          <template #label><span class="tab-label"><el-icon><Notebook /></el-icon>日志管理</span></template>
          <LogView embedded />
        </el-tab-pane>
        <!-- 权限中心(2026-11-05 收敛: 功能勾选/数据范围锚点/公海名单/激励授权, lazy=点开才挂载) -->
        <el-tab-pane name="permCenter" lazy>
          <template #label><span class="tab-label"><el-icon><Lock /></el-icon>权限中心</span></template>
          <PermCenter embedded />
        </el-tab-pane>

      </el-tabs>
    </div>

      </div>
</template>

<script setup>
import { computed } from 'vue'
import ApprovalConfig from '@/views/system/approvalConfig.vue'
import { Setting, Notebook, Select, Coin, Lock } from '@element-plus/icons-vue'
import { usePermissionData } from '@/composables/usePermissionData'
import { useUserStore } from '@/store/user'
import DictManager from './DictManager.vue'
import BasicSettings from './BasicSettings.vue'
import CategoryManager from './CategoryManager.vue'
import PermCenter from './PermCenter.vue'
import LogView from './log.vue'

const userStore = useUserStore()
// 日志管理页签显隐=注册码 system:log:view(与原菜单节点同码, 零硬编码)
const canViewLog = computed(() => userStore.hasPermission('system:log:view'))

// activeTab 为模块级单例(composables/usePermissionData.js), 权限中心与设置页共享
const { activeTab } = usePermissionData()
</script>


<style scoped>
.config-page { height: calc(100vh - 80px); display: flex; flex-direction: column }
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md); padding: 16px; flex: 1; overflow: hidden; display: flex; flex-direction: column }
/* 左侧导航布局（2026-08-08 重构） */
.section-box :deep(.el-tabs) { flex: 1; display: flex; flex-direction: row; overflow: hidden }
.section-box :deep(.el-tabs__header) { width: 200px; flex-shrink: 0; margin: 0; border-right: 1px solid #ebeef5; padding: 8px 0 8px 0 }
.section-box :deep(.el-tabs__nav-wrap) { padding: 0 10px }
.section-box :deep(.el-tabs__nav) { float: none }
.section-box :deep(.el-tabs__active-bar) { display: none }
.section-box :deep(.el-tabs__item) { height: 42px; line-height: 42px; padding: 0 12px; margin: 3px 0; border-radius: 8px; font-size: 14px; color: #4b5563; transition: all .2s }
.section-box :deep(.el-tabs__item.is-active) { background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 600 }
.section-box :deep(.el-tabs__item:hover) { color: var(--el-color-primary); background: #f5f7fa }
.section-box :deep(.el-tabs__content) { flex: 1; overflow: auto; padding: 4px 20px 16px; min-width: 0 }
.tab-label { display: inline-flex; align-items: center; gap: 7px; white-space: nowrap }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }

.form-tip {
  font-size: 12px;
  color: var(--el-color-info);
  line-height: 1.4;
  margin-top: 4px;
}

</style>
