<template>
  <!-- 右侧：已上传文件表格 -->
  <div class="right-panel">
    <el-table :data="pagedRecords" stripe style="width:100%" empty-text="暂无已上传文件" size="small" @row-click="startReplace" :row-style="{cursor:'pointer'}">
      <el-table-column label="大类·子类" min-width="120" show-overflow-tooltip v-if="isColVisible('cat')">
        <template #default="{ row }">{{ row.majorMinor }}</template>
      </el-table-column>
      <el-table-column prop="industry" label="行业" min-width="70" v-if="isColVisible('industry')">
        <template #default="{ row }">{{ row.industry || '—' }}</template>
      </el-table-column>
      <el-table-column prop="relatedProducts" label="相关产品" min-width="110" show-overflow-tooltip v-if="isColVisible('relatedProducts')">
        <template #default="{ row }">{{ row.relatedProducts || '—' }}</template>
      </el-table-column>
      <el-table-column label="分" min-width="45" align="center" v-if="isColVisible('score')">
        <template #default="{ row }">
          <span :style="{ color: scoreColor(row.score), fontWeight: 600 }">{{ row.score }}</span>
        </template>
      </el-table-column>
      <el-table-column label="安装文件" min-width="180" show-overflow-tooltip v-if="isColVisible('files')">
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
      <el-table-column label="客户" min-width="100" show-overflow-tooltip v-if="isColVisible('customer')">
        <template #default="{ row }">
          <span class="customer-link">{{ row.customerName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="共享" min-width="66" align="center" v-if="isColVisible('shared')">
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
      <el-table-column label="上传日期" min-width="90" v-if="isColVisible('createdAt')">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="userName" label="上传人" min-width="65" v-if="isColVisible('uploader')" />
      <el-table-column label="修改人" min-width="65" v-if="isColVisible('updater')">
        <template #default="{ row }">{{ row.updatedByName || '—' }}</template>
      </el-table-column>
      <el-table-column label="修改时间" min-width="90" v-if="isColVisible('updatedAt')">
        <template #default="{ row }">{{ row.updatedByName ? formatDate(row.updatedAt) : '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-if="userStore.isAdmin() || canEdit || row.userId === userStore.userId" link type="primary" size="small" @click="startReplace(row)">追加</el-button>
          <el-button v-if="userStore.isAdmin() || canDelete || row.userId === userStore.userId" link type="danger" size="small" @click="handleDeleteRecord(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 列设置弹窗: 勾选要显示的列, 按人保存到服务器 -->
    <el-dialog v-model="colSettingVisible" title="列设置" width="360px" append-to-body>
      <div class="col-setting-list">
        <el-checkbox v-for="c in colDraft" :key="c.key" v-model="c.checked">{{ c.label }}</el-checkbox>
      </div>
      <template #footer>
        <el-button size="small" @click="resetColDraft">恢复默认</el-button>
        <el-button size="small" @click="colSettingVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveColSetting">确定</el-button>
      </template>
    </el-dialog>
    <div class="list-footer">
      <div class="list-footer-left">
      <span class="list-footer-count">共 <b>{{ filteredRecords.length }}</b> 条</span>
      <el-button link size="small" @click="openColSetting"><el-icon><Setting /></el-icon> 列设置</el-button>
    </div>
      <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[30, 50, 100]" :total="filteredRecords.length" layout="total, sizes, prev, pager, next" size="small" background @size-change="pageNum = 1" />
    </div>
  </div>
</template>

<script setup>
import { injectIncentiveUpload } from '@/composables/useIncentiveUpload'
import { onMounted } from 'vue'
import { Setting } from '@element-plus/icons-vue'

const {
  userStore, pagedRecords, filteredRecords, pageNum, pageSize,
  COLUMN_DEFS, hiddenCols, colSettingVisible, colDraft, isColVisible,
  loadColumnPref, openColSetting, saveColSetting, resetColDraft,
  canEdit, canDelete, scoreColor, formatDate, fileType, previewFile,
  startReplace, toggleShare, handleDeleteRecord, isRecordOwner,
} = injectIncentiveUpload()

// 【2026-08-17 修复】必须在解构之后调用: 此前写在解构前触发 TDZ ReferenceError,
// 组件 setup 直接崩溃 → 右侧表格整个不渲染(用户"看不到数据")
onMounted(loadColumnPref)
</script>

<style scoped>
.install-files .file-tag.previewable { cursor: pointer; text-decoration: underline; }
.install-files .file-tag.previewable:hover { color: #409eff; }
.right-panel { flex:1; background:#fff; padding:14px 18px; display:flex; flex-direction:column; min-height:0 }
.right-panel .el-table { flex:1; min-height:0 }
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
