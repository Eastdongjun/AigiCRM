<template>
  <!-- 【2026-11-10 文件权限·授权台v3(对象优先)】例外授权。上一版"人优先+对象组选择器"违和:
       管理员的自然句子是"给张三开技术方案夹的编辑", 专业系统(钉钉/Google)全是 对象优先→加人→点角色卡。
       本版: ①对什么授权(选文件夹/整空间) ②给谁(组织树/角色) ③什么权限(角色卡; 文件/文件夹动作藏进卡内,
       管理者卡=文件全套+文件夹管理, 保存时自动拆 FILE/FOLDER 两行落库) ④有效期(可选, 默认永久)。 -->
  <div class="fsd">
    <div class="fsd-head">
      <div>
        <div class="fsd-title">例外授权</div>
        <div class="fsd-hint">默认权限=组织矩阵（全员可看 · 本部门可管 · 删仅本人，在「功能与范围」按角色调档）；这里只配<b>跨范围特批</b>，勾选即生效、删除即收回（默认永久，可选限期）</div>
      </div>
      <el-button v-if="canEdit" type="primary" size="small" @click="openAdd">＋ 添加授权</el-button>
    </div>

    <el-table :data="rows" size="small" style="width:100%">
      <el-table-column label="范围" min-width="150">
        <template #default="scope">
          <el-tag size="small" :type="scope?.row?.folderType === 'public' ? 'info' : 'warning'" class="fsd-pill">
            {{ scope?.row?.folderType === 'public' ? '公共' : '私人' }}
          </el-tag>
          <span v-if="scope?.row?.folderName">📂 {{ scope?.row?.folderName }}</span>
          <span v-else class="fsd-muted">整个空间</span>
        </template>
      </el-table-column>
      <el-table-column label="给谁" min-width="140">
        <template #default="scope">
          <el-tag size="small" :type="scope?.row?.subjectType === 'USER' ? 'warning' : 'info'" class="fsd-pill">
            {{ scope?.row?.subjectType === 'USER' ? '人员' : '角色' }}
          </el-tag>{{ scope?.row?.subjectName }}
        </template>
      </el-table-column>
      <el-table-column label="权限" min-width="230">
        <template #default="scope">
          <el-tag v-if="presetNameOf(scope?.row)" size="small" type="success" class="fsd-pill">{{ presetNameOf(scope?.row) }}</el-tag>
          <el-tag v-for="p in (scope?.row?.perms || [])" :key="p" size="small"
                  :type="p === 'delete' ? 'danger' : 'info'" class="fsd-pill">{{ labelOf(scope?.row?.objectType, p) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="到期" width="95">
        <template #default="scope">
          <span v-if="scope?.row?.expired" class="fsd-muted" style="text-decoration:line-through">已过期</span>
          <span v-else-if="!scope?.row?.expiresAt" class="fsd-muted">永久</span>
          <span v-else :class="nearExpiry(scope?.row) ? 'fsd-near' : ''">{{ shortDate(scope?.row?.expiresAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="116" align="center">
        <template #default="scope">
          <el-button v-if="canEdit && scope?.row?.expiresAt && !scope?.row?.expired" link type="primary" size="small" @click="renewRow(scope?.row?.id)">续期</el-button>
          <el-button v-if="canEdit" link type="danger" size="small" @click="removeRow(scope?.row?.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!rows.length" description="暂无例外授权——默认规则已覆盖日常，需要给某人特别开权时再添加" :image-size="48" />

    <!-- 对象优先四段向导 -->
    <el-dialog v-model="addVisible" title="添加授权" width="660px" top="5vh" :close-on-click-modal="false" destroy-on-close>
      <!-- ① 对什么授权 -->
      <div class="fsd-step">
        <div class="fsd-step-no">①</div>
        <div class="fsd-step-body">
          <div class="fsd-step-title">对什么授权</div>
          <div class="fsd-row">
            <el-radio-group v-model="form.scopeType" size="small">
              <el-radio-button value="folder">某个文件夹</el-radio-button>
              <el-radio-button value="public">整个公共空间</el-radio-button>
              <el-radio-button value="private">整个私人空间</el-radio-button>
            </el-radio-group>
            <el-select v-if="form.scopeType === 'folder'" v-model="form.folderId" filterable placeholder="选择文件夹（可搜索）" size="small" style="flex:1;min-width:200px">
              <el-option v-for="f in folderOptions" :key="f.id" :label="`${f.folderType === 'public' ? '公共' : '私人'} · ${f.name}`" :value="f.id" />
            </el-select>
          </div>
        </div>
      </div>
      <!-- ② 给谁 -->
      <div class="fsd-step">
        <div class="fsd-step-no">②</div>
        <div class="fsd-step-body">
          <div class="fsd-step-title">给谁</div>
          <el-radio-group v-model="form.subjectType" size="small">
            <el-radio-button value="USER">按人员</el-radio-button>
            <el-radio-button value="ROLE">按角色</el-radio-button>
          </el-radio-group>
          <el-select v-if="form.subjectType === 'ROLE'" v-model="form.subjectIds" multiple collapse-tags collapse-tags-tooltip
                     placeholder="选择角色（可多选，成员自动获得）" size="small" style="width:100%;margin-top:8px">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
          <div v-else class="fsd-tree-wrap">
            <div class="fsd-tree-tools">
              <span class="fsd-tree-count">已选 <b>{{ userCount }}</b> 人</span>
              <el-button size="small" link type="primary" @click="checkAll">全选</el-button>
              <el-button size="small" link type="danger" style="margin-left:auto" @click="clearUsers">清空</el-button>
            </div>
            <el-scrollbar height="180px" class="fsd-tree-scroll">
              <el-tree ref="treeRef" :data="orgUserTree" show-checkbox node-key="key"
                       :props="{ label: 'label', children: 'children' }" :expand-on-click-node="false" default-expand-all
                       @check="onTreeCheck" />
            </el-scrollbar>
          </div>
        </div>
      </div>
      <!-- ③ 什么权限(角色卡, 对象组已藏进卡内) -->
      <div class="fsd-step">
        <div class="fsd-step-no">③</div>
        <div class="fsd-step-body">
          <div class="fsd-step-title">什么权限</div>
          <div class="fsd-preset-cards">
            <div v-for="ps in ROLE_CARDS" :key="ps.key" class="fsd-preset-card" :class="{ active: preset === ps.key }" @click="choosePreset(ps.key)">
              <div class="fsd-preset-name">{{ ps.name }}</div>
              <div class="fsd-preset-desc">{{ ps.desc }}</div>
            </div>
            <div class="fsd-preset-card" :class="{ active: preset === 'custom' }" @click="choosePreset('custom')">
              <div class="fsd-preset-name">自定义</div>
              <div class="fsd-preset-desc">逐动作勾选（高级）</div>
            </div>
          </div>
          <el-collapse v-if="preset === 'custom'" class="fsd-adv">
            <el-collapse-item title="高级：逐动作自定义" name="adv">
              <div class="fsd-adv-group">文件</div>
              <el-checkbox-group v-model="form.permsFile" size="small" class="fsd-perms">
                <el-checkbox v-for="o in FILE_ACTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
              </el-checkbox-group>
              <div class="fsd-adv-group">文件夹</div>
              <el-checkbox-group v-model="form.permsFolder" size="small" class="fsd-perms">
                <el-checkbox v-for="o in FOLDER_ACTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
              </el-checkbox-group>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
      <!-- ④ 有效期(可选) -->
      <div class="fsd-step">
        <div class="fsd-step-no">④</div>
        <div class="fsd-step-body">
          <div class="fsd-step-title">有效期（可选）</div>
          <div class="fsd-row">
            <el-radio-group v-model="expiryQuick" size="small" @change="applyExpiry">
              <el-radio-button value="forever">永久</el-radio-button>
              <el-radio-button value="30">30 天</el-radio-button>
              <el-radio-button value="90">90 天</el-radio-button>
              <el-radio-button value="1y">1 年</el-radio-button>
              <el-radio-button value="custom">自定义</el-radio-button>
            </el-radio-group>
            <el-date-picker v-if="expiryQuick === 'custom'" v-model="form.expiresAt" type="datetime" size="small"
                            placeholder="选择到期时间" value-format="YYYY-MM-DD HH:mm:ss" style="width:200px" />
          </div>
        </div>
      </div>
      <template #footer>
        <div class="fsd-foot">
          <span class="fsd-muted">{{ footSummary }}</span>
          <div>
            <el-button size="small" @click="addVisible = false">取消</el-button>
            <el-button type="primary" size="small" :disabled="!canSubmit" :loading="saving" @click="save">保存授权</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
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
const labelOf = (t, v) => (t === 'FOLDER' ? FOLDER_ACTIONS : FILE_ACTIONS).find(o => o.value === v)?.label || LEGACY[v] || v

// 三档角色卡(对象组藏进卡内: 管理者=文件全套+文件夹管理; 保存时自动拆 FILE/FOLDER 两行)
const ROLES = {
  viewer: { name: '查看者', file: ['view', 'download'], folder: [] },
  editor: { name: '编辑者', file: ['view', 'download', 'upload', 'rename', 'replace', 'share'], folder: [] },
  manager: { name: '管理者', file: ['view', 'download', 'upload', 'rename', 'replace', 'share', 'delete'], folder: ['create', 'rename', 'delete'] }
}
const ROLE_CARDS = [
  { key: 'viewer', name: '查看者', desc: '可查看、下载' },
  { key: 'editor', name: '编辑者', desc: '查看下载 + 上传、改名、替换、分享' },
  { key: 'manager', name: '管理者', desc: '编辑者全部 + 删除文件 + 新建/改名/删除文件夹' }
]
const presetNameOf = (row) => {
  const perms = (row?.perms || []).join(',')
  const obj = row?.objectType
  return Object.entries(ROLES).find(([, r]) =>
    (obj === 'FILE' ? r.file : r.folder).join(',') === perms)?.[1].name || null
}

const rows = ref([])
const roleOptions = ref([])
const folderOptions = ref([])
const saving = ref(false)
const addVisible = ref(false)
const treeRef = ref(null)
const userCount = ref(0)
const orgUserTree = ref([])
const preset = ref('viewer')
const expiryQuick = ref('forever')
const form = reactive({
  scopeType: 'folder', folderId: null, subjectType: 'USER', subjectIds: [],
  permsFile: [], permsFolder: [], expiresAt: ''
})

const targetFolder = computed(() => folderOptions.value.find(f => f.id === form.folderId))
const choosePreset = (k) => {
  preset.value = k
  if (k === 'custom') return
  form.permsFile = [...ROLES[k].file]
  form.permsFolder = [...ROLES[k].folder]
}

const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 23:59:59`
const applyExpiry = (v) => {
  if (v === 'custom') { form.expiresAt = ''; return }
  if (v === 'forever') { form.expiresAt = ''; return }
  form.expiresAt = fmt(new Date(Date.now() + (v === '1y' ? 365 : Number(v)) * 864e5))
}
const openAdd = () => {
  Object.assign(form, { scopeType: 'folder', folderId: null, subjectType: 'USER', subjectIds: [], permsFile: [], permsFolder: [], expiresAt: '' })
  preset.value = 'viewer'; choosePreset('viewer')
  expiryQuick.value = 'forever'; applyExpiry('forever')
  userCount.value = 0
  addVisible.value = true
}

const scopeValid = computed(() => form.scopeType !== 'folder' || !!form.folderId)
const canSubmit = computed(() =>
  scopeValid.value && (form.permsFile.length > 0 || form.permsFolder.length > 0) &&
  (form.subjectType === 'ROLE' ? form.subjectIds.length > 0 : userCount.value > 0) &&
  (expiryQuick.value !== 'custom' || !!form.expiresAt))
const scopeText = computed(() => {
  if (form.scopeType === 'folder') { const f = targetFolder.value; return f ? `${f.folderType === 'public' ? '公共' : '私人'}夹「${f.name}」` : '未选夹' }
  return form.scopeType === 'public' ? '整个公共空间' : '整个私人空间'
})
const footSummary = computed(() => {
  const n = form.subjectType === 'ROLE' ? form.subjectIds.length : userCount.value
  const unit = form.subjectType === 'ROLE' ? '个角色' : '人'
  const rl = preset.value === 'custom' ? '自定义' : ROLE_CARDS.find(c => c.key === preset.value)?.name
  return `${scopeText.value} · ${n || '—'} ${unit} · ${rl} · ${form.expiresAt ? '至 ' + shortDate(form.expiresAt) : '永久'}`
})

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
    const flat = []
    const walk = (nodes, type) => (nodes || []).forEach(n => { flat.push({ id: n.id, name: n.rawName || n.name, folderType: type }); walk(n.children, type) })
    walk((await getFolderTree('public')).data || [], 'public')
    walk((await getFolderTree('private')).data || [], 'private')
    folderOptions.value = flat
  } catch (e) { folderOptions.value = [] }
}
const loadOrgUserTree = async () => {
  try {
    const [dRes, uRes] = await Promise.all([request.get('/common/scope/depts'), getUserList({ current: 1, size: 500 })])
    const depts = dRes.data || []
    const users = uRes.data?.records || []
    const nodeIds = new Set()
    const collect = (nodes) => { for (const n of nodes) { nodeIds.add(n.id); collect(n.children || []) } }
    collect(depts)
    const attach = {}; const orphans = []
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

// 保存: 按主体 × (FILE行+FOLDER行) 拆分并行下发(allSettled: 成功N/失败M, 失败保留直接重试)
const save = async () => {
  saving.value = true
  const ids = form.subjectType === 'USER' ? selectedUserIds() : [...form.subjectIds]
  const folderType = form.scopeType === 'folder' ? targetFolder.value?.folderType : form.scopeType
  const folderId = form.scopeType === 'folder' ? form.folderId : undefined
  const payloads = []
  for (const id of ids) {
    if (form.permsFile.length) payloads.push({ folderType, objectType: 'FILE', folderId, subjectType: form.subjectType, subjectId: id, perms: form.permsFile, expiresAt: form.expiresAt || undefined })
    if (form.permsFolder.length) payloads.push({ folderType, objectType: 'FOLDER', folderId, subjectType: form.subjectType, subjectId: id, perms: form.permsFolder, expiresAt: form.expiresAt || undefined })
  }
  const results = await Promise.allSettled(payloads.map(p => saveFolderTypeDefault(p)))
  const ok = results.filter(r => r.status === 'fulfilled').length
  const fail = payloads.length - ok
  if (fail === 0) {
    ElMessage.success(`已授权 ${ids.length} 个主体，立即生效${form.expiresAt ? '（' + shortDate(form.expiresAt) + ' 到期自动收回）' : '（永久，删除即收回）'}`)
    addVisible.value = false
  } else {
    ElMessage.warning(`授权：成功 ${ok} / 失败 ${fail}（请检查后重试）`)
  }
  await load()
  saving.value = false
}

const renewRow = async (id) => {
  const text = fmt(new Date(Date.now() + 365 * 864e5))
  try { await ElMessageBox.confirm(`续期一年至 ${text.slice(0, 10)}？`, '续期授权', { type: 'warning' }) } catch (e) { return }
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
.fsd-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.fsd-title { font-size: 14px; font-weight: 600; color: #1f2d3d; }
.fsd-hint { font-size: 12px; color: var(--el-color-info); margin-top: 2px; }
.fsd-muted { color: var(--el-color-info); font-size: 12px; }
.fsd-near { color: #b45200; font-weight: 600; }
.fsd-pill { margin: 0 4px 4px 0; }
/* 四段向导 */
.fsd-step { display: flex; gap: 10px; padding: 12px 0; border-bottom: 1px dashed #f0f2f5; }
.fsd-step:last-of-type { border-bottom: none; }
.fsd-step-no { width: 22px; height: 22px; border-radius: 50%; background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: 12px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 1px; }
.fsd-step-body { flex: 1; min-width: 0; }
.fsd-step-title { font-size: 13px; font-weight: 600; color: #4e5969; margin-bottom: 8px; }
.fsd-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
/* 角色卡 */
.fsd-preset-cards { display: flex; gap: 8px; flex-wrap: wrap; }
.fsd-preset-card { flex: 1; min-width: 118px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 8px 10px; cursor: pointer; transition: all .15s; }
.fsd-preset-card:hover { border-color: var(--el-color-primary-light-5); }
.fsd-preset-card.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.fsd-preset-name { font-size: 13px; font-weight: 600; color: #1f2d3d; }
.fsd-preset-card.active .fsd-preset-name { color: var(--el-color-primary); }
.fsd-preset-desc { font-size: 11px; color: var(--el-color-info); margin-top: 3px; line-height: 1.4; }
.fsd-adv { margin-top: 8px; border: none; }
.fsd-adv :deep(.el-collapse-item__header) { height: 30px; font-size: 12px; color: var(--el-color-info); }
.fsd-adv-group { font-size: 12px; color: var(--el-color-info); margin: 6px 0 2px; }
.fsd-perms { display: flex; flex-wrap: wrap; gap: 0 8px; }
.fsd-perms :deep(.el-checkbox) { margin-right: 0; height: 24px; }
.fsd-tree-wrap { border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 8px 10px; background: #fff; margin-top: 8px; }
.fsd-tree-tools { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; }
.fsd-tree-count { font-size: 12px; color: #4e5969; }
.fsd-tree-count b { color: var(--el-color-primary); }
.fsd-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
</style>
