<template>
  <div class="customer-add">
    <div class="page-header">
      <h2>{{ isEdit ? '编辑客户' : '新增客户' }}</h2>
      <el-button link @click="goBack">← 返回</el-button>
    </div>

    <div class="form-card">
      <div class="card-title"><el-icon><InfoFilled /></el-icon> 客户信息登记</div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="add-form">
        <!-- 客户全称 -->
        <el-form-item label="订货客户全称" prop="customerName">
          <el-input v-model="form.customerName" placeholder="请输入订货客户全称" />
        </el-form-item>

        <!-- 开票名称 -->
        <el-form-item label="开票名称">
          <el-input v-model="form.invoiceName" placeholder="请输入开票名称" />
        </el-form-item>

        <!-- 行业 / 开发类型 / 大区 / 办事处 -->
        <div class="form-grid col-4">
          <el-form-item label="行业">
            <el-select v-model="form.industry" placeholder="请选择行业">
              <el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" />
            </el-select>
          </el-form-item>
          <el-form-item label="开发类型">
            <el-select v-model="form.devType" placeholder="请选择开发类型">
              <el-option v-for="t in devTypeOptions" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属大区" prop="regionId">
            <el-select v-model="form.regionId" placeholder="请选择大区" @change="onRegionChange">
              <el-option v-for="d in regions" :key="d.id" :label="d.deptName" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属办事处" prop="officeId">
            <el-select v-model="form.officeId" :placeholder="form.regionId ? '请选择办事处' : '请先选择大区'" :disabled="!form.regionId">
              <el-option v-for="o in filteredOffices" :key="o.id" :label="o.deptName" :value="o.id" />
            </el-select>
          </el-form-item>
        </div>

        <!-- 负责人 / 集团 / 分公司 / 单位 -->
        <div class="form-grid col-4">
          <el-form-item label="第一负责人">
            <el-select v-model="form.ownerId" placeholder="请选择负责人" filterable>
              <el-option v-for="u in users" :key="u.id" :label="u.realName" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属集团">
            <el-input v-model="form.groupName" placeholder="例如：陕煤集团" />
          </el-form-item>
          <el-form-item label="所属分公司">
            <el-input v-model="form.branchCompany" placeholder="例如：榆林化学" />
          </el-form-item>
          <el-form-item label="所属单位">
            <el-input v-model="form.unitName" placeholder="例如：选煤厂" />
          </el-form-item>
        </div>

        <!-- 产能 + 省市区 + 厂址 -->
        <div class="form-grid col-3">
          <el-form-item label="产能（万吨）">
            <el-input v-model="form.capacity" placeholder="请输入数字" />
          </el-form-item>
          <el-form-item label="省/市/区">
            <el-cascader
              v-model="regionValue"
              :options="regionData"
              :props="{ expandTrigger: 'hover' }"
              placeholder="请选择省/市/区"
              clearable
              filterable
              style="width: 100%"
              @change="handleRegionChange"
            />
          </el-form-item>
          <el-form-item label="厂址">
            <el-input v-model="form.address" placeholder="如：榆神工业区" />
          </el-form-item>
        </div>

        <!-- 注册日 / 开票日 / 保护期 -->
        <div class="form-grid col-3">
          <el-form-item label="注册日">
            <el-date-picker v-model="form.registerDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
          </el-form-item>
          <el-form-item label="最近开票日">
            <el-date-picker v-model="form.lastInvoiceDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
          </el-form-item>
          <el-form-item label="保护期">
            <div class="protect-hint"><el-icon><Timer /></el-icon> 注册日/开票日 + 360天</div>
          </el-form-item>
        </div>

        <!-- 联系人 -->
        <div class="contacts-section">
          <div class="section-header">
            <span class="section-title"><el-icon><User /></el-icon> 联系人</span>
            <el-button type="primary" link @click="addContactRow">+ 添加联系人</el-button>
          </div>
          <div v-for="(c, idx) in contacts" :key="idx" class="contact-row">
            <el-input v-model="c.name" placeholder="姓名" style="width:120px" />
            <el-input v-model="c.position" placeholder="职位" style="width:120px" />
            <el-input v-model="c.phone" placeholder="电话" style="width:160px" />
            <el-input v-model="c.remark" placeholder="备注" style="flex:1" />
            <el-button type="danger" link @click="contacts.splice(idx, 1)">删除</el-button>
          </div>
          <div v-if="!contacts.length" class="empty-hint">暂无联系人，点击上方添加</div>
        </div>

        <!-- 备注 -->
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="备注信息（选填）" />
        </el-form-item>

        <!-- 按钮 -->
        <div class="form-actions">
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :loading="loading" @click="handleSubmit">
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
import { ElMessage } from 'element-plus'
import { addCustomer, getCustomer, updateCustomer, getSimpleUserList, getContacts, addContact } from '@/api/customer'
import { getDeptList, getDictData } from '@/api/system'
import regionData from '@/utils/region-data'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const route = useRoute()
const formRef = ref(null)
const loading = ref(false)
const regions = ref([])
const offices = ref([])
const users = ref([])
// 办事处联动: 只显示所选大区下面的办事处(parentId===regionId); 未选大区时为空
const filteredOffices = computed(() => {
  if (!form.regionId) return []
  return offices.value.filter(o => o.parentId === form.regionId)
})
const regionValue = ref([])
const industryOptions = ref([])
const devTypeOptions = ref([])
const isEdit = computed(() => !!route.params.id)
// 来源(如从公海池进入 from=sea),保存/返回时透传,保证详情页返回按钮回到公海池
const detailQuery = computed(() => (route.query.from ? { from: route.query.from } : {}))
const contacts = ref([])

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
  regionId: [{ required: true, message: '请选择所属大区', trigger: 'change' }],
  officeId: [{ required: true, message: '请选择所属办事处', trigger: 'change' }]
}

// 切换大区时, 若已选办事处不属于新大区则清空
const onRegionChange = () => {
  if (form.officeId && !filteredOffices.value.some(o => o.id === form.officeId)) {
    form.officeId = null
  }
}

const handleRegionChange = (val) => {
  form.province = val[0] || ''
  form.city = val[1] || ''
  form.district = val[2] || ''
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
  // 新增模式: 第一负责人默认为当前登录用户
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
  } catch (e) { devTypeOptions.value = ['成熟','成熟创新','突破'] }

  // 编辑模式: 加载已有数据
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

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (isEdit.value) {
      await updateCustomer(route.params.id, form)
      // 保存联系人（编辑模式下新增的联系人没有id）
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
      // 保存联系人
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
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 { font-size: 20px; font-weight: 600; }

.form-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  padding: 24px 32px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 24px;
}
.card-title .el-icon { color: #e6a23c; }

/* 表单网格布局 */
.form-grid {
  display: grid;
  gap: 0 20px;
}
.form-grid.col-2 { grid-template-columns: 1fr 1fr; }
.form-grid.col-3 { grid-template-columns: 1fr 1fr 1fr; }
.form-grid.col-4 { grid-template-columns: 1fr 1fr 1fr 1fr; }

.form-grid :deep(.el-select),
.form-grid :deep(.el-cascader) {
  width: 100%;
}

.protect-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #409eff;
  font-size: 14px;
  height: 32px;
}

.form-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #ebeef5;
}

/* 联系人 */
.contacts-section {
  margin-bottom: 18px;
  padding: 16px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}
.contact-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.empty-hint {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 8px 0;
}
</style>
