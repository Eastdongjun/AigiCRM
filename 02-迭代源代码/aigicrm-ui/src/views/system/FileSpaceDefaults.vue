<template>
  <!-- 【2026-11-09 文件权限 v2】文件权限: 管理员按 主体(某个人|角色) × 空间(公共/私人) × 动作 勾选授权,
       保存即实时生效(判定层 typeGranted, 覆盖该空间全部存量+未来文件夹, 取消勾选立即收回);
       四项激励文件跟随激励记录授权(专项权限页签), 此处不配置。词表=8 细动作(edit 已拆改名/替换) + create。 -->
  <div class="fsd">
    <div class="pc-sec" style="margin-top:20px">
      <div class="pc-sec-head">
        <span class="pc-sec-title">文件权限</span>
        <span class="pc-sec-hint">某个人或角色 × 公共/私人空间 × 动作，保存立即生效（覆盖该空间全部文件夹，取消即收回）</span>
        <el-radio-group v-model="subjectType" size="small" style="margin-left:auto">
          <el-radio-button value="ROLE">按角色</el-radio-button>
          <el-radio-button value="USER">按人员</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div v-for="t in TYPES" :key="t.value" class="pc-sec" style="margin-top:16px">
      <div class="pc-sec-head">
        <span class="pc-sec-title">{{ t.label }}</span>
        <span class="pc-sec-hint">{{ t.hint }}</span>
      </div>
      <el-table :data="rowsOf(t.value)" size="small" style="width:100%">
        <el-table-column label="主体" min-width="170">
          <template #default="scope">
            <el-tag size="small" :type="scope?.row?.subjectType === 'USER' ? 'warning' : 'info'" class="fsd-pill">
              {{ scope?.row?.subjectType === 'USER' ? '人员' : '角色' }}
            </el-tag>
            {{ scope?.row?.subjectName }}
          </template>
        </el-table-column>
        <el-table-column label="授权动作" min-width="320">
          <template #default="scope">
            <el-tag v-for="p in (scope?.row?.perms || [])" :key="p" size="small"
                    :type="p === 'create' ? 'success' : 'info'" class="fsd-pill">{{ labelOf(p) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope">
            <el-button v-if="canEdit" link type="danger" size="small"
                       @click="removeRow(t.value, scope?.row?.subjectType, scope?.row?.subjectId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rowsOf(t.value).length" :description="`暂无${t.label}授权`" :image-size="48" />
      <div v-if="canEdit" class="fsd-form">
        <el-select v-if="subjectType === 'ROLE'" v-model="form[t.value].subjectId" placeholder="选择角色" size="small"
                   style="width:200px" class="fsd-subject">
          <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
        </el-select>
        <el-select v-else v-model="form[t.value].subjectId" filterable remote :remote-method="searchUsers"
                   :loading="userSearching" placeholder="输入姓名 / 账号搜索" size="small" style="width:240px" class="fsd-subject"
                   @focus="searchUsers('')">
          <el-option v-for="u in userOptions" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
        </el-select>
        <el-checkbox-group v-model="form[t.value].perms" size="small" class="fsd-perms">
          <el-checkbox v-for="o in PERM_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
        </el-checkbox-group>
        <el-button type="primary" size="small" :disabled="!form[t.value].subjectId || !form[t.value].perms.length"
                   :loading="saving === t.value" @click="saveRow(t.value)">保存</el-button>
      </div>
      <div v-if="t.value === 'private' && canEdit" class="fsd-warn">
        ⚠️ 勾「查看/下载」给某人 = 该人可看<strong>全员</strong>私人文件夹，属高权限动作，慎用；本人与其上级对私人夹天然全权，通常无需勾选。
      </div>
    </div>

    <div class="pc-sec" style="margin-top:16px">
      <div class="pc-sec-head">
        <span class="pc-sec-title">四项激励文件</span>
        <span class="pc-sec-hint">跟随激励记录授权——能看记录即能看文件，能编辑记录即能改名/替换/删附件</span>
      </div>
      <el-alert type="info" :closable="false" show-icon
                title="四项激励的文件是业务记录附件，权限由本页签上方「激励专项授权」管理（查看分享 / 查看全部 / 编辑 / 删除），此处不可配置。" />
    </div>
  </div>
</template>

<script setup>
// 权限词表=8 细动作 + create(可建该类型根文件夹, 仅类型层); 存量行的 edit 由后端读时展开兼容
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFolderTypeDefaults, saveFolderTypeDefault, removeFolderTypeDefault, getAclRoles } from '@/api/file'
import { getUserList } from '@/api/system'

defineProps({ canEdit: { type: Boolean, default: false } })

const TYPES = [
  { value: 'public', label: '公共空间（公司文件）', hint: '勾选即对该空间全部公共文件夹生效（含存量与今后新建）；「新建文件夹」=可建根级公共文件夹（否则仅管理员）' },
  { value: 'private', label: '个人空间', hint: '作用于全员私人文件夹；夹主本人与其上级链天然全权；建私人夹门槛不变：持有文件夹管理权限即可' }
]
const PERM_OPTIONS = [
  { value: 'view', label: '查看' },
  { value: 'upload', label: '上传' },
  { value: 'rename', label: '改名' },
  { value: 'replace', label: '替换' },
  { value: 'delete', label: '删除' },
  { value: 'share', label: '分享' },
  { value: 'download', label: '下载' },
  { value: 'create', label: '新建文件夹' }
]
// 存量行遗留 edit 伞的展示兜底
const LEGACY_LABELS = { edit: '编辑(改名+替换)' }
const labelOf = (v) => PERM_OPTIONS.find(o => o.value === v)?.label || LEGACY_LABELS[v] || v

const rows = ref([])
const roleOptions = ref([])
const userOptions = ref([])
const userSearching = ref(false)
const saving = ref('')
const subjectType = ref('USER') // 默认按人(用户主诉求: 点名某人)
const form = reactive({
  public: { subjectId: null, perms: [] },
  private: { subjectId: null, perms: [] }
})

const rowsOf = (type) => rows.value.filter(r => r.folderType === type)

const load = async () => {
  try { rows.value = (await getFolderTypeDefaults()).data || [] } catch (e) { rows.value = [] }
}

const searchUsers = async (query) => {
  userSearching.value = true
  try {
    const res = await getUserList({ current: 1, size: 20, keyword: query || undefined })
    userOptions.value = res.data?.records || []
  } catch (e) { /* 检索失败保留现有选项(与生效预览同口径) */ }
  userSearching.value = false
}

const saveRow = async (type) => {
  saving.value = type
  try {
    await saveFolderTypeDefault({ folderType: type, subjectType: subjectType.value, subjectId: form[type].subjectId, perms: form[type].perms })
    ElMessage.success('已保存，立即生效（该空间全部文件夹）')
    form[type].subjectId = null
    form[type].perms = []
    await load()
  } catch (e) { /* 拦截器已提示 */ }
  saving.value = ''
}

const removeRow = async (type, st, sid) => {
  try {
    await ElMessageBox.confirm('删除后该主体在此空间的授权立即收回（本人夹主权/上级链不受影响）', '删除授权', { type: 'warning' })
  } catch (e) { return }
  try {
    await removeFolderTypeDefault(type, st, sid)
    ElMessage.success('已删除，立即生效')
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
.fsd-warn { margin-top: 8px; padding: 6px 10px; font-size: 12px; color: #b45200; background: #fdf6ec; border-radius: 6px; }
</style>
