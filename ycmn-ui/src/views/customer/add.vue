<template>
  <div class="customer-add">
    <div class="breadcrumb-bar">
      <el-button link @click="goBack" class="back-btn">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <span class="page-subtitle">{{ isEdit ? '编辑客户信息' : '新增客户' }}</span>
    </div>

    <div class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="add-form">

        <!-- ======== 基本信息 ======== -->
        <div class="section-block">
          <div class="section-head">
            <span class="section-icon"><el-icon><Document /></el-icon></span>
            <span class="section-label">基本信息</span>
          </div>

          <el-form-item label="订货客户全称" prop="customerName" class="full-width">
            <el-input v-model="form.customerName" placeholder="请输入订货客户全称" size="large" @blur="checkDup" />
            <div v-if="dupInfo.exists" class="dup-warning">
              <el-icon><WarningFilled /></el-icon>
              此客户已在 {{ dupInfo.createdAt }} 由 {{ dupInfo.createdBy }} 创建，请更换名称
            </div>
          </el-form-item>

          <div class="form-row two">
            <el-form-item label="开票名称">
              <el-input v-model="form.invoiceName" placeholder="请输入开票名称" />
            </el-form-item>
            <el-form-item label="产能（万吨）">
              <el-input v-model="form.capacity" placeholder="请输入数字" />
            </el-form-item>
          </div>

          <div class="form-row two">
            <el-form-item label="所属集团">
              <el-input v-model="form.groupName" placeholder="例如：陕煤集团" />
            </el-form-item>
            <el-form-item label="所属分公司">
              <el-input v-model="form.branchCompany" placeholder="例如：榆林化学" />
            </el-form-item>
          </div>

          <div class="form-row two">
            <el-form-item label="所属单位">
              <el-input v-model="form.unitName" placeholder="例如：选煤厂" />
            </el-form-item>
          </div>
        </div>

        <!-- ======== 业务分类 ======== -->
        <div class="section-block">
          <div class="section-head">
            <span class="section-icon accent"><el-icon><PriceTag /></el-icon></span>
            <span class="section-label">业务分类</span>
            <span class="section-tip">必填项，决定客户归属的 Tab 分类</span>
          </div>

          <div class="form-row two">
            <el-form-item label="开发类型" prop="devType" required>
              <el-select v-model="form.devType" placeholder="请选择开发类型" size="large">
                <el-option v-for="t in devTypeOptions" :key="t" :label="t" :value="t">
                  <span>{{ t }}</span>
                  <span class="opt-tag" :style="devTypeTagStyle(t)">{{ devTypeTagText(t) }}</span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="行业" prop="industry" required>
              <el-select v-model="form.industry" placeholder="请选择行业" size="large">
                <el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" />
              </el-select>
            </el-form-item>
          </div>
        </div>

        <!-- ======== 归属与负责人 ======== -->
        <div class="section-block">
          <div class="section-head">
            <span class="section-icon warn"><el-icon><MapLocation /></el-icon></span>
            <span class="section-label">归属与负责人</span>
          </div>

          <div class="form-row two">
            <el-form-item label="所属大区" prop="regionId">
              <el-select v-model="form.regionId" placeholder="请选择大区" size="large" @change="onRegionChange">
                <el-option v-for="d in regions" :key="d.id" :label="d.deptName" :value="d.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="所属办事处" prop="officeId">
              <el-select v-model="form.officeId" placeholder="请选择办事处" size="large" filterable :disabled="!form.regionId">
                <el-option v-for="o in filteredOffices" :key="o.id" :label="o.deptName" :value="o.id" />
              </el-select>
            </el-form-item>
          </div>

          <div class="form-row two">
            <el-form-item label="第一负责人" prop="ownerId" required>
              <el-select v-model="form.ownerId" placeholder="请选择负责人" size="large" filterable>
                <el-option v-for="u in users" :key="u.id" :label="u.realName" :value="u.id" />
              </el-select>
            </el-form-item>
          </div>
        </div>

        <!-- ======== 地址信息 ======== -->
        <div class="section-block">
          <div class="section-head">
            <span class="section-icon info"><el-icon><Location /></el-icon></span>
            <span class="section-label">地址信息</span>
          </div>

          <div class="form-row three">
            <el-form-item label="省/市/区">
              <el-cascader v-model="regionValue" :options="regionData" :props="{ expandTrigger: 'hover' }" placeholder="请选择省/市/区" clearable filterable style="width:100%" @change="handleRegionChange" />
            </el-form-item>
            <el-form-item label="厂址">
              <el-input v-model="form.address" placeholder="如：榆神工业区" />
            </el-form-item>
            <el-form-item label="保护期">
              <div class="protect-hint"><el-icon><Timer /></el-icon> 注册日/开票日 + 360天</div>
            </el-form-item>
          </div>

          <div class="form-row two">
            <el-form-item label="注册日">
              <el-date-picker v-model="form.registerDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
            <el-form-item label="最近开票日">
              <el-date-picker v-model="form.lastInvoiceDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </div>
        </div>

        <!-- ======== 联系人 ======== -->
        <div class="section-block">
          <div class="section-head">
            <span class="section-icon success"><el-icon><User /></el-icon></span>
            <span class="section-label">联系人</span>
            <el-button type="primary" link size="small" @click="addContactRow">
              <el-icon><Plus /></el-icon> 添加联系人
            </el-button>
          </div>
          <div v-if="contacts.length" class="contacts-list">
            <div v-for="(c, idx) in contacts" :key="idx" class="contact-row">
              <el-input v-model="c.name" placeholder="姓名" />
              <el-input v-model="c.position" placeholder="职位" />
              <el-input v-model="c.phone" placeholder="电话" />
              <el-input v-model="c.remark" placeholder="备注" />
              <el-button type="danger" link size="small" @click="contacts.splice(idx, 1)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <div v-else class="empty-hint">暂无联系人，点击上方按钮添加</div>
        </div>

        <!-- ======== 备注 ======== -->
        <el-form-item label="备注" class="remark-field">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="备注信息（选填）" />
        </el-form-item>

        <!-- ======== 提交按钮 ======== -->
        <div class="form-actions">
          <el-button size="large" @click="goBack">取消</el-button>
          <el-button type="primary" size="large" :loading="loading" @click="handleSubmit">
            <el-icon><Document /></el-icon> {{ isEdit ? '更新客户' : '保存客户' }}
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { addCustomer, getCustomer, updateCustomer, getSimpleUserList, getContacts, addContact } from '@/api/customer'
import { getDeptList, getDictData } from '@/api/system'
import regionData from '@/utils/region-data'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const route = useRoute()
const formRef = ref(null)
const dupInfo = reactive({ exists: false, createdBy: "", createdAt: "" })
const loading = ref(false)
const regions = ref([])
const offices = ref([])
const users = ref([])
const filteredOffices = computed(() => {
  if (!form.regionId) return []
  return offices.value.filter(o => o.parentId === form.regionId)
})
const regionValue = ref([])
const industryOptions = ref([])
const devTypeOptions = ref([])
const isEdit = computed(() => !!route.params.id)
const detailQuery = computed(() => (route.query.from ? { from: route.query.from } : {}))
const contacts = ref([])

const devTypeTagStyle = (t) => {
  const m = { '成熟创新': 'background:#ecf5ff;color:#409eff', '突破开拓': 'background:#fdf6ec;color:#e6a23c', '铺垫宣传': 'background:#f0f9eb;color:#67c23a' }
  return m[t] || 'background:#f5f7fa;color:#909399'
}
const devTypeTagText = (t) => ({ '成熟创新': '创新', '突破开拓': '突破', '铺垫宣传': '铺垫' }[t] || '')

const addContactRow = () => {
  contacts.value.push({ name: '', position: '', phone: '', remark: '' })
}

const form = reactive({
  customerName: '',
  invoiceName: '',
  industry: '',
  devType: '',
  officeId: null,
  regionId: null,
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

onMounted(async () => {
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
  } catch (e) { industryOptions.value = ['煤矿','化工','电力','洗煤厂','有色','其他'] }
  try {
    const res = await getDictData('dev_type')
    devTypeOptions.value = (res.data || []).map(d => d.dictValue)
  } catch (e) { devTypeOptions.value = ['成熟创新','突破开拓','铺垫宣传'] }

  if (isEdit.value) {
    try {
      const res = await getCustomer(route.params.id)
      const c = res.data
      Object.keys(form).forEach(k => { if (c[k] !== undefined && c[k] !== null) form[k] = c[k] })
      if (c.province) regionValue.value = [c.province, c.city, c.district].filter(Boolean)
    } catch (e) {}
    try {
      const res = await getContacts(route.params.id)
      contacts.value = (res.data || []).map(c => ({ id: c.id, name: c.name, position: c.position, phone: c.phone, remark: c.remark }))
    } catch (e) {}
  }
})

const goBack = () => {
  if (isEdit.value) {
    router.push({ path: `/customer/detail/${route.params.id}`, query: detailQuery.value })
  } else {
    router.push('/customer')
  }
}

const checkDup = async () => {
  const n = form.customerName?.trim()
  if (!n || n.length < 2) { dupInfo.exists = false; return }
  try {
    const res = await request.get('/customer/check-name', { params: { name: n } })
    if (res?.data) Object.assign(dupInfo, res.data)
  } catch(e) { dupInfo.exists = false }
}

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
</script>

<style scoped>
.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.back-btn { font-size: 14px; color: #606266; }
.page-subtitle { font-size: 13px; color: #909399; }

.form-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e8ecf1;
  padding: 32px 40px;
  box-shadow: 0 1px 4px rgba(0,0,0,.03);
}

/* ===== Sections ===== */
.section-block {
  margin-bottom: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid #f2f3f5;
}
.section-block:last-of-type { border-bottom: none; margin-bottom: 12px; }

.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
}
.section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px; height: 30px;
  border-radius: 8px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 16px;
}
.section-icon.accent { background: #fef0f0; color: #f56c6c; }
.section-icon.warn { background: #fdf6ec; color: #e6a23c; }
.section-icon.info { background: #ecf5ff; color: #409eff; }
.section-icon.success { background: #f0f9eb; color: #67c23a; }

.section-label {
  font-size: 15px;
  font-weight: 700;
  color: #1f2329;
}
.section-tip {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

/* ===== Form Rows ===== */
.form-row {
  display: grid;
  gap: 0 24px;
  margin-bottom: 4px;
}
.form-row.two { grid-template-columns: 1fr 1fr; }
.form-row.three { grid-template-columns: 1fr 1fr 1fr; }
.full-width { grid-column: 1 / -1; }

.form-row :deep(.el-select),
.form-row :deep(.el-cascader) { width: 100%; }

:deep(.el-form-item__label) {
  font-size: 13px;
  color: #4e5969;
  font-weight: 600;
  padding-bottom: 6px;
}
:deep(.el-input__wrapper) { box-shadow: 0 0 0 1px #e5e6eb inset; }
:deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px #c9cdd4 inset; }

/* ===== Dup Warning ===== */
.dup-warning {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #f56c6c;
  font-size: 12px;
  margin-top: 6px;
  padding: 8px 12px;
  background: #fef0f0;
  border-radius: 6px;
}

/* ===== DevType Option Tags ===== */
.opt-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  margin-left: 8px;
}

/* ===== Protect Hint ===== */
.protect-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #409eff;
  font-size: 13px;
  height: 32px;
  padding: 0 12px;
  background: #ecf5ff;
  border-radius: 6px;
}

/* ===== Contacts ===== */
.contacts-list { margin-top: 4px; }
.contact-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 10px 12px;
  background: #fafbfc;
  border-radius: 8px;
  border: 1px solid #f0f1f3;
}
.contact-row .el-input { width: 130px; }
.contact-row .el-input:last-of-type { flex: 1; }
.empty-hint {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
  background: #fafbfc;
  border-radius: 8px;
}

.remark-field {
  margin-top: 8px;
}

/* ===== Actions ===== */
.form-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #f2f3f5;
}
</style>
