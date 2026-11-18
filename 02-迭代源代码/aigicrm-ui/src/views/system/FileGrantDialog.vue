<template>
  <el-dialog v-model="visible" title="文件统一授权（biz_file_grant）" width="900px" destroy-on-close>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px"
      title="DENY 优先于 ALLOW 和上传者本人；文件夹授权默认继承到夹内文件；过期行自动失效。" />
    <div style="margin-bottom:8px">
      <el-button v-if="canEdit" type="primary" size="small" @click="openAdd">新增授权</el-button>
      <el-button size="small" @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" size="small" stripe max-height="360">
      <el-table-column label="主体" width="150">
        <template #default="{ row }">{{ row.subjectType }} #{{ row.subjectId }}</template>
      </el-table-column>
      <el-table-column label="对象" width="150">
        <template #default="{ row }">{{ row.objectType }}{{ row.objectId ? ' #' + row.objectId : '' }}</template>
      </el-table-column>
      <el-table-column prop="action" label="动作" width="90" />
      <el-table-column label="效果" width="80">
        <template #default="{ row }"><el-tag size="small" :type="row.effect === 'DENY' ? 'danger' : 'success'">{{ row.effect }}</el-tag></template>
      </el-table-column>
      <el-table-column label="继承" width="60">
        <template #default="{ row }">{{ row.inherit ? '是' : '否' }}</template>
      </el-table-column>
      <!-- 【A2】空间类型/范围模式拆列：spaceType=SPACE 空间类型(NULL=任意,存量迁移行)；scopeMode+scopeLevel=数据范围 -->
      <el-table-column label="空间类型" width="90">
        <template #default="{ row }">{{ row.spaceType || (row.objectType === 'SPACE' ? '任意' : '—') }}</template>
      </el-table-column>
      <el-table-column label="范围" width="110">
        <template #default="{ row }">{{ row.scopeMode === 'RELATIVE' ? `相对·${row.scopeLevel}` : (row.scopeMode || (row.scopeLevel || '不限')) }}</template>
      </el-table-column>
      <el-table-column prop="expireAt" label="到期" width="160" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button v-if="canEdit" link size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" title="新增统一授权" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="主体类型">
          <el-radio-group v-model="form.subjectType">
            <el-radio value="USER">人员</el-radio>
            <el-radio value="ROLE">角色</el-radio>
            <el-radio value="POSITION">岗位</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="主体 ID" required>
          <el-select v-if="form.subjectType === 'USER'" v-model="form.subjectId" filterable style="width:100%">
            <el-option v-for="u in users" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
          </el-select>
          <el-select v-else-if="form.subjectType === 'ROLE'" v-model="form.subjectId" filterable style="width:100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
          <el-input v-else v-model.number="form.subjectId" placeholder="岗位 ID" />
        </el-form-item>
        <el-form-item label="对象类型">
          <el-radio-group v-model="form.objectType">
            <el-radio value="SPACE">空间</el-radio>
            <el-radio value="FOLDER">文件夹</el-radio>
            <el-radio value="FILE">文件</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.objectType !== 'SPACE'" label="对象 ID" required>
          <el-input v-model.number="form.objectId" placeholder="文件夹/文件 ID" />
        </el-form-item>
        <el-form-item v-else label="空间类型" required>
          <!-- 【A2 §2.6/§2.7】SPACE 必填 spaceType（新保存禁止空值；NULL=任意空间仅存量迁移行） -->
          <el-select v-model="form.spaceType" placeholder="public/private/incentive/fabushi" style="width:100%">
            <el-option label="公共 public" value="public" />
            <el-option label="私人 private" value="private" />
            <el-option label="激励 incentive" value="incentive" />
            <el-option label="法布施 fabushi" value="fabushi" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="form.action" style="width:100%">
            <el-option v-for="a in ACTIONS" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="效果">
          <el-radio-group v-model="form.effect">
            <el-radio value="ALLOW">ALLOW</el-radio>
            <el-radio value="DENY">DENY</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="继承到文件">
          <el-switch v-model="form.inherit" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="组织范围">
          <!-- 【A2】scope_mode 默认 SUBTREE：组织节点按子树(含下级组织)匹配；留空=不限组织 -->
          <el-input v-model.number="form.scopeOrgId" placeholder="组织节点 ID（含下级组织），留空=不限" />
        </el-form-item>
        <el-form-item label="到期">
          <el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空=长期" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { getAclRoles } from '@/api/file'
import { getUserList } from '@/api/system'

const visible = defineModel({ type: Boolean, default: false })
defineProps({ canEdit: { type: Boolean, default: false } })
const rows = ref([])
const users = ref([])
const roles = ref([])
const formVisible = ref(false)
const saving = ref(false)
const ACTIONS = ['view', 'download', 'upload', 'edit', 'delete', 'share', 'manage', 'create', '*']
const emptyForm = () => ({ subjectType: 'USER', subjectId: null, objectType: 'SPACE', objectId: null,
  spaceType: '', action: 'view', effect: 'ALLOW', inherit: 1, scopeOrgId: null, scopeMode: '', expireAt: null })
const form = reactive(emptyForm())

const load = async () => {
  try {
    const res = await request.get('/file/grants')
    rows.value = res.data || []
  } catch (e) { /* 拦截器提示 */ }
}
const loadRefs = async () => {
  try { roles.value = (await getAclRoles()).data || [] } catch (e) { roles.value = [] }
  try { users.value = (await getUserList({ current: 1, size: 1000 })).data?.records || [] } catch (e) { users.value = [] }
}
const openAdd = () => {
  Object.assign(form, emptyForm())
  formVisible.value = true
}
const save = async () => {
  if (!form.subjectId || !form.action) return ElMessage.warning('请选择主体与动作')
  // 【A2 §2.6】SPACE 必填 spaceType（后端同口径校验，前端先行提示）
  if (form.objectType === 'SPACE' && !form.spaceType) return ElMessage.warning('空间授权必须选择空间类型')
  saving.value = true
  try {
    await request.post('/file/grants', form)
    ElMessage.success('授权已保存')
    formVisible.value = false
    load()
  } catch (e) { /* 拦截器提示 */ } finally { saving.value = false }
}
const remove = async (row) => {
  await ElMessageBox.confirm(`删除 ${row.subjectType} #${row.subjectId} 的 ${row.action} 授权？`, '删除授权', { type: 'warning' })
  await request.delete(`/file/grants/${row.id}`)
  ElMessage.success('已删除')
  load()
}
const open = () => { visible.value = true; load(); loadRefs() }
defineExpose({ open })
</script>
