<template>
  <div class="config-page">

    <div class="section-box">
      <el-tabs v-model="activeTab">
        <!-- 基本设置 -->
        <el-tab-pane label="基本设置" name="basic">
          <el-form label-width="120px" class="basic-form">
            <el-form-item label="系统名称">
              <el-input v-model="configMap['system.name']" style="width: 300px" />
            </el-form-item>
            <el-form-item label="系统Logo">
              <div style="display: flex; align-items: center; gap: 12px">
                <img v-if="configMap['system.logo']" :src="configMap['system.logo']" class="logo-preview" />
                <el-upload
                  :action="'/api/file/upload'"
                  :headers="{ Authorization: 'Bearer ' + token }"
                  :data="{ folderId: 0, scope: 'public' }"
                  :show-file-list="false"
                  accept="image/*"
                  :on-success="handleLogoUpload"
                >
                  <el-button type="primary">上传Logo</el-button>
                </el-upload>
                <el-button v-if="configMap['system.logo']" type="danger" link @click="configMap['system.logo'] = ''">清除</el-button>
              </div>
            </el-form-item>
            <el-form-item label="客户保护期">
              <el-input v-model="configMap['customer.protection.days']" style="width: 300px">
                <template #suffix>天</template>
              </el-input>
            </el-form-item>
            <el-form-item label="提醒提前">
              <el-input v-model="configMap['customer.warning.days']" style="width: 300px">
                <template #suffix>天</template>
              </el-input>
            </el-form-item>
            <el-form-item label="编号规则">
              <el-select v-model="configMap['customer.code.rule']" style="width: 300px">
                <el-option label="大区首字母-年月-顺序号 (如 NM-202606-001)" value="region_date_seq" />
                <el-option label="大区首字母-顺序号 (如 NM-001)" value="region_seq" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSave">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 字典管理 -->
        <el-tab-pane label="字典管理" name="dict">
          <div class="dict-layout">
            <!-- 左侧: 字典类型 -->
            <div class="dict-types">
              <div class="dict-header">
                <span>字典类型</span>
                <el-button type="primary" size="small" :icon="Plus" @click="handleAddType">新增</el-button>
              </div>
              <el-table :data="dictTypes" highlight-current-row @current-change="handleTypeSelect" size="small" style="width: 100%">
                <el-table-column type="index" label="#" width="40" />
                <el-table-column prop="dictName" label="名称" min-width="100" />
                <el-table-column prop="dictCode" label="编码" width="100" show-overflow-tooltip />
                <el-table-column label="操作" width="100" align="center">
                  <template #default="{ row }">
                    <el-button link size="small" @click.stop="handleEditType(row)">编辑</el-button>
                    <el-button v-if="!row.builtIn" link size="small" type="danger" @click.stop="handleDeleteType(row)">删除</el-button>
                    <el-tag v-else size="small" type="info" effect="plain">内置</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <!-- 右侧: 字典数据 -->
            <div class="dict-data">
              <div class="dict-header">
                <span>{{ currentType ? currentType.dictName + ' - 数据列表' : '请选择字典类型' }}</span>
                <el-button type="primary" size="small" :icon="Plus" :disabled="!currentType" @click="handleAddData">新增</el-button>
              </div>
              <el-table :data="dictDataList" size="small" style="width: 100%">
                <el-table-column prop="dictLabel" label="显示标签" />
                <el-table-column prop="dictValue" label="数据值" />
                <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
                <el-table-column label="状态" width="70" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center">
                  <template #default="{ row }">
                    <el-button link size="small" @click="handleEditData(row)">编辑</el-button>
                    <el-button link size="small" type="danger" @click="handleDeleteData(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>
        <!-- 公海配置 -->
        <el-tab-pane label="公海配置" name="sea">
          <div class="dict-header">
            <span>公海池列表</span>
            <el-button type="primary" size="small" :icon="Plus" @click="handleAddPool">新增公海池</el-button>
          </div>
          <el-table :data="seaPools" size="small" style="width: 100%">
            <el-table-column prop="poolName" label="公海池名称" width="160" />
            <el-table-column prop="visibleNames" label="可见部门/人员" min-width="200" show-overflow-tooltip />
            <el-table-column prop="regionNames" label="对应办事处" min-width="140" show-overflow-tooltip />
            <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
            <el-table-column label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="{ row }">
                <el-button link size="small" @click="handleEditPool(row)">编辑</el-button>
                <el-button link size="small" type="danger" @click="handleDeletePool(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <!-- 激励类别 -->
        <el-tab-pane label="激励类别" name="incentiveCategory">
          <div class="dict-header">
            <span>激励类别管理</span>
            <el-button type="primary" size="small" :icon="Plus" @click="openCategoryDialog()">新增类别</el-button>
          </div>
          <el-table :data="categoryTree" stripe size="small" row-key="id" default-expand-all :tree-props="{ children: 'children' }">
            <el-table-column prop="categoryName" label="类别名称" min-width="200" />
            <el-table-column prop="categoryCode" label="编码" width="120" />
            <el-table-column label="分值" width="70" align="center">
              <template #default="{ row }">
                <span v-if="row.score > 0">{{ row.score }}分</span>
                <span v-else style="color:#c0c4cc">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
            <el-table-column label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openCategoryDialog(row)">编辑</el-button>
                <el-button link type="primary" size="small" v-if="row.parentId === 0" @click="openCategoryDialog(null, row.id)">添加子项</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteCategory(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 公海池编辑弹窗 -->
    <el-dialog v-model="poolDialogVisible" :title="poolForm.id ? '编辑公海池' : '新增公海池'" width="500px">
      <el-form :model="poolForm" label-width="100px">
        <el-form-item label="公海池名称">
          <el-input v-model="poolForm.poolName" placeholder="如：内蒙办公海" />
        </el-form-item>
        <el-form-item label="可见部门/人员">
          <el-tree-select
            v-model="poolForm.visibleKeys"
            :data="orgTree"
            multiple
            check-strictly
            show-checkbox
            :render-after-expand="false"
            node-key="id"
            :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
            placeholder="选择可见的部门或人员"
            style="width: 100%"
            clearable
            collapse-tags
            collapse-tags-tooltip
          />
          <div class="form-tip">可多选部门或部门下的人员；办事处/总部不可选，仅用于展开层级</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="poolForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="poolDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPool">确定</el-button>
      </template>
    </el-dialog>

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
    <!-- 激励类别编辑弹窗 -->
    <el-dialog v-model="categoryDialogVisible" :title="categoryForm.id ? '编辑类别' : '新增类别'" width="500px">
      <el-form :model="categoryForm" label-width="80px">
        <el-form-item label="上级类别">
          <el-tree-select
            v-model="categoryForm.parentId"
            :data="categoryParentOptions"
            :props="{ label: 'categoryName', value: 'id', children: 'children' }"
            check-strictly
            placeholder="无（作为顶级类别）"
            style="width:100%"
            clearable
          />
        </el-form-item>
        <el-form-item label="类别名称">
          <el-input v-model="categoryForm.categoryName" placeholder="如：外出参观、涂层类" />
        </el-form-item>
        <el-form-item label="类别编码">
          <el-input v-model="categoryForm.categoryCode" placeholder="如：visit、trial_coating" />
        </el-form-item>
        <el-form-item label="分值">
          <el-input-number v-model="categoryForm.score" :min="0" :max="100" />
          <span style="color:#999;font-size:12px;margin-left:8px">顶级类别分值为0，子项设置实际分值</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="categoryForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  getConfigList, updateConfigs,
  getDictTypes, createDictType, updateDictType, deleteDictType,
  getDictDataByTypeId, createDictData, updateDictData, deleteDictData
} from '@/api/system'
import { getSeaConfigs, createSeaConfig, updateSeaConfig, deleteSeaConfig, getOrgTree } from '@/api/sea'
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '@/api/incentive'

const userStore = useUserStore()
const activeTab = ref('basic')
const token = localStorage.getItem('token')

// ===== 基本设置 =====
const configs = ref([])
const configMap = reactive({})

const loadConfigs = async () => {
  try {
    const res = await getConfigList()
    configs.value = res.data || []
    configs.value.forEach(c => { configMap[c.configKey] = c.configValue })
  } catch (e) {}
}

const handleLogoUpload = async (res) => {
  if (res.code === 200 && res.data) {
    configMap['system.logo'] = res.data.ossUrl || res.data.url || ''
    ElMessage.success('Logo上传成功，正在保存...')
    await handleSave()
  } else {
    ElMessage.error('上传失败')
  }
}

const handleSave = async () => {
  try {
    // 以 configMap 为准提交所有键(含 DB 中尚不存在的键),后端按 configKey upsert
    const metaByKey = {}
    configs.value.forEach(c => { metaByKey[c.configKey] = c })
    const updated = Object.keys(configMap).map(key => ({
      id: metaByKey[key]?.id ?? null,
      configKey: key,
      configValue: configMap[key],
      description: metaByKey[key]?.description ?? null
    }))
    await updateConfigs(updated)
    ElMessage.success('设置已保存')
    await loadConfigs()
    userStore.loadSystemConfig()
  } catch (e) {}
}

// ===== 字典类型 =====
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

// ===== 公海池配置 =====
const seaPools = ref([])
const orgTree = ref([])
const poolDialogVisible = ref(false)
const poolForm = reactive({ id: null, poolName: '', visibleKeys: [], sortOrder: 0 })

const loadSeaPools = async () => {
  try {
    const res = await getSeaConfigs()
    seaPools.value = res.data || []
  } catch (e) {}
}

const loadOrgTree = async () => {
  try {
    const res = await getOrgTree()
    orgTree.value = res.data || []
  } catch (e) {}
}

// 由 visibleDeptIds/visibleUserIds 还原树节点 key (d_<id> / u_<id>)
const buildVisibleKeys = (deptIds, userIds) => {
  const keys = []
  if (deptIds) deptIds.split(',').filter(Boolean).forEach(id => keys.push('d_' + id.trim()))
  if (userIds) userIds.split(',').filter(Boolean).forEach(id => keys.push('u_' + id.trim()))
  return keys
}

const handleAddPool = () => {
  Object.assign(poolForm, { id: null, poolName: '', visibleKeys: [], sortOrder: 0 })
  poolDialogVisible.value = true
}

const handleEditPool = (row) => {
  Object.assign(poolForm, {
    id: row.id,
    poolName: row.poolName,
    visibleKeys: buildVisibleKeys(row.visibleDeptIds, row.visibleUserIds),
    sortOrder: row.sortOrder || 0
  })
  poolDialogVisible.value = true
}

const handleDeletePool = async (row) => {
  await ElMessageBox.confirm(`确定删除公海池「${row.poolName}」？`, '提示', { type: 'warning' })
  await deleteSeaConfig(row.id)
  ElMessage.success('删除成功')
  loadSeaPools()
}

const submitPool = async () => {
  if (!poolForm.poolName) { ElMessage.warning('请输入公海池名称'); return }
  if (!poolForm.visibleKeys.length) { ElMessage.warning('请选择可见部门/人员'); return }
  try {
    // 拆分树节点 key 为可见部门ID / 可见人员ID
    const deptIds = poolForm.visibleKeys.filter(k => k.startsWith('d_')).map(k => k.slice(2))
    const userIds = poolForm.visibleKeys.filter(k => k.startsWith('u_')).map(k => k.slice(2))
    const payload = {
      poolName: poolForm.poolName,
      visibleDeptIds: deptIds.join(','),
      visibleUserIds: userIds.join(','),
      sortOrder: poolForm.sortOrder
    }
    if (poolForm.id) {
      await updateSeaConfig(poolForm.id, payload)
    } else {
      await createSeaConfig(payload)
    }
    ElMessage.success('保存成功')
    poolDialogVisible.value = false
    loadSeaPools()
  } catch (e) {}
}

// ===== 激励类别 =====
const categoryTree = ref([])
const categoryDialogVisible = ref(false)
const categoryForm = reactive({ id: null, parentId: 0, categoryName: '', categoryCode: '', score: 0, sortOrder: 0, status: 1 })

const categoryParentOptions = computed(() =>
  categoryTree.value.map(c => ({ id: c.id, categoryName: c.categoryName }))
)

const loadCategoryTree = async () => {
  try {
    const res = await getCategoryTree()
    categoryTree.value = res.data || []
  } catch (e) {}
}

const openCategoryDialog = (row, parentId) => {
  if (row) {
    Object.assign(categoryForm, { id: row.id, parentId: row.parentId, categoryName: row.categoryName, categoryCode: row.categoryCode, score: row.score, sortOrder: row.sortOrder, status: row.status })
  } else {
    Object.assign(categoryForm, { id: null, parentId: parentId || 0, categoryName: '', categoryCode: '', score: parentId ? 1 : 0, sortOrder: 0, status: 1 })
  }
  categoryDialogVisible.value = true
}

const submitCategory = async () => {
  if (!categoryForm.categoryName.trim()) { ElMessage.warning('请输入类别名称'); return }
  try {
    if (categoryForm.id) {
      await updateCategory(categoryForm.id, categoryForm)
    } else {
      await createCategory(categoryForm)
    }
    ElMessage.success('保存成功')
    categoryDialogVisible.value = false
    loadCategoryTree()
  } catch (e) {}
}

const handleDeleteCategory = async (row) => {
  const hasChildren = row.children && row.children.length > 0
  const msg = hasChildren ? `"${row.categoryName}" 下有 ${row.children.length} 个子项，确认删除？` : `确认删除 "${row.categoryName}"？`
  try {
    await ElMessageBox.confirm(msg, '删除确认', { type: 'warning' })
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    loadCategoryTree()
  } catch (e) {}
}

onMounted(() => {
  loadConfigs()
  loadDictTypes()
  loadSeaPools()
  loadOrgTree()
  loadCategoryTree()
})
</script>

<style scoped>
.config-page { height: calc(100vh - 80px); display: flex; flex-direction: column }
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; padding: 20px 24px; flex: 1; overflow: hidden; display: flex; flex-direction: column }
.section-box :deep(.el-tabs) { flex: 1; display: flex; flex-direction: column }
.section-box :deep(.el-tabs__content) { flex: 1; overflow: auto }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }

.basic-form {
  max-width: 520px;
  padding: 20px 0;
}
.logo-preview {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  object-fit: contain;
  border: 1px solid #ebeef5;
  background: #fafafa;
}

.dict-layout {
  display: flex;
  gap: 20px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.dict-types {
  width: 360px;
  flex-shrink: 0;
  background: #fafbfd;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #f0f2f5;
  overflow: auto;
}
.dict-data {
  flex: 1;
  min-width: 0;
  background: #fafbfd;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #f0f2f5;
  overflow: auto;
}
.dict-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
  font-weight: 600;
  font-size: 14px;
  color: #1f2329;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
</style>
