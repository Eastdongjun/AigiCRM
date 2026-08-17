<template>
  <!-- 右侧：已上传文件表格 -->
  <div class="right-panel">
    <div class="table-toolbar">
      <el-popover v-model:visible="colSettingVisible" placement="bottom-end" :width="290" trigger="click" popper-class="col-setting-pop" @show="initColDraft">
        <template #reference>
          <el-button link size="small"><el-icon><Setting /></el-icon> 列设置</el-button>
        </template>
        <div class="col-setting-panel">
          <div class="col-setting-title">
            <span>显示列</span>
            <el-link type="primary" underline="never" @click="resetCols">恢复默认</el-link>
          </div>
          <div class="col-setting-grid">
            <label v-for="d in colDraft" :key="d.key" class="col-item" :class="{ off: !d.checked }">
              <input type="checkbox" :checked="d.checked" @change="onToggleCol(d)" />
              <span>{{ d.label }}</span>
            </label>
          </div>
        </div>
      </el-popover>
    </div>
    <div ref="tableWrapRef" class="table-wrap">
    <el-table ref="tableRef" :data="pagedRecords" stripe style="width:100%" :height="tableHeight" empty-text="暂无已上传文件" size="small" @row-click="startReplace" :row-style="{cursor:'pointer'}">
      <el-table-column label="大类·子类" width="130" show-overflow-tooltip v-if="isColVisible('cat')">
        <template #default="{ row }">{{ row.majorMinor }}</template>
      </el-table-column>
      <el-table-column prop="industry" label="行业" width="80" v-if="isColVisible('industry')">
        <template #default="{ row }">{{ row.industry || '—' }}</template>
      </el-table-column>
      <el-table-column prop="relatedProducts" label="相关产品" width="110" show-overflow-tooltip v-if="isColVisible('relatedProducts')">
        <template #default="{ row }">{{ row.relatedProducts || '—' }}</template>
      </el-table-column>
      <el-table-column label="分" width="40" align="center" v-if="isColVisible('score')">
        <template #default="{ row }">
          <span :style="{ color: scoreColor(row.score), fontWeight: 600 }">{{ row.score }}</span>
        </template>
      </el-table-column>
      <el-table-column label="安装文件" min-width="170" show-overflow-tooltip v-if="isColVisible('files')">
        <template #default="{ row }">
          <div class="install-files">
            <span v-if="row.filePreName" class="file-tag" :class="{previewable: fileType(row.filePreName)!=='doc'}" @click="previewFile(row.filePreId, row.filePreName)"><b>前</b>{{ row.filePreName }}</span>
            <span v-if="row.fileMidName" class="file-tag" :class="{previewable: fileType(row.fileMidName)!=='doc'}" @click="previewFile(row.fileMidId, row.fileMidName)"><b>中</b>{{ row.fileMidName }}</span>
            <span v-if="row.filePostName" class="file-tag" :class="{previewable: fileType(row.filePostName)!=='doc'}" @click="previewFile(row.filePostId, row.filePostName)"><b>后</b>{{ row.filePostName }}</span>
            <span v-if="!row.filePreName && !row.fileMidName && !row.filePostName" class="file-cell">
              <el-icon class="file-icon"><Document /></el-icon>{{ row.fileName || '—' }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="客户" width="110" show-overflow-tooltip v-if="isColVisible('customer')">
        <template #default="{ row }">
          <span class="customer-link">{{ row.customerName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="共享" width="70" align="center" v-if="isColVisible('shared')">
        <template #default="{ row }">
          <el-switch
            v-if="isRecordOwner(row)"
            :model-value="!!row.shared"
            size="small"
            @change="(v) => toggleShare(row, v)"
          />
          <span v-else :class="row.shared ? 'shared-yes' : 'shared-no'">{{ row.shared ? '● 共享' : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="上传日期" width="100" v-if="isColVisible('createdAt')">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="userName" label="上传人" width="80" v-if="isColVisible('uploader')" />
      <el-table-column label="修改人" width="80" v-if="isColVisible('updater')">
        <template #default="{ row }">{{ row.updatedByName || '—' }}</template>
      </el-table-column>
      <el-table-column label="修改时间" width="90" v-if="isColVisible('updatedAt')">
        <template #default="{ row }">{{ row.updatedByName ? formatDate(row.updatedAt) : '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-if="userStore.isAdmin() || canEdit || row.userId === userStore.userId" link type="primary" size="small" @click="startReplace(row)">追加</el-button>
          <el-button v-if="userStore.isAdmin() || canDelete || row.userId === userStore.userId" link type="danger" size="small" @click="handleDeleteRecord(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>
    <!-- 自定义横向滚动条(表格外部, 与 el-table 内部滚动双向同步) -->
    <div v-show="hscrollMax > 0" class="hscroll-bar">
      <input type="range" :min="0" :max="hscrollMax" :value="hscrollVal" @input="onHScrollInput" />
    </div>
    <div class="list-footer">
      <div class="list-footer-left">
      <span class="list-footer-count">共 <b>{{ filteredRecords.length }}</b> 条</span>
    </div>
      <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[30, 50, 100]" :total="filteredRecords.length" layout="total, sizes, prev, pager, next" size="small" background @size-change="pageNum = 1" />
    </div>
  </div>
</template>

<script setup>
import { injectIncentiveUpload } from '@/composables/useIncentiveUpload'
import { onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
import { Setting } from '@element-plus/icons-vue'

const {
  userStore, pagedRecords, filteredRecords, pageNum, pageSize,
  COLUMN_DEFS, hiddenCols, colSettingVisible, colDraft, isColVisible,
  loadColumnPref, initColDraft, onToggleCol, resetCols,
  canEdit, canDelete, scoreColor, formatDate, fileType, previewFile,
  startReplace, toggleShare, handleDeleteRecord, isRecordOwner,
} = injectIncentiveUpload()

// 【2026-08-17 修复】必须在解构之后调用: 此前写在解构前触发 TDZ ReferenceError,
// 组件 setup 直接崩溃 → 右侧表格整个不渲染(用户"看不到数据")
onMounted(loadColumnPref)

// ===== 表格外部横向滚动条(与 el-table 内部滚动同步) =====
const tableRef = ref(null)
const tableWrapRef = ref(null)
const tableHeight = ref(400)
let resizeObserver = null

// 表格高度: 动态计算可用高度, 保证表格体撑满面板、分页与滚动条不被裁切
const updateTableHeight = () => {
  if (tableWrapRef.value) {
    tableHeight.value = Math.max(200, tableWrapRef.value.clientHeight)
    syncHScroll()
  }
}
const hscrollMax = ref(0)
const hscrollVal = ref(0)
let wrapEl = null

const syncHScroll = () => {
  wrapEl = tableRef.value?.$el?.querySelector('.el-scrollbar__wrap') || null
  if (!wrapEl) return
  hscrollMax.value = Math.max(0, wrapEl.scrollWidth - wrapEl.clientWidth)
  hscrollVal.value = wrapEl.scrollLeft
}
const onHScrollInput = (e) => {
  if (wrapEl) wrapEl.scrollLeft = Number(e.target.value)
}
const onWrapScroll = () => {
  if (wrapEl) hscrollVal.value = wrapEl.scrollLeft
}
onMounted(() => {
  nextTick(() => {
    updateTableHeight()
    syncHScroll()
    wrapEl?.addEventListener('scroll', onWrapScroll)
    if (tableWrapRef.value) {
      resizeObserver = new ResizeObserver(updateTableHeight)
      resizeObserver.observe(tableWrapRef.value)
    }
    // 列设置显隐变化后列宽变化, 重新计算滚动范围
    watch(() => hiddenCols.value.size, () => nextTick(() => { updateTableHeight(); syncHScroll() }))
  })
})
onBeforeUnmount(() => {
  wrapEl?.removeEventListener('scroll', onWrapScroll)
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.install-files .file-tag.previewable { cursor: pointer; text-decoration: underline; }
.install-files .file-tag.previewable:hover { color: #409eff; }
.right-panel { flex:1; background:#fff; padding:14px 18px; display:flex; flex-direction:column; min-height:0 }
.table-toolbar { display:flex; justify-content:flex-end; align-items:center; padding-bottom:8px }
.table-wrap { flex:1; min-height:0 }
.right-panel .el-table { height:100% }
.hscroll-bar { padding: 4px 2px 0 }.hscroll-bar input[type=range] { width:100%; height:10px; -webkit-appearance:none; appearance:none; background:#e4e7ed; border-radius:5px; outline:none; cursor:pointer }.hscroll-bar input[type=range]::-webkit-slider-thumb { -webkit-appearance:none; appearance:none; width:60px; height:10px; border-radius:5px; background:#909399; cursor:grab }.hscroll-bar input[type=range]::-webkit-slider-thumb:hover { background:#409eff }.hscroll-bar input[type=range]::-moz-range-thumb { width:60px; height:10px; border:none; border-radius:5px; background:#909399; cursor:grab }
.list-footer { display:flex; align-items:center; justify-content:space-between; padding:10px 2px 0; margin-top:8px; border-top:1px solid #f0f2f5 }
.list-footer-left { display:flex; align-items:center; gap:10px }
.col-setting-list { display:grid; grid-template-columns:repeat(2,1fr); gap:8px 12px }
.list-footer-count { font-size:13px; color:#606266 }
.list-footer-count b { color:#409eff; font-weight:700; margin:0 2px }
.file-cell { display: inline-flex; align-items: center; gap: 4px; }
.file-icon { color: #909399; font-size: 14px; flex-shrink: 0; }
.customer-link { color: #409eff; cursor: pointer; }
.customer-link:hover { text-decoration: underline; }
.shared-yes { color: #67c23a; font-size: 12px; font-weight: 500; }
.shared-no { color: #c0c4cc; }
</style>

<style>
/* 列设置 popper 面板(挂 body 下, scoped 样式不生效, 必须全局) */
.col-setting-pop .col-setting-panel { padding: 2px 0 }
.col-setting-pop .col-setting-title { display:flex; justify-content:space-between; align-items:center; padding: 2px 14px 10px; font-size:13px; color:#606266; font-weight:600 }
.col-setting-pop .col-setting-grid { display:grid; grid-template-columns:1fr 1fr; row-gap:2px; column-gap:10px; padding: 0 14px 10px }
.col-setting-pop .col-item { display:flex; align-items:center; gap:7px; font-size:13px; color:#303133; padding:6px 8px; border-radius:6px; cursor:pointer; user-select:none; transition:background .15s }
.col-setting-pop .col-item:hover { background:#f5f7fa }
.col-setting-pop .col-item.off { color:#c0c4cc }
.col-setting-pop .col-item input { accent-color:#409eff; width:14px; height:14px; cursor:pointer; margin:0 }
</style>
