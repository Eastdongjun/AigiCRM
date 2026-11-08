<template>
  <el-dialog v-model="aclDialogVisible" :title="'权限设置：' + (aclFolder?.folderName || '')" width="560px">
    <div class="acl-block">
      <div class="acl-block-title">授权角色</div>
      <el-select v-model="aclSelectedRoles" multiple filterable placeholder="选择要授权的角色..." style="width:100%">
        <el-option v-for="r in allRoles" :key="r.id" :label="r.roleName" :value="r.id" />
      </el-select>
      <div class="acl-perms">
        <el-checkbox-group v-model="aclSelectedPerms">
          <el-checkbox v-for="p in PERM_OPTIONS" :key="p.value" :value="p.value">{{ p.label }}</el-checkbox>
        </el-checkbox-group>
      </div>
      <div>
        <el-button type="primary" :loading="aclSaving" :disabled="!aclSelectedRoles.length || !aclSelectedPerms.length" @click="saveAcl">
          保存授权
        </el-button>
      </div>
    </div>
    <el-divider />
    <div class="acl-block">
      <div class="acl-block-title">下载开关</div>
      <div class="acl-download-row">
        <span>允许下载该文件夹下的文件</span>
        <el-switch v-model="aclDownloadable" @change="handleDownloadableChange" />
      </div>
    </div>
    <el-divider />
    <div class="acl-block">
      <div class="acl-block-title">已授权角色</div>
      <div v-if="aclList.length" class="acl-list">
        <div v-for="row in aclList" :key="row.roleId" class="acl-item">
          <span class="acl-role">{{ row.roleName }}</span>
          <span class="acl-tags">
            <el-tag v-for="p in row.perms" :key="p" size="small" type="info">{{ permLabel(p) }}</el-tag>
          </span>
          <el-button type="danger" link size="small" @click="removeAcl(row)">移除</el-button>
        </div>
      </div>
      <el-empty v-else description="暂无授权角色" :image-size="50" />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getFolderAcl, saveFolderAcl, removeFolderAcl, setFolderDownloadable, getFolderPermissions, getAclRoles } from '@/api/file'
import { injectFilePage } from '@/composables/useFilePage'

const { aclDialogVisible, aclFolder, selectedFolderId, loadFolderPerms, folderDownloadable } = injectFilePage()

const allRoles = ref([])
const aclList = ref([])
const aclSelectedRoles = ref([])
const aclSelectedPerms = ref([])
const aclDownloadable = ref(true)
const aclSaving = ref(false)

// 6 项权限选项
const PERM_OPTIONS = [
  { value: 'view', label: '查看' },
  { value: 'upload', label: '上传' },
  { value: 'edit', label: '编辑（重命名/替换）' },
  { value: 'delete', label: '删除' },
  { value: 'share', label: '分享' },
  { value: 'download', label: '下载' }
]
const permLabel = (v) => PERM_OPTIONS.find(p => p.value === v)?.label || v

const loadAclList = async (id) => {
  try {
    const res = await getFolderAcl(id)
    aclList.value = res.data || []
  } catch (e) {}
}

const loadAclDownloadable = async (id) => {
  try {
    const res = await getFolderPermissions(id)
    aclDownloadable.value = (res.data?.downloadable ?? 1) === 1
  } catch (e) {}
}

// 打开时重置选择并加载已授权列表/下载开关（角色列表懒加载一次）
watch([aclDialogVisible, aclFolder], async ([vis, folder]) => {
  if (vis && folder) {
    aclSelectedRoles.value = []
    aclSelectedPerms.value = []
    if (!allRoles.value.length) {
      try {
        // 【体检 P1 2026-10-12】角色字典改走 ACL 专用轻量端点(file:acl:manage 同权限放行):
        // 原 /system/roles 要求 system:role:view(仅 admin/0), 05/06 与非 admin 夹主打开弹窗
        // 时下拉 403 被静默吞恒空, 保存按钮恒禁用——新增授权路径断裂
        const res = await getAclRoles()
        allRoles.value = res.data || []
      } catch (e) {}
    }
    await Promise.all([loadAclList(folder.id), loadAclDownloadable(folder.id)])
  }
})

const saveAcl = async () => {
  if (!aclSelectedRoles.value.length || !aclFolder.value) return
  const folder = aclFolder.value
  aclSaving.value = true
  try {
    // 【2026-11-08 审计修复】多角色保存由 for-await 串行改 Promise.allSettled 并行：
    // 原实现中途某角色失败即中断（余下角色静默未保存、已保存部分零提示），且逐个往返耗时随角色数线性涨。
    // 现并行下发全部角色，结束后按「成功 N / 失败 M」汇总；失败角色保留勾选便于直接重试。
    const roleIds = [...aclSelectedRoles.value]
    const results = await Promise.allSettled(
      roleIds.map(roleId => saveFolderAcl(folder.id, { roleId, perms: aclSelectedPerms.value }))
    )
    const failedIds = roleIds.filter((_, i) => results[i].status === 'rejected')
    const okCount = roleIds.length - failedIds.length
    const failCount = failedIds.length
    if (failCount === 0) {
      ElMessage.success(`权限已保存（成功 ${okCount} / 失败 ${failCount}）`)
      aclSelectedRoles.value = []
      aclSelectedPerms.value = []
    } else if (okCount === 0) {
      // 单条错误的具体原因已由 request 拦截器逐条提示，此处只给汇总
      ElMessage.error(`保存失败：成功 ${okCount} / 失败 ${failCount}，请重试`)
    } else {
      ElMessage.warning(`部分成功：成功 ${okCount} / 失败 ${failCount}，失败角色已保留勾选，请重试`)
      aclSelectedRoles.value = failedIds
    }
    if (okCount > 0) {
      await loadAclList(folder.id)
      // 授权变更影响当前选中文件夹时同步刷新列表页按钮状态
      if (selectedFolderId.value === folder.id) loadFolderPerms()
    }
  } finally {
    aclSaving.value = false
  }
}

const removeAcl = async (row) => {
  if (!aclFolder.value) return
  try {
    await removeFolderAcl(aclFolder.value.id, row.roleId)
    ElMessage.success('已移除该角色授权')
    await loadAclList(aclFolder.value.id)
    // 授权变更影响当前选中文件夹时同步刷新列表页按钮状态
    if (selectedFolderId.value === aclFolder.value.id) loadFolderPerms()
  } catch (e) {}
}

const handleDownloadableChange = async (val) => {
  if (!aclFolder.value) return
  try {
    await setFolderDownloadable(aclFolder.value.id, val ? 1 : 0)
    ElMessage.success(val ? '已允许下载' : '已关闭下载')
    // 若正是当前选中文件夹，同步列表页按钮状态
    if (selectedFolderId.value === aclFolder.value.id) {
      folderDownloadable.value = val ? 1 : 0
    }
  } catch (e) {
    aclDownloadable.value = !val
  }
}
</script>

<style scoped>
/* 权限设置弹窗 */
.acl-block { display: flex; flex-direction: column; gap: 10px }
.acl-block-title { font-size: 13px; font-weight: 600; color: #333 }
.acl-perms { display: flex; flex-wrap: wrap; gap: 4px 16px; padding: 4px 0 }
.acl-download-row { display: flex; align-items: center; justify-content: space-between; font-size: 13px; color: #666 }
.acl-list { max-height: 260px; overflow: auto; display: flex; flex-direction: column; gap: 6px }
.acl-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; background: #f5f7fa; border-radius: var(--radius-sm) }
.acl-role { font-size: 13px; font-weight: 500; color: #303133; min-width: 80px; flex-shrink: 0 }
.acl-tags { flex: 1; display: flex; flex-wrap: wrap; gap: 4px }
</style>
