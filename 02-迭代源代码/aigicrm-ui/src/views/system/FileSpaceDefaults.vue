<template>
  <!-- 【2026-11-10 文件权限终版 + 授权台重构】例外授权(唯一加授通道, 授权必须过期)。
       UI=列表 + 三段向导弹窗: ①给谁(组织树/角色) ②配什么(空间×对象组×三档角色卡) ③多久(快捷期限chips)。
       底座=组织矩阵(默认: 全员可看/本部门可管/删仅本人, 档位在「功能与范围」调), 本表只配跨范围特批。 -->
  <div class="fsd">
    <div class="fsd-head">
      <div>
        <div class="fsd-title">例外授权</div>
        <div class="fsd-hint">默认权限=组织矩阵（全员可看 · 本部门可管 · 删仅本人，在「功能与范围」按角色调档）；这里只配<b>跨范围特批</b>（默认永久，可选限期）</div>
      </div>
      <el-button v-if="canEdit" type="primary" size="small" @click="openAdd">＋ 添加授权</el-button>
    </div>

    <el-table :data="rows" size="small" style="width:100%">
      <el-table-column label="空间" width="64">
        <template #default="scope">{{ scope?.row?.folderType === 'public' ? '公共' : '私人' }}</template>
      </el-table-column>
      <el-table-column label="对象" width="60">
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
      <el-table-column label="主体" min-width="140">
        <template #default="scope">
          <el-tag size="small" :type="scope?.row?.subjectType === 'USER' ? 'warning' : 'info'" class="fsd-pill">
            {{ scope?.row?.subjectType === 'USER' ? '人员' : '角色' }}
          </el-tag>{{ scope?.row?.subjectName }}
        </template>
      </el-table-column>
      <el-table-column label="动作" min-width="200">
        <template #default="scope">
          <el-tag v-for="p in (scope?.row?.perms || [])" :key="p" size="small"
                  :type="p === 'delete' ? 'danger' : 'info'" class="fsd-pill">{{ labelOf(scope?.row?.objectType, p) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="到期" width="105">
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
    <el-empty v-if="!rows.length" description="暂无例外授权——默认规则已覆盖日常，需要跨范围特批时再添加" :image-size="48" />

    <!-- 三段向导弹窗 -->
    <el-dialog v-model="addVisible" title="添加授权" width="640px" top="6vh" :close-on-click-modal="false" destroy-on-close>
      <!-- ① 给谁 -->
      <div class="fsd-step">
        <div class="fsd-step-no">①</div>
        <div class="fsd-step-body">
          <div class="fsd-step-title">给谁授权</div>
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
            <el-scrollbar height="190px" class="fsd-tree-scroll">
              <el-tree ref="treeRef" :data="orgUserTree" show-checkbox node-key="key"
                       :props="{ label: 'label', children: 'children' }" :expand-on-click-node="false" default-expand-all
                       @check="onTreeCheck" />
            </el-scrollbar>
          </div>
        </div>
      </div>
      <!-- ② 配什么 -->
      <div class="fsd-step">
        <div class="fsd-step-no">②</div>
        <div class="fsd-step-body">
          <div class="fsd-step-title">配什么权限</div>
          <div class="fsd-row">
            <el-select v-model="form.folderType" size="small" style="width:110px">
              <el-option label="公共空间" value="public" /><el-option label="私人空间" value="private" />
            </el-select>
            <el-radio-group v-model="form.objectType" size="small">
              <el-radio-button value="FILE">文件动作</el-radio-button>
              <el-radio-button value="FOLDER">文件夹动作</el-radio-button>
            </el-radio-group>
            <el-select v-model="form.folderId" clearable placeholder="定向到某夹（默认整空间）" size="small" style="flex:1;min-width:170px">
              <el-option v-for="f in folderOptions" :key="f.id" :label="`${f.folderType === 'public' ? '公共' : '私人'} · ${f.name}`" :value="f.id" />
            </el-select>
          </div>
          <div class="fsd-preset-cards">
            <div v-for="ps in presetCards" :key="ps.key" class="fsd-preset-card" :class="{ active: preset === ps.key, disabled: !ps.perms.length }"
                 @click="choosePreset(ps.key)">
              <div class="fsd-preset-name">{{ ps.name }}</div>
              <div class="fsd-preset-desc">{{ ps.desc }}</div>
            </div>
          </div>
          <el-collapse v-if="preset === 'custom'" class="fsd-adv">
            <el-collapse-item title="高级：逐动作自定义" name="adv">
              <el-checkbox-group v-model="form.perms" size="small" class="fsd-perms" @change="preset = 'custom'">
                <el-checkbox v-for="o in permOptionsOf(form.objectType)" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
              </el-checkbox-group>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
      <!-- ③ 多久 -->
      <div class="fsd-step">
        <div class="fsd-step-no">③</div>
        <div class="fsd-step-body">
          <div class="fsd-step-title">有效期（可选）</div>
          <div class="fsd-row">
            <el-radio-group v-model="expiryQuick" size="small" @change="applyExpiry">
              <el-radio-button value="forever">永久</el-radio-button>
              <el-radio-button value="30">30 天</el-radio-button>
              <el-radio-button value="90">90 天</el-radio-button>
              <el-radio-button value="365">1 年</el-radio-button>
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
// 三档角色预设(业界口径): 查看/编辑/管理
const PRESETS = {
  FILE: {
    viewer: ['view', 'download'],
    editor: ['view', 'download', 'upload', 'rename', 'replace', 'share'],
    manager: ['view', 'download', 'upload', 'rename', 'replace', 'share', 'delete']
  },
  FOLDER: { viewer: [], editor: [], manager: ['create', 'rename', 'delete'] }
}
const PRESET_CARDS = {
  FILE: [
    { key: 'viewer', name: '查看者', desc: '可查看、下载' },
    { key: 'editor', name: '编辑者', desc: '查看下载 + 上传、改名、替换、分享' },
    { key: 'manager', name: '管理者', desc: '编辑者全部 + 删除（高敏，审计）' },
    { key: 'custom', name: '自定义', desc: '逐动作勾选（高级）' }
  ],
  FOLDER: [
    { key: 'manager', name: '管理者', desc: '可新建、改名、删除文件夹' },
    { key: 'custom', name: '自定义', desc: '逐动作勾选（高级）' }
  ]
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
const expiryQuick = ref('365')
const form = reactive({
  subjectType: 'USER', subjectIds: [], folderType: 'public', objectType: 'FILE',
  folderId: null, perms: [], expiresAt: ''
})

const presetCards = computed(() =>
  (PRESET_CARDS[form.objectType] || []).map(c => ({ ...c, perms: PRESETS[form.objectType][c.key] || [] })))
const choosePreset = (k) => {
  preset.value = k
  form.perms = k === 'custom' ? [] : [...PRESETS[form.objectType][k]]
}
// 对象组切换时预设跟随(FOLDER 无查看/编辑档 → 落管理者)
watch(() => form.objectType, () => {
  const keys = PRESET_CARDS[form.objectType].map(c => c.key)
  choosePreset(keys.includes(preset.value) ? preset.value : keys[0])
})
// 手动勾选 → 自动落自定义
watch(() => form.perms, (v) => {
  const hit = Object.entries(PRESETS[form.objectType]).find(([, list]) =>
    list.length === v.length && list.every(p => v.includes(p)))
  if (!hit && preset.value !== 'custom') preset.value = 'custom'
})

const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 23:59:59`
const applyExpiry = (v) => {
  if (v === 'custom') { form.expiresAt = ''; return }
  if (v === 'forever') { form.expiresAt = ''; return }
  form.expiresAt = fmt(new Date(Date.now() + Number(v) * 864e5))
}
const openAdd = () => {
  Object.assign(form, { subjectType: 'USER', subjectIds: [], folderType: 'public', objectType: 'FILE', folderId: null, perms: [], expiresAt: '' })
  preset.value = 'viewer'; expiryQuick.value = 'forever'; applyExpiry('forever')
  userCount.value = 0
  addVisible.value = true
}

const canSubmit = computed(() =>
  form.perms.length > 0 &&
  (form.subjectType === 'ROLE' ? form.subjectIds.length > 0 : userCount.value > 0) &&
  (expiryQuick.value !== 'custom' || !!form.expiresAt)  // 自定义才要求选时间
)
const presetName = computed(() => ((PRESET_CARDS[form.objectType] || []).find(c => c.key === preset.value) || {}).name || preset.value)
const footSummary = computed(() => {
  const n = form.subjectType === 'ROLE' ? form.subjectIds.length : userCount.value
  const unit = form.subjectType === 'ROLE' ? '个角色' : '人'
  return `将为 ${n || '—'} ${unit} · ${form.folderType === 'public' ? '公共' : '私人'}空间${form.folderId ? '·指定夹' : ''} · ${presetName.value} · ${form.expiresAt ? '至 ' + shortDate(form.expiresAt) : '永久有效'}`
})

const shortDate = (v) => String(v || '').replace('T', ' ').slice(5, 16)
const nearExpiry = (row) => {
  const t = new Date(String(row?.expiresAt || '').replace(' ', 'T')).getTime()
  return t - Date.now() < 30 * 864e5
}
const labelOf = (t, v) => permOptionsOf(t).find(o => o.value === v)?.label || LEGACY[v] || v

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

// 逐主体并行下发(allSettled: 成功N/失败M, 失败保留勾选直接重试)
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
    ElMessage.success(`已授权 ${ok} 个主体，立即生效${form.expiresAt ? '（' + shortDate(form.expiresAt) + ' 到期自动收回）' : '（永久，删除即收回）'}`)
    addVisible.value = false
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
/* 三段向导 */
.fsd-step { display: flex; gap: 10px; padding: 12px 0; border-bottom: 1px dashed #f0f2f5; }
.fsd-step:last-of-type { border-bottom: none; }
.fsd-step-no { width: 22px; height: 22px; border-radius: 50%; background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: 12px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 1px; }
.fsd-step-body { flex: 1; min-width: 0; }
.fsd-step-title { font-size: 13px; font-weight: 600; color: #4e5969; margin-bottom: 8px; }
.fsd-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
/* 预设角色卡 */
.fsd-preset-cards { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.fsd-preset-card { flex: 1; min-width: 118px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 8px 10px; cursor: pointer; transition: all .15s; }
.fsd-preset-card:hover { border-color: var(--el-color-primary-light-5); }
.fsd-preset-card.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.fsd-preset-card.disabled { opacity: .45; cursor: not-allowed; }
.fsd-preset-name { font-size: 13px; font-weight: 600; color: #1f2d3d; }
.fsd-preset-card.active .fsd-preset-name { color: var(--el-color-primary); }
.fsd-preset-desc { font-size: 11px; color: var(--el-color-info); margin-top: 3px; line-height: 1.4; }
.fsd-adv { margin-top: 8px; border: none; }
.fsd-adv :deep(.el-collapse-item__header) { height: 30px; font-size: 12px; color: var(--el-color-info); }
.fsd-perms { display: flex; flex-wrap: wrap; gap: 0 8px; }
.fsd-perms :deep(.el-checkbox) { margin-right: 0; height: 24px; }
.fsd-tree-wrap { border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 8px 10px; background: #fff; margin-top: 8px; }
.fsd-tree-tools { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; }
.fsd-tree-count { font-size: 12px; color: #4e5969; }
.fsd-tree-count b { color: var(--el-color-primary); }
.fsd-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
</style>
