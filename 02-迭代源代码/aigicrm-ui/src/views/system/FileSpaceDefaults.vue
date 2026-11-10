<template>
  <!-- 【2026-11-10 文件权限终版】例外授权(唯一加授通道, 授权必须过期):
       谁(人|角色, 组织树勾选) × 空间(公共/私人) × 对象组(文件夹: 新建/改名/删除 | 文件: 上传/查看/下载/改名/替换/分享/删除)
       × 逐夹定向(可选) × 期限(expiresAt 必填, 到期自动收回, 行内续期)。
       底座=组织矩阵(默认本人, 在「功能与范围」按角色放开档位), 本表只做跨范围特批;
       逐夹 ACL 已退役(存量由迁移转换为 folder_id 定向行)。 -->
  <div class="fsd">
    <div class="fsd-toolbar">
      <span class="fsd-tb-hint">默认权限=组织矩阵（各人只看自己的，负责人看本级子树——在「功能与范围」按角色调档）；下表只配<b>跨范围特批</b>，到期自动收回</span>
    </div>

    <div class="fsd-sec">
      <div class="fsd-sec-head"><span class="fsd-sec-title">现有授权</span></div>
      <el-table :data="rows" size="small" style="width:100%">
        <el-table-column label="空间" width="70">
          <template #default="scope">{{ scope?.row?.folderType === 'public' ? '公共' : '私人' }}</template>
        </el-table-column>
        <el-table-column label="对象" width="64">
          <template #default="scope">
            <el-tag size="small" :type="scope?.row?.objectType === 'FOLDER' ? 'warning' : 'info'" class="fsd-pill">
              {{ scope?.row?.objectType === 'FOLDER' ? '文件夹' : '文件' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="定向" min-width="90">
          <template #default="scope">
            <span v-if="scope?.row?.folderName">📂 {{ scope?.row?.folderName }}</span>
            <span v-else class="fsd-muted">整空间</span>
          </template>
        </el-table-column>
        <el-table-column label="主体" min-width="150">
          <template #default="scope">
            <el-tag size="small" :type="scope?.row?.subjectType === 'USER' ? 'warning' : 'info'" class="fsd-pill">
              {{ scope?.row?.subjectType === 'USER' ? '人员' : '角色' }}
            </el-tag>{{ scope?.row?.subjectName }}
          </template>
        </el-table-column>
        <el-table-column label="动作" min-width="220">
          <template #default="scope">
            <el-tag v-for="p in (scope?.row?.perms || [])" :key="p" size="small"
                    :type="p === 'delete' ? 'danger' : 'info'" class="fsd-pill">{{ labelOf(scope?.row?.objectType, p) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到期" width="110">
          <template #default="scope">
            <span v-if="scope?.row?.expired" class="fsd-muted" style="text-decoration:line-through">已过期</span>
            <span v-else :class="nearExpiry(scope?.row) ? 'fsd-near' : ''">{{ shortDate(scope?.row?.expiresAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="scope">
            <el-button v-if="canEdit && !scope?.row?.expired" link type="primary" size="small" @click="renewRow(scope?.row?.id)">续期</el-button>
            <el-button v-if="canEdit" link type="danger" size="small" @click="removeRow(scope?.row?.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length" description="暂无例外授权——默认规则(组织矩阵)已覆盖日常，需要跨范围特批时再添加" :image-size="48" />
    </div>

    <div v-if="canEdit" class="fsd-sec">
      <div class="fsd-sec-head"><span class="fsd-sec-title">添加授权</span></div>
      <div class="fsd-form">
        <el-select v-model="form.subjectType" size="small" style="width:110px">
          <el-option label="按人员" value="USER" /><el-option label="按角色" value="ROLE" />
        </el-select>
        <el-select v-if="form.subjectType === 'ROLE'" v-model="form.subjectIds" multiple collapse-tags collapse-tags-tooltip
                   placeholder="选择角色（可多选）" size="small" style="width:200px">
          <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
        </el-select>
        <div v-else class="fsd-tree-wrap">
          <div class="fsd-tree-tools">
            <span class="fsd-tree-count">已选 <b>{{ userCount }}</b> 人</span>
            <el-button size="small" link type="primary" @click="checkAll">全选</el-button>
            <el-button size="small" link type="danger" style="margin-left:auto" @click="clearUsers">清空</el-button>
          </div>
          <el-scrollbar height="170px" class="fsd-tree-scroll">
            <el-tree ref="treeRef" :data="orgUserTree" show-checkbox node-key="key"
                     :props="{ label: 'label', children: 'children' }" :expand-on-click-node="false" default-expand-all
                     @check="onTreeCheck" />
          </el-scrollbar>
        </div>
        <el-select v-model="form.folderType" size="small" style="width:96px">
          <el-option label="公共空间" value="public" /><el-option label="私人空间" value="private" />
        </el-select>
        <el-radio-group v-model="form.objectType" size="small">
          <el-radio-button value="FILE">文件动作</el-radio-button>
          <el-radio-button value="FOLDER">文件夹动作</el-radio-button>
        </el-radio-group>
        <el-select v-model="form.folderId" clearable placeholder="定向到…（默认整空间）" size="small" style="width:190px">
          <el-option v-for="f in folderOptions" :key="f.id" :label="`${f.folderType === 'public' ? '公共' : '私人'} · ${f.name}`" :value="f.id" />
        </el-select>
        <el-radio-group v-model="preset" size="small" @change="applyPreset">
          <el-radio-button value="viewer">查看者</el-radio-button>
          <el-radio-button value="editor">编辑者</el-radio-button>
          <el-radio-button value="manager">管理者</el-radio-button>
          <el-radio-button value="custom">自定义</el-radio-button>
        </el-radio-group>
        <el-collapse v-if="preset === 'custom'" class="fsd-adv">
          <el-collapse-item title="高级：逐动作自定义" name="adv">
            <el-checkbox-group v-model="form.perms" size="small" class="fsd-perms" @change="preset = 'custom'">
              <el-checkbox v-for="o in permOptionsOf(form.objectType)" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
            </el-checkbox-group>
          </el-collapse-item>
        </el-collapse>
        <span v-else class="fsd-preset-hint">{{ presetHint }}</span>
        <el-date-picker v-model="form.expiresAt" type="datetime" size="small" placeholder="到期时间(必填)"
                        value-format="YYYY-MM-DD HH:mm:ss" :default-value="defaultExpiry" style="width:200px" />
        <el-button type="primary" size="small" :disabled="!canSubmit" :loading="saving" @click="save">保存</el-button>
      </div>
      <div v-if="form.objectType === 'FOLDER'" class="fsd-warn">文件夹动作=新建（可建该空间根级文件夹）/ 改名 / 删除；删除是高敏动作，全程审计。</div>
      <div v-else-if="form.perms.includes('delete')" class="fsd-warn">⚠️ 「删除」为独立高敏授权（组织矩阵之外单独开删），全程审计，建议短周期。</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFolderTypeDefaults, saveFolderTypeDefault, removeFolderTypeDefault, renewFolderTypeDefault, getAclRoles, getFolderTree } from '@/api/file'
import { getUserList } from '@/api/system'
import request from '@/utils/request'

defineProps({ canEdit: { type: Boolean, default: false } })

const FILE_ACTIONS = [
  { value: 'view', label: '查看' }, { value: 'upload', label: '上传' }, { value: 'download', label: '下载' },
  { value: 'rename', label: '改名' }, { value: 'replace', label: '替换' }, { value: 'share', label: '分享' },
  { value: 'delete', label: '删除' }
]
const FOLDER_ACTIONS = [
  { value: 'create', label: '新建' }, { value: 'rename', label: '改名' }, { value: 'delete', label: '删除' }
]
const LEGACY = { edit: '编辑(改名+替换)' }
const permOptionsOf = (t) => t === 'FOLDER' ? FOLDER_ACTIONS : FILE_ACTIONS
// 【拍板③】三档角色预设(业界口径): 查看=看+下载; 编辑=+上传/改名/替换/分享; 管理=+删除(FILE)/+新建改名删除(FOLDER)
const PRESETS = {
  FILE: {
    viewer: ['view', 'download'],
    editor: ['view', 'download', 'upload', 'rename', 'replace', 'share'],
    manager: ['view', 'download', 'upload', 'rename', 'replace', 'share', 'delete']
  },
  FOLDER: {
    viewer: [],
    editor: [],
    manager: ['create', 'rename', 'delete']
  }
}
const preset = ref('viewer')
const applyPreset = (v) => {
  if (v === 'custom') return
  form.perms = [...PRESETS[form.objectType][v]]
}
const presetHint = computed(() => ({
  viewer: form.objectType === 'FILE' ? '可查看、下载' : '（文件夹无“查看者”档，请选管理者或自定义）',
  editor: '可查看、下载、上传、改名、替换、分享',
  manager: form.objectType === 'FILE' ? '编辑者全部 + 删除（高敏，审计）' : '可新建、改名、删除文件夹'
})[preset.value] || '')
const labelOf = (t, v) => permOptionsOf(t).find(o => o.value === v)?.label || LEGACY[v] || v

const rows = ref([])
const roleOptions = ref([])
const folderOptions = ref([])
const saving = ref(false)
const treeRef = ref(null)
const userCount = ref(0)
const orgUserTree = ref([])
const defaultExpiry = new Date(Date.now() + 365 * 864e5)
const form = reactive({
  subjectType: 'USER', subjectIds: [], folderType: 'public', objectType: 'FILE',
  folderId: null, perms: [], expiresAt: ''
})
watch(() => form.perms, (v) => {
  const match = Object.entries(PRESETS[form.objectType]).find(([, list]) =>
    list.length === v.length && list.every(p => v.includes(p)))
  if (match && match[0] !== 'viewer' && match[0] !== 'editor' && match[0] !== 'manager') preset.value = 'custom'
})

const canSubmit = computed(() =>
  form.perms.length > 0 && !!form.expiresAt &&
  (form.subjectType === 'ROLE' ? form.subjectIds.length > 0 : userCount.value > 0))

const shortDate = (v) => String(v || '').replace('T', ' ').slice(5, 16)
const nearExpiry = (row) => {
  const t = new Date(String(row?.expiresAt || '').replace(' ', 'T')).getTime()
  return t - Date.now() < 30 * 864e5
}

const load = async () => {
  try { rows.value = (await getFolderTypeDefaults()).data || [] } catch (e) { rows.value = [] }
}
const loadFolders = async () => {
  try {
    const res = await getFolderTree('public')
    const second = await getFolderTree('private')
    const flat = []
    const walk = (nodes) => (nodes || []).forEach(n => { flat.push({ id: n.id, name: n.rawName || n.name, folderType: n.folderType || (second?.data ? 'private' : 'public') }); walk(n.children) })
    walk(res.data || []); walk(second?.data || [])
    folderOptions.value = flat
  } catch (e) { folderOptions.value = [] }
}

// 组织树(与激励授权弹窗同源建树算法)
const loadOrgUserTree = async () => {
  try {
    const [dRes, uRes] = await Promise.all([
      request.get('/common/scope/depts'),
      getUserList({ current: 1, size: 500 }),
    ])
    const depts = dRes.data || []
    const users = uRes.data?.records || []
    const nodeIds = new Set()
    const collect = (nodes) => { for (const n of nodes) { nodeIds.add(n.id); collect(n.children || []) } }
    collect(depts)
    const attach = {}
    const orphans = []
    for (const u of users) {
      if (u.deptId && nodeIds.has(u.deptId)) (attach[u.deptId] ||= []).push(u)
      else if (u.officeId && nodeIds.has(u.officeId)) (attach[u.officeId] ||= []).push(u)
      else orphans.push(u)
    }
    const walk = (nodes) => nodes.map(n => {
      const children = [
        ...(attach[n.id] || []).map(u => ({ key: 'u' + u.id, label: `${u.realName} (${u.username})`, isUser: true })),
        ...walk(n.children || []),
      ]
      if (!children.length) return null
      return { key: 'd' + n.id, label: n.deptName || n.orgName || ('组织' + n.id), children }
    }).filter(Boolean)
    const tree = walk(depts)
    if (orphans.length) tree.push({ key: 'd-orphan', label: '未分部门', children: orphans.map(u => ({ key: 'u' + u.id, label: `${u.realName} (${u.username})`, isUser: true })) })
    orgUserTree.value = tree
  } catch (e) { /* 树加载失败保留空树, 角色通道仍可用 */ }
}
const selectedUserIds = () =>
  ((treeRef.value?.getCheckedKeys()) || []).filter(k => String(k).startsWith('u')).map(k => Number(String(k).slice(1)))
const onTreeCheck = () => { userCount.value = selectedUserIds().length }
const checkAll = () => {
  const keys = []
  const collect = (nodes) => { for (const n of nodes) { if (n.isUser) keys.push(n.key); collect(n.children || []) } }
  collect(orgUserTree.value)
  treeRef.value?.setCheckedKeys(keys)
  userCount.value = keys.length
}
const clearUsers = () => { treeRef.value?.setCheckedKeys([]); userCount.value = 0 }

// 逐主体并行下发(allSettled 配方: 成功N/失败M, 失败保留勾选直接重试)
const save = async () => {
  saving.value = true
  const ids = form.subjectType === 'USER' ? selectedUserIds() : [...form.subjectIds]
  const results = await Promise.allSettled(ids.map(id =>
    saveFolderTypeDefault({
      folderType: form.folderType, objectType: form.objectType, folderId: form.folderId || undefined,
      subjectType: form.subjectType, subjectId: id, perms: form.perms, expiresAt: form.expiresAt
    })))
  const ok = results.filter(r => r.status === 'fulfilled').length
  const fail = ids.length - ok
  if (fail === 0) {
    ElMessage.success(`已授权 ${ok} 个主体，立即生效（到期 ${shortDate(form.expiresAt)} 自动收回）`)
    clearUsers(); form.subjectIds = []; form.perms = []; form.expiresAt = ''
  } else {
    const failed = ids.filter((id, i) => results[i].status === 'rejected')
    if (form.subjectType === 'USER') { treeRef.value?.setCheckedKeys(failed.map(id => 'u' + id)); userCount.value = failed.length }
    else form.subjectIds = failed
    ElMessage.warning(`授权：成功 ${ok} / 失败 ${fail}（失败主体已保留勾选，可直接重试）`)
  }
  await load()
  saving.value = false
}

const renewRow = async (id) => {
  const to = new Date(Date.now() + 365 * 864e5)
  const text = `${to.getFullYear()}-${String(to.getMonth() + 1).padStart(2, '0')}-${String(to.getDate()).padStart(2, '0')} 23:59:59`
  try {
    await ElMessageBox.confirm(`续期一年至 ${text.slice(0, 10)}？`, '续期授权', { type: 'warning' })
  } catch (e) { return }
  try { await renewFolderTypeDefault(id, text); ElMessage.success('已续期一年'); await load() } catch (e) { /* 拦截器已提示 */ }
}

const removeRow = async (id) => {
  try { await ElMessageBox.confirm('删除后该授权立即收回（组织矩阵默认权限不受影响）', '删除授权', { type: 'warning' }) } catch (e) { return }
  try { await removeFolderTypeDefault(id); ElMessage.success('已删除，立即生效'); await load() } catch (e) { /* 拦截器已提示 */ }
}

onMounted(async () => {
  await load()
  try { roleOptions.value = (await getAclRoles()).data || [] } catch (e) { roleOptions.value = [] }
  loadFolders()
  loadOrgUserTree()
})
</script>

<style scoped>
.fsd-sec + .fsd-sec { margin-top: 16px; }
.fsd-sec-head { display: flex; align-items: baseline; gap: 10px; font-size: 14px; color: #1f2d3d; margin-bottom: 10px; }
.fsd-sec-title { font-weight: 600; white-space: nowrap; }
.fsd-toolbar { margin-bottom: 12px; }
.fsd-tb-hint { font-size: 12px; color: var(--el-color-info); }
.fsd-muted { color: var(--el-color-info); font-size: 12px; }
.fsd-near { color: #b45200; font-weight: 600; }
.fsd-pill { margin: 0 4px 4px 0; }
.fsd-form { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 10px 12px; background: #f7f8fa; border-radius: 8px; }
.fsd-perms { display: flex; flex-wrap: wrap; gap: 0 8px; }
.fsd-perms :deep(.el-checkbox) { margin-right: 0; height: 24px; }
.fsd-preset-hint { font-size: 12px; color: var(--el-color-info); }
.fsd-adv { flex-basis: 100%; border: none; }
.fsd-adv :deep(.el-collapse-item__header) { height: 32px; font-size: 12px; color: var(--el-color-info); }
.fsd-warn { margin-top: 8px; padding: 6px 10px; font-size: 12px; color: #b45200; background: #fdf6ec; border-radius: 6px; }
.fsd-tree-wrap { flex-basis: 100%; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 8px 10px; background: #fff; }
.fsd-tree-tools { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; }
.fsd-tree-count { font-size: 12px; color: #4e5969; }
.fsd-tree-count b { color: var(--el-color-primary); }
</style>
