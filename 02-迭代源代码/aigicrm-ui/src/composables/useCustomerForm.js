// 新增/编辑客户表单页共享态（C4 拆组件 2026-08-09）
// 页面级 provide/inject（per-route）：customer/add.vue 实例化一次并 provide，
// 5 个表单 section 子组件(基本信息/业务分类/归属负责人/地址/联系人) inject 同一上下文。
// 共享内容：form 表单态与校验规则 + 部门/用户/字典选项 + 联系人列表 + 重复名称检测 + 提交动作。
import { ref, reactive, computed, inject } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import {
  addCustomer, getCustomer, updateCustomer, getSimpleUserList, getContacts, addContact,
  getIndicator, saveIndicator, getCustomerGroupNames,
} from '@/api/customer'
import { getDeptList, getDictData } from '@/api/system'
import regionData from '@/utils/region-data'
import { INDICATOR_FIELDS, sumIndicatorScore } from '@/utils/customer-fields'
import { useUserStore } from '@/store/user'

export const CustomerFormKey = Symbol('customerForm')

/** 子组件注入父级 customer/add.vue 提供的共享上下文 */
export function injectCustomerForm() {
  const ctx = inject(CustomerFormKey)
  if (!ctx) throw new Error('injectCustomerForm 必须在 customer/add.vue 的组件内使用')
  return ctx
}

export function useCustomerForm() {
  const router = useRouter()
  const userStore = useUserStore()
  const route = useRoute()

  const formRef = ref(null)
  const dupInfo = reactive({ exists: false, createdBy: "", createdAt: "" })
  const loading = ref(false)
  const customerStatus = ref('') // 编辑加载时记录的客户状态(sea=公海池内, 负责人字段需禁用)
  const regions = ref([])
  const offices = ref([])
  const users = ref([])
  // 【2026-09-13】「所属集团」候选：后端按数据范围返回客户历史集团名(去重), 前端做可选可新建下拉,
  // 根治同一集团多种写法(此前是纯自由文本输入框)
  const groupNameOptions = ref([])
  const filteredOffices = computed(() => {
    if (!form.regionId) return []
    return offices.value.filter(o => o.parentId === form.regionId)
  })
  const regionValue = ref([])
  const industryOptions = ref([])
  const devTypeOptions = ref([])
  const isEdit = computed(() => !!route.params.id)
  const canEditProtectedDates = computed(() => !isEdit.value || userStore.isAdmin() || userStore.hasPermission('customer:date:edit'))
  // 【2026-08-10 池级权限】公海池内客户(status=sea)负责人不可改——后端会拒绝, 前端直接禁用防误导
  const isPoolCustomer = computed(() => customerStatus.value === 'sea')
  const detailQuery = computed(() => (route.query.from ? { from: route.query.from } : {}))
  const contacts = ref([])

  // 【2026-09-13】客情五项评分(0-3 分/项)：此前只能在客户详情页录入, 建档时无入口 →
  // 新客户综合得分与核心客户判定恒为 0, 须事后进详情页补录; 现在新增/编辑页即可录入。
  const indicatorForm = reactive(INDICATOR_FIELDS.reduce((o, f) => { o[f.field] = 0; return o }, {}))
  const indicatorLoaded = ref(false) // 库内已有指标行(编辑场景): 保存时需整体回写, 即使全 0
  const indicatorTotal = computed(() => sumIndicatorScore(indicatorForm))

  // 【2026-09-13】新建专用默认值：只在"新增"时有意义，编辑加载时必须以库内真实值覆盖（含 null）。
  // registerDate 默认"今天"若在编辑态继续生效，后端 protectCustomerDateFields 会把它判成
  // "注册日被修改" → 普通销售编辑 register_date 为空的客户直接报错
  // "注册日和最近开票日录入后仅总监或行政可修改"（存不了）；总监/行政则把 NULL 静默写成今天。
  const CREATE_DEFAULTS = {
    hasTerminal: 0,
    registerDate: new Date().toISOString().split('T')[0]
  }

  const form = reactive({
    customerName: '',
    invoiceName: '',
    industry: '',
    devType: '',
    // 终端：与导入模板同名同口径（1=是 0=否），新增缺省按导入默认 0=否
    hasTerminal: CREATE_DEFAULTS.hasTerminal,
    officeId: null,
    regionId: null,
    ownerId: null,
    // 管理上级（选填）：落 biz_customer.manager_id，列表「上级」列与详情页据此显示
    managerId: null,
    groupName: '',
    branchCompany: '',
    unitName: '',
    capacity: '',
    province: '',
    city: '',
    district: '',
    address: '',
    registerDate: CREATE_DEFAULTS.registerDate,
    // 最近开票日留空：此前默认"今天"，会把从未开票的新客户记为当天开票，保护期口径失真
    lastInvoiceDate: '',
    remark: ''
  })

  const rules = {
    customerName: [{ required: true, message: '请输入客户全称', trigger: 'blur' }],
    devType: [{ required: true, message: '请选择开发类型', trigger: 'change' }],
    industry: [{ required: true, message: '请选择行业', trigger: 'change' }],
    regionId: [{ required: true, message: '请选择所属大区', trigger: 'change' }],
    officeId: [{ required: true, message: '请选择所属办事处', trigger: 'change' }],
    ownerId: [{ required: true, message: '请选择第一负责人', trigger: 'change' }]
  }

  // ---- 开发类型角标 ----
  const devTypeTagStyle = (t) => {
    const m = { '成熟创新': 'background:#ecf5ff;color:#409eff', '突破开拓': 'background:#fdf6ec;color:#e6a23c', '铺垫宣传': 'background:#f0f9eb;color:#67c23a' }
    return m[t] || 'background:#f5f7fa;color:#909399'
  }
  const devTypeTagText = (t) => ({ '成熟创新': '创新', '突破开拓': '突破', '铺垫宣传': '铺垫' }[t] || '')

  // ---- 级联联动 ----
  // 注：客户归属部门(dept_id)由后端按「第一负责人所在部门」自动确定(ORG 数据范围锚点)，
  // 表单不再提供「所属部门」下拉——此前该下拉可填但会被后端覆盖，属误导性死字段(2026-09-13 移除)。
  const onRegionChange = () => {
    if (form.officeId && !filteredOffices.value.some(o => o.id === form.officeId)) {
      form.officeId = null
    }
  }
  const handleRegionChange = (val) => {
    form.province = val?.[0] || ''
    form.city = val?.[1] || ''
    form.district = val?.[2] || ''
  }

  // ---- 联系人 ----
  const addContactRow = () => {
    contacts.value.push({ name: '', position: '', phone: '', remark: '' })
  }

  /**
   * 联系人行校验：填了职位/电话/备注但没填姓名的行，提交时会被丢弃（后端按姓名判重、无姓名不落库）。
   * 此前是静默丢弃 → 用户以为已保存。现在返回错误文案并在提交前拦截。
   * 整行全空视为未填写，不报错（允许保留空行）。
   */
  const validateContacts = () => {
    const idx = contacts.value.findIndex(c =>
      !String(c.name || '').trim()
      && ['position', 'phone', 'remark'].some(k => String(c[k] || '').trim()))
    return idx < 0 ? '' : `第 ${idx + 1} 行联系人未填姓名，请补全或删除该行`
  }

  /** 提交用联系人行：去掉全空行、统一 trim */
  const contactRows = () => contacts.value
    .filter(c => String(c.name || '').trim())
    .map(c => ({
      ...c,
      name: c.name.trim(),
      position: String(c.position || '').trim(),
      phone: String(c.phone || '').trim(),
      remark: String(c.remark || '').trim()
    }))

  // ---- 导航 ----
  const goBack = () => {
    if (isEdit.value) {
      router.push({ path: `/customer/detail/${route.params.id}`, query: detailQuery.value })
    } else {
      router.push('/customer')
    }
  }

  // ---- 重复名称检测 ----
  const checkDup = async () => {
    const n = form.customerName?.trim()
    if (!n || n.length < 2) { dupInfo.exists = false; return }
    try {
      const res = await request.get('/customer/check-name', { params: { name: n } })
      if (res?.data) Object.assign(dupInfo, res.data)
    } catch (e) { dupInfo.exists = false }
  }

  // ---- 提交 ----
  /**
   * 请求体：
   * · 日期清空在表单里是空串，统一转 null；null 在后端是"不修改"语义（updateById 跳过 null 字段），
   *   与既有保护期权限校验一致，不会误写。
   * · 新建时把空串归一为 null：insert 语义下写出干净的 NULL，避免库里出现 '' 与 NULL 两套空值。
   * · 编辑时**不**做空串归一：updateById 跳过 null 字段，空串是"把这个字段清空"的唯一表达方式，
   *   归一成 null 会让用户清空字段失效（存回去还是旧值）。
   */
  const buildPayload = () => {
    const payload = {
      ...form,
      registerDate: form.registerDate || null,
      lastInvoiceDate: form.lastInvoiceDate || null
    }
    if (isEdit.value) return payload
    Object.keys(payload).forEach((k) => {
      if (typeof payload[k] === 'string' && payload[k].trim() === '') payload[k] = null
    })
    return payload
  }

  /** 有评分或库内已有指标行才写库：全 0 且无指标行时不建空记录 */
  const shouldSaveIndicator = () => indicatorLoaded.value || sumIndicatorScore(indicatorForm) > 0

  const handleSubmit = async () => {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return

    const contactError = validateContacts()
    if (contactError) {
      ElMessage.error(contactError)
      return
    }
    const rows = contactRows()

    loading.value = true
    try {
      if (isEdit.value) {
        await updateCustomer(route.params.id, buildPayload())
        for (const c of rows) {
          if (!c.id) {
            await addContact(route.params.id, c)
          }
        }
        if (shouldSaveIndicator()) {
          await saveIndicator(route.params.id, { ...indicatorForm })
        }
        ElMessage.success('客户更新成功')
        router.push({ path: `/customer/detail/${route.params.id}`, query: detailQuery.value })
      } else {
        const res = await addCustomer(buildPayload())
        const newId = res.data
        if (newId) {
          for (const c of rows) {
            await addContact(newId, c)
          }
          if (shouldSaveIndicator()) {
            await saveIndicator(newId, { ...indicatorForm })
          }
        }
        ElMessage.success('客户添加成功')
        router.push('/customer')
      }
    } catch (e) {
      // 提示由 utils/request 响应拦截器统一弹出；此处只负责复位 loading，避免重复弹窗
    } finally {
      loading.value = false
    }
  }

  // ---- 初始加载 ----
  const loadFormData = async () => {
    try {
      const res = await getDeptList()
      const all = res.data || []
      regions.value = all.filter(d => d.deptType === 2)
      offices.value = all.filter(d => d.deptType === 3)
    } catch (e) { regions.value = []; offices.value = [] }
    try {
      const res = await getSimpleUserList()
      users.value = res.data || []
    } catch (e) { users.value = [] }
    try {
      const res = await getCustomerGroupNames()
      groupNameOptions.value = (res.data || []).filter(n => String(n || '').trim())
    } catch (e) { groupNameOptions.value = [] }
    if (!isEdit.value && userStore.userId) {
      form.ownerId = userStore.userId
    }
    try {
      const res = await getDictData('industry')
      industryOptions.value = (res.data || []).map(d => d.dictValue)
    } catch (e) { industryOptions.value = ['煤矿', '化工', '电力', '洗煤厂', '有色', '其他'] }
    try {
      const res = await getDictData('dev_type')
      devTypeOptions.value = (res.data || []).map(d => d.dictValue)
    } catch (e) { devTypeOptions.value = ['成熟创新', '突破开拓', '铺垫宣传'] }

    if (isEdit.value) {
      try {
        const res = await getCustomer(route.params.id)
        const c = res.data
        // 编辑：一律以库内值为准，null 也照实落到表单（不回退到新增默认值——否则默认"今天"会被
        // 当成用户输入提交，触发后端保护期校验报错或静默改数据）
        Object.keys(form).forEach(k => { if (c[k] !== undefined) form[k] = c[k] })
        customerStatus.value = c.status || ''
        if (c.province) regionValue.value = [c.province, c.city, c.district].filter(Boolean)
      } catch (e) { customerStatus.value = '' }
      try {
        const res = await getContacts(route.params.id)
        contacts.value = (res.data || []).map(c => ({ id: c.id, name: c.name, position: c.position, phone: c.phone, remark: c.remark }))
      } catch (e) { contacts.value = [] }
      try {
        const res = await getIndicator(route.params.id)
        const ind = res.data
        if (ind) {
          indicatorLoaded.value = true
          INDICATOR_FIELDS.forEach(f => { if (ind[f.field] !== undefined && ind[f.field] !== null) indicatorForm[f.field] = ind[f.field] })
        }
      } catch (e) { indicatorLoaded.value = false }
    }
  }

  return {
    formRef, dupInfo, loading, regions, offices, users, groupNameOptions,
    filteredOffices, regionValue, industryOptions, devTypeOptions,
    isEdit, isPoolCustomer, canEditProtectedDates, detailQuery, contacts, form, rules,
    indicatorForm, indicatorTotal, indicatorLoaded,
    devTypeTagStyle, devTypeTagText, onRegionChange,
    handleRegionChange, addContactRow, goBack, checkDup, handleSubmit, loadFormData,
    regionData,
  }
}
