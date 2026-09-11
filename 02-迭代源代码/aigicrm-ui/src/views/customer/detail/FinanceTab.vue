<template>
  <div>
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
  </div>
</template>

<script setup>
import { injectCustomerDetail } from '@/composables/useCustomerDetail'

const {
  readonly, invoices, invoiceSummary, payments, paymentSummary,
  showInvoiceDialog, showPaymentDialog, invoiceForm, paymentForm,
  handleAddInvoice, handleDeleteInvoice, handleAddPayment, handleDeletePayment,
} = injectCustomerDetail()
</script>

<style scoped>
.finance-card { background:#fafbfc; border-radius:10px; padding:18px 20px; margin-bottom:14px; border:1px solid #f0f1f3 }
.fin-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; flex-wrap:wrap; gap:10px }
.fin-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333 }
.fin-summary { display:flex; align-items:center; gap:14px; font-size:13px; color:#666 }
.fin-summary b { color:#333 }.fin-summary .red { color:#f56c6c; font-size:15px }
.empty-text { color:#c0c4cc; text-align:center; padding:24px 0; font-size:13px }
</style>
