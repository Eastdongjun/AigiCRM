<template>
  <div class="position-rule-panel">
    <div class="prp-head">
      <div>
        <div class="prp-title">岗位规则</div>
        <div class="prp-hint">岗位规则决定该岗位可查看的数据范围及操作权限；此处是唯一写入口。</div>
      </div>
      <el-button size="small" :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <el-table :data="positions" size="small" stripe border v-loading="loading">
      <el-table-column prop="name" label="岗位" min-width="160" />
      <el-table-column prop="code" label="编码" width="150" />
      <el-table-column label="规则" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.ruleCount ? 'warning' : 'info'">{{ row.ruleCount || 0 }} 条</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center">
        <template #default="{ row }">
          <el-button v-if="canEdit" link type="primary" size="small" @click="openRuleDialog(row)">配置规则</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="岗位规则分配" width="680px">
      <el-alert type="info" :closable="false" style="margin-bottom:14px">
        为岗位 <b>{{ currentPosition?.name }}</b> 绑定规则；勾选即授予，取消即收回。
      </el-alert>
      <el-checkbox-group v-model="checkedRuleIds" class="prp-rules">
        <div v-for="rule in allRules" :key="rule.id" class="prp-rule-item">
          <el-checkbox :label="rule.id" :value="rule.id">
            <span class="prp-rule-name">{{ rule.name }}</span>
            <el-tag size="small" :type="ruleTypeTag(rule.ruleType)" style="margin-left:8px">
              {{ ruleTypeLabel(rule.ruleType) }}
            </el-tag>
            <div class="prp-rule-desc">{{ rule.description || rule.code }} — 目标: {{ rule.targetEntity }}</div>
          </el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRules" :loading="saving">保存规则</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPositionList, getRuleList, getPositionRules, updatePositionRules } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const canEdit = computed(() => userStore.hasPermission('system:position:edit'))
const positions = ref([])
const allRules = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const currentPosition = ref(null)
const checkedRuleIds = ref([])

const ruleTypeMap = {
  column_filter: { label: '字段过滤', tag: 'primary' },
  org_filter: { label: '组织过滤', tag: 'success' },
  field_visible: { label: '字段可见', tag: 'warning' },
  action_control: { label: '操作控制', tag: 'danger' },
  time_range: { label: '时间范围', tag: 'info' }
}
const ruleTypeLabel = (type) => ruleTypeMap[type]?.label || type
const ruleTypeTag = (type) => ruleTypeMap[type]?.tag || 'info'

const loadData = async () => {
  loading.value = true
  try {
    const [pos, rules] = await Promise.all([getPositionList(), getRuleList()])
    positions.value = pos.data || []
    allRules.value = rules.data || []
  } catch (e) {
    positions.value = []
    allRules.value = []
  } finally {
    loading.value = false
  }
}

const openRuleDialog = async (row) => {
  currentPosition.value = row
  try {
    const res = await getPositionRules(row.id)
    checkedRuleIds.value = (res.data || []).map(r => r.ruleId || r.id)
  } catch (e) {
    checkedRuleIds.value = []
  }
  dialogVisible.value = true
}

const saveRules = async () => {
  saving.value = true
  try {
    await updatePositionRules(currentPosition.value.id, checkedRuleIds.value)
    ElMessage.success('规则保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('规则保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.prp-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 12px; }
.prp-title { font-size: 14px; font-weight: 700; color: #1f2937; }
.prp-hint { margin-top: 4px; font-size: 12px; color: #909399; }
.prp-rules { display: flex; flex-direction: column; gap: 10px; max-height: 420px; overflow: auto; }
.prp-rule-item { padding: 8px 10px; border: 1px solid #ebeef5; border-radius: 8px; }
.prp-rule-name { font-weight: 600; }
.prp-rule-desc { margin-top: 3px; margin-left: 24px; font-size: 12px; color: #909399; }
</style>
