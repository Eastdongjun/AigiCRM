<template>
  <div class="approval-config-page">
    <el-card>
      <div class="page-header">
        <span style="font-size:15px;font-weight:600">审批流程配置</span>
        <el-button v-if="canEditConfig" type="primary" size="small" @click="openDialog()">新增流程</el-button>
      </div>

      <el-table :data="configList" stripe style="margin-top:12px">
        <el-table-column prop="flowName" label="流程名称" width="180" />
        <el-table-column prop="flowType" label="流程类型" width="150" />
        <el-table-column label="审批人类型" width="120">
          <template #default="{ row }">{{ {USER:'指定人员',ROLE:'指定角色'}[row.approverType] || row.approverType }}</template>
        </el-table-column>
        <el-table-column label="签署方式" width="100">
          <template #default="{ row }">
            <el-tag :type="row.signMode==='ALL'?'warning':'success'" size="small">
              {{ row.signMode==='ALL'?'会签':'非会签' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批人" min-width="200">
          <template #default="{row}">{{ parseNames(row) }}</template>
        </el-table-column>
        <el-table-column label="排序" width="60" prop="sortOrder" />
        <el-table-column label="操作" width="120">
          <template #default="{row}">
            <el-button v-if="canEditConfig" link size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm v-if="canEditConfig" title="确定删除？" @confirm="doDelete(row.id)">
              <template #reference><el-button link size="small" type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="visible" :title="form.id?'编辑流程':'新增流程'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="流程名称" required>
          <el-input v-model="form.flowName" placeholder="如：客户报备审批" />
        </el-form-item>
        <el-form-item label="流程类型" required>
          <el-input v-model="form.flowType" placeholder="英文标识，如：customer_report" :disabled="!!form.id" />
        </el-form-item>
        <el-divider content-position="left">审批人设置</el-divider>
        <el-form-item label="审批人类型">
          <el-radio-group v-model="form.approverType">
            <el-radio value="USER">指定人员</el-radio>
            <el-radio value="ROLE">指定角色</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.approverType==='ROLE'?'选择角色':'选择人员'" required>
          <el-select v-if="form.approverType==='USER'" v-model="approverIds" multiple filterable placeholder="可多选" style="width:100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
          <el-select v-else v-model="approverIds" multiple placeholder="可多选" style="width:100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="签署方式" required>
          <el-radio-group v-model="form.signMode">
            <el-radio value="ANY">非会签（任一人审批即可）</el-radio>
            <el-radio value="ALL">会签（所有人必须审批）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
// 【D3 2026-10-05 api 层收口】页面不再裸调 URL：审批配置 CRUD 走 api/approval.js 封装，
// 角色列表复用 api/system.getRoleList，精简用户列表复用 api/customer.getSimpleUserList
import { getApprovalConfigList, createApprovalConfig, updateApprovalConfig, deleteApprovalConfig } from '@/api/approval'
import { getRoleList } from '@/api/system'
import { getSimpleUserList } from '@/api/customer'

const userStore = useUserStore()
const canEditConfig = computed(() => userStore.hasPermission('system:config:edit'))
const configList = ref([])
const visible = ref(false)
const form = reactive({ id:null, flowName:'', flowType:'', approverType:'USER', signMode:'ANY', sortOrder:0 })
const approverIds = ref([])
const userOptions = ref([])
const roleOptions = ref([])

onMounted(() => { loadConfigs(); loadOptions() })

async function loadConfigs() {
  const res = await getApprovalConfigList()
  configList.value = res.data || []
}

async function loadOptions() {
  try { const roleRes = await getRoleList(); roleOptions.value = roleRes.data || [] } catch(e){}
  try { const userRes = await getSimpleUserList(); userOptions.value = (userRes.data||[]).map(u=>({id:u.id, realName:u.realName})) } catch(e){}
}

function parseApproverIds(raw) {
  // 【L1 2026-10-04】approverIds 为 JSON 字符串，异常/历史脏数据时兜底空数组，避免打开弹窗即抛错
  try { return JSON.parse(raw || '[]') } catch (e) { return [] }
}

function parseNames(row) {
  const ids = parseApproverIds(row.approverIds)
  return ids.length ? ids.join(', ') : '未设置'
}

function openDialog(row) {
  if (row) { Object.assign(form, row); approverIds.value = parseApproverIds(row.approverIds) }
  else { Object.assign(form, {id:null,flowName:'',flowType:'',approverType:'USER',signMode:'ANY',sortOrder:0}); approverIds.value=[] }
  visible.value = true
}

async function doSave() {
  if (!form.flowName || !form.flowType) return ElMessage.warning('流程名称和类型必填')
  if (!approverIds.value.length) return ElMessage.warning('请选择审批人')
  const payload = { ...form, approverIds: JSON.stringify(approverIds.value) }
  try {
    if (form.id) await updateApprovalConfig(form.id, payload)
    else await createApprovalConfig(payload)
    ElMessage.success('保存成功')
    visible.value = false
    loadConfigs()
  } catch(e) { ElMessage.error('保存失败') }
}

async function doDelete(id) {
  await deleteApprovalConfig(id)
  ElMessage.success('已删除')
  loadConfigs()
}
</script>

<style scoped>
.approval-config-page { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
