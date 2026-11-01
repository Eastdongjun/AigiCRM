// 权限相关共享状态（C4 架构重构 2026-08-09）
// **模块级单例**：状态定义在模块作用域，多个组件(settings 页各 tab)调用 usePermissionData()
// 拿到的是同一份 ref——跨组件共享 allRoles/allUsers/permList/showPermDialog 等，授权弹窗可跨组件联动。
//   allRoles/allUsers 被基本设置+权限矩阵共用；permList/openPermDialog 被激励类别+权限共用。
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoleList, getMenuTree, updateRole } from '@/api/system'
import request from '@/utils/request'
import { confirmAction } from '@/utils/confirm'

// ---- 模块级共享状态（单例） ----
const activeTab = ref('basic')
const permLoadError = ref(false)

const allRoles = ref([])
const menuTree = ref([])
const rolePermMap = ref({}) // roleId -> Set of menuIds

const permModules = computed(() => {
  const groups = [
    { name: '工作台', items: [] },
    { name: '客户管理', items: [] },
    { name: '文件管理', items: [] },
    { name: '四项服务', items: [] },
    { name: '提醒管理', items: [] },
    { name: '公海管理', items: [] },
    { name: '审批管理', items: [] },
    { name: '统计分析', items: [] },
    { name: '学分管理', items: [] },
    { name: '核心客户指标', items: [] },
    { name: '任务管理', items: [] },
    { name: '系统管理', items: [] },
  ]
  const labelMap = {
    'dashboard': '工作台', 'customer': '客户管理', 'file': '文件管理', 'incentive': '四项服务',
    'reminder': '提醒管理', 'sea': '公海管理', 'approval': '审批管理', 'statistics': '统计分析',
    'score': '学分管理', 'indicator': '核心客户指标', 'task': '任务管理', 'system': '系统管理',
  }
  const flatten = (nodes) => {
    for (const n of nodes) {
      if (n.permission) {
        const prefix = n.permission.split(':')[0]
        const group = groups.find(g => {
          for (const [k, v] of Object.entries(labelMap)) {
            if (prefix === k) return g.name === v
          }
          return false
        })
        const target = group || groups[groups.length - 1]
        target.items.push({ id: n.id, label: n.menuName, permission: n.permission })
      }
      if (n.children) flatten(n.children)
    }
  }
  flatten(menuTree.value)
  return groups.filter(g => g.items.length > 0)
})

// 【D2 2026-10-04】权限点总数从菜单树动态计算（旧文案写死"39 个"，菜单一变即失真）
const permPointCount = computed(() => permModules.value.reduce((sum, g) => sum + g.items.length, 0))

// 【D1 2026-10-04】权限总览文案对齐当前实现：
//  - 数据范围为 self/team/dept/office/region/all/custom 七档（与 role.vue、UserFormDialog.vue 选项一致），
//    旧文案"全部→本组织及下级→仅本组织→…"为过期层级；
//  - 激励专项自 2026-08-16 授权表化后编辑/删除为按人勾选（见 permForm 四开关），旧文案"由角色 admin∨销售总监 控制"已失真；
//  - 组织管理员数不再写死（后端仅有按组织查询接口，无汇总口径）。
const permOverview = computed(() => [
  {
    icon: '📋', title: '菜单权限',
    tag: '角色管理 → 权限',
    tagType: 'primary',
    desc: `控制用户能看到哪些页面和功能。分配时勾选菜单树即可，共 ${permPointCount.value} 个权限点。`,
    buttons: [{ label: '前往配置 →', tab: 'perm' }]
  },
  {
    icon: '📊', title: '数据权限',
    tag: '角色管理 → 编辑',
    tagType: 'warning',
    desc: '控制用户能看到多少条数据。共 7 档：全部(本集团) → 本大区及以下 → 本办事处及以下 → 本部门及以下 → 本人及下级 → 仅本人 → 自定义组织。',
    buttons: [{ label: '前往配置 →', tab: 'perm' }]
  },
  {
    icon: '✏️', title: '操作权限',
    tag: '系统内置',
    tagType: 'success',
    desc: '内置规则：每个人只能编辑/删除自己的客户和激励记录，管理员例外。无需手动配置。',
    buttons: []
  },
  {
    icon: '🏢', title: '组织管理权限',
    tag: '组织管理 → 管理',
    tagType: 'danger',
    desc: '指定组织的管理员，能管理该组织及下级的所有用户。在各组织的「管理」入口按组织配置。',
    buttons: [{ label: '前往配置 →', type: 'warning', href: '/system/org' }]
  },
  {
    icon: '⭐', title: '激励专项权限',
    tag: '激励类别与权限',
    tagType: 'warning',
    desc: '独立于菜单权限，按人勾选 查看已分享/查看全部/可编辑/可删除 四项开关。看全部=含未分享也可见。分享的记录对全员默认可见。',
    buttons: [{ label: '前往配置 →', type: 'warning', tab: 'incentiveCategory' }]
  }
])

const hasPerm = (role, item) => {
  const set = rolePermMap.value[role.id]
  return set ? set.has(item.id) : false
}

// 【P0 2026-08-12 对齐审计修复方向4】勾选权限点时只提交该真实权限载体(menu_type=2/3)自身,
// 不再自动补父菜单/目录行——父菜单(type=2 权限载体)须显式勾选, 目录行(type=1 锚点)由后端
// saveRoleMenus 闭包只补 type=1; 与 role.vue 权限弹窗同一契约, 杜绝"目录行入库→回显深传播"与静默授权。
const togglePerm = async (role, item) => {
  const set = rolePermMap.value[role.id]
  if (!set) return
  const had = set.has(item.id)
  if (had) {
    set.delete(item.id)
  } else {
    set.add(item.id)
  }
  try {
    await updateRole(role.id, { menuIds: [...set] })
  } catch (e) {
    // 保存失败:回滚本地勾选状态,避免"界面已改、数据库没变"的假成功
    if (had) set.add(item.id); else set.delete(item.id)
    ElMessage.error('权限保存失败,已还原勾选')
  }
}

const loadPermData = async () => {
  permLoadError.value = false
  try { const res = await getRoleList(); allRoles.value = res.data || [] } catch { permLoadError.value = true }
  try { const res = await getMenuTree(); menuTree.value = res.data || [] } catch { permLoadError.value = true }
  // 加载每个角色的菜单权限
  // 【性能 2026-10-04】各角色的菜单绑定互不依赖，串行 for 改为 Promise.all 并发（参照 reminder 页 2026-08-08 改法）
  await Promise.all(allRoles.value.map(async (role) => {
    try {
      const r = await request.get(`/system/roles/${role.id}`)
      rolePermMap.value[role.id] = new Set(r.data?.menuIds || [])
    } catch {}
  }))
}

// 四项服务权限
const permList = ref([])
const showPermDialog = ref(false)
// 【2026-08-16 授权表化】编辑/删除恢复按人勾选（不再由角色判定），permForm 含四个专项开关
const permForm = reactive({ canViewShared: true, canViewAll: true, canEdit: false, canDelete: false })
const allUsers = ref([])
// 层级人员树：大区 → 办事处 → 部门 → 人员（节点 key: 'd'+orgId / 'u'+userId）
const orgUserTree = ref([])
const userTreeRef = ref(null)
const selectedUserCount = ref(0)
const loadPermList = async () => {
  try { const res = await request.get('/incentive/permission/list'); permList.value = res.data || [] } catch(e) {}
}
const loadAllUsers = async () => {
  try { const res = await request.get('/system/users', { params: { current:1, size:500 }}); allUsers.value = res.data?.records || [] } catch(e) {}
}
// 构建层级人员树（人员挂到 deptId 匹配的组织节点，其次 officeId，其余归"未分部门"）
const loadOrgUserTree = async () => {
  try {
    const [dRes, uRes] = await Promise.all([
      request.get('/common/scope/depts'),
      allUsers.value.length ? Promise.resolve({ data: { records: allUsers.value } }) : request.get('/system/users', { params: { current: 1, size: 500 } }),
    ])
    const depts = dRes.data || []
    const users = uRes.data?.records || allUsers.value || []
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
    if (orphans.length) {
      tree.push({ key: 'd-orphan', label: '未分部门', children: orphans.map(u => ({ key: 'u' + u.id, label: `${u.realName} (${u.username})`, isUser: true })) })
    }
    orgUserTree.value = tree
  } catch (e) {}
}
const checkAllUsers = () => {
  if (!userTreeRef.value) return
  const keys = []
  const collect = (nodes) => { for (const n of nodes) { if (n.isUser) keys.push(n.key); collect(n.children || []) } }
  collect(orgUserTree.value)
  userTreeRef.value.setCheckedKeys(keys)
  selectedUserCount.value = keys.length
}
const clearUsers = () => {
  userTreeRef.value?.setCheckedKeys([])
  selectedUserCount.value = 0
}
const onTreeCheck = () => {
  selectedUserCount.value = (userTreeRef.value?.getCheckedKeys() || []).filter(k => String(k).startsWith('u')).length
}
const openPermDialog = async () => {
  showPermDialog.value = true
  await loadOrgUserTree()
  clearUsers()
}
const grantPerm = async () => {
  const checked = userTreeRef.value ? (userTreeRef.value.getCheckedKeys() || []).filter(k => String(k).startsWith('u')) : []
  const userIds = checked.map(k => Number(String(k).slice(1)))
  if (!userIds.length) { ElMessage.warning('请选择人员'); return }
  try {
    await request.post('/incentive/permission/grant', {
      userIds,
      canViewShared: permForm.canViewShared,
      canViewAll: permForm.canViewAll,
      canEdit: permForm.canEdit,
      canDelete: permForm.canDelete
    })
    ElMessage.success(`已授权 ${userIds.length} 人`); showPermDialog.value = false; loadPermList()
  } catch(e) { ElMessage.error('授权失败') }
}
const revokePerm = async (id) => {
  try { await confirmAction('撤销后该人员将回到基础可见范围：只能看到已分享的记录与本人上传内容。', '撤销激励授权', { confirmText: '确认撤销' }); await request.delete('/incentive/permission/'+id); ElMessage.success('已撤销'); loadPermList() } catch(e) {}
}

export function usePermissionData() {
  return {
    activeTab,
    permOverview, allRoles, menuTree, rolePermMap, permModules, hasPerm, togglePerm, loadPermData,
    permList, showPermDialog, permForm, allUsers, orgUserTree, userTreeRef, selectedUserCount,
    loadPermList, loadAllUsers, loadOrgUserTree, checkAllUsers, clearUsers, onTreeCheck, openPermDialog, grantPerm, revokePerm,
    permLoadError,
  }
}
