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
          <el-form-item label="迁移激励">
            <el-switch v-model="handover.transferIncentives" />
            <span class="hint">开启后将离职人当前激励记录的归属人改为继任人（历史业绩随之转移）</span>
          </el-form-item>
          <el-form-item label="迁移直属下级">
            <el-switch v-model="handover.transferReports" />
            <span class="hint">开启后将 manager_id=离职人的直属下级改挂继任人</span>
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
          <el-table-column label="审批人" width="130">
            <template #default="{ row }">
              <div>{{ row.approver_name || row.approver_id || '—' }}</div>
              <div v-if="approverSource(row)" class="approver-src">{{ approverSource(row) }}</div>
              <el-tooltip v-if="row.approver_changed_at" :content="`已转交 · ${row.approver_changed_at}`">
                <el-tag size="small" type="warning">已转交</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button v-if="row.status === 'pending' && (userStore.hasPermission('approval:view') || userStore.hasPermission('system:user:edit'))" link size="small" type="success" @click="approve(row)">通过</el-button>
              <el-button v-if="row.status === 'pending' && (userStore.hasPermission('approval:view') || userStore.hasPermission('system:user:edit'))" link size="small" type="danger" @click="reject(row)">驳回</el-button>
              <el-button v-if="row.status === 'approved' && userStore.hasPermission('system:user:edit')" link size="small" type="warning" @click="rollback(row)">回滚</el-button>
              <el-button v-if="row.status === 'pending' && userStore.isAdmin" link size="small" type="primary" @click="openTransfer(row)">转交</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  
    <!-- 【A4 拍板 #4/#5】管理员转交审批人（仅 pending 单；单人快照模型下不自动换人） -->
    <el-dialog v-model="transferVisible" title="转交审批人" width="420px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="当前审批人">{{ transferRow.approver_name || transferRow.approver_id }}</el-form-item>
        <el-form-item label="新审批人" required>
          <el-select v-model="transferForm.newApproverId" filterable style="width:100%">
            <el-option v-for="u in userOptions" :key="u.value" :label="u.label" :value="u.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="transferForm.reason" type="textarea" :rows="2" placeholder="原审批人停用/离职等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible=false">取消</el-button>
        <el-button type="primary" :loading="transferring" @click="submitTransfer">确认转交</el-button>
      </template>
    </el-dialog>
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
const handover = reactive({ fromUserId: null, toUserId: null, reason: '', copyRoles: false, transferIncentives: false, transferReports: false })
const move = reactive({ userId: null, toDeptId: null, reason: '' })
const transferVisible = ref(false)
const transferring = ref(false)
/** 转交目标行(后端 list 行, 动态结构) */
const transferRow = /** @type {any} */ (ref({}))
const transferForm = reactive({ newApproverId: null, reason: '' })

const approverSource = (row) => {
  let snap = row.approverSnapshot
  if (!snap && row.approver_snapshot_json) {
    try { snap = JSON.parse(row.approver_snapshot_json) } catch (e) { snap = null }
  }
  if (!snap || !snap.type) return ''
  return snap.type === 'ROLE' ? `角色#${snap.roleId || ''}${snap.roleCode ? '(' + snap.roleCode + ')' : ''}·首位成员` : (snap.type === 'USER' ? '指定人员' : snap.type)
}
const openTransfer = (row) => {
  transferRow.value = row
  transferForm.newApproverId = null
  transferForm.reason = ''
  transferVisible.value = true
}
const submitTransfer = async () => {
  if (!transferForm.newApproverId) return ElMessage.warning('请选择新审批人')
  transferring.value = true
  try {
    await request.post(`/system/lifecycle/requests/${transferRow.value.id}/transfer-approver`, transferForm)
    ElMessage.success('审批人已转交')
    transferVisible.value = false
    loadRequests()
  } catch (e) { /* 拦截器提示 */ } finally { transferring.value = false }
}

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
    Object.assign(handover, { fromUserId: null, toUserId: null, reason: '', copyRoles: false, transferIncentives: false, transferReports: false })
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
  const res = await request.post(`/system/lifecycle/requests/${row.id}/rollback`, {})
  // 【2026-11-17 P3】显示逐类恢复计数；全部为 0 时提示“可能已被后续变更”，避免误以为全量恢复。
  const result = res.data?.result || {}
  const labels = { customer: '客户', customerOrg: '客户组织', file: '文件', fileOrg: '文件组织',
    folder: '文件夹', folderOrg: '文件夹组织', reminder: '提醒', reminderOffice: '提醒办事处',
    task: '任务', approval: '审批', approvalNode: '审批节点', role: '角色', incentive: '激励', report: '直属下级' }
  const parts = Object.entries(result)
    .filter(([k, v]) => Number(v) > 0 && labels[k])
    .map(([k, v]) => `${labels[k]} ${v}`)
  if (!parts.length) ElMessage.warning('已标记回滚，但没有行被恢复（数据可能已被后续变更）')
  else ElMessage.success(`已回滚：${parts.join('、')}`)
  loadRequests()
}
const statusText = (s) => ({ pending: '待审批', approved: '已通过', rejected: '已驳回', rolled_back: '已回滚' }[s] || s)
const statusType = (s) => ({ pending: 'warning', approved: 'success', rejected: 'danger', rolled_back: 'info' }[s] || 'info')
</script>

<style scoped>
.hint { margin-left: 10px; color: var(--el-color-info); font-size: 12px }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px }
.approver-src { font-size: 11px; color: var(--el-text-color-secondary); line-height: 1.2; }
</style>
