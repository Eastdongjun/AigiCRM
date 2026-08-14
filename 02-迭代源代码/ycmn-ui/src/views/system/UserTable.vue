<template>
  <div class="section-box table-box">
    <div ref="tableWrapRef" style="flex:1;overflow:auto">
    <el-table :data="tableData" stripe style="width: 100%" size="small" :height="tableHeight">
      <el-table-column type="index" label="#" width="45" />
      <el-table-column label="编号" min-width="100">
        <template #default="{ row }">
          <span class="user-no">{{ row.username }}</span>
        </template>
      </el-table-column>
      <el-table-column label="姓名" min-width="110">
        <template #default="{ row }">
          <div class="user-cell">
            <span class="user-avatar">{{ (row.realName || '?').slice(0, 1) }}</span>
            <span class="user-name">{{ row.realName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="115" />
      <el-table-column prop="managerName" label="直接上级" width="80">
        <template #default="{ row }">
          <span :class="{ 'no-value': !row.managerName }">{{ row.managerName || '未设置' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="105" show-overflow-tooltip />
      <el-table-column prop="officeName" label="办事处" width="120" show-overflow-tooltip />
      <el-table-column prop="deptName" label="部门" width="105" show-overflow-tooltip />
      <el-table-column label="角色" min-width="90">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.roleName)" size="small" effect="light" class="role-tag">
            {{ row.roleName || '—' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="65" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
            {{ row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="存储空间" min-width="120">
        <template #default="{ row }">
          <div class="storage-cell">
            <el-progress :percentage="storagePercent(row)" :show-text="false" :stroke-width="4" :color="storageColor(row)" />
            <span class="storage-text">{{ formatSize(row.storageUsed) }} / {{ formatSize(row.storageQuota) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">
          <span class="create-time">{{ (row.createdAt || '').slice(0, 10) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="userStore.hasPermission('system:user:edit')" link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button v-if="userStore.hasPermission('system:user:edit')" link type="warning" size="small" @click="handleResetPwd(row)">重置密码</el-button>
          <el-button v-if="userStore.hasPermission('system:user:delete')" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { injectUserList } from '@/composables/useUserList'

const {
  userStore, tableData, openDialog, handleResetPwd, handleDelete,
  roleTagType, storagePercent, storageColor, formatSize,
} = injectUserList()

// 表格高度：动态计算可用高度，保证分页器完整显示、不被裁切
const tableWrapRef = ref(null)
const tableHeight = ref(300)
let resizeObserver = null
const updateTableHeight = () => {
  if (tableWrapRef.value) tableHeight.value = tableWrapRef.value.clientHeight
}

onMounted(() => {
  nextTick(updateTableHeight)
  if (tableWrapRef.value) {
    resizeObserver = new ResizeObserver(updateTableHeight)
    resizeObserver.observe(tableWrapRef.value)
  }
})
onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect()
})
</script>

<style scoped>
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; padding: 14px 20px; flex-shrink: 0 }
.section-box.table-box { flex: 1; padding: 0; display: flex; flex-direction: column; overflow: hidden }
.section-box.table-box > div:first-child { flex: 1; overflow: auto }
.section-box.table-box :deep(.el-table th) { font-size: 14px; font-weight: 700; color: #1f2329 }
.section-box.table-box :deep(.el-table td) { font-size: 13px; padding: 6px 0 }
.section-box.table-box :deep(.el-table__row) { height: 44px }

/* 编号列 */
.user-no {
  font-family: 'SF Mono', 'Menlo', Consolas, monospace;
  font-weight: 600;
  color: #409eff;
  font-size: 12.5px;
  background: #ecf5ff;
  border-radius: 4px;
  padding: 1px 6px;
}

/* 姓名列：首字头像 */
.user-cell { display: flex; align-items: center; gap: 8px }
.user-avatar {
  width: 26px; height: 26px; border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #79bbff);
  color: #fff; font-size: 12px; font-weight: 600;
  display: inline-flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.user-name { font-weight: 500; color: #303133 }

/* 直接上级空值 */
.no-value { color: #c0c4cc }

/* 角色标签 */
.role-tag { border-radius: 4px }

/* 存储列：迷你进度条 + 文本 */
.storage-cell { display: flex; flex-direction: column; gap: 3px; min-width: 110px }
.storage-text { font-size: 11px; color: #909399 }
.create-time { color: #909399; font-size: 12px }
</style>
