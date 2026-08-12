// 客户详情共享态（C4 拆组件 2026-08-09）
// 客户详情页 header + 5 个 tab 共享同一份状态(customer/invoices/indicator/incentiveRecords 等)，
// 且 header 计算(isCore/totalScore)依赖各 tab 数据。用 provide/inject：父级 detail.vue 实例化一次并
// provide，各 tab 子组件 inject 同一上下文（非模块单例——客户详情是 per-route，避免跨客户状态残留）。
import { ref, reactive, computed, watch, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCustomer, getCustomerLogs, addCustomerLog, updateCustomer,
  getInvoices, getInvoiceSummary, addInvoice, deleteInvoice,
  getPayments, getPaymentSummary, addPayment, deletePayment,
  getContacts, addContact, updateContact, deleteContact,
  getIndicator, saveIndicator, saveRelationship,
  getShares, addShares, removeShare, getSimpleUserList,
} from '@/api/customer'
import { getDictData } from '@/api/system'
import { submitApproval } from '@/api/approval'
import { getScoreByCustomer, getCategoryTree } from '@/api/incentive'
import request from '@/utils/request'
import { getDownloadToken } from '@/utils/token'
import { downloadFile } from '@/utils/download'
import { useUserStore } from '@/store/user'

export const CustomerDetailKey = Symbol('customerDetail')

/** 子组件注入父级 detail.vue 提供的共享上下文 */
export function injectCustomerDetail() {
  const ctx = inject(CustomerDetailKey)
  if (!ctx) throw new Error('injectCustomerDetail 必须在 customer/detail.vue 的 tab 子组件内使用')
  return ctx
}

export function useCustomerDetail() {
  const route = useRoute(); const router = useRouter(); const userStore = useUserStore()
  const isAdmin = computed(() => userStore.roleName === '系统管理员' || (userStore.roles || []).includes('admin'))
  const readonly = computed(() => {
    const from = route.query.from
    return from === 'share' || from === 'sea' || (route.query.readonly === '1')
  })
  const readonlyTag = computed(() => route.query.from === 'sea' ? '只读 · 公海客户' : '只读 · 共享客户')

  const loading = ref(true); const activeTab = ref('info')
  const customer = ref(null); const logs = ref([]); const newLog = ref(''); const invoices = ref([])
  const invoiceSummary = ref({ totalCount: 0, totalAmount: 0 }); const payments = ref([])
  const paymentSummary = ref({ totalCount: 0, totalAmount: 0 }); const contacts = ref([])
  const indicator = ref(null); const shares = ref([]); const allUsers = ref([]); const incentiveScore = ref(0)
  const incentiveRecords = ref([])
  const catMap = ref({})

  const showInvoiceDialog = ref(false); const showPaymentDialog = ref(false)
  const showContactDialog = ref(false); const showShareDialog = ref(false)
  const shareUserIds = ref([]); const editingContact = ref(null)

  const invoiceForm = reactive({ invoiceDate:'', invoiceNumber:'', invoiceType:'增值税专票', invoiceCompany:'', amount:null })
  const paymentForm = reactive({ paymentDate:'', paymentAmount:null, paymentMethod:'', remark:'' })
  const contactForm = reactive({ name:'', position:'', phone:'', remark:'' })
  const indicatorForm = reactive({ indicator02:0, indicator03:0, indicator04:0, indicator05:0, indicator06:0 })

  const avatarColor = computed(() => {
    const colors = ['#409eff', '#e6a23c', '#9c27b0', '#13c2c2', '#f56c6c', '#67c23a']
    let h = 0; const n = customer.value?.customerName || ''
    for (const c of n) h = (h * 31 + c.charCodeAt(0)) % 997
    return colors[h % colors.length]
  })
  const relationshipOptions = ref([{label:'商务少',value:'0',icon:'📊'},{label:'纯商务',value:'1',icon:'📋'},{label:'感情商务',value:'2',icon:'🤝'},{label:'多样性',value:'3',icon:'🌐'}])
  const indicatorDefs = ref([
    { code: '02', field: 'indicator02', label: '决策链深度(1-3)', options: [{ dictValue: '1', dictLabel: '浅' }, { dictValue: '2', dictLabel: '中' }, { dictValue: '3', dictLabel: '深' }] },
    { code: '03', field: 'indicator03', label: '关键人覆盖(1-3)', options: [{ dictValue: '1', dictLabel: '少' }, { dictValue: '2', dictLabel: '中' }, { dictValue: '3', dictLabel: '全' }] },
    { code: '04', field: 'indicator04', label: '信息透明度(1-3)', options: [{ dictValue: '1', dictLabel: '低' }, { dictValue: '2', dictLabel: '中' }, { dictValue: '3', dictLabel: '高' }] },
    { code: '05', field: 'indicator05', label: '合作深度(1-3)', options: [{ dictValue: '1', dictLabel: '浅' }, { dictValue: '2', dictLabel: '中' }, { dictValue: '3', dictLabel: '深' }] },
    { code: '06', field: 'indicator06', label: '私交程度(1-3)', options: [{ dictValue: '1', dictLabel: '弱' }, { dictValue: '2', dictLabel: '中' }, { dictValue: '3', dictLabel: '强' }] },
  ])
  const indicatorTotal = computed(() => indicatorForm.indicator02+indicatorForm.indicator03+indicatorForm.indicator04+indicatorForm.indicator05+indicatorForm.indicator06)
  const invoiceThresholdMet = computed(() => (invoiceSummary.value.totalAmount||0) >= 100)
  const paymentThresholdMet = computed(() => (paymentSummary.value.totalAmount||0) >= 100)
  const bothThresholdMet = computed(() => invoiceThresholdMet.value && paymentThresholdMet.value)
  const isCore = computed(() => bothThresholdMet.value && indicatorTotal.value >= 12)
  const totalScore = computed(() => incentiveScore.value + (indicator.value?.relationshipScore||0))
  const expireLevel = computed(() => {
    const d = customer.value?.expireDate
    if (!d) return 'blue'
    const days = Math.ceil((new Date(d) - new Date()) / 86400000)
    return days < 30 ? 'red' : days < 90 ? 'orange' : 'blue'
  })
  const statusLabel = computed(() => {
    const d = customer.value?.expireDate
    if (!d) return '未知'
    const days = Math.ceil((new Date(d) - new Date()) / 86400000)
    if (days < 0) return '已过期'
    if (days < 30) return `即将到期(${days}天)`
    if (days < 90) return `关注中(${days}天)`
    return '正常'
  })
  const customerId = computed(() => route.params.id)

  const loadAll = async () => {
    try {
      const cRes = await getCustomer(customerId.value); customer.value = cRes.data
      const [lRes, invRes, summRes, payRes, paySummRes, cRes2, indRes, shaRes, scoreRes] = await Promise.all([
        getCustomerLogs(customerId.value), getInvoices(customerId.value), getInvoiceSummary(customerId.value),
        getPayments(customerId.value), getPaymentSummary(customerId.value), getContacts(customerId.value),
        getIndicator(customerId.value), getShares(customerId.value), getScoreByCustomer(customerId.value),
      ])
      logs.value = lRes.data || []; invoices.value = invRes.data || []; invoiceSummary.value = summRes.data || {}
      payments.value = payRes.data || []; paymentSummary.value = paySummRes.data || {}; contacts.value = cRes2.data || []
      indicator.value = indRes.data; shares.value = shaRes.data || []; incentiveScore.value = scoreRes.data || 0
      if (indicator.value) {
        indicatorForm.indicator02 = indicator.value.indicator02 || 0
        indicatorForm.indicator03 = indicator.value.indicator03 || 0
        indicatorForm.indicator04 = indicator.value.indicator04 || 0
        indicatorForm.indicator05 = indicator.value.indicator05 || 0
        indicatorForm.indicator06 = indicator.value.indicator06 || 0
      }
    } catch (e) {} finally { loading.value = false }
  }
  const loadDicts = async () => {
    try { const res = await getCategoryTree(); catMap.value = (res.data||[]).reduce((m,c)=>{ m[c.id]=c; (c.children||[]).forEach(s=>m[s.id]=s); return m },{}) } catch(e) {}
  }
  const loadUsers = async () => { try { allUsers.value = (await getSimpleUserList()).data||[] } catch(e) {} }
  const loadIncentiveRecords = async () => {
    try {
      // 【修复 2026-08-12】/incentive/records 返回数组非分页对象，且按 customerId 过滤（去掉 current/size）
      const res = await request.get('/incentive/records', { params: { customerId: customerId.value } })
      incentiveRecords.value = res.data || []
    } catch (e) {}
  }
  const getMainCategory = (r) => { const c = catMap.value[r.categoryId]; if (!c) return ''; const p = catMap.value[c.parentId]; return p ? p.categoryName : c.categoryName }
  const getSubCategory = (r) => { const c = catMap.value[r.categoryId]; return c ? c.categoryName : '' }
  const parentCodeOf = (r) => { let c = catMap.value[r.categoryId]; if (!c) return ''; while (c.parentId && catMap.value[c.parentId]) c = catMap.value[c.parentId]; return c.categoryCode || '' }
  const detailLabelOf = (r) => { const code = parentCodeOf(r); const sub = getSubCategory(r) || r.categoryName || ''; if (code === 'plan') return sub + '文件详情描述'; if (code === 'visit' || code === 'exchange') return sub + '详情描述'; return sub + '安装详情' }
  const isTrialRec = (r) => parentCodeOf(r).startsWith('trial')
  const scoreColor = (r) => { const c = catMap.value[r.categoryId]; if (!c) return '#333'; const p = catMap.value[c.parentId]; const gp = p ? catMap.value[p.parentId] : null; const code = (gp && gp.parentId === 0 ? gp.categoryCode : p?.categoryCode) || ''; return { visit: '#e6a23c', exchange: '#409eff', plan: '#9c27b0', trial: '#67c23a' }[code] || '#333' }
  function parseId(v){if(!v)return null;if(typeof v==='number')return v;try{const a=JSON.parse(v);return Array.isArray(a)?a[0]:v}catch{return v}}
  const getFiles = (r) => { const f = []; if (r.filePreId) f.push({ id: parseId(r.filePreId), label: r.filePreName || '文件', type: detectType(r.filePreName), tag: '安装前' }); if (r.fileMidId) f.push({ id: parseId(r.fileMidId), label: r.fileMidName || '文件', type: detectType(r.fileMidName), tag: '安装中' }); if (r.filePostId) f.push({ id: parseId(r.filePostId), label: r.filePostName || '文件', type: detectType(r.filePostName), tag: '安装后' }); if (r.fileId) f.push({ id: parseId(r.fileId), label: r.fileName || '文件', type: detectType(r.fileName), tag: '' }); return f }
  const detectType = (n) => { if (!n) return 'doc'; if (/\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(n)) return 'image'; if (/\.(mp4|mov|avi|webm)$/i.test(n)) return 'video'; return 'doc' }
  const fmtDate = (t) => { if (!t) return ''; const d = new Date(t); return d.getFullYear()+'-'+(d.getMonth()+1).toString().padStart(2,'0')+'-'+d.getDate().toString().padStart(2,'0') }

  const downloadIncentiveFile = async (f) => {
    if (!f?.id) return
    const url = fileUrl(f.id)
    await downloadFile(url, { name: f.label || ('file-' + f.id) })
  }
  const fileUrl = (id) => '/api/file/download/' + id + '?token=' + (getDownloadToken() || '')
  const filePreviewVisible = ref(false), filePreviewUrl = ref(''), filePreviewType = ref('image'), filePreviewName = ref('')
  // 激励Tab文件预览:复用 FilePreview(图片放大/视频播放/文档下载);点文件行打开预览而非直接下载
  const previewIncentiveFile = (f) => {
    if (!f?.id) return
    filePreviewUrl.value = fileUrl(f.id)
    filePreviewType.value = f.type || detectType(f.label)
    filePreviewName.value = f.label
    filePreviewVisible.value = true
  }
  const submitLog = async () => { if(!newLog.value.trim()) return; try { await addCustomerLog(customerId.value,{content:newLog.value}); ElMessage.success('已记录'); newLog.value=''; logs.value = (await getCustomerLogs(customerId.value)).data||[] } catch(e) {} }
  const handleExtend = async () => { try { await ElMessageBox.confirm('确认申请延期？审批通过后保护期延长360天','申请延期'); await submitApproval({type:'extend',customerId:String(customer.value.id),actionAfterApprove:'保护期+360天'}); ElMessage.success('延期申请已提交') } catch(e) {} }
  const handleEdit = () => router.push(`/customer/edit/${customerId.value}`)
  const handleAddInvoice = async () => { if(!invoiceForm.invoiceDate||!invoiceForm.invoiceNumber||!invoiceForm.amount) return ElMessage.warning('请填写完整'); try { await addInvoice(customerId.value,{...invoiceForm}); ElMessage.success('已添加'); showInvoiceDialog.value=false; Object.assign(invoiceForm,{invoiceDate:'',invoiceNumber:'',invoiceType:'增值税专票',invoiceCompany:'',amount:null}); const [invRes,summRes,indRes,cRes] = await Promise.all([getInvoices(customerId.value),getInvoiceSummary(customerId.value),getIndicator(customerId.value),getCustomer(customerId.value)]); invoices.value=invRes.data||[]; invoiceSummary.value=summRes.data||{}; indicator.value=indRes.data; customer.value=cRes.data } catch(e) {} }
  const handleDeleteInvoice = async (row) => { try { await ElMessageBox.confirm('确认删除?','提示'); await deleteInvoice(customerId.value,row.id); ElMessage.success('已删除'); const [invRes,summRes,indRes,cRes] = await Promise.all([getInvoices(customerId.value),getInvoiceSummary(customerId.value),getIndicator(customerId.value),getCustomer(customerId.value)]); invoices.value=invRes.data||[]; invoiceSummary.value=summRes.data||{}; indicator.value=indRes.data; customer.value=cRes.data } catch(e) {} }
  const handleAddPayment = async () => { if(!paymentForm.paymentDate||!paymentForm.paymentAmount) return ElMessage.warning('请填写完整'); try { await addPayment(customerId.value,{...paymentForm}); ElMessage.success('已添加'); showPaymentDialog.value=false; Object.assign(paymentForm,{paymentDate:'',paymentAmount:null,paymentMethod:'',remark:''}); const [payRes,paySummRes,indRes] = await Promise.all([getPayments(customerId.value),getPaymentSummary(customerId.value),getIndicator(customerId.value)]); payments.value=payRes.data||[]; paymentSummary.value=paySummRes.data||{}; indicator.value=indRes.data } catch(e) {} }
  const handleDeletePayment = async (row) => { try { await ElMessageBox.confirm('确认删除?','提示'); await deletePayment(customerId.value,row.id); ElMessage.success('已删除'); const [payRes,paySummRes,indRes] = await Promise.all([getPayments(customerId.value),getPaymentSummary(customerId.value),getIndicator(customerId.value)]); payments.value=payRes.data||[]; paymentSummary.value=paySummRes.data||{}; indicator.value=indRes.data } catch(e) {} }
  const openEditContact = (row) => { editingContact.value=row; Object.assign(contactForm,{name:row.name,position:row.position,phone:row.phone,remark:row.remark}); showContactDialog.value=true }
  const handleSaveContact = async () => { if(!contactForm.name) return ElMessage.warning('请填写姓名'); try { editingContact.value ? await updateContact(customerId.value,editingContact.value.id,{...contactForm}) : await addContact(customerId.value,{...contactForm}); ElMessage.success(editingContact.value?'已更新':'已添加'); showContactDialog.value=false; editingContact.value=null; Object.assign(contactForm,{name:'',position:'',phone:'',remark:''}); contacts.value = (await getContacts(customerId.value)).data||[] } catch(e) {} }
  const handleDeleteContact = async (row) => { try { await ElMessageBox.confirm('确认删除?','提示'); await deleteContact(customerId.value,row.id); ElMessage.success('已删除'); contacts.value = (await getContacts(customerId.value)).data||[] } catch(e) {} }
  const handleSaveIndicator = async () => { try { await saveIndicator(customerId.value,{...indicatorForm}); ElMessage.success('已保存'); indicator.value = (await getIndicator(customerId.value)).data } catch(e) {} }
  const onIndicatorChange = () => {}
  const selectRelationship = async (score) => { try { await saveRelationship(customerId.value,score); indicator.value = (await getIndicator(customerId.value)).data } catch(e) {} }
  const handleAddShare = async () => { if(!shareUserIds.value.length) return; try { await addShares(customerId.value,shareUserIds.value); ElMessage.success('已分享'); shareUserIds.value=[]; showShareDialog.value=false; shares.value = (await getShares(customerId.value)).data||[] } catch(e) {} }
  const handleRemoveShare = async (s) => { try { await removeShare(customerId.value,s.sharedToUserId); shares.value = (await getShares(customerId.value)).data||[] } catch(e) {} }

  const loadAllFn = loadAll
  const loadDictsFn = loadDicts
  const loadUsersFn = loadUsers

  return {
    customerId, route, router, userStore, isAdmin, readonly, readonlyTag,
    loading, activeTab, customer, logs, newLog, invoices, invoiceSummary, payments, paymentSummary,
    contacts, indicator, shares, allUsers, incentiveScore, incentiveRecords, catMap,
    showInvoiceDialog, showPaymentDialog, showContactDialog, showShareDialog, shareUserIds, editingContact,
    invoiceForm, paymentForm, contactForm, indicatorForm,
    avatarColor, relationshipOptions, indicatorDefs, indicatorTotal, invoiceThresholdMet,
    paymentThresholdMet, bothThresholdMet, isCore, totalScore, expireLevel, statusLabel,
    loadAll: loadAllFn, loadDicts: loadDictsFn, loadUsers: loadUsersFn, loadIncentiveRecords,
    getMainCategory, getSubCategory, parentCodeOf, detailLabelOf, isTrialRec, scoreColor,
    parseId, getFiles, detectType, fmtDate, downloadIncentiveFile,
    filePreviewVisible, filePreviewUrl, filePreviewType, filePreviewName, previewIncentiveFile,
    submitLog, handleExtend, handleEdit,
    handleAddInvoice, handleDeleteInvoice, handleAddPayment, handleDeletePayment,
    openEditContact, handleSaveContact, handleDeleteContact,
    handleSaveIndicator, onIndicatorChange, selectRelationship, handleAddShare, handleRemoveShare,
  }
}
