<template>
  <div class="position-manage">
    <div class="page-header">
      <h2>岗位管理</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增岗位</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="positions" stripe border>
        <el-table-column prop="name" label="岗位名称" width="180" />
        <el-table-column prop="code" label="岗位编码" width="150" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="所属组织" width="150">
          <template #default="{ row }">
            {{ row.orgName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="权限规则" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.ruleCount || 0 }} 条规则</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="openRuleDialog(row)">规则</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑岗位' : '新增岗位'" width="500px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="岗位名称" required>
          <el-input v-model="editForm.name" placeholder="如：华东大区经理" maxlength="50" />
        </el-form-item>
        <el-form-item label="岗位编码" required>
          <el-input v-model="editForm.code" :disabled="!!editForm.id" placeholder="如：east_manager" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="岗位职责说明" />
        </el-form-item>
        <el-form-item label="所属组织">
          <el-tree-select
            v-model="editForm.orgId"
            :data="orgOptions"
            :props="{ label: 'orgName', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="选择组织（可选）"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 规则分配对话框 -->
    <el-dialog v-model="ruleDialogVisible" title="权限规则分配" width="700px">
      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        为岗位 <b>{{ currentPosition?.name }}</b> 绑定权限规则。规则决定该岗位可查看的数据范围及操作权限。
      </el-alert>
      <el-checkbox-group v-model="checkedRuleIds" style="display:flex;flex-direction:column;gap:8px">
        <div v-for="rule in allRules" :key="rule.id" class="rule-item">
          <el-checkbox :label="rule.id" :value="rule.id">
            <div>
              <span style="font-weight:bold">{{ rule.name }}</span>
              <el-tag size="small" :type="ruleTypeTag(rule.ruleType)" style="margin-left:8px">
                {{ ruleTypeLabel(rule.ruleType) }}
              </el-tag>
            </div>
            <div style="font-size:12px;color:#909399;margin-top:2px">
              {{ rule.description || rule.code }} — 目标: {{ rule.targetEntity }}
            </div>
          </el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRules">保存规则</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPositionList, createPosition, updatePosition, deletePosition, getRuleList, getPositionRules, updatePositionRules, getOrgTree } from '@/api/system'

const positions = ref([])
const allRules = ref([])
const orgOptions = ref([])
const dialogVisible = ref(false)
const ruleDialogVisible = ref(false)
const checkedRuleIds = ref([])
const currentPosition = ref(null)

const editForm = reactive({
  id: null, name: '', code: '', description: '',
  orgId: null, sort: 0, status: 1
})

/** 规则类型标签 */
const ruleTypeMap = {
  column_filter:  { label: '字段过滤', tag: 'primary' },
  org_filter:     { label: '组织过滤', tag: 'success' },
  field_visible:  { label: '字段可见', tag: 'warning' },
  action_control: { label: '操作控制', tag: 'danger' },
  time_range:     { label: '时间范围', tag: 'info' }
}
const ruleTypeLabel = (type) => ruleTypeMap[type]?.label || type
const ruleTypeTag = (type) => ruleTypeMap[type]?.tag || 'info'

const loadData = async () => {
  try {
    const res = await getPositionList()
    positions.value = res.data || []
  } catch (e) {}
}

const loadRules = async () => {
  try {
    const res = await getRuleList()
    allRules.value = res.data || []
  } catch (e) {}
}

const loadOrgs = async () => {
  try {
    const res = await getOrgTree()
    orgOptions.value = res.data || []
  } catch (e) {}
}

const openDialog = (row) => {
  if (row) {
    Object.assign(editForm, {
      id: row.id, name: row.name, code: row.code,
      description: row.description || '', orgId: row.orgId,
      sort: row.sort || 0, status: row.status
    })
  } else {
    Object.assign(editForm, {
      id: null, name: '', code: '', description: '',
      orgId: null, sort: 0, status: 1
    })
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    if (editForm.id) {
      await updatePosition(editForm.id, editForm)
    } else {
      await createPosition(editForm)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除岗位 ${row.name}？`, '提示')
    await deletePosition(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

const openRuleDialog = async (row) => {
  currentPosition.value = row
  try {
    const res = await getPositionRules(row.id)
    checkedRuleIds.value = (res.data || []).map(r => r.ruleId || r.id)
  } catch (e) {
    checkedRuleIds.value = []
  }
  ruleDialogVisible.value = true
}

const handleSaveRules = async () => {
  try {
    await updatePositionRules(currentPosition.value.id, checkedRuleIds.value)
    ElMessage.success('规则保存成功')
    ruleDialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('规则保存失败')
  }
}

onMounted(() => {
  loadData()
  loadRules()
  loadOrgs()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.rule-item { padding: 8px 12px; border: 1px solid #e5e5e5; border-radius: 6px; }
.rule-item:hover { background: #f5f7fa; }
</style>
