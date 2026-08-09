// 人员管理页共享态（C4 拆组件 2026-08-09）
// 页面级 provide/inject（per-route）：system/user.vue 实例化一次并 provide，
// 筛选栏/表格/翻页/新增编辑弹窗 inject 同一上下文。
// 共享内容：查询参数与数据 + 部门树/角色/人员下拉 + 弹窗表单态 + 加载/增删改动作。
// 表格高度机（tableWrapRef/tableHeight/ResizeObserver）留在 UserTable 组件内（本页模板真在用）。
import { ref, reactive, computed, inject } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, getUserById, createUser, updateUser, resetPassword, deleteUser, getDeptList, getRoleList } from '@/api/system'
import { getSimpleUserList } from '@/api/customer'
import { useUserStore } from '@/store/user'

export const UserListKey = Symbol('userList')

/** 子组件注入父级 system/user.vue 提供的共享上下文 */
export function injectUserList() {
  const ctx = inject(UserListKey)
  if (!ctx) throw new Error('injectUserList 必须在 system/user.vue 的组件内使用')
  return ctx
}

export function useUserList() {
  const userStore = useUserStore()

  // ---- 数据态 ----
  const tableData = ref([])
  const total = ref(0)
  const depts = ref([])
  const deptTree = ref([])
  let deptMap = new Map()
  const roles = ref([])
  const userOptions = ref([])

  // ---- 查询参数 ----
  const query = reactive({ current: 1, size: 30, keyword: '', deptId: null, status: null }) // 默认30/页与 UserPager page-sizes=[30,50,100] 一致

  // ---- 新增/编辑弹窗态 ----
  const dialogVisible = ref(false)
  const saving = ref(false)
  const formRef = ref(null)
  const orgPath = ref([])
  const cascaderProps = { label: 'deptName', value: 'id', children: 'children', checkStrictly: true, emitPath: true }
  const editForm = reactive({ id: null, username: '', realName: '', phone: '', email: '', deptId: null, officeId: null, roleId: null, managerId: null, dataScopeOverride: '', status: 1, orgIds: [] })
  const formRules = {
    realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
    roleId: [{ required: true, message: '请选择角色', trigger: 'change' }],
    phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
    email: [
      { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
    ]
  }

  // ---- 计算 ----
  const orgHintText = computed(() => {
    const node = deptMap.get(editForm.officeId)
    return node ? (node.deptCode || node.deptName) : ''
  })

  // 根据所选组织查找当前最大账号
  const latestAccountHint = computed(() => {
    const officeId = editForm.officeId || (orgPath.value?.length && orgPath.value[orgPath.value.length - 1])
    if (!officeId) return ''
    // 同级办事处的用户
    const sameOffice = tableData.value.filter(u => u.officeId === officeId && u.username)
    if (!sameOffice.length) return ''
    // 取第一个用户的账号前缀（去掉末尾数字）
    const sample = sameOffice[0].username
    const prefix = sample.replace(/\d+$/, '')
    if (!prefix) return ''
    // 找最大编号
    const nums = sameOffice
      .map(u => parseInt(u.username.replace(prefix, '')))
      .filter(n => !isNaN(n))
    const maxNum = nums.length > 0 ? Math.max(...nums) : 0
    const node = deptMap.get(officeId)
    const deptName = node?.deptName || ''
    return `当前 ${deptName} 最大账号: ${prefix}${String(maxNum).padStart(2, '0')}`
  })

  // ---- 工具 ----
  // 选中部门时匹配其自身 + 所有下级,便于按大区/办事处一键筛选整棵子树
  const descendantIds = (rootId) => {
    const ids = [rootId]
    const stack = [rootId]
    while (stack.length) {
      const cur = stack.pop()
      for (const d of depts.value) {
        if (Number(d.parentId) === Number(cur)) { ids.push(d.id); stack.push(d.id) }
      }
    }
    return ids
  }

  // 由扁平部门列表构建级联树(去掉空 children 以免出现空展开箭头)
  const buildTree = (list, parentId = 0) =>
    list
      .filter(d => Number(d.parentId) === Number(parentId))
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
      .map(d => {
        const children = buildTree(list, d.id)
        return children.length ? { ...d, children } : { ...d }
      })

  // 由 dept_id 沿 parentId 反推出级联路径 [总部, 大区, 办事处]
  const pathOf = (deptId) => {
    const path = []
    let cur = deptId
    let guard = 0
    while (cur != null && deptMap.has(cur) && guard++ < 20) {
      const node = deptMap.get(cur)
      path.unshift(node.id)
      cur = node.parentId
    }
    return path
  }

  // 从级联路径取办事处:路径中最浅的 type=3 节点(直属大区/总部的那个办事处)。
  // 子部门(无论 type=4 部门,还是被建成 type=3 的子办事处)都不覆盖顶层办事处,保证公海池路由稳定。
  const officeFromPath = (ids) => {
    for (const id of (ids || [])) {
      const node = deptMap.get(id)
      if (node && node.deptType === 3) return id
    }
    return null
  }

  const formatSize = (bytes) => {
    if (!bytes) return '0'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + 'KB'
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(0) + 'MB'
    return (bytes / (1024 * 1024 * 1024)).toFixed(1) + 'GB'
  }

  // 角色标签色：按角色名映射，未识别用默认
  const roleTagType = (name) => {
    if (!name) return 'info'
    if (name.includes('管理员')) return 'danger'
    if (name.includes('总监')) return 'success'
    if (name.includes('行政')) return 'warning'
    return 'primary'
  }

  // 存储使用率（0-100，上限 100）
  const storagePercent = (row) => {
    const used = row.storageUsed || 0
    const quota = row.storageQuota || 0
    if (!quota) return 0
    return Math.min(100, Math.round((used / quota) * 100))
  }
  const storageColor = (row) => {
    const p = storagePercent(row)
    if (p >= 90) return '#f56c6c'
    if (p >= 70) return '#e6a23c'
    return '#67c23a'
  }

  // ---- 加载 ----
  const loadData = async () => {
    try {
      const params = { current: query.current, size: query.size, keyword: query.keyword, status: query.status }
      if (query.deptId != null) params.deptIds = descendantIds(query.deptId).join(',')
      const res = await getUserList(params)
      tableData.value = res.data?.records || []
      total.value = res.data?.total || 0
    } catch (e) {}
  }

  const loadDepts = async () => {
    try {
      const res = await getDeptList()
      depts.value = res.data || []
      deptMap = new Map(depts.value.map(d => [d.id, d]))
      deptTree.value = buildTree(depts.value)
    } catch (e) {}
  }

  const loadRoles = async () => {
    try {
      const res = await getRoleList()
      roles.value = res.data || []
    } catch (e) {}
  }

  const loadUserOptions = async () => {
    try {
      const res = await getSimpleUserList()
      userOptions.value = res.data || []
    } catch (e) {}
  }

  // ---- 筛选/搜索 ----
  const onDeptFilterChange = () => {
    query.current = 1
    loadData()
  }
  // 搜索/状态筛选回到第 1 页（分页 @change 仍用 loadData 保持当前页）
  const search = () => { query.current = 1; loadData() }

  // ---- 弹窗动作 ----
  // 选择变化时派生:最深节点为部门(dept_id),办事处取路径中的办事处节点(office_id)
  const onOrgChange = (path) => {
    const ids = Array.isArray(path) ? path : []
    editForm.deptId = ids.length ? ids[ids.length - 1] : null
    editForm.officeId = officeFromPath(ids)
  }

  const openDialog = async (row) => {
    if (row) {
      Object.assign(editForm, { id: row.id, username: row.username, realName: row.realName, phone: row.phone, email: row.email || '', deptId: row.deptId, officeId: row.officeId, roleId: row.roleId, managerId: row.managerId || null, dataScopeOverride: row.dataScopeOverride || '', status: row.status, orgIds: row.orgIds || [] })
      orgPath.value = pathOf(row.deptId)
      editForm.officeId = officeFromPath(orgPath.value)
      // 2026-08-08 用户级 custom 授权：拉取个人授权组织回显
      if (row.dataScopeOverride === 'custom') {
        try {
          const res = await getUserById(row.id)
          editForm.orgIds = (res.data?.orgIds) || []
        } catch (e) {}
      }
    } else {
      Object.assign(editForm, { id: null, username: '', realName: '', phone: '', email: '', deptId: null, officeId: null, roleId: null, managerId: null, dataScopeOverride: '', status: 1, orgIds: [] })
      orgPath.value = []
    }
    dialogVisible.value = true
  }

  const handleSave = async () => {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return
    // 【2026-08-08 需求】数据范围必选，不允许跟随角色/留空
    if (!editForm.dataScopeOverride) { ElMessage.warning('请选择数据范围'); return }
    saving.value = true
    try {
      if (editForm.id) {
        await updateUser(editForm.id, editForm)
      } else {
        await createUser(editForm)
      }
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadData()
    } catch (e) {} finally { saving.value = false }
  }

  const handleResetPwd = async (row) => {
    try {
      await ElMessageBox.confirm(`确定重置 ${row.realName} 的密码为 123456？`, '提示')
      await resetPassword(row.id)
      ElMessage.success('密码已重置')
    } catch (e) {}
  }

  const handleDelete = async (row) => {
    try {
      await ElMessageBox.confirm(
        `确定删除用户 ${row.realName}？该用户名下的客户将自动释放到公海池。`,
        '删除用户',
        { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
      )
      await deleteUser(row.id)
      ElMessage.success('用户已删除，客户已释放到公海')
      loadData()
    } catch (e) {}
  }

  return {
    userStore,
    tableData, total, depts, deptTree, roles, userOptions, query,
    dialogVisible, saving, formRef, orgPath, cascaderProps, editForm, formRules,
    orgHintText, latestAccountHint,
    loadData, loadDepts, loadRoles, loadUserOptions,
    onDeptFilterChange, search, onOrgChange, openDialog, handleSave, handleResetPwd, handleDelete,
    descendantIds, buildTree, pathOf, officeFromPath,
    formatSize, roleTagType, storagePercent, storageColor,
  }
}
