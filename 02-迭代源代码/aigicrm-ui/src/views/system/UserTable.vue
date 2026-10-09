<template>
  <div class="section-box table-box">
    <!-- 【C12 2026-10-08】加载失败页面级错误态（区别于空态）：错误条+重试按钮 -->
    <el-alert v-if="listError" type="error" :closable="false" class="list-error-bar" show-icon>
      <template #title>
        人员列表加载失败
        <el-button size="small" type="primary" plain style="margin-left:10px" @click="retryLoad">重试</el-button>
      </template>
    </el-alert>
    <div ref="tableWrapRef" style="flex:1;overflow:auto">
    <!-- 【D13 2026-10-08】30 行高频页补 v-loading：fetch 期间不再空白易误读为"暂无数据" -->
    <el-table v-loading="listLoading" :data="tableData" stripe style="width: 100%" size="small" :height="tableHeight">
      <el-table-column type="index" label="#" width="40" />
      <el-table-column label="编号" min-width="105">
        <template #default="{ row }">
          <span class="user-no">{{ row.username }}</span>
        </template>
      </el-table-column>
      <el-table-column label="姓名" min-width="92">
        <template #default="{ row }">
          <div class="user-cell">
            <span class="user-avatar">{{ (row.realName || '?').slice(0, 1) }}</span>
            <span class="user-name">{{ row.realName }}</span>
          </div>
        </template>
      </el-table-column>
      <!-- 【A2 三轮 2026-10-09】13 列总宽 1435px 超 1440 主视口(内容区约 1172px)约 263px，
           未滚动时「存储空间」列被固定操作列死压(截图 12)——本轮压缩定宽列冗余 + .cell 内边距 12→8px，
           总宽收敛到约 1210px：存储/创建时间列在 1440 展开侧栏下基本可见、折叠侧栏完全无滚动 -->
      <el-table-column prop="phone" label="手机号" width="108" :formatter="dashIfEmpty" />
      <el-table-column prop="managerName" label="直接上级" width="74">
        <template #default="{ row }">
          <!-- 【A2/C10 2026-10-08】空值占位统一 '—'（此前与同表空白列两套口径并存） -->
          <span :class="{ 'no-value': !row.managerName }">{{ row.managerName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="88" show-overflow-tooltip :formatter="dashIfEmpty" />
      <el-table-column prop="officeName" label="办事处" width="96" show-overflow-tooltip :formatter="dashIfEmpty" />
      <el-table-column prop="deptName" label="部门" width="88" show-overflow-tooltip :formatter="dashIfEmpty" />
      <!-- 【C10 2026-10-08】角色列长角色名悬浮完整值，不挤压相邻列 -->
      <el-table-column label="角色" min-width="88" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.roleName)" size="small" effect="light" class="role-tag">
            {{ row.roleName || '—' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="58" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
            {{ row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- 【C10 2026-10-08】存储空间列给足最小宽度（本轮 140→130，文本最长 "999.9 GB / 999.9 GB" 仍单行） -->
      <el-table-column label="存储空间" min-width="130">
        <template #default="{ row }">
          <div class="storage-cell">
            <el-progress :percentage="storagePercent(row)" :show-text="false" :stroke-width="4" :color="storageColor(row)" />
            <span class="storage-text">{{ formatSize(row.storageUsed) }} / {{ formatSize(row.storageQuota) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="108">
        <template #default="{ row }">
          <span class="create-time">{{ (row.createdAt || '').slice(0, 10) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
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
// 【A2/C10 三轮 2026-10-09】手机号/邮箱/办事处/部门四列空值占位统一 '—'（消除同表部分列有占位、部分列空白两种口径）
import { dashIfEmpty } from '@/utils/format'

const {
  userStore, tableData, openDialog, handleResetPwd, handleDelete,
  roleTagType, storagePercent, storageColor, formatSize,
  listLoading, listError, loadData,
} = injectUserList()

// 【C12 2026-10-08】错误条重试
const retryLoad = () => loadData()

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
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md); padding: 14px 20px; flex-shrink: 0 }
.section-box.table-box { flex: 1; padding: 0; display: flex; flex-direction: column; overflow: hidden }
.section-box.table-box > div:first-child { flex: 1; overflow: auto }
.section-box.table-box :deep(.el-table th) { font-size: 14px; font-weight: 700; color: #1f2329 }
.section-box.table-box :deep(.el-table td) { font-size: 13px; padding: 6px 0 }
/* 【A2 三轮 2026-10-09】单元格内边距 12→8px：13 列 × 8px 共省约 104px，是列宽收敛的关键一档 */
.section-box.table-box :deep(.el-table .cell) { padding: 0 8px }
.section-box.table-box :deep(.el-table__row) { height: 44px }

/* 编号列（【B6】12.5px 奇数档归并 13px；【C10】不断行防 smoke_sales 折断） */
.user-no {
  font-family: 'SF Mono', 'Menlo', Consolas, monospace;
  font-weight: 600;
  color: var(--el-color-primary);
  font-size: 13px;
  background: var(--el-color-primary-light-9);
  border-radius: 4px;
  padding: 1px 6px;
  white-space: nowrap;
}
.list-error-bar { margin: 10px 14px 0; }

/* 姓名列：首字头像 */
.user-cell { display: flex; align-items: center; gap: 8px }
.user-avatar {
  width: 26px; height: 26px; border-radius: 50%;
  background: linear-gradient(135deg, var(--el-color-primary), var(--el-color-primary-light-3));
  color: #fff; font-size: 12px; font-weight: 600;
  display: inline-flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.user-name { font-weight: 500; color: #303133 }

/* 直接上级空值 */
.no-value { color: var(--text-placeholder) }

/* 角色标签 */
.role-tag { border-radius: 4px }

/* 存储列：迷你进度条 + 文本 */
.storage-cell { display: flex; flex-direction: column; gap: 3px; min-width: 110px }
.storage-text { font-size: 12px; color: #6b7280 }
.create-time { color: var(--el-color-info); font-size: 12px }
</style>
