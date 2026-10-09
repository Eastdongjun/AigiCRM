<template>
  <div>
    <!-- 开票 -->
    <div class="finance-card">
      <div class="fin-head">
        <div class="fin-title"><el-icon><Document /></el-icon> 开票记录</div>
        <div class="fin-summary">
          <span>累计 <b>{{ invoiceSummary.totalCount }}</b> 次</span>
          <span>有效总额 <b class="red">{{ formatWan(invoiceSummary.totalAmount) }}</b><template v-if="hasAmount(invoiceSummary.totalAmount)"> 万</template></span>
          <el-button v-if="!readonly" size="small" type="primary" @click="showInvoiceDialog = true">+ 录入开票</el-button>
        </div>
      </div>
      <el-table :data="invoices" stripe size="small" v-if="invoices.length" :row-class-name="ledgerRowClass">
        <el-table-column prop="invoiceDate" label="日期" width="105" />
        <!-- 【C10 三轮 2026-10-09】19 位发票号在 width=140 下折行（截图 04「26322000005922182551」两行）：
             改 min-width=170 + 超长悬浮完整值，与全表 show-overflow-tooltip 口径一致 -->
        <el-table-column prop="invoiceNumber" label="发票号" min-width="170" show-overflow-tooltip :formatter="dashIfEmpty" />
        <el-table-column prop="invoiceType" label="类型" width="100">
          <template #default="{row}"><el-tag size="small">{{ row.invoiceType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="invoiceCompany" label="开票单位" min-width="130" show-overflow-tooltip :formatter="dashIfEmpty" />
        <el-table-column prop="amount" label="金额(万)" width="85" align="right">
          <!-- 【UI 整改 2026-10-08】金额空值显示 '—' 且不渲染"万"后缀（历史导入行曾出现孤立"万"字）；0 为有效金额 -->
          <template #default="{row}">
            <template v-if="hasAmount(row.amount)"><b>{{ formatWan(row.amount) }}</b>万</template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{row}">
            <el-tooltip v-if="row.status === 'voided'" :content="'作废原因: ' + (row.voidedReason || '未填写')" placement="top">
              <el-tag size="small" :type="financeStatus(row.status).type">{{ financeStatus(row.status).text }}</el-tag>
            </el-tooltip>
            <el-tag v-else size="small" :type="financeStatus(row.status).type">{{ financeStatus(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!readonly" label="操作" width="150" align="center">
          <template #default="{row}">
            <el-button v-if="row.status === 'pending'" link type="success" size="small" @click="handleConfirmInvoice(row)">确认</el-button>
            <el-button v-if="row.status !== 'voided'" link type="warning" size="small" @click="handleVoidInvoice(row)">作废</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteInvoice(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-text">暂无开票记录，点击右上角「录入开票」开始记录</div>
    </div>

    <!-- 回款 -->
    <div class="finance-card">
      <div class="fin-head">
        <div class="fin-title"><el-icon color="var(--el-color-success)"><Money /></el-icon> 回款记录</div>
        <div class="fin-summary">
          <span>累计 <b>{{ paymentSummary.totalCount }}</b> 次</span>
          <span>有效总额 <b class="red">{{ formatWan(paymentSummary.totalAmount) }}</b><template v-if="hasAmount(paymentSummary.totalAmount)"> 万</template></span>
          <el-button v-if="!readonly" size="small" type="primary" @click="showPaymentDialog = true">+ 录入回款</el-button>
        </div>
      </div>
      <el-table :data="payments" stripe size="small" v-if="payments.length" :row-class-name="ledgerRowClass">
        <el-table-column prop="paymentDate" label="日期" width="105" />
        <el-table-column prop="paymentAmount" label="金额(万)" width="85" align="right">
          <template #default="{row}">
            <template v-if="hasAmount(row.paymentAmount)"><b>{{ formatWan(row.paymentAmount) }}</b>万</template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="paymentMethod" label="方式" width="95" :formatter="dashIfEmpty" />
        <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip :formatter="dashIfEmpty" />
        <el-table-column label="状态" width="96">
          <template #default="{row}">
            <el-tooltip v-if="row.status === 'voided'" :content="'作废原因: ' + (row.voidedReason || '未填写')" placement="top">
              <el-tag size="small" :type="financeStatus(row.status).type">{{ financeStatus(row.status).text }}</el-tag>
            </el-tooltip>
            <el-tag v-else size="small" :type="financeStatus(row.status).type">{{ financeStatus(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!readonly" label="操作" width="150" align="center">
          <template #default="{row}">
            <el-button v-if="row.status === 'pending'" link type="success" size="small" @click="handleConfirmPayment(row)">确认</el-button>
            <el-button v-if="row.status !== 'voided'" link type="warning" size="small" @click="handleVoidPayment(row)">作废</el-button>
            <el-button link type="danger" size="small" @click="handleDeletePayment(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-text">暂无回款记录，点击右上角「录入回款」开始记录</div>
    </div>

    <!-- 【PR-18】口径脚注: 作废留痕不计入 -->
    <div class="ledger-note">
      已作废记录保留展示(悬停查看原因), 不计入上方累计与有效总额、不延长保护期。任何记录均可删除(裁决 2026-09-24): 删除必填原因并留审计痕迹, 数据行逻辑保留可回查; 更正常规口径仍推荐"作废"。
    </div>

    <!-- 【C11 补漏 2026-10-09】开票/回款弹窗此前无 :rules(第 1 轮所有权交界漏项):
         必填+金额>0 与 useCustomerDetail 提交守卫同口径, 校验不过不触达提交 -->
    <el-dialog v-model="showInvoiceDialog" title="录入开票" width="480px" :close-on-click-modal="false">
      <el-form ref="invoiceFormRef" :model="invoiceForm" :rules="ledgerRules.invoice" label-width="80px">
        <el-form-item label="开票日期" prop="invoiceDate"><el-date-picker v-model="invoiceForm.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="发票号码" prop="invoiceNumber"><el-input v-model="invoiceForm.invoiceNumber" /></el-form-item>
        <el-form-item label="发票类型"><el-select v-model="invoiceForm.invoiceType" style="width:100%"><el-option label="增值税专票" value="增值税专票" /><el-option label="增值税普票" value="增值税普票" /></el-select></el-form-item>
        <el-form-item label="开票单位"><el-input v-model="invoiceForm.invoiceCompany" /></el-form-item>
        <el-form-item label="金额(万)" prop="amount"><el-input-number v-model="invoiceForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showInvoiceDialog=false">取消</el-button><el-button type="primary" @click="submitInvoice">确认录入</el-button></template>
    </el-dialog>

    <el-dialog v-model="showPaymentDialog" title="录入回款" width="480px" :close-on-click-modal="false">
      <el-form ref="paymentFormRef" :model="paymentForm" :rules="ledgerRules.payment" label-width="80px">
        <el-form-item label="回款日期" prop="paymentDate"><el-date-picker v-model="paymentForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="金额(万)" prop="paymentAmount"><el-input-number v-model="paymentForm.paymentAmount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="回款方式"><el-input v-model="paymentForm.paymentMethod" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="paymentForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showPaymentDialog=false">取消</el-button><el-button type="primary" @click="submitPayment">确认录入</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { injectCustomerDetail } from '@/composables/useCustomerDetail'
import { formatWan, dashIfEmpty } from '@/utils/format'
// 【B7 2026-10-08】台账状态三态映射收敛到 utils/status-maps.js 唯一来源
import { FINANCE_LEDGER_STATUS, statusOf } from '@/utils/status-maps'

const {
  readonly, invoices, invoiceSummary, payments, paymentSummary,
  showInvoiceDialog, showPaymentDialog, invoiceForm, paymentForm,
  handleAddInvoice, handleDeleteInvoice, handleAddPayment, handleDeletePayment,
  handleConfirmInvoice, handleVoidInvoice, handleConfirmPayment, handleVoidPayment,
} = injectCustomerDetail()

// 已作废行置灰(记录保留展示, 金额不计入口径)
const ledgerRowClass = ({ row }) => (row.status === 'voided' ? 'ledger-voided-row' : '')
// 金额空值判定：仅 null/undefined/'' 算无数据；0 是有效金额，正常渲染（0.00万）
const hasAmount = (v) => v !== null && v !== undefined && v !== ''
// 【B7 2026-10-08】开票/回款两表状态章统一走 status-maps（文案+type 一处维护）
const financeStatus = (s) => statusOf(FINANCE_LEDGER_STATUS, s)

// 【C11 补漏 2026-10-09】弹窗内联校验: 必填+金额>0, 与 useCustomerDetail 提交守卫同口径
// (录入守卫历来拒 0 金额, 0 仅作为历史导入行的合法展示值)
const invoiceFormRef = ref(null)
const paymentFormRef = ref(null)
const amountRule = {
  required: true, trigger: 'blur',
  validator: (_r, v, cb) => (v === null || v === undefined || v === '' || Number(v) <= 0)
    ? cb(new Error('金额必须大于 0')) : cb(),
}
const ledgerRules = {
  invoice: {
    invoiceDate: [{ required: true, message: '请选择开票日期', trigger: 'change' }],
    invoiceNumber: [{ required: true, message: '请填写发票号码', trigger: 'blur' }],
    amount: [amountRule],
  },
  payment: {
    paymentDate: [{ required: true, message: '请选择回款日期', trigger: 'change' }],
    paymentAmount: [amountRule],
  },
}
const submitInvoice = async () => {
  try { await invoiceFormRef.value?.validate() } catch { return }
  handleAddInvoice()
}
const submitPayment = async () => {
  try { await paymentFormRef.value?.validate() } catch { return }
  handleAddPayment()
}
</script>

<style scoped>
.finance-card { background:#fafbfc; border-radius: var(--radius-md); padding:18px 20px; margin-bottom:14px; border:1px solid #f0f1f3 }
.fin-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; flex-wrap:wrap; gap:10px }
.fin-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333 }
.fin-summary { display:flex; align-items:center; gap:14px; font-size:13px; color:#666 }
.fin-summary b { color:#333 }.fin-summary .red { color:var(--el-color-danger); font-size:15px }
.empty-text { color: var(--text-placeholder); text-align:center; padding:24px 0; font-size:13px }
.ledger-note { color:var(--el-color-info); font-size:12px; padding:2px 4px 10px }
:deep(.ledger-voided-row) { color:#b9bcc2; text-decoration:line-through }
:deep(.ledger-voided-row .el-tag) { text-decoration:none }
</style>
