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
  confirmInvoice, voidInvoice, confirmPayment, voidPayment,
  getPayments, getPaymentSummary, addPayment, deletePayment,
  getContacts, addContact, updateContact, deleteContact,
  getIndicator, saveIndicator, saveRelationship,
  getShares, addShares, removeShare, getSimpleUserList,
} from '@/api/customer'
import { getDictData } from '@/api/system'
import { submitApproval } from '@/api/approval'
import { getScoreByCustomer, getCategoryTree } from '@/api/incentive'
import request from '@/utils/request'
import { getDownloadToken, refreshDownloadToken } from '@/utils/token'
import { downloadFile } from '@/utils/download'
import { useUserStore } from '@/store/user'
import { INDICATOR_FIELDS, INDICATOR_CORE_THRESHOLD, sumIndicatorScore } from '@/utils/customer-fields'
import { mediaTypeOf } from '@/utils/media-type'

export const CustomerDetailKey = Symbol('customerDetail')

/** 子组件注入父级 detail.vue 提供的共享上下文 */
export function injectCustomerDetail() {
  const ctx = inject(CustomerDetailKey)
  if (!ctx) throw new Error('injectCustomerDetail 必须在 customer/detail.vue 的 tab 子组件内使用')
  return ctx
}

export function useCustomerDetail() {
  const route = useRoute(); const router = useRouter(); const userStore = useUserStore()
  const canViewFinance = computed(() => userStore.hasPermission('finance:view'))
  const canViewContacts = computed(() => userStore.hasPermission('contact:view'))
  const isAdmin = computed(() => userStore.isAdmin())
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
  const showContactDialog = ref(false); const showShareDialog = ref(false); const showTransferDialog = ref(false); const transferUserId = ref(null)
  const shareUserIds = ref([]); const editingContact = ref(null)

  const invoiceForm = reactive({ invoiceDate:'', invoiceNumber:'', invoiceType:'增值税专票', invoiceCompany:'', amount:null })
  const paymentForm = reactive({ paymentDate:'', paymentAmount:null, paymentMethod:'', remark:'' })
  const contactForm = reactive({ name:'', position:'', phone:'', remark:'' })
  // 五项评分初始值由统一定义生成，字段名不再在两处硬编码
  const indicatorForm = reactive(INDICATOR_FIELDS.reduce((o, f) => { o[f.field] = 0; return o }, {}))

  const avatarColor = computed(() => {
    const colors = ['#409eff', '#e6a23c', '#9c27b0', '#13c2c2', '#f56c6c', '#67c23a']
    let h = 0; const n = customer.value?.customerName || ''
    for (const c of n) h = (h * 31 + c.charCodeAt(0)) % 997
    return colors[h % colors.length]
  })
  const relationshipOptions = ref([{label:'陌生',value:'0',icon:'0'},{label:'知道',value:'1',icon:'1'},{label:'熟悉',value:'2',icon:'2'},{label:'支持',value:'3',icon:'3'}])
  // 五项指标定义与新增/编辑表单共用同一份口径（utils/customer-fields.js），避免两处标签漂移
  const indicatorDefs = ref(INDICATOR_FIELDS)
  const indicatorTotal = computed(() => sumIndicatorScore(indicatorForm))
  const invoiceThresholdMet = computed(() => (invoiceSummary.value.totalAmount||0) >= 100)
  const paymentThresholdMet = computed(() => (paymentSummary.value.totalAmount||0) >= 100)
  const bothThresholdMet = computed(() => invoiceThresholdMet.value && paymentThresholdMet.value)
  const isCore = computed(() => bothThresholdMet.value && indicatorTotal.value >= INDICATOR_CORE_THRESHOLD)
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
  // 【PR-11 首批 2026-09-19】客户统一作业页:保护剩余天数与最近跟进集中呈现(数据均已在上下文加载)
  const expireDaysLeft = computed(() => {
    const d = customer.value?.expireDate
    if (!d) return null
    return Math.ceil((new Date(d) - new Date()) / 86400000)
  })
  const latestLog = computed(() => {
    const list = (logs.value || []).slice()
      .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
    return list[0] || null
  })
  const notifyActionFailure = (error, fallback = '操作失败') => {
    const message = error?.response?.data?.message || error?.message || fallback
    ElMessage.error(message === 'Error' ? fallback : message)
  }

  const loadAll = async () => {
    try {
      const cRes = await getCustomer(customerId.value); customer.value = cRes.data
      const [lRes, invRes, summRes, payRes, paySummRes, cRes2, indRes, shaRes, scoreRes] = await Promise.all([
        getCustomerLogs(customerId.value),
        canViewFinance.value ? getInvoices(customerId.value) : Promise.resolve({ data: [] }),
        canViewFinance.value ? getInvoiceSummary(customerId.value) : Promise.resolve({ data: { totalCount: 0, totalAmount: 0 } }),
        canViewFinance.value ? getPayments(customerId.value) : Promise.resolve({ data: [] }),
        canViewFinance.value ? getPaymentSummary(customerId.value) : Promise.resolve({ data: { totalCount: 0, totalAmount: 0 } }),
        canViewContacts.value ? getContacts(customerId.value) : Promise.resolve({ data: [] }),
        getIndicator(customerId.value), getShares(customerId.value), getScoreByCustomer(customerId.value),
      ])
      logs.value = lRes.data || []; invoices.value = invRes.data || []; invoiceSummary.value = summRes.data || {}
      payments.value = payRes.data || []; paymentSummary.value = paySummRes.data || {}; contacts.value = cRes2.data || []
      indicator.value = indRes.data; shares.value = shaRes.data || []; incentiveScore.value = scoreRes.data || 0
      if (indicator.value) {
        INDICATOR_FIELDS.forEach((f) => { indicatorForm[f.field] = indicator.value[f.field] || 0 })
      }
    } catch (e) { notifyActionFailure(e, '客户详情加载失败') } finally { loading.value = false }
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
  // 文件名三态判定(image/video/doc)统一在 utils/media-type.js，与激励上传页 fileType 同源
  const detectType = mediaTypeOf
  const fmtDate = (t) => { if (!t) return ''; const d = new Date(t); return d.getFullYear()+'-'+(d.getMonth()+1).toString().padStart(2,'0')+'-'+d.getDate().toString().padStart(2,'0') }

  const downloadIncentiveFile = async (f) => {
    if (!f?.id) return
    await refreshDownloadToken()
    const url = fileUrl(f.id)
    await downloadFile(url, { name: f.label || ('file-' + f.id) })
  }
  // 【修复 2026-08-14】下载 token 未就绪时不拼 token=（避免必然 403 的空 token 请求）
  const fileUrl = (id) => { const t = getDownloadToken(); return t ? '/api/file/download/' + id + '?token=' + t : '/api/file/download/' + id }
  const filePreviewVisible = ref(false), filePreviewUrl = ref(''), filePreviewType = ref('image'), filePreviewName = ref('')
  // 激励Tab文件预览:复用 FilePreview(图片放大/视频播放/文档下载);点文件行打开预览而非直接下载
  const previewIncentiveFile = async (f) => {
    if (!f?.id) return
    await refreshDownloadToken()
    filePreviewUrl.value = fileUrl(f.id)
    filePreviewType.value = f.type || detectType(f.label)
    filePreviewName.value = f.label
    filePreviewVisible.value = true
  }
  // 【修复 2026-10-04】返回是否保存成功(不再吞错让调用方误判成功):LogsTab 据此决定是否继续建任务/弹成功;失败时错误提示仍在此处统一弹出
  const submitLog = async () => { if(!newLog.value.trim()) return false; try { await addCustomerLog(customerId.value,{content:newLog.value}); ElMessage.success('已记录'); newLog.value=''; logs.value = (await getCustomerLogs(customerId.value)).data||[]; return true } catch(e) { notifyActionFailure(e, '跟进日志保存失败'); return false } }
  const handleExtend = async () => { try { await ElMessageBox.confirm('确认申请延期？审批通过后保护期延长360天','申请延期'); await submitApproval({type:'extend',customerId:String(customer.value.id),actionAfterApprove:'保护期+360天'}); ElMessage.success('延期申请已提交') } catch(e) { if (e !== 'cancel' && e !== 'close') notifyActionFailure(e, '延期申请失败') } }
  const handleEdit = () => router.push(`/customer/edit/${customerId.value}`)
  // 【PR-18 台账状态机】确认/作废后统一刷新(金额汇总已排除 voided, 客户最近开票日/指标联动)
  // 【重构 2026-10-04】开票/回款的新增、删除四处 handler 原各自内联近似复制的 Promise.all 刷新块,统一改调本函数(取刷新超集,最终状态一致)
  const reloadFinance = async () => { const [invRes,summRes,payRes,paySummRes,indRes,cRes] = await Promise.all([getInvoices(customerId.value),getInvoiceSummary(customerId.value),getPayments(customerId.value),getPaymentSummary(customerId.value),getIndicator(customerId.value),getCustomer(customerId.value)]); invoices.value=invRes.data||[]; invoiceSummary.value=summRes.data||{}; payments.value=payRes.data||[]; paymentSummary.value=paySummRes.data||{}; indicator.value=indRes.data; customer.value=cRes.data }
  const handleAddInvoice = async () => { if(!invoiceForm.invoiceDate||!invoiceForm.invoiceNumber||!invoiceForm.amount) return ElMessage.warning('请填写完整'); try { await addInvoice(customerId.value,{...invoiceForm}); ElMessage.success('已添加'); showInvoiceDialog.value=false; Object.assign(invoiceForm,{invoiceDate:'',invoiceNumber:'',invoiceType:'增值税专票',invoiceCompany:'',amount:null}); await reloadFinance() } catch(e) { notifyActionFailure(e, '开票记录保存失败') } }
  const handleDeleteInvoice = async (row) => { try { const { value } = await ElMessageBox.prompt('删除将留痕(审计快照+逻辑删),保护期自动回算。请填写删除原因(必填)', '删除开票记录', { confirmButtonText:'删除', cancelButtonText:'取消', inputType:'textarea', inputValidator:(v)=> (v&&v.trim()) ? true : '删除原因必填' }); await deleteInvoice(customerId.value,row.id,value.trim()); ElMessage.success('已删除(已留痕)'); await reloadFinance() } catch(e) { if (e !== 'cancel' && e !== 'close') notifyActionFailure(e, '开票记录删除失败') } }
  const handleAddPayment = async () => { if(!paymentForm.paymentDate||!paymentForm.paymentAmount) return ElMessage.warning('请填写完整'); try { await addPayment(customerId.value,{...paymentForm}); ElMessage.success('已添加'); showPaymentDialog.value=false; Object.assign(paymentForm,{paymentDate:'',paymentAmount:null,paymentMethod:'',remark:''}); await reloadFinance() } catch(e) { notifyActionFailure(e, '回款记录保存失败') } }
  const handleDeletePayment = async (row) => { try { const { value } = await ElMessageBox.prompt('删除将留痕(审计快照+逻辑删)。请填写删除原因(必填)', '删除回款记录', { confirmButtonText:'删除', cancelButtonText:'取消', inputType:'textarea', inputValidator:(v)=> (v&&v.trim()) ? true : '删除原因必填' }); await deletePayment(customerId.value,row.id,value.trim()); ElMessage.success('已删除(已留痕)'); await reloadFinance() } catch(e) { if (e !== 'cancel' && e !== 'close') notifyActionFailure(e, '回款记录删除失败') } }
  const handleConfirmInvoice = async (row) => { try { await confirmInvoice(customerId.value,row.id); ElMessage.success('已确认'); await reloadFinance() } catch(e) { notifyActionFailure(e, '开票记录确认失败') } }
  const handleVoidInvoice = async (row) => { try { const { value } = await ElMessageBox.prompt('作废后该笔不再计入统计/保护期, 记录保留留痕。请填写作废原因(必填)', '作废开票记录', { confirmButtonText:'作废', cancelButtonText:'取消', inputType:'textarea', inputValidator:(v)=> (v&&v.trim()) ? true : '作废原因必填' }); await voidInvoice(customerId.value,row.id,value.trim()); ElMessage.success('已作废'); await reloadFinance() } catch(e) { if (e !== 'cancel' && e !== 'close') notifyActionFailure(e, '开票记录作废失败') } }
  const handleConfirmPayment = async (row) => { try { await confirmPayment(customerId.value,row.id); ElMessage.success('已确认'); await reloadFinance() } catch(e) { notifyActionFailure(e, '回款确认失败') } }
  const handleVoidPayment = async (row) => { try { const { value } = await ElMessageBox.prompt('作废后该笔不再计入统计, 记录保留留痕。请填写作废原因(必填)', '作废回款记录', { confirmButtonText:'作废', cancelButtonText:'取消', inputType:'textarea', inputValidator:(v)=> (v&&v.trim()) ? true : '作废原因必填' }); await voidPayment(customerId.value,row.id,value.trim()); ElMessage.success('已作废'); await reloadFinance() } catch(e) { if (e !== 'cancel' && e !== 'close') notifyActionFailure(e, '回款作废失败') } }
  const openEditContact = (row) => { editingContact.value=row; Object.assign(contactForm,{name:row.name,position:row.position,phone:row.phone,remark:row.remark}); showContactDialog.value=true }
  const handleSaveContact = async () => { if(!contactForm.name) return ElMessage.warning('请填写姓名'); try { editingContact.value ? await updateContact(customerId.value,editingContact.value.id,{...contactForm}) : await addContact(customerId.value,{...contactForm}); ElMessage.success(editingContact.value?'已更新':'已添加'); showContactDialog.value=false; editingContact.value=null; Object.assign(contactForm,{name:'',position:'',phone:'',remark:''}); contacts.value = (await getContacts(customerId.value)).data||[] } catch(e) { notifyActionFailure(e, '联系人保存失败') } }
  const handleDeleteContact = async (row) => { try { await ElMessageBox.confirm('确认删除?','提示'); await deleteContact(customerId.value,row.id); ElMessage.success('已删除'); contacts.value = (await getContacts(customerId.value)).data||[] } catch(e) { if (e !== 'cancel' && e !== 'close') notifyActionFailure(e, '联系人删除失败') } }
  const handleSaveIndicator = async () => { try { await saveIndicator(customerId.value,{...indicatorForm}); ElMessage.success('已保存'); indicator.value = (await getIndicator(customerId.value)).data } catch(e) { notifyActionFailure(e, '核心指标保存失败') } }
  const selectRelationship = async (score) => { try { await saveRelationship(customerId.value,score); indicator.value = (await getIndicator(customerId.value)).data } catch(e) { notifyActionFailure(e, '客情关系保存失败') } }
  const handleAddShare = async () => { if(!shareUserIds.value.length) return; try { await addShares(customerId.value,shareUserIds.value); ElMessage.success('已分享'); shareUserIds.value=[]; showShareDialog.value=false; shares.value = (await getShares(customerId.value)).data||[] } catch(e) { notifyActionFailure(e, '客户分享失败') } }
  // 【PR-09 残余】客户转移入口: 选接收人→提交 transfer 审批(审批通过后负责人/部门/转移日由后端落库)
  const openTransfer = () => { if (!allUsers.value.length) loadUsers(); showTransferDialog.value = true }
  const confirmTransfer = async () => {
    if (!transferUserId.value) return ElMessage.warning('请选择接收人')
    if (customer.value && String(transferUserId.value) === String(customer.value.ownerId)) return ElMessage.warning('接收人不能是当前负责人')
    try {
      await submitApproval({
        type: 'transfer',
        customerId: String(customerId.value),
        actionAfterApprove: '客户负责人转移',
        extraData: JSON.stringify({ newOwnerId: transferUserId.value })
      })
      ElMessage.success('转移申请已提交,审批通过后生效')
      showTransferDialog.value = false; transferUserId.value = null
    } catch (e) { notifyActionFailure(e, '转移申请提交失败') }
  }
  const handleRemoveShare = async (s) => { try { await removeShare(customerId.value,s.sharedToUserId); shares.value = (await getShares(customerId.value)).data||[] } catch(e) { notifyActionFailure(e, '取消分享失败') } }

  const loadAllFn = loadAll
  const loadDictsFn = loadDicts
  const loadUsersFn = loadUsers

  return {
    customerId, route, router, userStore, isAdmin, readonly, readonlyTag,
    loading, activeTab, customer, logs, newLog, invoices, invoiceSummary, payments, paymentSummary, canViewFinance, canViewContacts, expireDaysLeft, latestLog,
    contacts, indicator, shares, allUsers, incentiveScore, incentiveRecords, catMap,
    showInvoiceDialog, showPaymentDialog, showContactDialog, showShareDialog, shareUserIds, editingContact,
    showTransferDialog, transferUserId,
    invoiceForm, paymentForm, contactForm, indicatorForm,
    avatarColor, relationshipOptions, indicatorDefs, indicatorTotal, invoiceThresholdMet,
    paymentThresholdMet, bothThresholdMet, isCore, totalScore, expireLevel, statusLabel,
    loadAll: loadAllFn, loadDicts: loadDictsFn, loadUsers: loadUsersFn, loadIncentiveRecords,
    getMainCategory, getSubCategory, parentCodeOf, detailLabelOf, isTrialRec, scoreColor,
    parseId, getFiles, detectType, fmtDate, downloadIncentiveFile,
    filePreviewVisible, filePreviewUrl, filePreviewType, filePreviewName, previewIncentiveFile,
    submitLog, handleExtend, handleEdit, openTransfer, confirmTransfer,
    handleAddInvoice, handleDeleteInvoice, handleAddPayment, handleDeletePayment,
    handleConfirmInvoice, handleVoidInvoice, handleConfirmPayment, handleVoidPayment, reloadFinance,
    openEditContact, handleSaveContact, handleDeleteContact,
    handleSaveIndicator, selectRelationship, handleAddShare, handleRemoveShare,
  }
}
