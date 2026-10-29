<template>
  <!-- 左侧：子分类列表（点行=筛选右侧表格，再点同一行取消；「上传文件」按钮独立入口不受行点击影响） -->
  <div class="left-panel" :class="{ collapsed: panelCollapsed }">
    <template v-if="!panelCollapsed">
      <div class="sub-hint">
        <el-tag type="danger" size="small" effect="plain">{{ activeParent?.categoryName }}</el-tag>
        <span class="sub-hint-text">— 点击行筛选 / 上传</span>
        <el-button class="fold-btn" size="small" text :icon="CaretLeft" title="收起子分类面板" aria-label="收起子分类面板" @click="setPanelExpanded(false)" />
      </div>

      <div
        v-for="child in groupedChildren"
        :key="child.id"
        class="sub-item"
        :class="{ active: selectedCategoryId === child.id }"
        @click="toggleCategoryFilter(child.id)"
      >
        <span class="sub-name">{{ child.categoryName }}</span>
        <div class="sub-actions">
          <span class="score-badge" :class="'score-' + Math.min(child.score ?? 0, 3)">{{ child.score ?? 0 }}分</span>
          <el-button v-if="userStore.hasPermission('incentive:add')" size="small" plain :icon="Upload" @click.stop="startUpload(child)">上传文件</el-button>
        </div>
      </div>
    </template>

    <div v-else class="fold-rail">
      <el-button size="small" text :icon="CaretRight" title="展开子分类面板" aria-label="展开子分类面板" @click="setPanelExpanded(true)" />
      <span class="rail-text">子分类</span>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Upload, CaretLeft, CaretRight } from '@element-plus/icons-vue'
import { injectIncentiveUpload } from '@/composables/useIncentiveUpload'

const { userStore, activeParent, groupedChildren, startUpload, selectedCategoryId, toggleCategoryFilter } = injectIncentiveUpload()

// 折叠偏好持久化；首次访问按屏幕宽度给默认值（<1600 时表格需要宽度，默认收起）。
// 【工作流复检修复 2026-10-10】"阻止所有 Cookie"的 Safari/部分内嵌 WebView 里 localStorage
// 访问直接抛 SecurityError(Quota)——裸读写会中断 setup 炸掉整个面板，包 try/catch 优雅降级
const PANEL_PREF_KEY = 'incentive.subPanelExpanded'
const readPanelPref = () => { try { return localStorage.getItem(PANEL_PREF_KEY) } catch { return null } }
const writePanelPref = (v) => { try { localStorage.setItem(PANEL_PREF_KEY, v) } catch { /* 隐私模式不可写,仅本次会话生效 */ } }
const savedPref = readPanelPref()
const panelCollapsed = ref(savedPref === null ? window.innerWidth < 1600 : savedPref !== '1')
const setPanelExpanded = (expanded) => {
  panelCollapsed.value = !expanded
  writePanelPref(expanded ? '1' : '0')
}
</script>

<style scoped>
.left-panel { flex:0 0 auto; width:240px; background:#fafbfd; padding:16px; border-right:1px solid #f0f2f5; overflow-y:auto; transition:width .2s ease }
.left-panel.collapsed { width:36px; padding:12px 4px; overflow:hidden }
.sub-hint { display:flex; align-items:center; gap:8px; margin-bottom:12px; color:var(--el-color-info); font-size:13px }
.sub-hint-text { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap }
.fold-btn { flex-shrink:0; margin-left:auto }
.fold-rail { display:flex; flex-direction:column; align-items:center; gap:8px; padding-top:4px }
.rail-text { writing-mode:vertical-rl; font-size:11px; color:var(--el-text-color-secondary); letter-spacing:2px }
.sub-item { display:flex; justify-content:space-between; align-items:center; padding:10px 0 10px 12px; border-bottom:1px solid #f5f5f5; transition:background .15s; cursor:pointer }
.sub-item:hover { background:#f0f4ff }
/* 行选中=右侧表格正按此子分类过滤(品牌蓝描边与文字同色系, 与页面Tab激活语言一致) */
.sub-item.active { background:#f0f4ff; box-shadow:inset 3px 0 0 var(--el-color-primary) }
.sub-item.active .sub-name { color:var(--el-color-primary); font-weight:700 }
.sub-name { font-size:13px; color:#333; font-weight:500 }
.sub-actions { display:flex; align-items:center; gap:10px }
.score-badge { display:inline-block; min-width:28px; text-align:center; padding:2px 8px; border-radius: var(--radius-md); font-size:12px; font-weight:600 }
.score-0 { color:#bbb; background:#f5f5f5 } .score-1 { color:var(--el-color-warning); background:#fff7ed } .score-2 { color:var(--el-color-success); background:#f0f9eb } .score-3 { color:var(--el-color-danger); background:#fef0f0 }
</style>
