<template>
  <div class="config-page">

    <div class="section-box">
      <!-- 2026-08-08 布局重构：左侧导航 + 右侧内容 -->
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
        <!-- 公海配置 -->
        <el-tab-pane name="approvalConfig"><template #label><span class="tab-label"><el-icon><Select /></el-icon>审批配置</span></template><ApprovalConfig /></el-tab-pane>
        <el-tab-pane v-if="userStore.hasPermission('sea:config:view')" name="sea">
          <template #label><span class="tab-label"><el-icon><Ship /></el-icon>公海配置</span></template>
          <SeaConfigManager />
        </el-tab-pane>
        <!-- 激励类别与权限 -->
        <el-tab-pane name="incentiveCategory">
          <template #label><span class="tab-label"><el-icon><Coin /></el-icon>激励类别与权限</span></template>
          <CategoryManager />
        </el-tab-pane>


        <!-- 权限体系总览 -->
        <el-tab-pane name="permOverview">
          <template #label><span class="tab-label"><el-icon><Lock /></el-icon>权限体系</span></template>
          <PermMatrixManager mode="overview" />
        </el-tab-pane>
        <el-tab-pane name="perm">
          <template #label><span class="tab-label"><el-icon><Grid /></el-icon>权限管理</span></template>
          <PermScopeManager />
        </el-tab-pane>

      </el-tabs>
    </div>

      </div>
</template>

<script setup>
import ApprovalConfig from '@/views/system/approvalConfig.vue'
import { watch } from 'vue'
import { Setting, Notebook, Select, Ship, Lock, Grid, Coin } from '@element-plus/icons-vue'
import { usePermissionData } from '@/composables/usePermissionData'
import DictManager from './DictManager.vue'
import SeaConfigManager from './SeaConfigManager.vue'
import BasicSettings from './BasicSettings.vue'
import CategoryManager from './CategoryManager.vue'
import PermMatrixManager from './PermMatrixManager.vue'
import PermScopeManager from './PermScopeManager.vue'

// 权限状态为模块级单例(composables/usePermissionData.js),各 tab 子组件共享;切到"权限矩阵"时加载
const { activeTab, loadPermData } = usePermissionData()
watch(activeTab, (tab) => { if (tab === 'perm') loadPermData() })

// 权限判断(公海配置 tab 按 sea:config:view 门控, 销售员等无配置权限者不显示该 tab, 避免空态报错)
import { useUserStore } from '@/store/user'
const userStore = useUserStore()
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
