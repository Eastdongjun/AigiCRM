// 新增/编辑客户表单页共享态（C4 拆组件 2026-08-09）
// 页面级 provide/inject（per-route）：customer/add.vue 实例化一次并 provide，
// 5 个表单 section 子组件(基本信息/业务分类/归属负责人/地址/联系人) inject 同一上下文。
// 共享内容：form 表单态与校验规则 + 部门/用户/字典选项 + 联系人列表 + 重复名称检测 + 提交动作。
import { ref, reactive, computed, inject } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { addCustomer, getCustomer, updateCustomer, getSimpleUserList, getContacts, addContact } from '@/api/customer'
import { getDeptList, getDictData } from '@/api/system'
import regionData from '@/utils/region-data'
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
  const filteredOffices = computed(() => {
    if (!form.regionId) return []
    return offices.value.filter(o => o.parentId === form.regionId)
  })
  // 所属部门：选中办事处下的全部子部门（部门可无限嵌套，递归收集）
  const filteredDepts = computed(() => {
    if (!form.officeId) return []
    const res = []
    const collect = (pid) => {
      for (const d of offices.value) {
        if (d.parentId === pid) { res.push(d); collect(d.id) }
      }
    }
    collect(form.officeId)
    return res
  })
  const regionValue = ref([])
  const industryOptions = ref([])
  const devTypeOptions = ref([])
  const isEdit = computed(() => !!route.params.id)
  // 【2026-08-10 池级权限】公海池内客户(status=sea)负责人不可改——后端会拒绝, 前端直接禁用防误导
  const isPoolCustomer = computed(() => customerStatus.value === 'sea')
  const detailQuery = computed(() => (route.query.from ? { from: route.query.from } : {}))
  const contacts = ref([])

  const form = reactive({
    customerName: '',
    invoiceName: '',
    industry: '',
    devType: '',
    officeId: null,
    regionId: null,
    deptId: null,
    ownerId: null,
    groupName: '',
    branchCompany: '',
    unitName: '',
    capacity: '',
    province: '',
    city: '',
    district: '',
    address: '',
    registerDate: new Date().toISOString().split('T')[0],
    lastInvoiceDate: new Date().toISOString().split('T')[0],
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
  const onRegionChange = () => {
    if (form.officeId && !filteredOffices.value.some(o => o.id === form.officeId)) {
      form.officeId = null
    }
  }
  const onOfficeChange = () => {
    form.deptId = null
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
  const handleSubmit = async () => {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return

    loading.value = true
    try {
      if (isEdit.value) {
        await updateCustomer(route.params.id, form)
        for (const c of contacts.value) {
          if (c.name && !c.id) {
            await addContact(route.params.id, c)
          }
        }
        ElMessage.success('客户更新成功')
        router.push({ path: `/customer/detail/${route.params.id}`, query: detailQuery.value })
      } else {
        const res = await addCustomer(form)
        const newId = res.data
        if (newId && contacts.value.length) {
          for (const c of contacts.value) {
            if (c.name) await addContact(newId, c)
          }
        }
        ElMessage.success('客户添加成功')
        router.push('/customer')
      }
    } catch (e) {} finally {
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
    } catch (e) {}
    try {
      const res = await getSimpleUserList()
      users.value = res.data || []
    } catch (e) {}
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
        Object.keys(form).forEach(k => { if (c[k] !== undefined && c[k] !== null) form[k] = c[k] })
        customerStatus.value = c.status || ''
        if (c.province) regionValue.value = [c.province, c.city, c.district].filter(Boolean)
      } catch (e) {}
      try {
        const res = await getContacts(route.params.id)
        contacts.value = (res.data || []).map(c => ({ id: c.id, name: c.name, position: c.position, phone: c.phone, remark: c.remark }))
      } catch (e) {}
    }
  }

  return {
    formRef, dupInfo, loading, regions, offices, users,
    filteredOffices, filteredDepts, regionValue, industryOptions, devTypeOptions,
    isEdit, isPoolCustomer, detailQuery, contacts, form, rules,
    devTypeTagStyle, devTypeTagText, onRegionChange, onOfficeChange,
    handleRegionChange, addContactRow, goBack, checkDup, handleSubmit, loadFormData,
    regionData,
  }
}
