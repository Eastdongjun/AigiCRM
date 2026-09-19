<template>
  <div class="dict-layout">
    <!-- 左侧: 字典类型 -->
    <div class="dict-types">
      <div class="dict-header">
        <span><el-icon style="vertical-align:middle"><Files /></el-icon> 字典类型 <el-tag size="small" type="info" effect="plain">{{ dictTypes.length }}</el-tag></span>
        <el-button v-if="canEditConfig" type="primary" size="small" :icon="Plus" @click="handleAddType">新增</el-button>
      </div>
      <div class="dict-type-list">
        <div
          v-for="t in dictTypes"
          :key="t.id"
          class="dict-type-item"
          :class="{ active: currentType && currentType.id === t.id }"
          @click="handleTypeSelect(t)"
        >
          <div class="dict-type-name">
            <el-icon class="dict-type-ico"><Collection /></el-icon>
            <span class="dict-type-title">{{ t.dictName }}</span>
            <el-tag v-if="t.builtIn" size="small" type="info" effect="plain">内置</el-tag>
          </div>
          <div class="dict-type-code">{{ t.dictCode }}</div>
          <div class="dict-type-acts">
            <el-button v-if="canEditConfig" link size="small" @click.stop="handleEditType(t)">编辑</el-button>
            <el-button v-if="canEditConfig && !t.builtIn" link size="small" type="danger" @click.stop="handleDeleteType(t)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!dictTypes.length" description="暂无字典类型" :image-size="60" />
      </div>
    </div>

    <!-- 右侧: 字典数据 -->
    <div class="dict-data">
      <div class="dict-header">
        <span>
          <el-icon style="vertical-align:middle"><PriceTag /></el-icon>
          {{ currentType ? currentType.dictName + ' · 数据列表' : '请选择字典类型' }}
          <el-tag v-if="currentType" size="small" type="success" effect="plain">{{ dictDataList.length }} 项</el-tag>
        </span>
        <el-button v-if="canEditConfig" type="primary" size="small" :icon="Plus" :disabled="!currentType" @click="handleAddData">新增</el-button>
      </div>
      <el-table v-if="currentType" :data="dictDataList" size="small" stripe style="width: 100%">
        <el-table-column prop="dictLabel" label="显示标签" min-width="120" />
        <el-table-column prop="dictValue" label="数据值" min-width="120" />
        <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
        <el-table-column label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button v-if="canEditConfig" link size="small" @click="handleEditData(row)">编辑</el-button>
            <el-button v-if="canEditConfig" link size="small" type="danger" @click="handleDeleteData(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="dict-empty-tip">
        <el-empty description="从左侧选择字典类型查看数据" :image-size="90" />
      </div>
    </div>
  </div>

  <!-- 字典类型编辑弹窗 -->
  <el-dialog v-model="typeDialogVisible" :title="typeForm.id ? '编辑字典类型' : '新增字典类型'" width="420px">
    <el-form :model="typeForm" label-width="80px">
      <el-form-item label="名称">
        <el-input v-model="typeForm.dictName" placeholder="如：行业类型" />
      </el-form-item>
      <el-form-item label="编码">
        <el-input v-model="typeForm.dictCode" placeholder="如：industry" :disabled="!!typeForm.id" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="typeForm.remark" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="typeDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submitType">确定</el-button>
    </template>
  </el-dialog>

  <!-- 字典数据编辑弹窗 -->
  <el-dialog v-model="dataDialogVisible" :title="dataForm.id ? '编辑字典数据' : '新增字典数据'" width="420px">
    <el-form :model="dataForm" label-width="80px">
      <el-form-item label="标签">
        <el-input v-model="dataForm.dictLabel" placeholder="显示名称" />
      </el-form-item>
      <el-form-item label="数据值">
        <el-input v-model="dataForm.dictValue" placeholder="实际存储值" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="dataForm.sortOrder" :min="0" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="dataForm.status" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dataDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submitData">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Files, Collection, PriceTag } from '@element-plus/icons-vue'
import {
  getDictTypes, createDictType, updateDictType, deleteDictType,
  getDictDataByTypeId, createDictData, updateDictData, deleteDictData,
} from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const canEditConfig = computed(() => userStore.hasPermission('system:config:edit'))

const dictTypes = ref([])
const currentType = ref(null)
const typeDialogVisible = ref(false)
const typeForm = reactive({ id: null, dictName: '', dictCode: '', remark: '' })

const loadDictTypes = async () => {
  try {
    const res = await getDictTypes()
    dictTypes.value = res.data || []
  } catch (e) {}
}

const handleTypeSelect = (row) => {
  currentType.value = row
  if (row) loadDictData(row.id)
}

const handleAddType = () => {
  Object.assign(typeForm, { id: null, dictName: '', dictCode: '', remark: '' })
  typeDialogVisible.value = true
}

const handleEditType = (row) => {
  Object.assign(typeForm, { id: row.id, dictName: row.dictName, dictCode: row.dictCode, remark: row.remark || '' })
  typeDialogVisible.value = true
}

const handleDeleteType = async (row) => {
  await ElMessageBox.confirm(`确定删除字典类型「${row.dictName}」及其所有数据？`, '提示', { type: 'warning' })
  await deleteDictType(row.id)
  ElMessage.success('删除成功')
  loadDictTypes()
  if (currentType.value?.id === row.id) {
    currentType.value = null
    dictDataList.value = []
  }
}

const submitType = async () => {
  try {
    if (typeForm.id) {
      await updateDictType(typeForm.id, typeForm)
    } else {
      await createDictType(typeForm)
    }
    ElMessage.success('保存成功')
    typeDialogVisible.value = false
    loadDictTypes()
  } catch (e) {}
}

// ===== 字典数据 =====
const dictDataList = ref([])
const dataDialogVisible = ref(false)
const dataForm = reactive({ id: null, dictTypeId: null, dictLabel: '', dictValue: '', sortOrder: 0, status: 1 })

const loadDictData = async (typeId) => {
  try {
    const res = await getDictDataByTypeId(typeId)
    dictDataList.value = res.data || []
  } catch (e) {}
}

const handleAddData = () => {
  Object.assign(dataForm, { id: null, dictTypeId: currentType.value.id, dictLabel: '', dictValue: '', sortOrder: 0, status: 1 })
  dataDialogVisible.value = true
}

const handleEditData = (row) => {
  Object.assign(dataForm, { id: row.id, dictTypeId: row.dictTypeId, dictLabel: row.dictLabel, dictValue: row.dictValue, sortOrder: row.sortOrder, status: row.status })
  dataDialogVisible.value = true
}

const handleDeleteData = async (row) => {
  await ElMessageBox.confirm(`确定删除「${row.dictLabel}」？`, '提示', { type: 'warning' })
  await deleteDictData(row.id)
  ElMessage.success('删除成功')
  loadDictData(currentType.value.id)
}

const submitData = async () => {
  try {
    if (dataForm.id) {
      await updateDictData(dataForm.id, dataForm)
    } else {
      await createDictData(dataForm)
    }
    ElMessage.success('保存成功')
    dataDialogVisible.value = false
    loadDictData(currentType.value.id)
  } catch (e) {}
}

onMounted(loadDictTypes)
</script>

<style scoped>
.dict-layout {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.dict-types {
  width: 380px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.dict-data {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
  overflow: auto;
}
.dict-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f2f5;
  font-weight: 600;
  font-size: 14px;
  color: #1f2329;
}
.dict-type-list { flex: 1; min-height: 0; overflow: auto; display: flex; flex-direction: column; gap: 8px; padding-right: 2px }
.dict-type-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all .18s;
  background: #fafbfc;
}
.dict-type-item:hover { border-color: #b3d3f8; background: #f5f9ff }
.dict-type-item.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 1px #409eff inset;
}
.dict-type-name { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: #1f2329 }
.dict-type-ico { color: #409eff; font-size: 15px }
.dict-type-item.active .dict-type-ico { color: #1f6fd6 }
.dict-type-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.dict-type-code { font-size: 11px; color: #a8abb2; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; margin: 3px 0 4px 21px }
.dict-type-acts { margin-left: 21px; display: flex; gap: 2px; opacity: 0; transition: opacity .15s }
.dict-type-item:hover .dict-type-acts, .dict-type-item.active .dict-type-acts { opacity: 1 }
.dict-empty-tip { display: flex; align-items: center; justify-content: center; height: 100%; min-height: 260px }
</style>
