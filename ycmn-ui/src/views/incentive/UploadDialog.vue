<template>
  <!-- 关联客户 + 安装文件 弹窗 -->
  <el-dialog v-model="showCustomerDialog" width="780px" top="2vh" :close-on-click-modal="false" class="upload-dlg">
    <template #header>
      <div class="dlg-header">
        <div class="dlg-header-left">
          <span class="dlg-title">{{ pendingReplace ? '追加文件' : '上传激励文件' }}</span>
          <el-tag v-if="pendingChild" size="small" effect="dark" type="warning">{{ pendingChild.categoryName }}</el-tag>
        </div>
      </div>
    </template>

    <div class="dlg-body">
      <!-- 基本信息 -->
      <div class="dlg-section">
        <div class="dlg-sec-head">
          <span class="dlg-sec-icon" style="background:#ecf5ff;color:#409eff"><el-icon><User /></el-icon></span>
          <span class="dlg-sec-title">基本信息</span>
        </div>
        <div class="dlg-row cols-3-2">
          <div class="dlg-field">
            <label>关联客户 <i>*</i></label>
            <el-select v-model="selectedCustomerId" filterable remote :remote-method="searchCustomers" placeholder="搜索客户名称..." :loading="customerLoading" size="large">
              <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id">
                <span>{{ c.customerName }}<el-tag v-if="c._shared" size="small" type="warning" style="margin-left:6px">分享</el-tag></span>
              </el-option>
            </el-select>
            <div v-if="selectedCustomerName" class="dlg-confirm">✅ {{ selectedCustomerName }}</div>
          </div>
          <div class="dlg-field"><label>行业</label>
            <el-select v-model="selectedIndustry" placeholder="选择" clearable size="large"><el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" /></el-select>
          </div>
        </div>
        <div class="dlg-field"><label>相关产品</label>
          <div style="display:flex;gap:8px">
            <el-select v-model="relatedCat" placeholder="选择大类" size="large" style="flex:1" @change="relatedSub=''">
              <el-option v-for="k in productCatKeys" :key="k" :label="k" :value="k" />
            </el-select>
            <el-select v-model="relatedSub" placeholder="选择小类" size="large" style="flex:1" :disabled="!relatedCat">
              <el-option v-for="s in (productCats[relatedCat] || [])" :key="s" :label="s" :value="s" />
            </el-select>
          </div></div>
        <div v-if="categoryCodeOf(pendingChild).startsWith('trial')" class="dlg-field">
          <label>具体产品 <span class="dlg-opt">选填</span></label>
          <el-input v-model="specificProduct" placeholder="填写具体产品名称/型号，选填" size="large" maxlength="200" show-word-limit clearable />
        </div>
      </div>

      <!-- 安装详情 -->
      <div class="dlg-section">
        <div class="dlg-sec-head">
          <span class="dlg-sec-icon" style="background:#fef0f0;color:#f56c6c"><el-icon><Edit /></el-icon></span>
          <span class="dlg-sec-title">{{ secTitle }}</span>
        </div>
        <div class="dlg-field"><label>{{ detailLabel }}</label><el-input v-model="preInstallQuestion" type="textarea" :rows="2" :placeholder="isSingleSlot ? '描述本次详情...' : '描述安装前存在的问题...'" /></div>
        <div class="dlg-field" v-if="isTrial"><label>{{ remarkLabel }}</label><el-input v-model="remarkText" type="textarea" :rows="2" placeholder="安装备注..." /></div>
      </div>

      <!-- 安装文件：单槽（visit/exchange/plan） -->
      <div v-if="isSingleSlot" class="dlg-section highlight">
        <div class="dlg-sec-head">
          <span class="dlg-sec-icon" style="background:#f0f9eb;color:#67c23a"><el-icon><Picture /></el-icon></span>
          <span class="dlg-sec-title">{{ slotLabel }}</span>
          <span class="dlg-sec-tip">至少 1 个，最多 {{ MAX_FILES }} 个</span>
        </div>
        <div class="file-slot-cards">
          <div v-for="(file, idx) in installFiles.pre" :key="idx" class="file-card has" @click.stop>
            <img v-if="!file._existing && isImage(file.name)" :src="getObjectURL(file)" class="file-card-img" />
            <div v-else class="file-card-icon"><el-icon :size="file._existing?20:24"><Document /></el-icon></div>
            <span class="file-card-del" @click.stop="removeSlotFile('pre', idx)"><el-icon><Close /></el-icon></span>
            <span class="file-card-name">{{ file.name?.substring(0, 8) }}{{ file.name?.length > 8 ? '…' : '' }}</span>
          </div>
          <div v-if="installFiles.pre.length < MAX_FILES" class="file-card add" @click="pickFile('pre')">
            <el-icon :size="28"><Plus /></el-icon>
          </div>
        </div>
      </div>

      <!-- 安装文件：三槽（trial） -->
      <div v-else class="dlg-section highlight">
        <div class="dlg-sec-head">
          <span class="dlg-sec-icon" style="background:#f0f9eb;color:#67c23a"><el-icon><Picture /></el-icon></span>
          <span class="dlg-sec-title">安装文件</span>
          <span class="dlg-sec-tip">每槽位最多 5 个文件</span>
        </div>
        <div class="file-slots">
          <div v-for="slot in fileSlots" :key="slot.key" class="file-slot">
            <div class="file-slot-head">
              <span class="file-slot-dot" :class="'dot-'+slot.key"></span>
              <span class="file-slot-label">{{ slot.label }}</span>
              <span class="file-slot-count">{{ installFiles[slot.key].length }}/{{ MAX_FILES }}</span>
            </div>
            <div class="file-slot-cards">
              <div v-for="(file, idx) in installFiles[slot.key]" :key="idx" class="file-card has" @click.stop>
                <img v-if="!file._existing && isImage(file.name)" :src="getObjectURL(file)" class="file-card-img" />
                <div v-else class="file-card-icon">
                  <el-icon :size="file._existing?20:24"><Document /></el-icon>
                </div>
                <span class="file-card-del" @click.stop="removeSlotFile(slot.key, idx)"><el-icon><Close /></el-icon></span>
                <span class="file-card-name">{{ file.name?.substring(0, 8) }}{{ file.name?.length > 8 ? '…' : '' }}</span>
              </div>
              <div v-if="installFiles[slot.key].length < MAX_FILES" class="file-card add" @click="pickFile(slot.key)">
                <el-icon :size="28"><Plus /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 跟踪记录（仅 trial 保留） -->
      <div v-if="!isSingleSlot" class="dlg-section">
        <div class="dlg-sec-head">
          <span class="dlg-sec-icon" style="background:#f5f0ff;color:#9c27b0"><el-icon><Notebook /></el-icon></span>
          <span class="dlg-sec-title">跟踪记录</span>
          <span class="dlg-sec-tip">不加分</span>
        </div>
        <div class="file-slot-cards">
          <div v-for="(file, idx) in trackFile" :key="idx" class="file-card has" @click.stop>
            <img v-if="!file._existing && isImage(file.name)" :src="getObjectURL(file)" class="file-card-img" />
            <div v-else class="file-card-icon"><el-icon :size="20"><Document /></el-icon></div>
            <span class="file-card-del" @click.stop="removeSlotFile('track', idx)"><el-icon><Close /></el-icon></span>
            <span class="file-card-name">{{ file.name?.substring(0, 8) }}{{ file.name?.length > 8 ? '…' : '' }}</span>
          </div>
          <div v-if="trackFile.length < MAX_FILES" class="file-card add" @click="pickTrackFile()">
            <el-icon :size="28"><Plus /></el-icon>
            <span style="font-size:11px;color:#909399;margin-top:4px">选择文件</span>
          </div>
        </div>
      </div>

      <!-- 审计信息（编辑/追加时展示：上传人/上传日期/最后修改人/最后修改时间） -->
      <div v-if="pendingReplace" class="dlg-section audit-section">
        <div class="dlg-sec-head">
          <span class="dlg-sec-icon" style="background:#f5f0ff;color:#9c27b0"><el-icon><InfoFilled /></el-icon></span>
          <span class="dlg-sec-title">审计信息</span>
        </div>
        <div class="audit-grid">
          <div class="audit-item"><span class="audit-label">上传人</span><span class="audit-value">{{ pendingReplace.userName || '—' }}</span></div>
          <div class="audit-item"><span class="audit-label">上传日期</span><span class="audit-value">{{ formatDate(pendingReplace.createdAt) }}</span></div>
          <div class="audit-item"><span class="audit-label">最后修改人</span><span class="audit-value">{{ pendingReplace.updatedByName || '—' }}</span></div>
          <div class="audit-item"><span class="audit-label">最后修改时间</span><span class="audit-value">{{ pendingReplace.updatedByName ? formatDate(pendingReplace.updatedAt) : '—' }}</span></div>
        </div>
      </div>

      <!-- 共享 + 提示 -->
      <div class="dlg-section last">
        <div class="dlg-row">
          <div style="display:flex;align-items:center;gap:10px">
            <label style="font-size:13px;font-weight:600;color:#4e5969">共享给团队</label>
            <el-switch v-model="shareToTeam" :disabled="!canToggleShare" />
            <span v-if="shareToTeam" class="shared-on">● 已共享</span>
            <span v-if="!canToggleShare" style="font-size:11px;color:#909399">（仅上传人本人可修改分享）</span>
          </div>
          <div class="dlg-tip">
            <el-icon><InfoFilled /></el-icon> 单文件最大 100MB，支持图片、PDF、Word、Excel、压缩包等格式
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div v-if="uploading && totalNewFiles > 0" class="upload-progress">
          <div class="progress-bar"><div class="progress-fill" :style="{width:uploadProgress+'%'}"></div></div>
          <div class="progress-text">{{ uploadProgressText }} — {{ uploadProgress }}%</div>
        </div>
        <div class="dlg-footer-btns">
          <el-button size="large" @click="showCustomerDialog=false">取消</el-button>
          <el-button type="primary" size="large" :loading="uploading" :disabled="!canSubmit" @click="confirmUpload">
            {{ pendingReplace?'追加提交':'上传并提交' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { injectIncentiveUpload } from '@/composables/useIncentiveUpload'

const {
  showCustomerDialog, pendingReplace, pendingChild,
  selectedCustomerId, customerOptions, customerLoading, searchCustomers, selectedCustomerName,
  selectedIndustry, industryOptions, relatedCat, relatedSub, productCatKeys, productCats,
  categoryCodeOf, specificProduct,
  secTitle, detailLabel, isSingleSlot, preInstallQuestion, isTrial, remarkLabel, remarkText,
  slotLabel, MAX_FILES, installFiles, isImage, getObjectURL, removeSlotFile, pickFile,
  fileSlots, trackFile, pickTrackFile,
  shareToTeam, uploading, totalNewFiles, uploadProgress, uploadProgressText, canSubmit, confirmUpload,
  canToggleShare, formatDate,
} = injectIncentiveUpload()
</script>

<style scoped>
.dlg-header { display:flex; align-items:center; justify-content:space-between }
.dlg-header-left { display:flex; align-items:center; gap:10px }
.dlg-title { font-size:18px; font-weight:700; color:#1f2329 }
/* 滚动统一由 .upload-dlg :deep(.el-dialog__body)（flex:1 overflow-y:auto）处理 */
.dlg-body { padding-right:4px }

.dlg-section { background:#fff; border:1px solid #e8ecf1; border-radius:12px; padding:18px 20px; margin-bottom:12px }
.dlg-section.highlight { background:linear-gradient(135deg,#f6fdf9,#f0f7ff); border-color:#c8e0d8 }
.dlg-section.last { border:none; background:transparent; padding:8px 0 0 }
.dlg-sec-head { display:flex; align-items:center; gap:8px; margin-bottom:14px }
.dlg-sec-icon { width:28px; height:28px; border-radius:7px; display:flex; align-items:center; justify-content:center; font-size:14px; flex-shrink:0 }
.dlg-sec-title { font-size:14px; font-weight:700; color:#1f2329 }
.dlg-sec-tip { font-size:11px; color:#909399; margin-left:auto }

.dlg-row { display:flex; gap:12px }
.dlg-row.cols-3-2 { display:grid; grid-template-columns:2fr 1fr; gap:12px; align-items:start }
.dlg-field { min-width:0 }
.dlg-field+.dlg-field { margin-top:10px }
.dlg-row .dlg-field { margin-top:0 }
.dlg-field label { display:block; font-size:12px; font-weight:600; color:#4e5969; margin-bottom:4px }
.dlg-field label i { color:#f56c6c; font-style:normal }
.dlg-opt { font-size:12px; font-weight:400; color:#a8abb2; margin-left:4px }
.dlg-field .el-select,.dlg-field .el-input { width:100% }
.dlg-confirm { color:#67c23a; font-weight:600; font-size:13px; margin-top:4px }
.span-2 { grid-column:span 1 }

/* 审计信息 */
.audit-section { background:#fdfbff; border-color:#e4ddf5 }
.audit-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:10px }
.audit-item { display:flex; flex-direction:column; gap:2px; padding:8px 10px; background:#f7f5fc; border-radius:8px }
.audit-label { font-size:11px; color:#909399 }
.audit-value { font-size:13px; font-weight:600; color:#4e5969 }
@media (max-width:720px){ .audit-grid { grid-template-columns:repeat(2,1fr) } }

.dlg-tip { display:flex; align-items:center; gap:4px; font-size:12px; color:#909399; margin-top:8px }
.shared-on { color:#67c23a; font-size:12px; font-weight:500 }

/* File Slots */
.file-slots { display:flex; gap:12px }
.file-slot { flex:1; min-width:0 }
.file-slot-head { display:flex; align-items:center; gap:6px; margin-bottom:8px }
.file-slot-dot { width:8px; height:8px; border-radius:50%; flex-shrink:0 }
.dot-pre { background:#409eff } .dot-mid { background:#e6a23c } .dot-post { background:#67c23a }
.file-slot-label { font-size:13px; font-weight:600; color:#333 }
.file-slot-count { font-size:11px; color:#909399; margin-left:auto }

.file-slot-cards { display:flex; flex-wrap:wrap; gap:8px }
.file-card { width:88px; height:100px; border:2px dashed #dcdfe6; border-radius:10px; display:flex; flex-direction:column; align-items:center; justify-content:center; cursor:pointer; transition:all .2s; position:relative; overflow:hidden; background:#fff }
.file-card:hover { border-color:#409eff; background:#f0f6ff }
.file-card.add { gap:2px }
.file-card.add .el-icon { color:#c0c4cc } .file-card.add:hover .el-icon { color:#409eff }
.file-card.has { border-style:solid; border-color:#d4e8d4; background:#f6fdf6; cursor:default }
.file-card.has:hover { border-color:#a3d9a3 }
.file-card-img { width:100%; height:100%; object-fit:cover; border-radius:8px }
.file-card-icon { display:flex; align-items:center; justify-content:center; color:#909399 }
.file-card-del { position:absolute; top:2px; right:2px; width:20px; height:20px; border-radius:50%; background:rgba(0,0,0,.5); color:#fff; display:flex; align-items:center; justify-content:center; font-size:12px; opacity:0; transition:opacity .15s }
.file-card:hover .file-card-del { opacity:1 }
.file-card-name { font-size:10px; color:#606266; text-align:center; margin-top:2px; max-width:80px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding:0 2px }
.file-card-del:hover { background:#f56c6c }

.dlg-footer-btns { display:flex; justify-content:flex-end; gap:10px; margin-top:8px }
.up-card-preview{width:100%;height:90px;overflow:hidden;display:flex;align-items:center;justify-content:center;background:#f5f5f5;border-radius:6px;margin-bottom:6px}
.up-card-preview img{width:100%;height:100%;object-fit:cover}
.up-card-icon{font-size:36px}
.up-card-name{font-size:11px;color:#555;text-align:center;word-break:break-all;max-width:100%;padding:0 4px}
.up-card-del{position:absolute;top:6px;right:6px;width:22px;height:22px;border-radius:50%;background:rgba(0,0,0,.5);color:#fff;display:flex;align-items:center;justify-content:center;font-size:12px;cursor:pointer;transition:background .2s}
.up-card-del:hover{background:#f56c6c}
.up-slot-group{margin-bottom:6px}
.up-slot-label{font-size:12px;color:#606266;font-weight:500;margin-bottom:4px}
.up-slot-files{display:flex;flex-wrap:wrap;gap:0}

.upload-dlg :deep(.el-dialog){display:flex;flex-direction:column;max-height:92vh;margin-top:4vh!important;margin-bottom:4vh!important}
.upload-dlg :deep(.el-dialog__header){flex-shrink:0}
.upload-dlg :deep(.el-dialog__body){flex:1;overflow-y:auto;min-height:0}
.upload-dlg :deep(.el-dialog__footer){flex-shrink:0}
@media (max-height:800px){.upload-dlg :deep(.el-dialog){max-height:96vh;margin-top:2vh!important;margin-bottom:2vh!important;width:95vw!important}}
@media (max-width:720px){.upload-dlg :deep(.el-dialog){width:96vw!important}}

.upload-progress { margin: 8px 0 12px }
.progress-bar { height: 6px; background: #e8e8e8; border-radius: 3px; overflow: hidden }
.progress-fill { height: 100%; background: linear-gradient(90deg, #409eff, #66b1ff); border-radius: 3px; transition: width .3s ease }
.progress-text { font-size: 12px; color: #909399; margin-top: 4px; text-align: center }
</style>
