<template>
  <!-- 右侧：已上传文件表格 -->
  <div class="right-panel">
    <el-table :data="pagedRecords" stripe style="width:100%" empty-text="暂无已上传文件" size="small" scrollbar-always-on @row-click="startReplace" :row-style="{cursor:'pointer'}">
      <!-- 双行行卡（2026-10-10 重设计）：11列收6列，行业/相关产品/上传人/日期/修改收进第二行灰字meta，空值零占位 -->
      <el-table-column label="大类·子类" min-width="200">
        <template #default="{ row }">
          <div class="cell-main">{{ row.majorMinor }}</div>
          <div v-if="row.tier || row.industry || row.relatedProducts" class="cell-meta">
            <!-- 三级分类时 majorMinor 只到中类, 叶子子类(tier)在此补全(四轮复检) -->
            <template v-if="row.tier">{{ row.tier }}</template>
            <template v-if="row.tier && (row.industry || row.relatedProducts)"> · </template>
            <template v-if="row.industry">{{ row.industry }}</template>
            <template v-if="row.industry && row.relatedProducts"> · </template>
            <template v-if="row.relatedProducts">{{ row.relatedProducts }}</template>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="安装文件" min-width="200">
        <template #default="{ row }">
          <div class="install-files">
            <!-- 【2026-11-08 用户令"激励上传里的图片也要能预览"】图片槽位直接显示缩略图
                 (签名 thumbnail 直链, 与文件管理行缩略同款), 点缩略=原文件名点击同款 FilePreview 预览;
                 非图片槽位与加载失败保持原文字 tag(可点预览的口径不变) -->
            <template v-for="slot in slotDefs(row)" :key="slot.key">
              <div v-if="slot.isImage" class="slot-thumb" @click.stop="previewFile(slot.id, slot.name)" :title="slot.name">
                <el-image class="slot-thumb-img" :src="fileThumbUrl(slot.fid)" fit="cover" lazy>
                  <template #error>
                    <span class="file-tag" :class="{previewable: slot.type!=='doc'}" @click.stop="previewFile(slot.id, slot.name)"><b>{{ slot.label }}</b>{{ slot.name }}</span>
                  </template>
                </el-image>
                <span class="slot-thumb-badge">{{ slot.label }}</span>
              </div>
              <span v-else class="file-tag" :class="{previewable: slot.type!=='doc'}" @click.stop="previewFile(slot.id, slot.name)"><b>{{ slot.label }}</b>{{ slot.name }}</span>
            </template>
            <span v-if="!row.filePreName && !row.fileMidName && !row.filePostName" class="file-cell">
              <el-icon class="file-icon"><Document /></el-icon>{{ row.fileName || '—' }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="客户" min-width="180">
        <template #default="{ row }">
          <div class="cell-main customer-link">{{ row.customerName || '—' }}</div>
          <div v-if="row.userName || row.createdAt || row.updatedByName" class="cell-meta">{{ metaLine(row) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="分" width="56" align="center">
        <template #default="{ row }">
          <span :style="{ color: scoreColor(row.score), fontWeight: 600 }">{{ row.score }}</span>
        </template>
      </el-table-column>
      <el-table-column label="共享" min-width="56" align="center">
        <template #default="{ row }">
          <!-- 【工作流复核确认修复】外包 .stop 防冒泡: 点开关会同时执行开关并弹出行点击的追加弹窗 -->
          <span v-if="isRecordOwner(row)" @click.stop>
            <el-switch
              :model-value="!!row.shared"
              size="small"
              @change="(v) => toggleShare(row, v)"
            />
          </span>
          <span v-else :class="row.shared ? 'shared-yes' : 'shared-no'">{{ row.shared ? '● 共享' : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="104" align="center">
        <template #default="{ row }">
          <el-button v-if="userStore.isAdmin() || canEdit || row.userId === userStore.userId" link type="primary" size="small" @click.stop="startReplace(row)">追加</el-button>
          <el-button v-if="userStore.isAdmin() || canDelete || row.userId === userStore.userId" link type="danger" size="small" @click.stop="handleDeleteRecord(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="list-footer">
      <span class="list-footer-count">共 <b>{{ filteredRecords.length }}</b> 条</span>
      <!-- 2026-09-13：补「每页条数」下拉（30/50/100），此前只有翻页按钮 -->
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="filteredRecords.length"
        :page-sizes="pageSizes(pageSize)"
        layout="sizes, prev, pager, next, jumper"
        size="small"
        background
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { injectIncentiveUpload } from '@/composables/useIncentiveUpload'
import { pageSizes } from '@/utils/pagination'

const {
  userStore, pagedRecords, filteredRecords, pageNum, pageSize,
  canEdit, canDelete, scoreColor, formatDate, fileType, previewFile,
  fileThumbUrl, parseFids,
  startReplace, toggleShare, handleDeleteRecord, isRecordOwner,
} = injectIncentiveUpload()

// 【2026-11-08 槽位缩略】前/中/后三槽位统一描述: isImage=首文件是图片(缩略形态),
// fid=首文件 id(缩略直链用), id/name 原样传 previewFile(保持多文件预览口径)
const slotDefs = (row) => {
  const defs = [
    { key: 'pre', label: '前', id: row.filePreId, name: row.filePreName },
    { key: 'mid', label: '中', id: row.fileMidId, name: row.fileMidName },
    { key: 'post', label: '后', id: row.filePostId, name: row.filePostName },
  ]
  return defs
    .filter(d => d.name)
    .map(d => {
      const firstName = String(d.name || '').split(',')[0].trim()
      const fid = parseFids(d.id)[0]
      const type = fileType(firstName)
      return { ...d, isImage: type === 'image' && !!fid, fid, type }
    })
}

// 【六轮复检修复】meta 行整体拼接: 空 userName/createdAt 不产生孤立「·」前缀,
// updatedAt 缺失时只显示「改 张三」不再带「—」
const metaLine = (row) => {
  const head = [row.userName, row.createdAt ? formatDate(row.createdAt) : ''].filter(Boolean).join(' · ')
  const tail = row.updatedByName ? `改 ${row.updatedByName}${row.updatedAt ? ' ' + formatDate(row.updatedAt) : ''}` : ''
  return [head, tail].filter(Boolean).join(' · ')
}
// 【六轮复检修复】previewable 按首文件名判定: 多文件槽位名是逗号连接串,
// 整串判定以末尾扩展名为准会与点击后实际预览的第一个文件错位
const firstType = (names) => fileType(String(names || '').split(',')[0].trim())

// 切换每页条数后回到第 1 页（否则可能停在一个已不存在的页码上看到空表）
const onSizeChange = (size) => {
  pageSize.value = size
  pageNum.value = 1
}
</script>

<style scoped>
.install-files .file-tag.previewable { cursor: pointer; text-decoration: underline; }
.install-files .file-tag.previewable:hover { color: var(--el-color-primary); }
/* 双行行卡：主行业务信息，meta行灰字审计信息，两行各自单行省略 */
.cell-main { font-size:13px; color:#1f2329; font-weight:500; overflow:hidden; text-overflow:ellipsis; white-space:nowrap }
/* 【复检修复】客户名仅作主色调标识——无独立点击行为, 去掉 pointer/hover 假链接暗示 */
.cell-main.customer-link { color:var(--el-color-primary); }
.cell-meta { font-size:12px; color:var(--el-text-color-secondary); margin-top:2px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap }
/* min-width:0 让右栏随视口收缩（flex 子项默认 min-width:auto 会被表格最小列宽顶死，
   窄屏下整表被 .main-layout 的 overflow:hidden 隐形裁切）；收缩后由 el-table 自带横向滚动接管 */
.right-panel { flex:1; min-width:0; background:#fff; padding:14px 18px; display:flex; flex-direction:column; min-height:0 }
.right-panel .el-table { flex:1; min-height:0 }
.list-footer { display:flex; align-items:center; justify-content:space-between; padding:10px 2px 0; margin-top:8px; border-top:1px solid #f0f2f5 }
.list-footer-count { font-size:13px; color:#606266 }
.list-footer-count b { color:var(--el-color-primary); font-weight:700; margin:0 2px }
.file-cell { display: inline-flex; align-items: center; gap: 4px; }
.file-icon { color: var(--el-color-info); font-size: 14px; flex-shrink: 0; }
.shared-yes { color: var(--el-color-success); font-size: 12px; font-weight: 500; }
.shared-no { color: var(--text-placeholder); }
/* 【2026-11-08 槽位缩略】44px 方块+左上角前/中/后角标; 失败由 error 槽退回文字 tag */
.slot-thumb { position: relative; width: 44px; height: 44px; flex: 0 0 44px; border-radius: 6px; overflow: hidden; cursor: pointer; }
.slot-thumb-img { width: 100%; height: 100%; }
.slot-thumb-badge { position: absolute; left: 0; top: 0; background: rgba(0,0,0,.55); color: #fff; font-size: 10px; line-height: 1; padding: 2px 4px; border-radius: 0 0 6px 0; }
</style>
