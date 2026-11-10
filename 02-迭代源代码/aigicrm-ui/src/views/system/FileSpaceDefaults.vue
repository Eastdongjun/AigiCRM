<template>
  <!-- 【2026-11-10 文件权限·三层定型(用户拍板)】类型(公共/私人/四项激励)→文件夹→文件。
       每类型分区: ①类型默认=角色×数据范围×动作 勾选即存矩阵(拍板"以角色为主但要区分数据范围")
       ②该类型下文件夹的授权(夹行+加人=向导预填) ③整空间特批/四项激励只有文件动作。 -->
  <div class="fsd">
    <div class="fsd-head">
      <div>
        <div class="fsd-title">文件权限</div>
        <div class="fsd-hint">底座=组织矩阵（全员可看 · 本部门可管 · 删仅本人，「功能与范围」调档）；类型默认与夹级授权在此勾选即生效、删除即收回</div>
      </div>
      <el-button v-if="canEdit" type="primary" size="small" @click="openAdd(null)">＋ 添加授权</el-button>
    </div>

    <div v-for="t in TYPES" :key="t.value" class="fsd-sec">
      <div class="fsd-sec-head">
        <span class="fsd-sec-title">{{ t.icon }} {{ t.label }}</span>
        <span class="fsd-sec-hint">{{ t.hint }}</span>
      </div>

      <!-- ① 类型默认: 角色 × 范围 × 动作 矩阵 -->
      <el-table :data="defaultRows(t.value)" size="small" style="width:100%">
        <el-table-column label="角色" width="90">
          <template #default="scope">{{ scope?.row?.roleName }}</template>
        </el-table-column>
        <el-table-column label="数据范围" width="110">
          <template #default="scope">
            <el-select v-if="canEdit" :model-value="scope?.row?.scopeLevel" size="small" style="width:96px"
                       @change="v => onDefaultChange(t.value, scope?.row?.roleId, { scopeLevel: v })">
              <el-option v-for="o in SCOPE_OPTS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <span v-else>{{ scopeLabel(scope?.row?.scopeLevel) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="t.value !== 'incentive'" label="文件夹动作" width="190">
          <template #default="scope">
            <el-checkbox-group :model-value="scope?.row?.folderPerms" size="small" class="fsd-chks"
                               :disabled="!canEdit" @change="v => onDefaultChange(t.value, scope?.row?.roleId, { folderPerms: v })">
              <el-checkbox v-for="o in FOLDER_ACTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
            </el-checkbox-group>
          </template>
        </el-table-column>
        <el-table-column label="文件动作">
          <template #default="scope">
            <el-checkbox-group :model-value="scope?.row?.filePerms" size="small" class="fsd-chks"
                               :disabled="!canEdit" @change="v => onDefaultChange(t.value, scope?.row?.roleId, { filePerms: v })">
              <el-checkbox v-for="o in FILE_ACTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
            </el-checkbox-group>
          </template>
        </el-table-column>
      </el-table>

      <!-- ② 夹级授权 + 整空间特批 -->
      <div v-if="t.value !== 'incentive' && folderGrants(t.value).length" class="fsd-folders">
        <div v-for="fg in folderGrants(t.value)" :key="fg.folderId" class="fsd-folder-row">
          <span class="fsd-folder-name">📂 {{ fg.folderName }}</span>
          <el-tag v-for="g in fg.rows" :key="g.id" size="small" closable class="fsd-pill"
                  :type="g.subjectType === 'USER' ? 'warning' : 'info'"
                  @close="removeRow(g.id)">{{ g.subjectName }}·{{ presetNameOf(g) || '自定义' }}{{ g.expiresAt ? '·至' + shortDate(g.expiresAt) : '' }}</el-tag>
          <el-button v-if="canEdit" size="small" link type="primary" @click="openAdd(fg)">＋加人</el-button>
        </div>
      </div>
      <div v-if="spaceGrants(t.value).length" class="fsd-folders">
        <div class="fsd-folder-row">
          <span class="fsd-folder-name">⭐ 整空间特批</span>
          <el-tag v-for="g in spaceGrants(t.value)" :key="g.id" size="small" closable class="fsd-pill"
                  :type="g.subjectType === 'USER' ? 'warning' : 'info'"
                  @close="removeRow(g.id)">{{ g.subjectName }}·{{ presetNameOf(g) || '自定义' }}{{ g.expiresAt ? '·至' + shortDate(g.expiresAt) : '' }}</el-tag>
        </div>
      </div>
    </div>

    <!-- 向导(加人/特批): 对象优先四段, scopeType 预填 -->
    <el-dialog v-model="addVisible" title="添加授权" width="660px" top="5vh" :close-on-click-modal="false" destroy-on-close>
      <div class="fsd-step">
        <div class="fsd-step-no">①</div>
        <div class="fsd-step-body">
          <div class="fsd-step-title">对什么授权</div>
          <div class="fsd-row">
            <el-radio-group v-model="form.scopeType" size="small">
              <el-radio-button value="folder">某个文件夹</el-radio-button>
              <el-radio-button value="public">整个公共空间</el-radio-button>
              <el-radio-button value="private">整个私人空间</el-radio-button>
              <el-radio-button value="incentive">四项激励</el-radio-button>
            </el-radio-group>
            <el-select v-if="form.scopeType === 'folder'" v-model="form.folderId" filterable placeholder="选择文件夹（可搜索）" size="small" style="flex:1;min-width:200px">
              <el-option v-for="f in folderOptions" :key="f.id" :label="`${f.folderType === 'public' ? '公共' : '私人'} · ${f.name}`" :value="f.id" />
            </el-select>
          </div>
        </div>
      </div>
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
      <div class="fsd-step">
        <div class="fsd-step-no">③</div>
        <div class="fsd-step-body">
          <div class="fsd-step-title">什么权限</div>
          <div class="fsd-preset-cards">
            <div v-for="ps in roleCards" :key="ps.key" class="fsd-preset-card" :class="{ active: preset === ps.key }" @click="choosePreset(ps.key)">
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
              <template v-if="form.scopeType !== 'incentive'">
                <div class="fsd-adv-group">文件</div>
                <el-checkbox-group v-model="form.permsFile" size="small" class="fsd-perms">
                  <el-checkbox v-for="o in FILE_ACTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
                </el-checkbox-group>
                <div class="fsd-adv-group">文件夹</div>
                <el-checkbox-group v-model="form.permsFolder" size="small" class="fsd-perms">
                  <el-checkbox v-for="o in FOLDER_ACTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
                </el-checkbox-group>
              </template>
              <template v-else>
                <div class="fsd-adv-group">激励文件</div>
                <el-checkbox-group v-model="form.permsFile" size="small" class="fsd-perms">
                  <el-checkbox v-for="o in FILE_ACTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
                </el-checkbox-group>
              </template>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
      <div class="fsd-step">
        <div class="fsd-step-no">④</div>
        <div class="fsd-step-body">
          <div class="fsd-step-title">数据范围 与 有效期（均可选）</div>
          <div class="fsd-row">
            <el-select v-model="form.scopeLevel" size="small" style="width:110px" placeholder="数据范围">
              <el-option v-for="o in SCOPE_OPTS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
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

const TYPES = [
  { value: 'public', icon: '📁', label: '公共空间', hint: '公司文件；文件夹动作=在此空间建夹/删夹/改名夹' },
  { value: 'private', icon: '🔒', label: '私人空间', hint: '个人文件；本人对私夹天然全权' },
  { value: 'incentive', icon: '⭐', label: '四项激励', hint: '激励附件（无文件夹层，只有文件动作）；记录可见性仍由「激励授权」管理' }
]
const SCOPE_OPTS = [
  { value: 'all', label: '全部' }, { value: 'region', label: '本大区' }, { value: 'office', label: '本办事处' },
  { value: 'dept', label: '本部门' }, { value: 'self', label: '仅本人' }
]
const FILE_ACTIONS = [
  { value: 'view', label: '查看' }, { value: 'upload', label: '上传' }, { value: 'download', label: '下载' },
  { value: 'rename', label: '改名' }, { value: 'replace', label: '替换' }, { value: 'share', label: '分享' },
  { value: 'delete', label: '删除' }
]
const FOLDER_ACTIONS = [
  { value: 'create', label: '新建' }, { value: 'rename', label: '改名' }, { value: 'delete', label: '删除' }
]
const ROLES = {
  viewer: { name: '查看者', file: ['view', 'download'], folder: [] },
  editor: { name: '编辑者', file: ['view', 'download', 'upload', 'rename', 'replace', 'share'], folder: [] },
  manager: { name: '管理者', file: ['view', 'download', 'upload', 'rename', 'replace', 'share', 'delete'], folder: ['create', 'rename', 'delete'] }
}
const presetNameOf = (row) => {
  const perms = (row?.perms || []).join(',')
  const list = row?.objectType === 'FOLDER' ? { viewer: [], editor: [], manager: ROLES.manager.folder } : { viewer: ROLES.viewer.file, editor: ROLES.editor.file, manager: ROLES.manager.file }
  return Object.entries(list).find(([, arr]) => arr.join(',') === perms)?.[0] === 'manager' ? '管理者'
    : Object.entries(list).find(([, arr]) => arr.join(',') === perms)?.[0] === 'editor' ? '编辑者'
    : Object.entries(list).find(([, arr]) => arr.join(',') === perms)?.[0] === 'viewer' ? '查看者' : null
}
const scopeLabel = (v) => SCOPE_OPTS.find(o => o.value === (v || 'all'))?.label || '全部'

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
  permsFile: [], permsFolder: [], scopeLevel: 'all', expiresAt: ''
})

// ===== 类型默认矩阵推导: (type) → 每角色一行 {roleId, roleName, scopeLevel, filePerms, folderPerms} =====
const typeDefaultRows = (type) => rows.value.filter(r =>
  r.folderType === type && r.subjectType === 'ROLE' && (r.folderId == null) && !r.folderName)
const defaultRows = (type) => roleOptions.value.map(role => {
  const mine = typeDefaultRows(type).filter(r => r.subjectId === role.id)
  const fileRow = mine.find(r => r.objectType === 'FILE')
  const folderRow = mine.find(r => r.objectType === 'FOLDER')
  return {
    roleId: role.id, roleName: role.roleName,
    scopeLevel: (fileRow || folderRow)?.scopeLevel || 'all',
    filePerms: fileRow?.perms || [], folderPerms: folderRow?.perms || []
  }
})
const folderGrants = (type) => {
  const map = new Map()
  rows.value.filter(r => r.folderType === type && r.folderId != null).forEach(g => {
    if (!map.has(g.folderId)) map.set(g.folderId, { folderId: g.folderId, folderName: g.folderName || ('夹#' + g.folderId), rows: [] })
    map.get(g.folderId).rows.push(g)
  })
  return [...map.values()]
}
const spaceGrants = (type) => rows.value.filter(r =>
  r.folderType === type && r.folderId == null && r.subjectType === 'USER')

// 勾选/换范围即存: 合成 FILE+FOLDER 两行(或删除)
const onDefaultChange = async (type, roleId, patch) => {
  const cur = defaultRows(type).find(r => r.roleId === roleId) || {}
  const next = { scopeLevel: cur.scopeLevel || 'all', filePerms: cur.filePerms || [], folderPerms: cur.folderPerms || [], ...patch }
  const prevRows = typeDefaultRows(type).filter(r => r.subjectId === roleId)
  const tasks = []
  const mk = (objectType, perms) => ({ folderType: type, objectType, folderId: undefined, subjectType: 'ROLE', subjectId: roleId, perms, scopeLevel: next.scopeLevel === 'all' ? null : next.scopeLevel, expiresAt: undefined })
  if (next.filePerms.length) tasks.push(saveFolderTypeDefault(mk('FILE', next.filePerms)))
  if (type !== 'incentive' && next.folderPerms.length) tasks.push(saveFolderTypeDefault(mk('FOLDER', next.folderPerms)))
  prevRows.forEach(r => { if (!tasks.length || (r.objectType === 'FILE' && !next.filePerms.length) || (r.objectType === 'FOLDER' && !next.folderPerms.length)) tasks.push(removeFolderTypeDefault(r.id)) })
  try {
    await Promise.all(tasks)
    ElMessage.success('已保存，立即生效')
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}

// ===== 向导 =====
const roleCards = computed(() => form.scopeType === 'incentive'
  ? [{ key: 'viewer', name: '查看者', desc: '可查看、下载' }, { key: 'editor', name: '编辑者', desc: '查看下载 + 上传、改名、替换、分享' }, { key: 'manager', name: '管理者', desc: '编辑者全部 + 删除' }]
  : [{ key: 'viewer', name: '查看者', desc: '可查看、下载' }, { key: 'editor', name: '编辑者', desc: '查看下载 + 上传、改名、替换、分享' }, { key: 'manager', name: '管理者', desc: '编辑者全部 + 删除文件 + 新建/改名/删除文件夹' }])
const choosePreset = (k) => {
  preset.value = k
  if (k === 'custom') return
  form.permsFile = [...ROLES[k].file]
  form.permsFolder = form.scopeType === 'incentive' ? [] : [...ROLES[k].folder]
}
const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 23:59:59`
const applyExpiry = (v) => {
  form.expiresAt = (v === 'custom') ? '' : (v === 'forever') ? '' : fmt(new Date(Date.now() + (v === '1y' ? 365 : Number(v)) * 864e5))
}
const openAdd = (folderGroup) => {
  Object.assign(form, { scopeType: folderGroup ? 'folder' : 'folder', folderId: folderGroup?.folderId || null, subjectType: 'USER', subjectIds: [], permsFile: [], permsFolder: [], scopeLevel: 'all', expiresAt: '' })
  preset.value = 'viewer'; choosePreset('viewer')
  expiryQuick.value = 'forever'; applyExpiry('forever')
  userCount.value = 0
  addVisible.value = true
}
const targetFolder = computed(() => folderOptions.value.find(f => f.id === form.folderId))
const scopeValid = computed(() => form.scopeType !== 'folder' || !!form.folderId)
const canSubmit = computed(() =>
  scopeValid.value && (form.permsFile.length > 0 || form.permsFolder.length > 0) &&
  (form.subjectType === 'ROLE' ? form.subjectIds.length > 0 : userCount.value > 0) &&
  (expiryQuick.value !== 'custom' || !!form.expiresAt))
const scopeText = computed(() => {
  if (form.scopeType === 'folder') { const f = targetFolder.value; return f ? `${f.folderType === 'public' ? '公共' : '私人'}夹「${f.name}」` : '未选夹' }
  return { public: '整个公共空间', private: '整个私人空间', incentive: '四项激励' }[form.scopeType]
})
const footSummary = computed(() => {
  const n = form.subjectType === 'ROLE' ? form.subjectIds.length : userCount.value
  const unit = form.subjectType === 'ROLE' ? '个角色' : '人'
  const rl = preset.value === 'custom' ? '自定义' : ROLES[preset.value]?.name
  return `${scopeText.value} · ${n || '—'} ${unit} · ${rl} · ${scopeLabel(form.scopeLevel)} · ${form.expiresAt ? '至 ' + shortDate(form.expiresAt) : '永久'}`
})
const shortDate = (v) => String(v || '').replace('T', ' ').slice(5, 16)

const save = async () => {
  saving.value = true
  const ids = form.subjectType === 'USER' ? selectedUserIds() : [...form.subjectIds]
  const folderType = form.scopeType === 'folder' ? targetFolder.value?.folderType : form.scopeType
  const folderId = form.scopeType === 'folder' ? form.folderId : undefined
  const payloads = []
  for (const id of ids) {
    if (form.permsFile.length) payloads.push({ folderType, objectType: 'FILE', folderId, subjectType: form.subjectType, subjectId: id, perms: form.permsFile, scopeLevel: form.scopeLevel === 'all' ? null : form.scopeLevel, expiresAt: form.expiresAt || undefined })
    if (form.permsFolder.length) payloads.push({ folderType, objectType: 'FOLDER', folderId, subjectType: form.subjectType, subjectId: id, perms: form.permsFolder, scopeLevel: form.scopeLevel === 'all' ? null : form.scopeLevel, expiresAt: form.expiresAt || undefined })
  }
  const results = await Promise.allSettled(payloads.map(p => saveFolderTypeDefault(p)))
  const ok = results.filter(r => r.status === 'fulfilled').length
  if (ok === payloads.length) {
    ElMessage.success(`已授权 ${ids.length} 个主体，立即生效${form.expiresAt ? '（' + shortDate(form.expiresAt) + ' 到期自动收回）' : '（永久，删除即收回）'}`)
    addVisible.value = false
  } else {
    ElMessage.warning(`授权：成功 ${ok} / 失败 ${payloads.length - ok}（请检查后重试）`)
  }
  await load()
  saving.value = false
}

const removeRow = async (id) => {
  try { await ElMessageBox.confirm('删除后该授权立即收回', '删除授权', { type: 'warning' }) } catch (e) { return }
  try { await removeFolderTypeDefault(id); ElMessage.success('已删除，立即生效'); await load() } catch (e) { /* 拦截器已提示 */ }
}
const renewRow = async (id) => {
  const text = fmt(new Date(Date.now() + 365 * 864e5))
  try { await renewFolderTypeDefault(id, text); ElMessage.success('已续期一年'); await load() } catch (e) { /* 拦截器已提示 */ }
}

// ===== 数据加载 =====
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
.fsd-sec + .fsd-sec { margin-top: 18px; }
.fsd-sec-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
.fsd-sec-title { font-size: 14px; font-weight: 600; color: #1f2d3d; }
.fsd-sec-hint { font-size: 12px; color: var(--el-color-info); }
.fsd-muted { color: var(--el-color-info); font-size: 12px; }
.fsd-pill { margin: 0 4px 4px 0; }
.fsd-chks { display: flex; flex-wrap: wrap; gap: 0 6px; }
.fsd-chks :deep(.el-checkbox) { margin-right: 0; height: 22px; }
.fsd-folders { margin-top: 8px; padding: 8px 10px; background: #f7f8fa; border-radius: 8px; }
.fsd-folder-row { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; padding: 2px 0; }
.fsd-folder-name { font-size: 13px; font-weight: 600; color: #4e5969; margin-right: 6px; }
/* 向导 */
.fsd-step { display: flex; gap: 10px; padding: 12px 0; border-bottom: 1px dashed #f0f2f5; }
.fsd-step:last-of-type { border-bottom: none; }
.fsd-step-no { width: 22px; height: 22px; border-radius: 50%; background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: 12px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 1px; }
.fsd-step-body { flex: 1; min-width: 0; }
.fsd-step-title { font-size: 13px; font-weight: 600; color: #4e5969; margin-bottom: 8px; }
.fsd-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
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
