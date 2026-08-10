<template>
  <div class="company-perm-panel">
    <div class="cp-toolbar">
      <span class="cp-desc">勾选可查看「{{ moduleLabel }}」公司级数据的用户；未配置时默认全员可见，保存后仅勾选用户（及管理员）可见。</span>
      <div class="cp-actions">
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </div>
    <el-table :data="users" size="small" stripe max-height="380" v-loading="loading">
      <el-table-column prop="realName" label="姓名" width="110" />
      <el-table-column prop="username" label="账号" width="120" />
      <el-table-column prop="officeName" label="所属" min-width="120" />
      <el-table-column label="公司级可见" width="120" align="center">
        <template #default="{ row }">
          <el-checkbox :model-value="checked.includes(row.userId)"
                       :disabled="isAdmin && row.userId === myUserId"
                       @change="(v) => toggle(row.userId, v)" />
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

const props = defineProps({ module: { type: String, required: true } }) // indicator / incentive / score
const userStore = useUserStore()
const moduleLabel = { indicator: '统计总览', incentive: '激励统计', score: '学分排行' }[props.module] || props.module
const FLAG_KEY = { indicator: 'canViewIndicator', incentive: 'canViewIncentive', score: 'canViewScore' }[props.module]

const users = ref([])
const checked = ref([])
const loading = ref(false)
const saving = ref(false)
const isAdmin = userStore.isAdmin()
const myUserId = userStore.userId

const load = async () => {
  loading.value = true
  try {
    const res = await getStatsPermissionList()
    users.value = res.data || []
    checked.value = users.value.filter(u => u[FLAG_KEY] === 1).map(u => u.userId)
    if (isAdmin && myUserId && !checked.value.includes(myUserId)) checked.value.push(myUserId)
  } finally { loading.value = false }
}

const toggle = (userId, v) => {
  if (v) { if (!checked.value.includes(userId)) checked.value.push(userId) }
  else { checked.value = checked.value.filter(id => id !== userId) }
}

const save = async () => {
  saving.value = true
  try {
    await syncStatsPermission(props.module, checked.value)
    ElMessage.success(`已保存：${moduleLabel}公司级可见用户`)
  } catch (e) { ElMessage.error(e?.response?.data?.message || '保存失败') }
  finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.cp-toolbar { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 12px; }
.cp-desc { font-size: 12px; color: #909399; line-height: 1.5; }
.cp-actions { display: flex; gap: 8px; }
</style>
