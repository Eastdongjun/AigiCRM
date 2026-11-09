<template>
  <!-- 【2026-11-09 类型级默认权限】文件空间默认权限: 管理员按 角色×空间(公共/个人)×动作 配置默认;
       新建文件夹自动继承该类型默认(后端物化进 biz_folder_acl, 可逐夹微调), 根级公共建夹门槛
       = admin ∨ 默认含「新建文件夹」。词表与逐夹 ACL 同源(短名) + create。 -->
  <div class="fsd">
    <div v-for="t in TYPES" :key="t.value" class="pc-sec" :style="t.value === 'private' ? 'margin-top:20px' : ''">
      <div class="pc-sec-head">
        <span class="pc-sec-title">{{ t.label }}</span>
        <span class="pc-sec-hint">{{ t.hint }}</span>
      </div>
      <el-table :data="rowsOf(t.value)" size="small" style="width:100%">
        <el-table-column prop="roleName" label="角色" min-width="120" />
        <el-table-column label="默认权限" min-width="320">
          <template #default="scope">
            <el-tag v-for="p in (scope?.row?.perms || [])" :key="p" size="small"
                    :type="p === 'create' ? 'success' : 'info'" class="fsd-pill">{{ labelOf(p) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope">
            <el-button v-if="canEdit" link type="danger" size="small" @click="removeRow(t.value, scope?.row?.roleId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rowsOf(t.value).length" :description="`暂无${t.label}默认权限`" :image-size="48" />
      <div v-if="canEdit" class="fsd-form">
        <el-select v-model="form[t.value].roleId" placeholder="选择角色" size="small" style="width:180px" class="fsd-role">
          <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
        </el-select>
        <el-checkbox-group v-model="form[t.value].perms" size="small" class="fsd-perms">
          <el-checkbox v-for="o in PERM_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
        </el-checkbox-group>
        <el-button type="primary" size="small" :disabled="!form[t.value].roleId || !form[t.value].perms.length"
                   :loading="saving === t.value" @click="saveRow(t.value)">保存</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
// 权限词表与逐夹 ACL(AclDialog PERM_OPTIONS)同源 + create(可建该类型根文件夹, 仅类型层)
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFolderTypeDefaults, saveFolderTypeDefault, removeFolderTypeDefault } from '@/api/file'
import { getAclRoles } from '@/api/file'

defineProps({ canEdit: { type: Boolean, default: false } })

const TYPES = [
  { value: 'public', label: '公共空间（公司文件）', hint: '默认权限作用于新建公共文件夹；「新建文件夹」=可建根级公共文件夹（否则仅管理员）' },
  { value: 'private', label: '个人空间', hint: '默认权限作用于新建个人文件夹（本人恒全权）；建个人夹门槛不变：持有文件夹管理权限即可' }
]
const PERM_OPTIONS = [
  { value: 'view', label: '查看' },
  { value: 'upload', label: '上传' },
  { value: 'edit', label: '编辑(重命名/替换)' },
  { value: 'delete', label: '删除' },
  { value: 'share', label: '分享' },
  { value: 'download', label: '下载' },
  { value: 'create', label: '新建文件夹' }
]
const labelOf = (v) => PERM_OPTIONS.find(o => o.value === v)?.label || v

const rows = ref([])
const roleOptions = ref([])
const saving = ref('')
const form = reactive({
  public: { roleId: null, perms: [] },
  private: { roleId: null, perms: [] }
})

const rowsOf = (type) => rows.value.filter(r => r.folderType === type)

const load = async () => {
  try { rows.value = (await getFolderTypeDefaults()).data || [] } catch (e) { rows.value = [] }
}

const saveRow = async (type) => {
  saving.value = type
  try {
    await saveFolderTypeDefault({ folderType: type, roleId: form[type].roleId, perms: form[type].perms })
    ElMessage.success('已保存，新建文件夹起生效')
    form[type].roleId = null
    form[type].perms = []
    await load()
  } catch (e) { /* 拦截器已提示 */ }
  saving.value = ''
}

const removeRow = async (type, roleId) => {
  try {
    await ElMessageBox.confirm('删除后该类型不再有此默认权限（已建文件夹的授权不受影响）', '删除默认权限', { type: 'warning' })
  } catch (e) { return }
  try {
    await removeFolderTypeDefault(type, roleId)
    ElMessage.success('已删除')
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(async () => {
  await load()
  try { roleOptions.value = (await getAclRoles()).data || [] } catch (e) { roleOptions.value = [] }
})
</script>

<style scoped>
.fsd-pill { margin: 0 4px 4px 0; }
.fsd-form { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-top: 12px; padding: 10px 12px; background: #f7f8fa; border-radius: 8px; }
.fsd-perms { display: flex; flex-wrap: wrap; gap: 0 8px; }
.fsd-perms :deep(.el-checkbox) { margin-right: 0; height: 24px; }
</style>
