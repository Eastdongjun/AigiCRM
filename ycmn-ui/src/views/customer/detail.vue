<template>
  <div class="customer-detail" v-if="customer" v-loading="loading">
    <!-- 导航行 -->
    <div class="top-bar">
      <span class="back-link" @click="$router.push(readonly ? '/sea' : '/customer')">
        <el-icon><ArrowLeft /></el-icon> {{ readonly ? '返回公海池' : '返回客户列表' }}
      </span>
      <div class="top-actions" v-if="!readonly">
        <el-button size="small" @click="handleEdit"><el-icon><Edit /></el-icon> 编辑</el-button>
        <el-button size="small" @click="showShareDialog = true"><el-icon><Share /></el-icon> 分享</el-button>
      </div>
      <el-tag v-else type="info" size="large">{{ readonlyTag }}</el-tag>
    </div>

    <!-- 头部卡片 -->
    <div class="hero-card">
      <div class="hero-left">
        <div class="hero-avatar" :style="{background: avatarColor}">{{ customer.customerName?.charAt(0) }}</div>
        <div class="hero-main">
          <h1>{{ customer.customerName }}</h1>
          <div class="hero-meta">
            <span class="meta-tag"><el-icon><OfficeBuilding /></el-icon> {{ customer.industry || '—' }}</span>
            <span class="meta-tag"><el-icon><PriceTag /></el-icon> {{ customer.devType || '—' }}</span>
            <span class="meta-tag"><el-icon><User /></el-icon> {{ customer.ownerName }}</span>
            <span v-if="customer.groupName" class="meta-tag"><el-icon><Connection /></el-icon> {{ customer.groupName }}</span>
          </div>
        </div>
      </div>
      <div class="hero-right">
        <div class="hero-badge" :class="isCore ? 'core' : 'non'">
          <el-icon><Medal /></el-icon> {{ isCore ? '核心客户' : '非核心' }}
        </div>
        <div class="hero-expire">
          <div class="expire-date">{{ customer.expireDate }}</div>
          <div class="expire-tag" :class="'lvl-'+expireLevel">
            <el-icon><Warning /></el-icon> {{ statusLabel }}
          </div>
        </div>
      </div>
    </div>

    <!-- 数据卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon-box gold"><el-icon><Trophy /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ incentiveScore }}</div>
          <div class="stat-label">激励学分</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box red"><el-icon><StarFilled /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ indicator?.relationshipScore || 0 }}</div>
          <div class="stat-label">客情关系</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box blue"><el-icon><TrendCharts /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ totalScore }}</div>
          <div class="stat-label">综合得分</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box green"><el-icon><CircleCheck /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ invoiceSummary.totalCount + paymentSummary.totalCount }}</div>
          <div class="stat-label">开票+回款</div>
        </div>
      </div>
    </div>

    <!-- Tab 内容区 -->
    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="基本信息" name="info">
        <div class="info-grid">
          <!-- 基础信息 -->
          <div class="info-card">
            <div class="info-card-head">
              <span class="info-card-icon c-blue"><el-icon><InfoFilled /></el-icon></span>
              <span>基础信息</span>
            </div>
            <div class="info-rows">
              <div class="info-row"><span class="lbl">行业</span><span class="val">{{ customer.industry || '—' }}</span></div>
              <div class="info-row"><span class="lbl">开发类型</span><span class="val">{{ customer.devType || '—' }}</span></div>
              <div class="info-row"><span class="lbl">产能(万吨)</span><span class="val">{{ customer.capacity || '—' }}</span></div>
            </div>
          </div>
          <!-- 归属 -->
          <div class="info-card">
            <div class="info-card-head">
              <span class="info-card-icon c-orange"><el-icon><MapLocation /></el-icon></span>
              <span>归属信息</span>
            </div>
            <div class="info-rows">
              <div class="info-row"><span class="lbl">所属大区</span><span class="val">{{ customer.regionName || '—' }}</span></div>
              <div class="info-row"><span class="lbl">所属办事处</span><span class="val">{{ customer.officeName || '—' }}</span></div>
              <div class="info-row"><span class="lbl">第一负责人</span><span class="val">{{ customer.ownerName || '—' }}</span></div>
            </div>
          </div>
          <!-- 组织 -->
          <div class="info-card">
            <div class="info-card-head">
              <span class="info-card-icon c-purple"><el-icon><OfficeBuilding /></el-icon></span>
              <span>组织信息</span>
            </div>
            <div class="info-rows">
              <div class="info-row"><span class="lbl">所属集团</span><span class="val">{{ customer.groupName || '—' }}</span></div>
              <div class="info-row"><span class="lbl">所属分公司</span><span class="val">{{ customer.branchCompany || '—' }}</span></div>
              <div class="info-row"><span class="lbl">所属单位</span><span class="val">{{ customer.unitName || '—' }}</span></div>
            </div>
          </div>
          <!-- 地址 -->
          <div class="info-card">
            <div class="info-card-head">
              <span class="info-card-icon c-teal"><el-icon><Location /></el-icon></span>
              <span>地址信息</span>
            </div>
            <div class="info-rows">
              <div class="info-row"><span class="lbl">省市县</span><span class="val">{{ customer.province }}{{ customer.city }}{{ customer.district || '—' }}</span></div>
              <div class="info-row"><span class="lbl">厂址</span><span class="val">{{ customer.address || '—' }}</span></div>
              <div class="info-row"><span class="lbl">保护期</span><span class="val"><span :class="'protect-badge badge-'+expireLevel">{{ statusLabel }}</span></span></div>
            </div>
          </div>
        </div>

        <!-- 保护期 + 延期 -->
        <div class="protect-bar">
          <div class="protect-dates">
            <div class="protect-date-item">
              <span class="pd-label">注册日</span>
              <span class="pd-value">{{ customer.registerDate }}</span>
            </div>
            <div class="protect-divider"><el-icon><ArrowRight /></el-icon></div>
            <div class="protect-date-item">
              <span class="pd-label">最近开票</span>
              <span class="pd-value">{{ customer.lastInvoiceDate || '—' }}</span>
            </div>
            <div class="protect-divider"><el-icon><ArrowRight /></el-icon></div>
            <div class="protect-date-item">
              <span class="pd-label">到期日 (360天)</span>
              <span class="pd-value" :class="'exp-'+expireLevel">{{ customer.expireDate }}</span>
            </div>
          </div>
          <el-button v-if="!readonly" size="small" type="warning" @click="handleExtend">
            <el-icon><Calendar /></el-icon> 申请延期
          </el-button>
        </div>

        <!-- 联系人 -->
        <div class="contact-card">
          <div class="contact-head">
            <span class="contact-title"><el-icon><User /></el-icon> 联系人</span>
            <el-button v-if="!readonly" size="small" type="primary" @click="editingContact = null; Object.assign(contactForm, { name: '', position: '', phone: '', remark: '' }); showContactDialog = true">
              <el-icon><Plus /></el-icon> 添加
            </el-button>
          </div>
          <div v-if="contacts.length" class="contact-grid">
            <div v-for="c in contacts" :key="c.id" class="contact-item">
              <div class="contact-avatar">{{ c.name?.charAt(0) }}</div>
              <div class="contact-info">
                <div class="contact-name">{{ c.name }}</div>
                <div class="contact-sub">{{ c.position || '—' }} · {{ c.phone || '—' }}</div>
              </div>
              <div v-if="!readonly" class="contact-acts">
                <el-button link size="small" @click="openEditContact(c)"><el-icon><Edit /></el-icon></el-button>
                <el-button link size="small" type="danger" @click="handleDeleteContact(c)"><el-icon><Delete /></el-icon></el-button>
              </div>
            </div>
          </div>
          <div v-else class="empty-text">暂无联系人</div>
        </div>

        <!-- 客情关系 -->
        <div class="relation-card">
          <div class="relation-title"><el-icon color="#f56c6c"><StarFilled /></el-icon> 客情 · 商务关系深度</div>
          <div class="relation-options">
            <div v-for="opt in relationshipOptions" :key="opt.value"
              class="relation-opt" :class="{on: (indicator?.relationshipScore || 0) == opt.value}"
              @click="!readonly && selectRelationship(parseInt(opt.value))">
              <span class="rel-icon">{{ opt.icon }}</span>
              <span>{{ opt.label }} · {{ opt.value }}分</span>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane name="finance">
        <template #label>
          <span>开票回款 <el-tag size="small" :type="bothThresholdMet?'success':'danger'">{{ bothThresholdMet?'✓达标':'✗未达标'}}</el-tag></span>
        </template>

        <!-- 开票 -->
        <div class="finance-card">
          <div class="fin-head">
            <div class="fin-title"><el-icon><Document /></el-icon> 开票记录</div>
            <div class="fin-summary">
              <span>累计 <b>{{ invoiceSummary.totalCount }}</b> 次</span>
              <span>总额 <b class="red">{{ invoiceSummary.totalAmount }}</b> 万</span>
              <el-button v-if="!readonly" size="small" type="primary" @click="showInvoiceDialog = true">+ 录入开票</el-button>
            </div>
          </div>
          <el-table :data="invoices" stripe size="small" v-if="invoices.length">
            <el-table-column prop="invoiceDate" label="日期" width="110" />
            <el-table-column prop="invoiceNumber" label="发票号" width="150" />
            <el-table-column prop="invoiceType" label="类型" width="110">
              <template #default="{row}"><el-tag size="small">{{ row.invoiceType }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="invoiceCompany" label="开票单位" min-width="140" />
            <el-table-column prop="amount" label="金额(万)" width="90" align="right">
              <template #default="{row}"><b>{{ row.amount }}</b>万</template>
            </el-table-column>
            <el-table-column v-if="!readonly" label="" width="50" align="center">
              <template #default="{row}"><el-button link type="danger" size="small" @click="handleDeleteInvoice(row)"><el-icon><Delete /></el-icon></el-button></template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-text">暂无开票记录</div>
        </div>

        <!-- 回款 -->
        <div class="finance-card">
          <div class="fin-head">
            <div class="fin-title"><el-icon color="#67c23a"><Money /></el-icon> 回款记录</div>
            <div class="fin-summary">
              <span>累计 <b>{{ paymentSummary.totalCount }}</b> 次</span>
              <span>总额 <b class="red">{{ paymentSummary.totalAmount }}</b> 万</span>
              <el-button v-if="!readonly" size="small" type="primary" @click="showPaymentDialog = true">+ 录入回款</el-button>
            </div>
          </div>
          <el-table :data="payments" stripe size="small" v-if="payments.length">
            <el-table-column prop="paymentDate" label="日期" width="110" />
            <el-table-column prop="paymentAmount" label="金额(万)" width="90" align="right">
              <template #default="{row}"><b>{{ row.paymentAmount }}</b>万</template>
            </el-table-column>
            <el-table-column prop="paymentMethod" label="方式" width="100" />
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column v-if="!readonly" label="" width="50" align="center">
              <template #default="{row}"><el-button link type="danger" size="small" @click="handleDeletePayment(row)"><el-icon><Delete /></el-icon></el-button></template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-text">暂无回款记录</div>
        </div>
      </el-tab-pane>

      <!-- 六项指标 -->
      <el-tab-pane name="indicator">
        <template #label>
          <span>核心指标 <el-tag size="small" :type="indicatorTotal >= 12 ? 'success' : 'warning'">{{ indicatorTotal }}/18</el-tag></span>
        </template>
        <div class="indicator-panel">
          <div class="ind-row main-threshold">
            <span class="ind-num">01</span>
            <span class="ind-name">开票回款 · 硬性门槛 ≥ ¥100万</span>
            <span class="ind-status" :class="bothThresholdMet?'pass':'fail'">{{ bothThresholdMet ? '✓ 达标' : '✗ 未达标' }}</span>
          </div>
          <div class="ind-row" v-for="(ind, idx) in indicatorDefs" :key="ind.code">
            <span class="ind-num">{{ String(idx + 2).padStart(2, '0') }}</span>
            <span class="ind-name">{{ ind.label }}</span>
            <el-select v-model="indicatorForm[ind.field]" size="small" style="width:170px" :disabled="readonly" @change="onIndicatorChange">
              <el-option v-for="opt in ind.options" :key="opt.dictValue" :label="`${opt.dictValue}分 · ${opt.dictLabel}`" :value="parseInt(opt.dictValue)" />
            </el-select>
          </div>
          <div class="ind-actions">
            <span>总分 <b class="ind-total">{{ indicatorTotal }}</b> / 18 <span v-if="indicatorTotal < 12" class="ind-warn">⚠ 需调整定位</span></span>
            <el-button v-if="!readonly" type="primary" size="small" @click="handleSaveIndicator">保存指标</el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- 四项激励 -->
      <el-tab-pane name="incentive">
        <template #label>
          <span>四项激励 <el-tag size="small" type="warning">{{ incentiveRecords.length }}</el-tag></span>
        </template>
        <div v-if="incentiveRecords.length" class="incentive-list">
          <div v-for="r in incentiveRecords" :key="r.id" class="incentive-item">
            <div class="inc-top">
              <span class="inc-category">{{ getMainCategory(r) }} · {{ getSubCategory(r) }}</span>
              <span class="inc-score" :style="{color: scoreColor(r)}">+{{ r.score }}分</span>
            </div>
            <div class="inc-meta">
              <span>{{ r.period }}</span><span>·</span><span>{{ r.userName }}</span>
              <span v-if="r.customerName">· {{ r.customerName }}</span>
              <span class="inc-time">{{ fmtDate(r.createdAt) }}</span>
            </div>
            <div class="inc-files" v-if="getFiles(r).length">
              <div v-for="f in getFiles(r)" :key="f.id" class="inc-file" @click="previewIncentiveFile(f)">
                <span class="inc-file-tag">{{ f.tag }}</span>
                <span class="inc-file-name">{{ f.label }}</span>
                <span class="inc-file-type">{{ f.type === 'image' ? '🖼' : f.type === 'video' ? '🎬' : '📄' }}</span>
              </div>
            </div>
            <div v-if="r.remark" class="inc-remark">{{ r.remark }}</div>
          </div>
        </div>
        <div v-else class="empty-text" style="padding:40px">该客户暂无四项激励记录</div>
      </el-tab-pane>

      <!-- 跟进日志 -->
      <el-tab-pane label="跟进日志" name="logs">
        <div class="log-panel">
          <div class="log-write" v-if="!readonly">
            <el-input v-model="newLog" type="textarea" :rows="3" placeholder="输入跟进记录..." resize="vertical" />
            <div class="log-submit"><el-button type="primary" size="small" @click="submitLog"><el-icon><Promotion /></el-icon> 记录</el-button></div>
          </div>
          <div class="timeline" v-if="logs.length">
            <div v-for="log in logs" :key="log.id" class="timeline-item">
              <div class="tl-dot"></div>
              <div class="tl-card">
                <div class="tl-text">{{ log.content }}</div>
                <div class="tl-meta"><span class="tl-user">{{ log.userName }}</span><span class="tl-time">{{ log.createdAt }}</span></div>
              </div>
            </div>
          </div>
          <div v-else class="empty-text">暂无跟进记录</div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 弹窗保持不变 -->
    <el-dialog v-model="showInvoiceDialog" title="录入开票" width="480px" :close-on-click-modal="false">
      <el-form :model="invoiceForm" label-width="80px">
        <el-form-item label="开票日期"><el-date-picker v-model="invoiceForm.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="发票号码"><el-input v-model="invoiceForm.invoiceNumber" /></el-form-item>
        <el-form-item label="发票类型"><el-select v-model="invoiceForm.invoiceType" style="width:100%"><el-option label="增值税专票" value="增值税专票" /><el-option label="增值税普票" value="增值税普票" /></el-select></el-form-item>
        <el-form-item label="开票单位"><el-input v-model="invoiceForm.invoiceCompany" /></el-form-item>
        <el-form-item label="金额(万)"><el-input-number v-model="invoiceForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showInvoiceDialog=false">取消</el-button><el-button type="primary" @click="handleAddInvoice">确认录入</el-button></template>
    </el-dialog>

    <el-dialog v-model="showPaymentDialog" title="录入回款" width="480px" :close-on-click-modal="false">
      <el-form :model="paymentForm" label-width="80px">
        <el-form-item label="回款日期"><el-date-picker v-model="paymentForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="金额(万)"><el-input-number v-model="paymentForm.paymentAmount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="回款方式"><el-input v-model="paymentForm.paymentMethod" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="paymentForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showPaymentDialog=false">取消</el-button><el-button type="primary" @click="handleAddPayment">确认录入</el-button></template>
    </el-dialog>

    <el-dialog v-model="showShareDialog" title="分享客户" width="480px">
      <p style="color:#909399;margin-bottom:12px;font-size:13px">选择用户后，该用户可查看此客户信息（只读）</p>
      <div v-if="shares.length" style="margin-bottom:12px;display:flex;flex-wrap:wrap;gap:6px">
        <el-tag v-for="s in shares" :key="s.id" closable size="small" @close="handleRemoveShare(s)">{{ s.sharedToUserName }}</el-tag>
      </div>
      <el-select v-model="shareUserIds" multiple filterable placeholder="选择用户..." style="width:100%">
        <el-option v-for="u in allUsers" :key="u.id" :label="u.realName" :value="u.id" />
      </el-select>
      <template #footer><el-button @click="showShareDialog=false">取消</el-button><el-button type="primary" @click="handleAddShare">确认分享</el-button></template>
    </el-dialog>

    <el-dialog v-model="showContactDialog" :title="editingContact ? '编辑联系人' : '添加联系人'" width="440px" :close-on-click-modal="false">
      <el-form :model="contactForm" label-width="50px">
        <el-form-item label="姓名"><el-input v-model="contactForm.name" /></el-form-item>
        <el-form-item label="职位"><el-input v-model="contactForm.position" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="contactForm.phone" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="contactForm.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showContactDialog=false">取消</el-button><el-button type="primary" @click="handleSaveContact">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCustomer, getCustomerLogs, addCustomerLog, updateCustomer,
  getInvoices, getInvoiceSummary, addInvoice, deleteInvoice,
  getPayments, getPaymentSummary, addPayment, deletePayment,
  getContacts, addContact, updateContact, deleteContact,
  getIndicator, saveIndicator, saveRelationship,
  getShares, addShares, removeShare, getSimpleUserList } from '@/api/customer'
import { getDictData } from '@/api/system'
import { submitApproval } from '@/api/approval'
import { getScoreByCustomer, getCategoryTree } from '@/api/incentive'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'

const route = useRoute(); const router = useRouter(); const userStore = useUserStore()
const isAdmin = computed(() => userStore.roleName === '系统管理员' || (userStore.roles || []).includes('admin'))
const readonly = computed(() => {
  if (route.query.from === 'sea') return true
  if (customer.value && userStore.userId && !isAdmin.value && customer.value.ownerId && customer.value.ownerId !== userStore.userId) return true
  return false
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
  const colors = ['#409eff','#e6a23c','#67c23a','#f56c6c','#9c27b0','#00bcd4']
  const n = customer.value?.customerName?.charCodeAt(0) || 0
  return colors[n % colors.length]
})

const relationshipOptions = ref([{label:'商务少',value:'0',icon:'📊'},{label:'纯商务',value:'1',icon:'📋'},{label:'感情商务',value:'2',icon:'🤝'},{label:'多样性',value:'3',icon:'🌐'}])
const indicatorDefs = ref([
  {code:'core_indicator_02',field:'indicator02',label:'高层 · 客情程度',options:[]},
  {code:'core_indicator_03',field:'indicator03',label:'渠道 · 编码数量',options:[]},
  {code:'core_indicator_04',field:'indicator04',label:'基层 · 分厂车间覆盖',options:[]},
  {code:'core_indicator_05',field:'indicator05',label:'产品 · 密封/润滑/防腐品类',options:[]},
  {code:'core_indicator_06',field:'indicator06',label:'项目 · 项目资金/类型',options:[]}])

const indicatorTotal = computed(() => indicatorForm.indicator02+indicatorForm.indicator03+indicatorForm.indicator04+indicatorForm.indicator05+indicatorForm.indicator06)
const invoiceThresholdMet = computed(() => (invoiceSummary.value.totalAmount||0) >= 100)
const paymentThresholdMet = computed(() => (paymentSummary.value.totalAmount||0) >= 100)
const bothThresholdMet = computed(() => invoiceThresholdMet.value && paymentThresholdMet.value)
const isCore = computed(() => bothThresholdMet.value && indicatorTotal.value >= 12)
const totalScore = computed(() => incentiveScore.value + (indicator.value?.relationshipScore||0))
const expireLevel = computed(() => {
  if (!customer.value) return ''; if (customer.value.protectionStatus==='expired') return 'red'; if (customer.value.protectionStatus==='expiring') return 'orange'; return 'blue'
})
const statusLabel = computed(() => {
  if (!customer.value) return ''; const d = Math.ceil((new Date(customer.value.expireDate) - new Date())/(86400000))
  return d < 0 ? `已超期${Math.abs(d)}天` : d <= 30 ? `距到期${d}天` : '保护中'
})

const customerId = computed(() => route.params.id)

const loadAll = async () => {
  loading.value = true
  try {
    const [cRes,logRes,invRes,summRes,payRes,paySummRes,contactRes,indRes,shareRes,scoreRes] = await Promise.all([
      getCustomer(customerId.value), getCustomerLogs(customerId.value), getInvoices(customerId.value), getInvoiceSummary(customerId.value),
      getPayments(customerId.value), getPaymentSummary(customerId.value), getContacts(customerId.value), getIndicator(customerId.value),
      getShares(customerId.value), getScoreByCustomer(customerId.value)])
    customer.value = cRes.data; logs.value = logRes.data||[]; invoices.value = invRes.data||[]
    invoiceSummary.value = summRes.data||{totalCount:0,totalAmount:0}; payments.value = payRes.data||[]
    paymentSummary.value = paySummRes.data||{totalCount:0,totalAmount:0}; contacts.value = contactRes.data||[]
    indicator.value = indRes.data; shares.value = shareRes.data||[]; incentiveScore.value = scoreRes.data||0
    if (indRes.data) { indicatorForm.indicator02=indRes.data.indicator02||0; indicatorForm.indicator03=indRes.data.indicator03||0; indicatorForm.indicator04=indRes.data.indicator04||0; indicatorForm.indicator05=indRes.data.indicator05||0; indicatorForm.indicator06=indRes.data.indicator06||0 }
  } catch(e) {} finally { loading.value = false }
}

const loadDicts = async () => {
  try { const relRes = await getDictData('relationship_depth'); if (relRes.data?.length) relationshipOptions.value = relRes.data.map((d,i) => ({label:d.dictLabel,value:d.dictValue,icon:['📊','📋','🤝','🌐'][i]||'📊'})) } catch(e) {}
  for (const def of indicatorDefs.value) { try { def.options = (await getDictData(def.code)).data||[] } catch(e) {} }
}
const loadUsers = async () => { try { allUsers.value = (await getSimpleUserList()).data||[] } catch(e) {} }

const loadIncentiveRecords = async () => {
  if (!customer.value?.customerName) return
  try {
    const res = await request.get('/incentive/customer-records', { params: { customerName: customer.value.customerName } })
    incentiveRecords.value = (res.data || []).flatMap(c => (c.records || []).map(r => ({ ...r, customerName: c.customerName })))
    // 加载品类树
    const catRes = await getCategoryTree()
    const walk = (list) => { list.forEach(c => { catMap.value[c.id] = c; if (c.children) walk(c.children) }) }
    walk(catRes.data || [])
  } catch(e) {}
}

const getMainCategory = (r) => { const c = catMap.value[r.categoryId]; if (!c) return ''; const p = catMap.value[c.parentId]; return p ? p.categoryName : c.categoryName }
const getSubCategory = (r) => { const c = catMap.value[r.categoryId]; return c ? c.categoryName : '' }
const scoreColor = (r) => { const c = catMap.value[r.categoryId]; if (!c) return '#333'; const p = catMap.value[c.parentId]; const gp = p ? catMap.value[p.parentId] : null; const code = (gp && gp.parentId === 0 ? gp.categoryCode : p?.categoryCode) || ''; return { visit: '#e6a23c', exchange: '#409eff', plan: '#9c27b0', trial: '#67c23a' }[code] || '#333' }
function parseId(v){if(!v)return null;if(typeof v==='number')return v;try{const a=JSON.parse(v);return Array.isArray(a)?a[0]:v}catch{return v}}
const getFiles = (r) => { const f = []; if (r.filePreId) f.push({ id: parseId(r.filePreId), label: r.filePreName || '文件', type: detectType(r.filePreName), tag: '安装前' }); if (r.fileMidId) f.push({ id: parseId(r.fileMidId), label: r.fileMidName || '文件', type: detectType(r.fileMidName), tag: '安装中' }); if (r.filePostId) f.push({ id: parseId(r.filePostId), label: r.filePostName || '文件', type: detectType(r.filePostName), tag: '安装后' }); if (r.fileId) f.push({ id: parseId(r.fileId), label: r.fileName || '文件', type: detectType(r.fileName), tag: '' }); return f }
const detectType = (n) => { if (!n) return 'doc'; if (/\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(n)) return 'image'; if (/\.(mp4|mov|avi|webm)$/i.test(n)) return 'video'; return 'doc' }
const fmtDate = (t) => { if (!t) return ''; const d = new Date(t); return d.getFullYear()+'-'+(d.getMonth()+1).toString().padStart(2,'0')+'-'+d.getDate().toString().padStart(2,'0') }
const previewIncentiveFile = (f) => { const token = localStorage.getItem('token') || ''; window.open('/api/file/download/' + f.id + '?token=' + token, '_blank') }

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
onMounted(() => { loadAll(); loadDicts(); loadUsers() })
watch(customer, (v) => { if (v?.customerName) loadIncentiveRecords() })
</script>

<style scoped>
/* === Top Bar === */
.top-bar { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px }
.back-link { color:#409eff; font-size:14px; cursor:pointer; display:flex; align-items:center; gap:4px }
.back-link:hover { color:#66b1ff }
.top-actions { display:flex; gap:8px }

/* === Hero Card === */
.hero-card { background:linear-gradient(135deg,#1d39c4,#409eff); border-radius:12px; padding:28px 32px; color:#fff; margin-bottom:16px; display:flex; justify-content:space-between; align-items:center; box-shadow:0 4px 20px rgba(64,158,255,.25) }
.hero-left { display:flex; align-items:center; gap:20px }
.hero-avatar { width:60px; height:60px; border-radius:14px; display:flex; align-items:center; justify-content:center; font-size:24px; font-weight:800; border:2px solid rgba(255,255,255,.3); flex-shrink:0 }
.hero-main h1 { font-size:22px; font-weight:700; margin:0 0 6px; letter-spacing:.5px }
.hero-meta { display:flex; gap:10px; flex-wrap:wrap }
.meta-tag { display:inline-flex; align-items:center; gap:4px; font-size:12px; padding:3px 10px; background:rgba(255,255,255,.15); border-radius:6px; opacity:.9 }
.hero-right { text-align:right; display:flex; flex-direction:column; align-items:flex-end; gap:8px }
.hero-badge { display:inline-flex; align-items:center; gap:4px; padding:6px 16px; border-radius:20px; font-size:13px; font-weight:600 }
.hero-badge.core { background:rgba(82,196,26,.3) }.hero-badge.non { background:rgba(255,255,255,.12) }
.hero-expire { text-align:right }.expire-date { font-size:28px; font-weight:700 }
.expire-tag { display:inline-flex; align-items:center; gap:4px; padding:2px 10px; border-radius:10px; font-size:12px; margin-top:2px }
.expire-tag.lvl-red { background:rgba(245,108,108,.3) }.expire-tag.lvl-orange { background:rgba(230,162,60,.3) }.expire-tag.lvl-blue { background:rgba(255,255,255,.2) }

/* === Stat Cards === */
.stat-cards { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; margin-bottom:20px }
.stat-card { background:#fff; border-radius:10px; padding:18px 16px; display:flex; align-items:center; gap:14px; box-shadow:0 1px 3px rgba(0,0,0,.04); transition:box-shadow .2s }
.stat-card:hover { box-shadow:0 4px 12px rgba(0,0,0,.08) }
.stat-icon-box { width:44px; height:44px; border-radius:12px; display:flex; align-items:center; justify-content:center; font-size:20px }
.stat-icon-box.gold { background:#fef3e2; color:#e6a23c }.stat-icon-box.red { background:#fef0f0; color:#f56c6c }
.stat-icon-box.blue { background:#ecf5ff; color:#409eff }.stat-icon-box.green { background:#f0f9eb; color:#67c23a }
.stat-num { font-size:22px; font-weight:700; color:#1f2329; line-height:1.2 }
.stat-label { font-size:12px; color:#909399; margin-top:2px }

/* === Tabs === */
.detail-tabs { background:#fff; border-radius:10px; padding:4px 20px 20px; box-shadow:0 1px 3px rgba(0,0,0,.04) }
.detail-tabs :deep(.el-tabs__header) { margin-bottom:16px }
.detail-tabs :deep(.el-tabs__item) { font-size:14px; font-weight:500 }

/* === Info Grid === */
.info-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:14px; margin-bottom:16px }
.info-card { background:#fafbfc; border-radius:10px; padding:18px 20px; border:1px solid #f0f1f3 }
.info-card-head { display:flex; align-items:center; gap:8px; font-size:14px; font-weight:600; color:#333; margin-bottom:14px }
.info-card-icon { display:flex; align-items:center; justify-content:center; width:28px; height:28px; border-radius:7px; font-size:14px }
.c-blue { background:#ecf5ff; color:#409eff }.c-orange { background:#fdf6ec; color:#e6a23c }
.c-purple { background:#f5f0ff; color:#9c27b0 }.c-teal { background:#e6fffb; color:#13c2c2 }
.info-rows { display:flex; flex-direction:column; gap:0 }
.info-row { display:flex; padding:10px 0; border-bottom:1px solid #f5f5f5 }
.info-row:last-child { border-bottom:none }
.lbl { width:85px; color:#909399; font-size:13px; flex-shrink:0 }
.val { color:#1f2329; font-size:14px; font-weight:500 }

.protect-badge { font-size:12px; padding:2px 8px; border-radius:4px; font-weight:600 }
.badge-blue { color:#67c23a; background:#f0f9eb }.badge-orange { color:#e6a23c; background:#fdf6ec }.badge-red { color:#f56c6c; background:#fef0f0 }

/* === Protect Bar === */
.protect-bar { display:flex; align-items:center; justify-content:space-between; background:#fafbfc; border-radius:10px; padding:16px 24px; margin-bottom:16px; border:1px solid #f0f1f3 }
.protect-dates { display:flex; align-items:center; gap:20px }
.protect-date-item { text-align:center }.pd-label { font-size:12px; color:#909399 }.pd-value { font-size:16px; font-weight:600; color:#333 }
.pd-value.exp-red { color:#f56c6c }.pd-value.exp-orange { color:#e6a23c }
.protect-divider { color:#c0c4cc; font-size:18px }

/* === Contacts === */
.contact-card { background:#fafbfc; border-radius:10px; padding:18px 20px; margin-bottom:16px; border:1px solid #f0f1f3 }
.contact-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px }
.contact-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333 }
.contact-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:10px }
.contact-item { display:flex; align-items:center; gap:12px; padding:12px; background:#fff; border-radius:8px; border:1px solid #f0f1f3 }
.contact-avatar { width:36px; height:36px; border-radius:50%; background:#ecf5ff; color:#409eff; display:flex; align-items:center; justify-content:center; font-size:14px; font-weight:700 }
.contact-info { flex:1 }.contact-name { font-size:14px; font-weight:500; color:#333 }.contact-sub { font-size:12px; color:#909399; margin-top:2px }
.contact-acts { display:flex }

/* === Relation === */
.relation-card { background:#fafbfc; border-radius:10px; padding:18px 20px; margin-bottom:16px; border:1px solid #f0f1f3 }
.relation-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333; margin-bottom:12px }
.relation-options { display:grid; grid-template-columns:repeat(4,1fr); gap:10px }
.relation-opt { display:flex; align-items:center; justify-content:center; gap:6px; padding:14px 8px; border:2px solid #e8e8e8; border-radius:8px; cursor:pointer; transition:all .15s; font-size:13px; color:#666; background:#fff }
.relation-opt:hover { border-color:#409eff; color:#409eff }
.relation-opt.on { border-color:#409eff; background:#ecf5ff; color:#409eff; font-weight:600 }

/* === Finance === */
.finance-card { background:#fafbfc; border-radius:10px; padding:18px 20px; margin-bottom:14px; border:1px solid #f0f1f3 }
.fin-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; flex-wrap:wrap; gap:10px }
.fin-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333 }
.fin-summary { display:flex; align-items:center; gap:14px; font-size:13px; color:#666 }
.fin-summary b { color:#333 }.fin-summary .red { color:#f56c6c; font-size:15px }

/* === Indicator === */
.indicator-panel { }
.ind-row { display:flex; align-items:center; gap:14px; padding:12px 0; border-bottom:1px solid #f5f5f5 }
.ind-row:last-child { border-bottom:none }
.ind-num { width:28px; height:28px; border-radius:50%; background:#409eff; color:#fff; display:flex; align-items:center; justify-content:center; font-size:12px; font-weight:700; flex-shrink:0 }
.ind-name { flex:1; font-size:14px; color:#333 }
.ind-status { font-size:13px; font-weight:600 }.ind-status.pass { color:#52c41a }.ind-status.fail { color:#f56c6c }
.ind-actions { display:flex; justify-content:space-between; align-items:center; margin-top:16px; padding-top:14px; border-top:2px solid #f0f0f0; font-size:14px; color:#666 }
.ind-total { font-size:24px; color:#409eff }.ind-warn { color:#fa8c16; font-size:12px; margin-left:8px }

/* === Log === */
.log-panel { }
.log-write { margin-bottom:16px }
.log-submit { display:flex; justify-content:flex-end; margin-top:8px }
.timeline { padding-left:8px }
.timeline-item { position:relative; padding:0 0 18px 24px }
.tl-dot { position:absolute; left:-2px; top:8px; width:10px; height:10px; border-radius:50%; background:#409eff; border:2px solid #fff; box-shadow:0 0 0 2px #409eff }
.timeline-item::before { content:''; position:absolute; left:2px; top:18px; bottom:0; width:2px; background:#e8e8e8 }
.timeline-item:last-child::before { display:none }
.tl-card { background:#fafbfc; border-radius:8px; padding:14px 16px; border:1px solid #f0f1f3 }
.tl-text { font-size:14px; color:#333; line-height:1.6; margin-bottom:6px }
.tl-meta { display:flex; gap:16px; font-size:12px }.tl-user { color:#409eff }.tl-time { color:#999 }

.empty-text { color:#c0c4cc; text-align:center; padding:24px 0; font-size:13px }

/* === Main threshold === */
.main-threshold { background:#fafbfc; border-radius:8px; padding:14px 16px; margin-bottom:4px }

/* === Incentive === */
.incentive-list { display:flex; flex-direction:column; gap:12px }
.incentive-item { background:#fafbfc; border:1px solid #f0f1f3; border-radius:10px; padding:16px 18px }
.inc-top { display:flex; justify-content:space-between; align-items:center; margin-bottom:6px }
.inc-category { font-size:14px; font-weight:600; color:#333 }
.inc-score { font-size:18px; font-weight:700 }
.inc-meta { display:flex; gap:8px; font-size:12px; color:#909399; margin-bottom:10px }
.inc-time { margin-left:auto }
.inc-files { display:flex; gap:8px; flex-wrap:wrap }
.inc-file { display:flex; align-items:center; gap:6px; padding:6px 12px; background:#fff; border:1px solid #e8e8e8; border-radius:6px; cursor:pointer; font-size:13px; transition:all .15s }
.inc-file:hover { border-color:#409eff; background:#ecf5ff }
.inc-file-tag { font-size:11px; padding:1px 6px; border-radius:3px; background:#ecf5ff; color:#409eff; font-weight:500 }
.inc-file-name { color:#333; max-width:200px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap }
.inc-file-type { font-size:14px }
.inc-remark { font-size:12px; color:#909399; margin-top:8px; padding:8px; background:#fff; border-radius:6px }
</style>
