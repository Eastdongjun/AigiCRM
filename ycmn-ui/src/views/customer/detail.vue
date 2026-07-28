<template>
  <div class="customer-detail" v-if="customer" v-loading="loading">
    <!-- 导航行 -->
    <div class="back-row">
      <span class="back-link" @click="$router.push(readonly ? '/sea' : '/customer')">
        <el-icon><ArrowLeft /></el-icon> ← {{ readonly ? '返回公海池' : '返回客户列表' }}
      </span>
      <div class="action-btns" v-if="!readonly">
        <el-button @click="handleEdit"><el-icon><Edit /></el-icon> 编辑</el-button>
        <el-button @click="showShareDialog = true"><el-icon><Share /></el-icon> 分享</el-button>
      </div>
      <el-tag v-else type="info" size="large">只读模式 · 公海客户</el-tag>
    </div>

    <!-- 头部卡片 -->
    <div class="header-card">
      <div class="header-card-left">
        <div class="header-avatar">{{ customer.customerName?.charAt(0) }}</div>
        <div class="header-info">
          <h2>{{ customer.customerName }}</h2>
          <div class="header-tags">
            {{ customer.industry }} · {{ customer.devType }}
            <template v-if="customer.groupName"> · {{ customer.groupName }}</template>
            · {{ customer.ownerName }}
          </div>
        </div>
      </div>
      <div class="header-card-right">
        <span class="core-badge" :class="isCore ? 'core' : 'non-core'">
          <el-icon><Medal /></el-icon> {{ isCore ? '核心客户' : '非核心客户' }}
        </span>
        <div class="due-block">
          <div class="due-date">{{ customer.expireDate }}</div>
          <div class="due-tag" :class="'due-' + expireLevel">
            <el-icon><Warning /></el-icon> {{ statusLabel }}
          </div>
        </div>
        <span v-if="!readonly" class="btn-edit-header" @click="handleEdit"><el-icon><Edit /></el-icon> 编辑</span>
      </div>
    </div>

    <!-- 学分栏 -->
    <div class="stats-row">
      <div class="stats-item">
        <el-icon color="#e6a23c"><Trophy /></el-icon>
        <span class="stats-label">激励学分</span>
        <span class="stats-value">{{ incentiveScore }}</span>
      </div>
      <div class="stats-item">
        <el-icon color="#f56c6c"><StarFilled /></el-icon>
        <span class="stats-label">客情关系</span>
        <span class="stats-value">{{ indicator?.relationshipScore || 0 }}</span>
      </div>
      <div class="stats-item">
        <el-icon color="#409eff"><TrendCharts /></el-icon>
        <span class="stats-label">综合得分</span>
        <span class="stats-value">{{ totalScore }}</span>
      </div>
      <span class="stats-desc">四项激励累计 + 客情关系评分</span>
    </div>

    <!-- 第一行: 基本信息 + 组织信息 -->
    <div class="grid-row">
      <div class="card">
        <div class="card-title"><el-icon class="card-icon"><InfoFilled /></el-icon> 基本信息</div>
        <div class="info-table">
          <div class="info-item"><span class="info-label">行业</span><span class="info-value">{{ customer.industry }}</span></div>
          <div class="info-item"><span class="info-label">开发类型</span><span class="info-value">{{ customer.devType }}</span></div>
          <div class="info-item"><span class="info-label">所属大区</span><span class="info-value">{{ customer.regionName }}</span></div>
          <div class="info-item"><span class="info-label">所属办事处</span><span class="info-value">{{ customer.officeName || '—' }}</span></div>
          <div class="info-item"><span class="info-label">第一负责人</span><span class="info-value">{{ customer.ownerName }}</span></div>
          <div class="info-item last"><span class="info-label">产能</span><span class="info-value">{{ customer.capacity || '—' }}</span></div>
        </div>
      </div>
      <div class="card">
        <div class="card-title"><el-icon class="card-icon"><OfficeBuilding /></el-icon> 组织信息</div>
        <div class="info-table">
          <div class="info-item"><span class="info-label">所属集团</span><span class="info-value">{{ customer.groupName || '—' }}</span></div>
          <div class="info-item"><span class="info-label">所属分公司</span><span class="info-value">{{ customer.branchCompany || '—' }}</span></div>
          <div class="info-item"><span class="info-label">所属单位</span><span class="info-value">{{ customer.unitName || '—' }}</span></div>
          <div class="info-item"><span class="info-label">省市县</span><span class="info-value">{{ customer.province }}{{ customer.city }}{{ customer.district }}</span></div>
          <div class="info-item last"><span class="info-label">厂址</span><span class="info-value">{{ customer.address || '—' }}</span></div>
        </div>
      </div>
    </div>

    <!-- 联系人 -->
    <div class="card" style="margin-bottom:16px">
      <div class="card-header">
        <div class="card-title"><el-icon class="card-icon"><User /></el-icon> 联系人</div>
        <el-button v-if="!readonly" size="small" type="primary" @click="editingContact = null; Object.assign(contactForm, { name: '', position: '', phone: '', remark: '' }); showContactDialog = true">+ 添加</el-button>
      </div>
      <el-table :data="contacts" stripe size="small" v-if="contacts.length">
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="position" label="职位" width="100" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column prop="remark" label="备注" min-width="120" />
        <el-table-column v-if="!readonly" label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditContact(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteContact(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else style="color:#c0c4cc;text-align:center;padding:16px;font-size:13px">暂无联系人</div>
    </div>

    <!-- 第二行: 保护期状态 + 客情关系深度 -->
    <div class="grid-row">
      <div class="card">
        <div class="card-title"><el-icon class="card-icon"><Timer /></el-icon> 保护期状态</div>
        <div class="protect-grid">
          <div class="protect-col">
            <div class="protect-label">注册日</div>
            <div class="protect-value">{{ customer.registerDate }}</div>
          </div>
          <div class="protect-col">
            <div class="protect-label">最近开票</div>
            <div class="protect-value">{{ customer.lastInvoiceDate || '—' }}</div>
          </div>
          <div class="protect-col">
            <div class="protect-label">到期日 <span class="highlight">(360天)</span></div>
            <div class="protect-value expire" :class="'expire-' + expireLevel">{{ customer.expireDate }}</div>
          </div>
          <div class="protect-col">
            <div class="protect-label">状态</div>
            <div class="protect-status-text" :class="'status-' + expireLevel">
              <el-icon><Warning /></el-icon> {{ statusLabel }}
            </div>
          </div>
        </div>
        <div class="protect-action" v-if="!readonly">
          <el-button @click="handleExtend"><el-icon><Calendar /></el-icon> 申请延期</el-button>
        </div>
      </div>
      <div class="card">
        <div class="card-title"><el-icon class="card-icon" style="color:#f56c6c"><StarFilled /></el-icon> 客情 · 商务关系深度</div>
        <div class="rel-tags">
          <div
            v-for="opt in relationshipOptions"
            :key="opt.value"
            class="rel-tag"
            :class="{ selected: (indicator?.relationshipScore || 0) == opt.value }"
            @click="!readonly && selectRelationship(parseInt(opt.value))"
          >
            <span class="rel-icon">{{ opt.icon }}</span>
            {{ opt.label }} · {{ opt.value }}分
          </div>
        </div>
      </div>
    </div>

    <!-- 第三行: 开票记录 + 回款记录 -->
    <div class="grid-row">
      <!-- 开票记录 -->
      <div class="card">
        <div class="card-header">
          <div class="card-title"><el-icon class="card-icon"><Document /></el-icon> 开票记录</div>
        </div>
        <div class="invoice-summary">
          <span>累计开票 <b>{{ invoiceSummary.totalCount }}</b> 次</span>
          <span>开票总额 <b class="amount-highlight">{{ invoiceSummary.totalAmount }}</b> 万</span>
          <span :class="invoiceThresholdMet ? 'threshold-met' : 'threshold-unmet'">{{ invoiceThresholdMet ? '✓ 达标' : '✗ 未达标' }}</span>
          <el-button v-if="!readonly" size="small" type="primary" @click="showInvoiceDialog = true">+ 录入开票</el-button>
        </div>
        <el-table :data="invoices" stripe size="small" style="margin-top:12px">
          <el-table-column prop="invoiceDate" label="开票日期" width="110" />
          <el-table-column prop="invoiceNumber" label="发票号码" width="140" />
          <el-table-column prop="invoiceType" label="发票类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" type="primary">{{ row.invoiceType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="invoiceCompany" label="开票单位" min-width="120" />
          <el-table-column prop="amount" label="金额(万)" width="90" align="right">
            <template #default="{ row }">{{ row.amount }}万</template>
          </el-table-column>
          <el-table-column v-if="!readonly" label="操作" width="60" align="center">
            <template #default="{ row }">
              <el-button type="danger" link size="small" @click="handleDeleteInvoice(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 回款记录 -->
      <div class="card">
        <div class="card-header">
          <div class="card-title"><el-icon class="card-icon" style="color:#67c23a"><Money /></el-icon> 回款记录</div>
        </div>
        <div class="invoice-summary">
          <span>累计回款 <b>{{ paymentSummary.totalCount }}</b> 次</span>
          <span>回款总额 <b class="amount-highlight">{{ paymentSummary.totalAmount }}</b> 万</span>
          <span :class="paymentThresholdMet ? 'threshold-met' : 'threshold-unmet'">{{ paymentThresholdMet ? '✓ 达标' : '✗ 未达标' }}</span>
          <el-button v-if="!readonly" size="small" type="primary" @click="showPaymentDialog = true">+ 录入回款</el-button>
        </div>
        <el-table :data="payments" stripe size="small" style="margin-top:12px">
          <el-table-column prop="paymentDate" label="回款日期" width="110" />
          <el-table-column prop="paymentAmount" label="金额(万)" width="90" align="right">
            <template #default="{ row }">{{ row.paymentAmount }}万</template>
          </el-table-column>
          <el-table-column prop="paymentMethod" label="回款方式" width="100" />
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column v-if="!readonly" label="操作" width="60" align="center">
            <template #default="{ row }">
              <el-button type="danger" link size="small" @click="handleDeletePayment(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 核心客户六项指标 -->
    <div class="card" style="margin-bottom:16px">
      <div class="card-header">
        <div class="card-title"><el-icon class="card-icon" style="color:#52c41a"><Aim /></el-icon> 核心客户六项指标</div>
      </div>
      <div class="indicator-list">
        <!-- 01 开票回款 硬性门槛 -->
        <div class="indicator-row">
          <span class="indicator-num">01</span>
          <span class="indicator-name">开票回款 · 硬性门槛 ≥ ¥100万</span>
          <span class="indicator-result" :class="bothThresholdMet ? 'met' : 'unmet'">
            {{ bothThresholdMet ? '✓ 已达标' : '✗ 未达标' }}
          </span>
        </div>
          <!-- 02-06 下拉指标 -->
          <div class="indicator-row" v-for="(ind, idx) in indicatorDefs" :key="ind.code">
            <span class="indicator-num">{{ String(idx + 2).padStart(2, '0') }}</span>
            <span class="indicator-name">{{ ind.label }}</span>
            <el-select
              v-model="indicatorForm[ind.field]"
              size="small"
              style="width: 160px"
              :disabled="readonly"
              @change="onIndicatorChange"
            >
              <el-option
                v-for="opt in ind.options"
                :key="opt.dictValue"
                :label="opt.dictValue + '分 · ' + opt.dictLabel"
                :value="parseInt(opt.dictValue)"
              />
            </el-select>
          </div>
        </div>
        <div class="indicator-footer">
          <div class="indicator-total">
            总分 <b class="total-score">{{ indicatorTotal }}</b> / 18
            <span v-if="indicatorTotal < 12" class="total-warn">⚠ 需调整定位</span>
          </div>
          <el-button v-if="!readonly" type="primary" size="small" @click="handleSaveIndicator">保存</el-button>
        </div>
    </div>

    <!-- 跟进日志 -->
    <div class="card">
      <div class="card-title"><el-icon class="card-icon"><ChatDotSquare /></el-icon> 跟进日志</div>
      <div class="log-input" v-if="!readonly">
        <el-input v-model="newLog" type="textarea" :rows="3" placeholder="请输入跟进记录..." resize="vertical" />
        <div class="log-submit">
          <el-button type="warning" @click="submitLog"><el-icon><Promotion /></el-icon> 记录</el-button>
        </div>
      </div>
      <div class="timeline">
        <div v-for="log in logs" :key="log.id" class="timeline-item">
          <div class="timeline-dot"></div>
          <div class="timeline-content">
            <div class="log-text">{{ log.content }}</div>
            <div class="log-meta">
              <span class="log-user">{{ log.userName }}</span>
              <span class="log-time">{{ log.createdAt }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 录入开票弹窗 -->
    <el-dialog v-model="showInvoiceDialog" title="录入开票" width="500px" :close-on-click-modal="false">
      <el-form :model="invoiceForm" label-width="80px">
        <el-form-item label="开票日期">
          <el-date-picker v-model="invoiceForm.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="发票号码">
          <el-input v-model="invoiceForm.invoiceNumber" />
        </el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="invoiceForm.invoiceType" style="width:100%">
            <el-option label="增值税专票" value="增值税专票" />
            <el-option label="增值税普票" value="增值税普票" />
          </el-select>
        </el-form-item>
        <el-form-item label="开票单位">
          <el-input v-model="invoiceForm.invoiceCompany" />
        </el-form-item>
        <el-form-item label="金额(万)">
          <el-input-number v-model="invoiceForm.amount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showInvoiceDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddInvoice">确认录入</el-button>
      </template>
    </el-dialog>

    <!-- 分享弹窗 -->
    <el-dialog v-model="showShareDialog" title="分享客户" width="500px">
      <p style="margin-bottom:12px;color:#999">选择用户后，该用户可查看此客户信息（只读）</p>
      <div v-if="shares.length" style="margin-bottom:16px">
        <div class="share-tag" v-for="s in shares" :key="s.id">
          {{ s.sharedToUserName }}
          <el-icon class="share-remove" @click="handleRemoveShare(s)"><Close /></el-icon>
        </div>
      </div>
      <el-select v-model="shareUserIds" multiple filterable placeholder="选择用户..." style="width:100%">
        <el-option v-for="u in allUsers" :key="u.id" :label="u.realName" :value="u.id" />
      </el-select>
      <template #footer>
        <el-button @click="showShareDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddShare">确认分享</el-button>
      </template>
    </el-dialog>

    <!-- 录入回款弹窗 -->
    <el-dialog v-model="showPaymentDialog" title="录入回款" width="500px" :close-on-click-modal="false">
      <el-form :model="paymentForm" label-width="80px">
        <el-form-item label="回款日期">
          <el-date-picker v-model="paymentForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="金额(万)">
          <el-input-number v-model="paymentForm.paymentAmount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="回款方式">
          <el-input v-model="paymentForm.paymentMethod" placeholder="如：银行转账" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="paymentForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPaymentDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddPayment">确认录入</el-button>
      </template>
    </el-dialog>

    <!-- 联系人弹窗 -->
    <el-dialog v-model="showContactDialog" :title="editingContact ? '编辑联系人' : '添加联系人'" width="480px" :close-on-click-modal="false">
      <el-form :model="contactForm" label-width="60px">
        <el-form-item label="姓名">
          <el-input v-model="contactForm.name" />
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="contactForm.position" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="contactForm.phone" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="contactForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showContactDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveContact">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCustomer, getCustomerLogs, addCustomerLog, updateCustomer,
  getInvoices, getInvoiceSummary, addInvoice, deleteInvoice,
  getPayments, getPaymentSummary, addPayment, deletePayment,
  getContacts, addContact, updateContact, deleteContact,
  getIndicator, saveIndicator, saveRelationship,
  getShares, addShares, removeShare, getSimpleUserList
} from '@/api/customer'
import { getDictData } from '@/api/system'
import { submitApproval } from '@/api/approval'
import { getScoreByCustomer } from '@/api/incentive'

const route = useRoute()
const router = useRouter()
const readonly = computed(() => route.query.from === 'sea')
const loading = ref(true)
const customer = ref(null)
const logs = ref([])
const newLog = ref('')
const invoices = ref([])
const invoiceSummary = ref({ totalCount: 0, totalAmount: 0, latestDate: null })
const payments = ref([])
const paymentSummary = ref({ totalCount: 0, totalAmount: 0, latestDate: null })
const contacts = ref([])
const indicator = ref(null)
const shares = ref([])
const allUsers = ref([])
const incentiveScore = ref(0)

// 弹窗
const showInvoiceDialog = ref(false)
const showPaymentDialog = ref(false)
const showContactDialog = ref(false)
const showShareDialog = ref(false)
const shareUserIds = ref([])
const editingContact = ref(null)

// 开票表单
const invoiceForm = reactive({
  invoiceDate: '', invoiceNumber: '', invoiceType: '增值税专票',
  invoiceCompany: '', amount: null
})

// 回款表单
const paymentForm = reactive({
  paymentDate: '', paymentAmount: null, paymentMethod: '', remark: ''
})

// 联系人表单
const contactForm = reactive({
  name: '', position: '', phone: '', remark: ''
})

// 指标表单
const indicatorForm = reactive({
  indicator02: 0, indicator03: 0, indicator04: 0, indicator05: 0, indicator06: 0
})

// 字典选项
const relationshipOptions = ref([
  { label: '商务少', value: '0', icon: '📊' },
  { label: '纯商务', value: '1', icon: '📋' },
  { label: '感情商务', value: '2', icon: '🤝' },
  { label: '多样性', value: '3', icon: '🌐' }
])

const indicatorDefs = ref([
  { code: 'core_indicator_02', field: 'indicator02', label: '高层 · 客情程度', options: [] },
  { code: 'core_indicator_03', field: 'indicator03', label: '渠道 · 编码数量', options: [] },
  { code: 'core_indicator_04', field: 'indicator04', label: '基层 · 分厂车间覆盖', options: [] },
  { code: 'core_indicator_05', field: 'indicator05', label: '产品 · 密封/润滑/防腐品类', options: [] },
  { code: 'core_indicator_06', field: 'indicator06', label: '项目 · 项目资金/类型', options: [] }
])

// 计算属性
const indicatorTotal = computed(() =>
  (indicatorForm.indicator02 || 0) + (indicatorForm.indicator03 || 0) +
  (indicatorForm.indicator04 || 0) + (indicatorForm.indicator05 || 0) +
  (indicatorForm.indicator06 || 0)
)

const invoiceThresholdMet = computed(() => (invoiceSummary.value.totalAmount || 0) >= 100)
const paymentThresholdMet = computed(() => (paymentSummary.value.totalAmount || 0) >= 100)
const bothThresholdMet = computed(() => invoiceThresholdMet.value && paymentThresholdMet.value)
const isCore = computed(() => bothThresholdMet.value && indicatorTotal.value >= 12)
const totalScore = computed(() => incentiveScore.value + (indicator.value?.relationshipScore || 0))

const expireLevel = computed(() => {
  if (!customer.value) return ''
  if (customer.value.protectionStatus === 'expired') return 'red'
  if (customer.value.protectionStatus === 'expiring') return 'orange'
  return 'blue'
})

const statusLabel = computed(() => {
  if (!customer.value) return ''
  const today = new Date()
  const expire = new Date(customer.value.expireDate)
  const days = Math.ceil((expire - today) / (1000 * 60 * 60 * 24))
  if (days < 0) return `已超期${Math.abs(days)}天`
  if (days <= 30) return `距到期${days}天`
  return '保护中'
})

// 加载数据
const customerId = computed(() => route.params.id)

const loadAll = async () => {
  loading.value = true
  try {
    const [cRes, logRes, invRes, summRes, payRes, paySummRes, contactRes, indRes, shareRes, scoreRes] = await Promise.all([
      getCustomer(customerId.value),
      getCustomerLogs(customerId.value),
      getInvoices(customerId.value),
      getInvoiceSummary(customerId.value),
      getPayments(customerId.value),
      getPaymentSummary(customerId.value),
      getContacts(customerId.value),
      getIndicator(customerId.value),
      getShares(customerId.value),
      getScoreByCustomer(customerId.value)
    ])
    customer.value = cRes.data
    logs.value = logRes.data || []
    invoices.value = invRes.data || []
    invoiceSummary.value = summRes.data || { totalCount: 0, totalAmount: 0, latestDate: null }
    payments.value = payRes.data || []
    paymentSummary.value = paySummRes.data || { totalCount: 0, totalAmount: 0, latestDate: null }
    contacts.value = contactRes.data || []
    indicator.value = indRes.data
    shares.value = shareRes.data || []
    incentiveScore.value = scoreRes.data || 0

    if (indRes.data) {
      indicatorForm.indicator02 = indRes.data.indicator02 || 0
      indicatorForm.indicator03 = indRes.data.indicator03 || 0
      indicatorForm.indicator04 = indRes.data.indicator04 || 0
      indicatorForm.indicator05 = indRes.data.indicator05 || 0
      indicatorForm.indicator06 = indRes.data.indicator06 || 0
    }
  } catch (e) {}
  loading.value = false
}

// 加载字典选项
const loadDicts = async () => {
  try {
    const relRes = await getDictData('relationship_depth')
    if (relRes.data?.length) {
      relationshipOptions.value = relRes.data.map(d => ({
        label: d.dictLabel, value: d.dictValue,
        icon: ['📊', '📋', '🤝', '🌐'][d.sortOrder - 1] || '📊'
      }))
    }
  } catch (e) {}

  for (const def of indicatorDefs.value) {
    try {
      const res = await getDictData(def.code)
      def.options = res.data || []
    } catch (e) {}
  }
}

// 加载用户列表（用于分享）
const loadUsers = async () => {
  try {
    const res = await getSimpleUserList()
    allUsers.value = res.data || []
  } catch (e) {}
}

// 操作方法
const submitLog = async () => {
  if (!newLog.value.trim()) return
  try {
    await addCustomerLog(customerId.value, { content: newLog.value })
    ElMessage.success('记录添加成功')
    newLog.value = ''
    const res = await getCustomerLogs(customerId.value)
    logs.value = res.data || []
  } catch (e) {}
}

const handleExtend = async () => {
  try {
    await ElMessageBox.confirm('确认申请延期？审批通过后保护期将延长360天', '申请延期')
    await submitApproval({ type: 'extend', customerId: String(customer.value.id), actionAfterApprove: '保护期+360天' })
    ElMessage.success('延期申请已提交')
  } catch (e) {}
}

const handleEdit = () => {
  router.push(`/customer/edit/${customerId.value}`)
}

const handleAddInvoice = async () => {
  if (!invoiceForm.invoiceDate || !invoiceForm.invoiceNumber || !invoiceForm.amount) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    await addInvoice(customerId.value, { ...invoiceForm })
    ElMessage.success('开票记录已添加')
    showInvoiceDialog.value = false
    Object.assign(invoiceForm, { invoiceDate: '', invoiceNumber: '', invoiceType: '增值税专票', invoiceCompany: '', amount: null })
    // 刷新开票和指标
    const [invRes, summRes, indRes] = await Promise.all([
      getInvoices(customerId.value),
      getInvoiceSummary(customerId.value),
      getIndicator(customerId.value)
    ])
    invoices.value = invRes.data || []
    invoiceSummary.value = summRes.data || {}
    indicator.value = indRes.data
    // 刷新客户（lastInvoiceDate可能变了）
    const cRes = await getCustomer(customerId.value)
    customer.value = cRes.data
  } catch (e) {}
}

const handleDeleteInvoice = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除此开票记录？', '提示')
    await deleteInvoice(customerId.value, row.id)
    ElMessage.success('已删除')
    const [invRes, summRes, indRes, cRes] = await Promise.all([
      getInvoices(customerId.value),
      getInvoiceSummary(customerId.value),
      getIndicator(customerId.value),
      getCustomer(customerId.value)
    ])
    invoices.value = invRes.data || []
    invoiceSummary.value = summRes.data || {}
    indicator.value = indRes.data
    customer.value = cRes.data
  } catch (e) {}
}

// ====== 回款操作 ======
const handleAddPayment = async () => {
  if (!paymentForm.paymentDate || !paymentForm.paymentAmount) {
    ElMessage.warning('请填写回款日期和金额')
    return
  }
  try {
    await addPayment(customerId.value, { ...paymentForm })
    ElMessage.success('回款记录已添加')
    showPaymentDialog.value = false
    Object.assign(paymentForm, { paymentDate: '', paymentAmount: null, paymentMethod: '', remark: '' })
    const [payRes, paySummRes, indRes] = await Promise.all([
      getPayments(customerId.value),
      getPaymentSummary(customerId.value),
      getIndicator(customerId.value)
    ])
    payments.value = payRes.data || []
    paymentSummary.value = paySummRes.data || {}
    indicator.value = indRes.data
  } catch (e) {}
}

const handleDeletePayment = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除此回款记录？', '提示')
    await deletePayment(customerId.value, row.id)
    ElMessage.success('已删除')
    const [payRes, paySummRes, indRes] = await Promise.all([
      getPayments(customerId.value),
      getPaymentSummary(customerId.value),
      getIndicator(customerId.value)
    ])
    payments.value = payRes.data || []
    paymentSummary.value = paySummRes.data || {}
    indicator.value = indRes.data
  } catch (e) {}
}

// ====== 联系人操作 ======
const openEditContact = (row) => {
  editingContact.value = row
  Object.assign(contactForm, { name: row.name, position: row.position, phone: row.phone, remark: row.remark })
  showContactDialog.value = true
}

const handleSaveContact = async () => {
  if (!contactForm.name) {
    ElMessage.warning('请填写联系人姓名')
    return
  }
  try {
    if (editingContact.value) {
      await updateContact(customerId.value, editingContact.value.id, { ...contactForm })
      ElMessage.success('联系人已更新')
    } else {
      await addContact(customerId.value, { ...contactForm })
      ElMessage.success('联系人已添加')
    }
    showContactDialog.value = false
    editingContact.value = null
    Object.assign(contactForm, { name: '', position: '', phone: '', remark: '' })
    const res = await getContacts(customerId.value)
    contacts.value = res.data || []
  } catch (e) {}
}

const handleDeleteContact = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除该联系人？', '提示')
    await deleteContact(customerId.value, row.id)
    ElMessage.success('已删除')
    const res = await getContacts(customerId.value)
    contacts.value = res.data || []
  } catch (e) {}
}

const handleSaveIndicator = async () => {
  try {
    await saveIndicator(customerId.value, { ...indicatorForm })
    ElMessage.success('指标已保存')
    const res = await getIndicator(customerId.value)
    indicator.value = res.data
  } catch (e) {}
}

const onIndicatorChange = () => {
  // 仅本地更新，等点保存
}

const selectRelationship = async (score) => {
  try {
    await saveRelationship(customerId.value, score)
    const res = await getIndicator(customerId.value)
    indicator.value = res.data
  } catch (e) {}
}

const handleAddShare = async () => {
  if (!shareUserIds.value.length) return
  try {
    await addShares(customerId.value, shareUserIds.value)
    ElMessage.success('分享成功')
    shareUserIds.value = []
    showShareDialog.value = false
    const res = await getShares(customerId.value)
    shares.value = res.data || []
  } catch (e) {}
}

const handleRemoveShare = async (s) => {
  try {
    await removeShare(customerId.value, s.sharedToUserId)
    const res = await getShares(customerId.value)
    shares.value = res.data || []
  } catch (e) {}
}

onMounted(() => {
  loadAll()
  loadDicts()
  loadUsers()
})
</script>

<style scoped>
/* 导航行 */
.back-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.back-link { color: #1890ff; font-size: 14px; cursor: pointer; display: flex; align-items: center; gap: 4px; }
.back-link:hover { color: #40a9ff; }
.action-btns { display: flex; gap: 10px; }

/* 头部卡片 */
.header-card {
  background: linear-gradient(135deg, #1890ff, #40a9ff);
  border-radius: 10px; padding: 24px 28px; color: #fff;
  margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between;
  box-shadow: 0 4px 16px rgba(24,144,255,0.2);
}
.header-card-left { display: flex; align-items: center; gap: 18px; }
.header-avatar {
  width: 56px; height: 56px; border-radius: 50%;
  background: rgba(255,255,255,0.2); display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 700; flex-shrink: 0; border: 2px solid rgba(255,255,255,0.3);
}
.header-info h2 { font-size: 22px; font-weight: 700; margin: 0 0 4px; }
.header-tags { font-size: 13px; opacity: 0.85; }
.header-card-right { display: flex; align-items: center; gap: 16px; }
.core-badge {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600;
}
.core-badge.core { background: rgba(82,196,26,0.25); color: #fff; }
.core-badge.non-core { background: rgba(255,255,255,0.15); color: rgba(255,255,255,0.85); }
.due-block { text-align: right; }
.due-date { font-size: 26px; font-weight: 700; line-height: 1.2; }
.due-tag {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 12px; border-radius: 12px; font-size: 12px; margin-top: 2px;
}
.due-red { background: rgba(245,108,108,0.3); }
.due-orange { background: rgba(230,162,60,0.3); }
.due-blue { background: rgba(255,255,255,0.2); }
.btn-edit-header {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: 6px; font-size: 13px; cursor: pointer;
  border: 1px solid rgba(255,255,255,0.4); background: rgba(255,255,255,0.15);
  color: #fff; transition: all .15s;
}
.btn-edit-header:hover { background: rgba(255,255,255,0.25); }

/* 学分栏 */
.stats-row {
  display: flex; align-items: center; gap: 32px;
  background: #fff; border-radius: 8px; padding: 14px 24px;
  margin-bottom: 16px; box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}
.stats-item { display: flex; align-items: center; gap: 8px; }
.stats-label { color: #999; font-size: 13px; }
.stats-value { font-size: 20px; font-weight: 700; color: #333; }
.stats-desc { margin-left: auto; color: #bbb; font-size: 12px; }

/* 双列网格 */
.grid-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }

/* 通用卡片 */
.card {
  background: #fff; border-radius: 8px; padding: 20px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}
.card-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0;
}
.card-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 15px; font-weight: 600; color: #333;
}
.card-icon { color: #1890ff; font-size: 16px; }

/* 信息表 */
.info-item { display: flex; align-items: center; padding: 12px 0; border-bottom: 1px solid #f2f3f5; }
.info-item.last { border-bottom: none; }
.info-label { width: 90px; flex-shrink: 0; color: #999; font-size: 13px; }
.info-value { flex: 1; color: #333; font-size: 14px; font-weight: 500; }

/* 保护期 */
.protect-grid { display: flex; text-align: center; }
.protect-col { flex: 1; padding: 12px 0; }
.protect-label { color: #999; font-size: 13px; margin-bottom: 8px; }
.protect-value { font-size: 18px; font-weight: 600; color: #333; }
.protect-value.expire-red { color: #f56c6c; }
.protect-value.expire-orange { color: #e6a23c; }
.protect-value.expire-blue { color: #409eff; }
.highlight { color: #e6a23c; font-size: 12px; }
.protect-status-text { display: inline-flex; align-items: center; gap: 4px; font-size: 14px; font-weight: 600; }
.status-blue { color: #67c23a; }
.status-orange { color: #e6a23c; }
.status-red { color: #f56c6c; }
.protect-action { margin-top: 16px; }

/* 客情关系标签 */
.rel-tags { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 8px; }
.rel-tag {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 14px; border: 2px solid #e8e8e8; border-radius: 8px;
  cursor: pointer; transition: all 0.2s; font-size: 14px; color: #666;
}
.rel-tag:hover { border-color: #1890ff; color: #1890ff; }
.rel-tag.selected { border-color: #1890ff; background: #e6f7ff; color: #1890ff; font-weight: 600; }
.rel-icon { font-size: 18px; }

/* 开票摘要 */
.invoice-summary { display: flex; align-items: center; gap: 16px; font-size: 13px; color: #666; flex-wrap: wrap; }
.invoice-summary b { color: #333; }
.amount-highlight { color: #f56c6c; font-size: 16px; }

/* 指标列表 */
.indicator-list { display: flex; flex-direction: column; gap: 0; }
.indicator-row {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 0; border-bottom: 1px solid #f5f5f5;
}
.indicator-row:last-child { border-bottom: none; }
.indicator-num {
  width: 28px; height: 28px; border-radius: 50%;
  background: #1890ff; color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; flex-shrink: 0;
}
.indicator-name { flex: 1; font-size: 14px; color: #333; }
.indicator-result { font-size: 13px; font-weight: 600; }
.indicator-result.met { color: #52c41a; }
.indicator-result.unmet { color: #f56c6c; }
.indicator-footer {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 16px; padding-top: 12px; border-top: 1px solid #f0f0f0;
}
.indicator-total { font-size: 14px; color: #666; }
.total-score { font-size: 24px; color: #1890ff; }
.total-warn { color: #fa8c16; font-size: 12px; margin-left: 8px; }

/* 备注 */
.remark-text { color: #555; font-size: 14px; line-height: 1.8; margin: 12px 0 0; }

/* 跟进日志 */
.log-input { margin: 12px 0 16px; }
.log-submit { display: flex; justify-content: flex-end; margin-top: 10px; }
.timeline { padding-left: 16px; }
.timeline-item { position: relative; padding: 0 0 20px 24px; }
.timeline-dot {
  position: absolute; left: -4px; top: 6px; width: 10px; height: 10px;
  border-radius: 50%; background: #1890ff;
}
.timeline-item::before {
  content: ''; position: absolute; left: 0; top: 16px; bottom: 0;
  width: 2px; background: #e8e8e8;
}
.timeline-item:last-child::before { display: none; }
.timeline-content { }
.log-text { color: #333; font-size: 14px; line-height: 1.6; margin-bottom: 6px; }
.log-meta { display: flex; gap: 16px; }
.log-user { color: #1890ff; font-size: 13px; font-weight: 500; }
.log-time { color: #999; font-size: 13px; }

/* 分享标签 */
.share-tag {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px; margin: 0 8px 8px 0;
  background: #f0f2f5; border-radius: 4px; font-size: 13px;
}
.share-remove { cursor: pointer; color: #999; }
.share-remove:hover { color: #f56c6c; }

/* 达标标签 */
.threshold-met { color: #52c41a; font-weight: 600; font-size: 13px; }
.threshold-unmet { color: #f56c6c; font-weight: 600; font-size: 13px; }
</style>
