<template>
  <div class="stats-perm-manager">
    <div class="perm-toolbar">
      <span class="perm-desc">勾选可查看「公司级统计总览 / 激励统计 / 学分排行」的用户；未配置时默认全员可见，保存后仅勾选用户（及管理员）可见。</span>
      <div class="perm-actions">
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-table :data="users" size="small" stripe max-height="calc(100vh - 260px)" v-loading="loading">
      <el-table-column type="index" label="#" width="46" align="center" />
      <el-table-column prop="username" label="账号" width="120" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="officeName" label="所属办事处" min-width="140" />
      <el-table-column label="可看全公司统计" width="140" align="center">
        <template #default="{ row }">
          <el-checkbox :model-value="checkedIds.includes(row.userId)"
                       :disabled="isAdmin && row.userId === myUserId"
                       @change="(v) => toggle(row.userId, v)" />
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="140">
        <template #default="{ row }">
          <span v-if="isAdmin && row.userId === myUserId" class="perm-note">管理员恒可见</span>
          <span v-else class="perm-note dim">控制全公司聚合可见性</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getStatsPermissionList, syncStatsPermission } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const users = ref([])
const checkedIds = ref([])
const loading = ref(false)
const saving = ref(false)
const isAdmin = userStore.isAdmin()
const myUserId = userStore.userId

const load = async () => {
  loading.value = true
  try {
    const res = await getStatsPermissionList()
    users.value = res.data || []
    checkedIds.value = users.value.filter(u => u.canViewCompany === 1).map(u => u.userId)
    // admin 恒可见: 前端强制显示为已勾选(即使表里没有该行)
    if (isAdmin && myUserId && !checkedIds.value.includes(myUserId)) checkedIds.value.push(myUserId)
  } finally { loading.value = false }
}

const toggle = (userId, v) => {
  if (v) { if (!checkedIds.value.includes(userId)) checkedIds.value.push(userId) }
  else { checkedIds.value = checkedIds.value.filter(id => id !== userId) }
}

const handleSave = async () => {
  saving.value = true
  try {
    await syncStatsPermission(checkedIds.value)
    ElMessage.success('已保存：勾选用户可查看全公司统计')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.stats-perm-manager { padding: 4px; }
.perm-toolbar { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 12px; }
.perm-desc { font-size: 12px; color: #909399; max-width: 520px; line-height: 1.5; }
.perm-actions { display: flex; gap: 8px; }
.perm-note { font-size: 12px; color: #67c23a; }
.perm-note.dim { color: #c0c4cc; }
</style>
