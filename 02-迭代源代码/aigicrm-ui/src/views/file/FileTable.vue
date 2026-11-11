<template>
  <div class="file-list-panel">
    <!-- 【C12/D13 2026-10-08 二轮】列表请求期间 v-loading，与其他 18 处列表加载反馈对齐 -->
    <el-table v-loading="filesLoading" :data="files" style="width: 100%" size="small"
              :row-class-name="rowClassName" @row-click="onRowClick">
      <!-- 【2026-10-17 客户反馈】文件名必须看全: 去 show-overflow-tooltip 截断,列内自动换行(word-break) -->
      <el-table-column label="文件名称" min-width="220" class-name="name-col">
        <template #default="{ row }">
          <div class="file-name-cell">
            <!-- 【2026-11-08 用户令"文件管理里的图片要能看到"】图片行直接显示缩略图(签名 thumbnail 直链, 40px),
                 加载失败退化回类型徽标; 点击缩略=点行同款预览 -->
            <el-image
              v-if="isImageRow(row) && !thumbFailedMap[row.id]"
              class="file-thumb"
              :src="thumbUrlOf(row)"
              fit="cover"
              lazy
              @error="thumbFailedMap[row.id] = true"
              @click.stop="onRowClick(row)"
            >
              <template #error>
                <span class="file-icon" :class="'icon-' + getFileExt(row.fileName)" @click.stop="onRowClick(row)">{{ getFileExtLabel(row.fileName) }}</span>
              </template>
            </el-image>
            <span v-else class="file-icon" :class="'icon-' + getFileExt(row.fileName)">{{ getFileExtLabel(row.fileName) }}</span>
            <span class="file-name-text">{{ row.fileName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="文件大小" min-width="80" align="center">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="类型" min-width="60" align="center">
        <template #default="{ row }">
          <span class="type-tag" :class="'type-' + getFileExt(row.fileName)">{{ getFileType(row.fileName) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="uploaderName" label="上传者" min-width="70" align="center" />
      <el-table-column label="更新时间" min-width="95" align="center">
        <template #default="{ row }">{{ (row.createdAt || '').substring(0, 10) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="100" align="center">
        <template #default="{ row }">
          <div class="action-icons">
            <el-tooltip content="打开" placement="top">
              <el-icon class="act-icon" @click.stop="handlePreview(row)"><View /></el-icon>
            </el-tooltip>
            <el-tooltip v-if="(folderPerms.includes('download') || isOwn(row)) && folderDownloadable === 1" content="下载" placement="top">
              <el-icon class="act-icon" @click.stop="handleDownload(row)"><Download /></el-icon>
            </el-tooltip>
            <el-tooltip v-if="activeTab !== 'public' && activeTab !== 'shared_to_me' && canFileAction(row, 'file:share', 'share')" content="分享" placement="top">
              <el-icon class="act-icon" @click.stop="openShareDialog(row)"><Share /></el-icon>
            </el-tooltip>
            <el-tooltip v-if="canFileAction(row, 'file:edit', 'edit')" content="替换" placement="top">
              <el-icon class="act-icon" @click.stop="openReplaceFile(row)"><Refresh /></el-icon>
            </el-tooltip>
            <!-- 【2026-11-08 PC 补文件改名入口】后端 PUT /file/{id}/rename（FileController file:edit +
                 checkFile(P_RENAME) 双门），显隐按"全局码 file:edit ∧ (夹内 edit ∨ 是我传的)"
                 （2026-10-27 文件夹管理按钮同款口径）；【2026-11-10 用户令"A"】上传者本人恒可管理
                 自己的文件——夹权不再优先于上传人，isOwn 行级放行与后端本人短路同口径 -->
            <el-tooltip v-if="canRename(row)" content="改名" placement="top">
              <el-icon class="act-icon" @click.stop="handleRenameFile(row)"><EditPen /></el-icon>
            </el-tooltip>
            <el-tooltip v-if="canFileAction(row, 'file:delete', 'delete')" content="删除" placement="top">
              <el-icon class="act-icon act-danger" @click.stop="handleDelete(row)"><Delete /></el-icon>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="file-footer">
      <span class="file-total">共 {{ fileTotal }} 个文件</span>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="fileTotal"
        :page-sizes="pageSizes(query.size)"
        layout="sizes, prev, pager, next, jumper"
        @size-change="query.current = 1"
        size="small"
        @change="loadFiles"
      />
    </div>

    <!-- 替换文件隐藏选择器 -->
    <input ref="replaceFileInputRef" type="file" style="display:none" @change="handleReplaceChosen" />
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDownloadToken, refreshDownloadToken } from '@/utils/token'
// 【D16】View 图标已由 main.js menuIconList 全局注册(app.component)，本地 import 冗余已删；模板 <View/> 仍可解析
// （EditPen 同为 menuIconList 白名单成员，模板 <EditPen/> 直接解析）
import { replaceFile, renameFile } from '@/api/file'
import { injectFilePage } from '@/composables/useFilePage'
import { useUserStore } from '@/store/user'
import { pageSizes } from '@/utils/pagination'
import { previewKindOf } from '@/utils/media-type'

const {
  files, fileTotal, filesLoading, query, activeTab, folderPerms, folderDownloadable,
  loadFiles, loadFolders, loadTabCounts,
  handlePreview, handleDownload, openShareDialog, handleDelete,
  formatSize, getFileExt, getFileExtLabel, getFileType,
} = injectFilePage()

// 【2026-11-08 用户令"图片的可以预览查看"】点图片行直开预览(与行尾"眼睛"同一条链路);
// 非图片行保持原样(PDF 等仍走眼睛/下载, 避免误触重量级预览)。
const isImageRow = (row) => previewKindOf(row?.fileName) === 'image'
const rowClassName = ({ row }) => (isImageRow(row) ? 'row-click-preview' : '')
const onRowClick = (row) => {
  if (isImageRow(row)) handlePreview(row)
}

// 【2026-11-08 用户令"文件管理里的图片要能看到"】图片行缩略图(签名 thumbnail 直链 ≤300px):
// token 沿用页面既有 download-token(与预览/下载同源, 切集团自动随 store 刷新);
// 失败行记入 thumbFailedMap 退化回类型徽标(响应式 Map)。
const thumbFailedMap = reactive({})
const thumbUrlOf = (row) => {
  const t = getDownloadToken() || ''
  return `/api/file/download/${row.id}?thumbnail=1${t ? `&token=${encodeURIComponent(t)}` : ''}`
}
watch(files, async () => {
  if ((files.value || []).some(isImageRow)) {
    try { await refreshDownloadToken() } catch (e) { /* 缩略退化类型徽标 */ }
  }
}, { immediate: true })

// 文件替换（行操作触发，独立隐藏选择器）
const replaceFileTarget = ref(null)
const replaceFileInputRef = ref(null)
const replacing = ref(false)

// 【2026-11-08 PC 补文件改名入口 + 2026-11-10 用户令"A"上传者本人恒可管理自己的文件】
// 后端双门：全局权限码 file:edit（@PreAuthorize）+ checkFile(P_RENAME)（管理员/上传者本人/夹判定）。
// 前端显隐：全局码 ∧ (夹内 edit ∨ 这行是我传的)，只勾其一的角色不再看到必 403 按钮。
const userStore = useUserStore()
// 行级"是我传的"判定（列表行自带 uploaderId），本人对自己上传的文件恒显示 改名/替换/删除/分享/下载
const isOwn = (row) => row?.uploaderId != null && Number(row.uploaderId) === Number(userStore.userId)
// 【T4 2026-11-11 审计整改】行级写动作单源: 全局码 ∧ (夹 ACL ∨ isOwn)——与后端双门
// (@PreAuthorize 全局码 + checkFile 本人恒管/夹判定)同口径。此前分享/替换/删除只看
// 夹 ACL/isOwn, 无全局码者可见可点、提交必 403; canRename 是首个正确范式, 收敛于此。
// (MP files 页 buildFileRowView 的 canDelete/canShare/canEditFile 同口径, 11-08 批已就位)
const canFileAction = (row, code, folderPerm) =>
  userStore.hasPermission(code) && (folderPerms.value.includes(folderPerm) || isOwn(row))
const canRename = (row) => canFileAction(row, 'file:edit', 'edit')

// 【2026-11-08 用户令"改名不更改格式"】扩展名自动保留: 输入带不带/换不换后缀,
// 提交前统一剥掉输入后缀拼回原扩展名(与后端 keepOriginalExtension 同口径, 后端为铁律守卫)
const extOf = (n) => { const i = (n || '').lastIndexOf('.'); return i > 0 && i < n.length - 1 ? n.slice(i) : '' }
const keepOriginalExt = (input, original) => {
  const origExt = extOf(original)
  const inExt = extOf(input)
  const main = inExt ? input.slice(0, input.length - inExt.length) : input
  return origExt ? main + origExt : main
}
const handleRenameFile = async (row) => {
  try {
    const origExt = extOf(row.fileName)
    const { value } = await ElMessageBox.prompt(
      `请输入新的文件名${origExt ? `（扩展名 ${origExt} 自动保留，无需输入）` : ''}`,
      '文件改名', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: row.fileName || '',
      inputValidator: (v) => {
        const name = (v || '').trim()
        if (!name) return '文件名不能为空'
        if (name.length > 200) return '文件名过长（不超过 200 字符）'
        if (keepOriginalExt(name, row.fileName) === row.fileName) return '新文件名与原文件名相同'
        return true
      }
    })
    const finalName = keepOriginalExt(value.trim(), row.fileName)
    await renameFile(row.id, finalName)
    ElMessage.success(`改名成功：${finalName}`)
    loadFiles()
  } catch (e) {
    // cancel/close 静默；接口错误提示由 request 拦截器统一处理
  }
}

const openReplaceFile = (row) => {
  replaceFileTarget.value = row
  replaceFileInputRef.value?.click()
}

const handleReplaceChosen = async (e) => {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || !replaceFileTarget.value || replacing.value) return
  const target = replaceFileTarget.value
  replaceFileTarget.value = null
  replacing.value = true
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await replaceFile(target.id, formData)
    ElMessage.success('替换成功，版本 ' + (res.data?.version ?? ''))
    loadFiles()
    loadFolders()
    loadTabCounts()
  } catch (err) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    replacing.value = false
  }
}
</script>

<style scoped>
.file-list-panel {
  flex: 1;
  min-width: 0;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
}
.file-name-cell {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
/* 【2026-10-17】文件名全显: 允许两行内换行(超两行才省略),图标对齐首行 */
.file-name-text {
  min-width: 0;
  word-break: break-all;
  white-space: normal;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  line-height: 1.5;
  padding-top: 4px;
}
.file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
  margin-top: 2px;
}
.icon-doc { background: #4a90d9; }
.icon-xls { background: #34a853; }
.icon-pdf { background: #ea4335; }
.icon-ppt { background: var(--el-color-warning); }
.icon-img { background: #9b59b6; }
.icon-video { background: #10b981; }
.icon-other { background: var(--el-color-info); }
.type-tag {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 3px;
}
.type-doc { color: #4a90d9; background: #eaf2fc; }
.type-xls { color: #34a853; background: #e8f5e9; }
.type-pdf { color: #ea4335; background: #fce8e6; }
.type-ppt { color: var(--el-color-warning); background: #fdf6ec; }
.type-img { color: #9b59b6; background: #f3e8ff; }
.type-video { color: #10b981; background: #ecfdf5; }
.type-other { color: var(--el-color-info); background: #f4f4f5; }

/* 操作图标 */
.action-icons {
  display: flex;
  gap: 8px;
  justify-content: center;
}
.act-icon {
  font-size: 16px;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.15s;
}
.act-icon:hover { color: var(--el-color-primary); }
.act-danger:hover { color: var(--el-color-danger); }
.file-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 12px;
}
.file-total { font-size: 12px; color: var(--text-muted); }
/* 【2026-11-08】图片行可点直预览: 手型光标提示可交互 */
:deep(.row-click-preview) { cursor: pointer; }
/* 【2026-11-08】文件名列缩略图(图片行): 40px 方块, 加载失败由模板 error 槽退化类型徽标 */
.file-thumb { width: 40px; height: 40px; border-radius: 6px; flex: 0 0 40px; overflow: hidden; }
</style>
