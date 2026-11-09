<template>
  <!-- 【2026-11-09 文件权限 v2 + 骨架重构】文件空间授权: 主体(某个人|角色) × 空间(公共/私人) × 动作,
       保存即实时生效(判定层 typeGranted, 覆盖该空间全部存量+未来文件夹, 取消勾选立即收回);
       四项激励文件跟随激励记录授权(本页签激励子域), 此处不配置。
       【骨架重构】随专项页签「文件权限」子域挂载: 顶部工具条(主体切换)+两空间区块+激励指引,
       样式自持(fsd-sec*), 不再借用父组件 scoped 的 pc-sec*(跨作用域实际不生效的暗病)。 -->
  <div class="fsd">
    <div class="fsd-toolbar">
      <span class="fsd-tb-hint">勾选即对该空间全部文件夹生效（含存量与今后新建），取消勾选立即收回</span>
      <el-radio-group v-model="subjectType" size="small">
        <el-radio-button value="ROLE">按角色</el-radio-button>
        <el-radio-button value="USER">按人员</el-radio-button>
      </el-radio-group>
    </div>

    <div v-for="t in TYPES" :key="t.value" class="fsd-sec">
      <div class="fsd-sec-head">
        <span class="fsd-sec-title">{{ t.label }}</span>
        <span class="fsd-sec-hint">{{ t.hint }}</span>
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
        <el-select v-if="subjectType === 'ROLE'" v-model="form[t.value].subjectIds" multiple collapse-tags
                   placeholder="选择角色（可多选）" size="small" style="width:220px" class="fsd-subject">
          <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
        </el-select>
        <el-select v-else v-model="form[t.value].subjectIds" multiple collapse-tags filterable remote :remote-method="searchUsers"
                   :loading="userSearching" placeholder="输入姓名 / 账号搜索（可多选）" size="small" style="width:260px" class="fsd-subject"
                   @focus="searchUsers('')">
          <el-option v-for="u in userOptions" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
        </el-select>
        <el-checkbox-group v-model="form[t.value].perms" size="small" class="fsd-perms">
          <el-checkbox v-for="o in PERM_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
        </el-checkbox-group>
        <el-button type="primary" size="small" :disabled="!form[t.value].subjectIds.length || !form[t.value].perms.length"
                   :loading="saving === t.value" @click="saveRow(t.value)">保存</el-button>
      </div>
      <div v-if="t.value === 'private' && canEdit" class="fsd-warn">
        ⚠️ 勾「查看/下载」给某人 = 该人可看<strong>全员</strong>私人文件夹，属高权限动作，慎用；本人与其上级对私人夹天然全权，通常无需勾选。
      </div>
    </div>

    <div class="fsd-sec">
      <div class="fsd-sec-head">
        <span class="fsd-sec-title">四项激励文件</span>
        <span class="fsd-sec-hint">跟随激励记录授权——能看记录即能看文件，能编辑记录即能改名/替换/删附件</span>
      </div>
      <el-alert type="info" :closable="false" show-icon
                title="四项激励的文件是业务记录附件，权限由本页签「激励授权」子域管理（查看分享 / 查看全部 / 编辑 / 删除），此处不可配置。" />
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
  { value: 'public', label: '公共空间（公司文件）', hint: '「新建文件夹」=可建根级公共文件夹（否则仅管理员）' },
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
  public: { subjectIds: [], perms: [] },
  private: { subjectIds: [], perms: [] }
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

// 【2026-11-10 用户令"需要能批量选择"】主体多选批量授权: 逐主体并行下发(Promise.allSettled,
// AclDialog 多角色保存同款配方——中途单个失败不中断, 成功 N / 失败 M 汇总, 失败主体保留勾选直接重试)
const saveRow = async (type) => {
  saving.value = type
  const ids = [...form[type].subjectIds]
  const results = await Promise.allSettled(ids.map(id =>
    saveFolderTypeDefault({ folderType: type, subjectType: subjectType.value, subjectId: id, perms: form[type].perms })))
  const okCount = results.filter(r => r.status === 'fulfilled').length
  const failCount = ids.length - okCount
  if (failCount === 0) {
    ElMessage.success(`已保存 ${okCount} 个主体，立即生效（该空间全部文件夹）`)
    form[type].subjectIds = []
    form[type].perms = []
  } else {
    form[type].subjectIds = ids.filter((id, i) => results[i].status === 'rejected')
    ElMessage.warning(`保存：成功 ${okCount} / 失败 ${failCount}（失败主体已保留勾选，可直接重试）`)
  }
  await load()
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
/* 区块骨架自持(同权限中心 pc-sec 语言, 不跨作用域借样式) */
.fsd-sec + .fsd-sec { margin-top: 16px; }
.fsd-sec-head { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 10px; font-size: 14px; color: #1f2d3d; margin-bottom: 10px; }
.fsd-sec-title { font-weight: 600; white-space: nowrap; flex-shrink: 0; }
.fsd-sec-hint { font-size: 12px; font-weight: 400; color: var(--el-color-info); }
.fsd-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px 12px; margin-bottom: 14px; }
.fsd-tb-hint { font-size: 12px; color: var(--el-color-info); }
.fsd-pill { margin: 0 4px 4px 0; }
.fsd-form { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-top: 12px; padding: 10px 12px; background: #f7f8fa; border-radius: 8px; }
.fsd-perms { display: flex; flex-wrap: wrap; gap: 0 8px; }
.fsd-perms :deep(.el-checkbox) { margin-right: 0; height: 24px; }
.fsd-warn { margin-top: 8px; padding: 6px 10px; font-size: 12px; color: #b45200; background: #fdf6ec; border-radius: 6px; }
</style>
