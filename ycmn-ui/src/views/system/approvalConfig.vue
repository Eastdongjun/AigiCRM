<template>
  <div class="approval-config-page">
    <el-card>
      <div class="page-header">
        <span style="font-size:15px;font-weight:600">审批流程配置</span>
        <el-button type="primary" size="small" @click="openDialog()">新增流程</el-button>
      </div>

      <el-table :data="configList" stripe style="margin-top:12px">
        <el-table-column prop="flowName" label="流程名称" width="180" />
        <el-table-column prop="flowType" label="流程类型" width="150" />
        <el-table-column label="审批人类型" width="120">
          <template #default="{ row }">{{ {USER:'指定人员',ROLE:'指定角色',DEPT:'指定部门'}[row.approverType] }}</template>
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
            <el-button link size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="doDelete(row.id)"><template #ref><el-button link size="small" type="danger">删除</el-button></template></el-popconfirm>
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
            <el-radio value="DEPT">指定部门</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.approverType==='ROLE'?'选择角色':form.approverType==='DEPT'?'选择部门':'选择人员'" required>
          <el-select v-if="form.approverType==='USER'" v-model="approverIds" multiple filterable placeholder="可多选" style="width:100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
          <el-select v-else-if="form.approverType==='ROLE'" v-model="approverIds" multiple placeholder="可多选" style="width:100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
          <el-tree-select v-else v-model="approverIds" :data="orgTree" multiple check-strictly node-key="id" :props="{label:'orgName',value:'id',children:'children'}" placeholder="可多选部门" style="width:100%" />
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
import { ref, reactive, onMounted } from 'vue'
import request from '@/utils/request'
import { getUserList } from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'

const configList = ref([])
const visible = ref(false)
const form = reactive({ id:null, flowName:'', flowType:'', approverType:'USER', signMode:'ANY', sortOrder:0 })
const approverIds = ref([])
const userOptions = ref([])
const roleOptions = ref([])
const orgTree = ref([])

onMounted(() => { loadConfigs(); loadOptions() })

async function loadConfigs() {
  const res = await request.get('/approval/config/list')
  configList.value = res.data || []
}

async function loadOptions() {
  try { const r1 = await request.get('/system/roles'); roleOptions.value = r1.data || [] } catch(e){}
  try { const r2 = await request.get('/org/tree'); orgTree.value = r2.data || [] } catch(e){}
  try { const r3 = await request.get('/customer/users/simple'); userOptions.value = (r3.data||[]).map(u=>({id:u.id, realName:u.realName})) } catch(e){}
}

function parseNames(row) {
  try { const ids = JSON.parse(row.approverIds||'[]'); return ids.length ? ids.join(', ') : '未设置' } catch(e){ return '未设置' }
}

function openDialog(row) {
  if (row) { Object.assign(form, row); approverIds.value = JSON.parse(row.approverIds||'[]') }
  else { Object.assign(form, {id:null,flowName:'',flowType:'',approverType:'USER',signMode:'ANY',sortOrder:0}); approverIds.value=[] }
  visible.value = true
}

async function doSave() {
  if (!form.flowName || !form.flowType) return ElMessage.warning('流程名称和类型必填')
  if (!approverIds.value.length) return ElMessage.warning('请选择审批人')
  const payload = { ...form, approverIds: JSON.stringify(approverIds.value) }
  try {
    if (form.id) await request.put(`/approval/config/${form.id}`, payload)
    else await request.post('/approval/config', payload)
    ElMessage.success('保存成功')
    visible.value = false
    loadConfigs()
  } catch(e) { ElMessage.error('保存失败') }
}

async function doDelete(id) {
  await request.delete(`/approval/config/${id}`)
  ElMessage.success('已删除')
  loadConfigs()
}
</script>

<style scoped>
.approval-config-page { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
