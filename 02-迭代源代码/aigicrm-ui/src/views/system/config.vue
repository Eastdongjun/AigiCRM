<template>
  <div class="config-page">
    <!-- 【2026-11-05 用户令"系统设置骨架重新设计，重新布局重新排版，功能要全好用"】v2 骨架:
         ①统一深蓝细条页头(标题+口径) ②左导航分组化(基础配置/权限与安全, 自定义按钮替代 el-tabs
         —— 根因修复: 外层对 el-tabs 的 :deep 全量样式会劫持任何嵌套页签, 自定义导航后无劫持面)
         ③权限中心内层改横向 chip 条(见 PermCenter embedded) ④功能零增减: 六个模块与原页签一一对应,
         日志管理仍按 system:log:view 显隐, 权限中心仍是唯一可写入口。 -->
    <div class="cfg-hero">
      <div>
        <h3 class="cfg-title">系统设置</h3>
        <p class="cfg-sub">基础参数 · 字典 · 审批 · 类别 · 权限 · 日志 —— 集中管理，保存即生效</p>
      </div>
    </div>

    <div class="cfg-body">
      <!-- 左侧分组导航 -->
      <aside class="cfg-nav">
        <div class="cfg-nav-caption">基础配置</div>
        <button class="cfg-nav-item" :class="{ active: activeTab === 'basic' }" @click="switchTab('basic')">
          <el-icon><Setting /></el-icon>基本设置
        </button>
        <button class="cfg-nav-item" :class="{ active: activeTab === 'dict' }" @click="switchTab('dict')">
          <el-icon><Notebook /></el-icon>字典管理
        </button>
        <button class="cfg-nav-item" :class="{ active: activeTab === 'approvalConfig' }" @click="switchTab('approvalConfig')">
          <el-icon><Select /></el-icon>审批配置
        </button>
        <button class="cfg-nav-item" :class="{ active: activeTab === 'incentiveCategory' }" @click="switchTab('incentiveCategory')">
          <el-icon><Coin /></el-icon>激励类别管理
        </button>

        <div class="cfg-nav-caption">权限与安全</div>
        <button class="cfg-nav-item" :class="{ active: activeTab === 'permCenter' }" @click="switchTab('permCenter')">
          <el-icon><Lock /></el-icon>权限中心
        </button>
        <!-- 日志管理页签显隐=注册码 system:log:view(与原菜单节点同码, 零硬编码) -->
        <button v-if="canViewLog" class="cfg-nav-item" :class="{ active: activeTab === 'log' }" @click="switchTab('log')">
          <el-icon><Notebook /></el-icon>日志管理
        </button>
      </aside>

      <!-- 内容区: visited-once 惰性挂载(重组件首访才加载, 之后 v-show 保态) -->
      <div class="cfg-content">
        <template v-if="visited.has('basic')"><BasicSettings v-show="activeTab === 'basic'" /></template>
        <template v-if="visited.has('dict')"><DictManager v-show="activeTab === 'dict'" /></template>
        <template v-if="visited.has('approvalConfig')"><ApprovalConfig v-show="activeTab === 'approvalConfig'" /></template>
        <template v-if="visited.has('incentiveCategory')"><CategoryManager v-show="activeTab === 'incentiveCategory'" /></template>
        <template v-if="visited.has('permCenter')"><PermCenter v-show="activeTab === 'permCenter'" embedded /></template>
        <template v-if="visited.has('log')"><LogView v-show="activeTab === 'log'" embedded /></template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
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
// 日志管理导航显隐=注册码 system:log:view(与原菜单节点同码, 零硬编码)
const canViewLog = computed(() => userStore.hasPermission('system:log:view'))

// activeTab 为模块级单例(composables/usePermissionData.js), 角色管理跳转等场景共享
const { activeTab } = usePermissionData()

// 惰性挂载: 首次访问才渲染对应模块(PermCenter/log 较重), 之后保态
const visited = reactive(new Set(['basic']))
const switchTab = (key) => { activeTab.value = key }
watch(activeTab, (v) => { if (v) visited.add(v) }, { immediate: true })
</script>

<style scoped>
.config-page { height: calc(100vh - 80px); display: flex; flex-direction: column }
/* 页头: 统一深蓝渐变细条 */
.cfg-hero { background: linear-gradient(135deg, #173f76, #24486b); border-radius: var(--radius-md); padding: 14px 20px; margin-bottom: 14px; flex-shrink: 0 }
.cfg-hero .cfg-title { margin: 0; font-size: 17px; font-weight: 700; color: #fff }
.cfg-hero .cfg-sub { margin: 4px 0 0; font-size: 12px; color: rgba(255, 255, 255, .72) }

.cfg-body { background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md); flex: 1; min-height: 0; display: flex; overflow: hidden }
/* 左导航: 分组 captions + pill 条目 */
.cfg-nav { width: 172px; flex-shrink: 0; border-right: 1px solid #ebeef5; padding: 12px 10px; overflow-y: auto }
.cfg-nav-caption { font-size: 11px; color: #9aa3ad; letter-spacing: 1px; padding: 10px 12px 4px; }
.cfg-nav-item { display: flex; align-items: center; gap: 8px; width: 100%; height: 40px; padding: 0 12px; margin: 3px 0; border: none; background: transparent; border-radius: 8px; font-size: 14px; color: #4b5563; cursor: pointer; transition: all .2s; text-align: left }
.cfg-nav-item:hover { color: var(--el-color-primary); background: #f5f7fa }
.cfg-nav-item.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 600 }
.cfg-nav-item .el-icon { font-size: 15px }

.cfg-content { flex: 1; min-width: 0; overflow: auto; padding: 16px 20px }
</style>
