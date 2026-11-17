<template>
  <el-dialog v-model="visible" title="离职交接 / 组织调动" width="760px" destroy-on-close>
    <el-tabs v-model="tab">
      <el-tab-pane label="离职交接" name="handover">
        <el-form label-width="90px">
          <el-form-item label="离职人" required>
            <el-select v-model="handover.fromUserId" filterable placeholder="选择离职人员" style="width:100%">
              <el-option v-for="u in userOptions" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="继任人" required>
            <el-select v-model="handover.toUserId" filterable placeholder="可跨区选择继任人" style="width:100%">
              <el-option v-for="u in userOptions" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="复制角色">
            <el-switch v-model="handover.copyRoles" />
            <span class="hint">默认关闭；开启后复制离职人现有角色（审批人负责确认）</span>
          </el-form-item>
          <el-form-item label="原因">
            <el-input v-model="handover.reason" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item>
            <el-button v-if="userStore.hasPermission('system:user:edit')" type="primary" :loading="saving" @click="submitHandover">提交审批</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="组织调动" name="move">
        <el-form label-width="90px">
          <el-form-item label="人员" required>
            <el-select v-model="move.userId" filterable placeholder="选择人员" style="width:100%">
              <el-option v-for="u in userOptions" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标部门" required>
            <el-tree-select v-model="move.toDeptId" :data="deptTree" :props="{ label: 'deptName', value: 'id', children: 'children' }" check-strictly default-expand-all filterable placeholder="选择目标部门/办事处" style="width:100%" />
          </el-form-item>
          <el-form-item label="原因">
            <el-input v-model="move.reason" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item>
            <el-button v-if="userStore.hasPermission('system:user:edit')" type="primary" :loading="saving" @click="submitMove">提交审批</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="申请记录" name="list">
        <div class="toolbar">
          <el-select v-model="filterStatus" clearable placeholder="全部状态" style="width:130px" @change="loadRequests">
            <el-option label="待审批" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已回滚" value="rolled_back" />
          </el-select>
          <el-button size="small" @click="loadRequests">刷新</el-button>
        </div>
        <el-table :data="requests" size="small" stripe max-height="360">
          <el-table-column prop="id" label="#" width="55" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ row.request_type === 'HANDOVER' ? '离职交接' : '组织调动' }}</template>
          </el-table-column>
          <el-table-column prop="subject_name" label="人员" width="100" />
          <el-table-column prop="target_user_name" label="继任人/目标" min-width="110">
            <template #default="{ row }">{{ row.target_user_name || row.target_dept_id || '—' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approver_name" label="审批人" width="90" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button v-if="row.status === 'pending' && (userStore.hasPermission('approval:view') || userStore.hasPermission('system:user:edit'))" link size="small" type="success" @click="approve(row)">通过</el-button>
              <el-button v-if="row.status === 'pending' && (userStore.hasPermission('approval:view') || userStore.hasPermission('system:user:edit'))" link size="small" type="danger" @click="reject(row)">驳回</el-button>
              <el-button v-if="row.status === 'approved' && userStore.hasPermission('system:user:edit')" link size="small" type="warning" @click="rollback(row)">回滚</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { injectUserList } from '@/composables/useUserList'

const { userStore, userOptions, deptTree } = injectUserList()
const visible = ref(false)
const tab = ref('handover')
const saving = ref(false)
const requests = ref([])
const filterStatus = ref('')
const handover = reactive({ fromUserId: null, toUserId: null, reason: '', copyRoles: false })
const move = reactive({ userId: null, toDeptId: null, reason: '' })

const open = () => {
  visible.value = true
  tab.value = 'handover'
  loadRequests()
}
defineExpose({ open })

const loadRequests = async () => {
  try {
    const res = await request.get('/system/lifecycle/requests', { params: { status: filterStatus.value || undefined } })
    requests.value = res.data || []
  } catch (e) { /* 统一拦截器提示 */ }
}

const submitHandover = async () => {
  if (!handover.fromUserId || !handover.toUserId) return ElMessage.warning('请选择离职人与继任人')
  saving.value = true
  try {
    await request.post('/system/lifecycle/handover', handover)
    ElMessage.success('已提交审批')
    Object.assign(handover, { fromUserId: null, toUserId: null, reason: '', copyRoles: false })
    tab.value = 'list'
    loadRequests()
  } catch (e) { /* 统一拦截器提示 */ } finally { saving.value = false }
}

const submitMove = async () => {
  if (!move.userId || !move.toDeptId) return ElMessage.warning('请选择人员与目标部门')
  saving.value = true
  try {
    await request.post('/system/lifecycle/org-move', move)
    ElMessage.success('已提交审批')
    Object.assign(move, { userId: null, toDeptId: null, reason: '' })
    tab.value = 'list'
    loadRequests()
  } catch (e) { /* 统一拦截器提示 */ } finally { saving.value = false }
}

const approve = async (row) => {
  await request.post(`/system/lifecycle/requests/${row.id}/approve`, {})
  ElMessage.success('已通过')
  loadRequests()
}
const reject = async (row) => {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回申请', { inputValue: '' })
  await request.post(`/system/lifecycle/requests/${row.id}/reject`, { reason: value || '' })
  ElMessage.success('已驳回')
  loadRequests()
}
const rollback = async (row) => {
  await ElMessageBox.confirm('将按申请明细恢复原归属，确定回滚？', '回滚', { type: 'warning' })
  await request.post(`/system/lifecycle/requests/${row.id}/rollback`, {})
  ElMessage.success('已回滚')
  loadRequests()
}
const statusText = (s) => ({ pending: '待审批', approved: '已通过', rejected: '已驳回', rolled_back: '已回滚' }[s] || s)
const statusType = (s) => ({ pending: 'warning', approved: 'success', rejected: 'danger', rolled_back: 'info' }[s] || 'info')
</script>

<style scoped>
.hint { margin-left: 10px; color: var(--el-color-info); font-size: 12px }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px }
</style>
