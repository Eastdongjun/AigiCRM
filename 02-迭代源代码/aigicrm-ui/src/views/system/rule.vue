<template>
  <div class="rule-manage">
    <div class="page-header">
      <h2>权限规则</h2>
      <el-button v-if="canAddRule" type="primary" :icon="Plus" @click="openDialog()">新增规则</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="rules" stripe border>
        <el-table-column prop="name" label="规则名称" width="160" show-overflow-tooltip />
        <el-table-column prop="code" label="编码" width="160" show-overflow-tooltip />
        <el-table-column prop="targetEntity" label="目标实体" width="100" align="center" />
        <el-table-column label="规则类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="ruleTypeTag(row.ruleType)">{{ ruleTypeText(row.ruleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetColumn" label="目标列" width="130" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作符" width="90" align="center" />
        <el-table-column prop="ruleValue" label="规则值" min-width="180" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canEditRule" link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="canDeleteRule" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑权限规则' : '新增权限规则'" width="640px">
      <el-form :model="editForm" label-width="110px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="规则名称" required>
          <el-input v-model="editForm.name" placeholder="如：销售只能看自己的客户" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="规则编码" required>
          <el-input v-model="editForm.code" :disabled="!!editForm.id" placeholder="英文标识，如：sale_self_customer" maxlength="50" />
          <div class="form-tip">唯一编码，创建后不可修改</div>
        </el-form-item>
        <el-form-item label="目标实体" required>
          <el-select v-model="editForm.targetEntity" style="width:100%" filterable allow-create default-first-option placeholder="选择或输入实体名">
            <el-option v-for="e in entityOptions" :key="e" :label="e" :value="e" />
          </el-select>
          <div class="form-tip">生产数据均为 customer，如需其他实体可手动输入</div>
        </el-form-item>
        <el-form-item label="规则类型" required>
          <el-select v-model="editForm.ruleType" style="width:100%" placeholder="选择规则类型">
            <el-option label="列过滤（字段值过滤）" value="column_filter" />
            <el-option label="组织过滤（按组织树递归）" value="org_filter" />
            <el-option label="时间范围（创建时间区间）" value="time_range" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标列">
          <el-input v-model="editForm.targetColumn" placeholder="如：owner_id / create_time，列过滤与时间范围时填写" maxlength="50" />
        </el-form-item>
        <el-form-item label="操作符">
          <el-select v-model="editForm.operator" style="width:100%" placeholder="选择操作符">
            <el-option label="in（属于集合）" value="in" />
            <el-option label="eq（等于）" value="eq" />
            <el-option label="between（区间）" value="between" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则值">
          <el-input v-model="editForm.ruleValue" placeholder="如：${user.userId}" maxlength="200" />
          <div class="form-tip">
            支持变量：<code>${user.userId}</code> 当前用户ID；
            <code>${user.orgId}</code> 当前组织；
            <code>${user.orgIdsAndChildren}</code> 组织及下级；
            <code>${time.thisMonthStart},${time.thisMonthEnd}</code> 本月时间范围
          </div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="editForm.priority" :min="0" />
          <div class="form-tip">数值越小优先级越高</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="规则用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { getRuleList, createRule, updateRule, deleteRule } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const canAddRule = computed(() => userStore.hasPermission('system:rule:add'))
const canEditRule = computed(() => userStore.hasPermission('system:rule:edit'))
const canDeleteRule = computed(() => userStore.hasPermission('system:rule:delete'))

const rules = ref([])
const dialogVisible = ref(false)
const saving = ref(false)

const entityOptions = ['customer']

const ruleTypeMap = {
  column_filter: { text: '列过滤', tag: '' },
  org_filter: { text: '组织过滤', tag: 'warning' },
  time_range: { text: '时间范围', tag: 'success' }
}
const ruleTypeText = (type) => ruleTypeMap[type]?.text || type || '-'
const ruleTypeTag = (type) => ruleTypeMap[type]?.tag || 'info'

const editForm = reactive({
  id: null, name: '', code: '', targetEntity: 'customer', ruleType: '',
  targetColumn: '', operator: '', ruleValue: '', description: '', priority: 0, status: 1
})

const loadData = async () => {
  try {
    const res = await getRuleList()
    rules.value = res.data || []
  } catch (e) {}
}

const openDialog = (row) => {
  if (row) {
    Object.assign(editForm, row)
  } else {
    Object.assign(editForm, {
      id: null, name: '', code: '', targetEntity: 'customer', ruleType: '',
      targetColumn: '', operator: '', ruleValue: '', description: '', priority: 0, status: 1
    })
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!editForm.name.trim()) { ElMessage.warning('请填写规则名称'); return }
  if (!editForm.code.trim()) { ElMessage.warning('请填写规则编码'); return }
  if (!editForm.targetEntity.trim()) { ElMessage.warning('请填写目标实体'); return }
  if (!editForm.ruleType) { ElMessage.warning('请选择规则类型'); return }
  saving.value = true
  try {
    if (editForm.id) {
      await updateRule(editForm.id, editForm)
    } else {
      await createRule(editForm)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row) => {
  try {
    // 【UI 整改 2026-10-08】删除类确认走 confirmAction：红色确认钮 + 取消左置
    await confirmAction(`确定删除权限规则「${row.name}」？删除后不可恢复。`, '删除确认', { confirmText: '删除' })
    await deleteRule(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.form-tip { font-size: 12px; color: var(--el-color-info); margin-top: 4px; }
.form-tip code { background: #f5f7fa; padding: 0 4px; border-radius: 3px; color: var(--el-color-primary); }
</style>
