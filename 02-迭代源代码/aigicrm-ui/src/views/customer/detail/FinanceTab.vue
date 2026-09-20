<template>
  <div>
    <!-- 开票 -->
    <div class="finance-card">
      <div class="fin-head">
        <div class="fin-title"><el-icon><Document /></el-icon> 开票记录</div>
        <div class="fin-summary">
          <span>累计 <b>{{ invoiceSummary.totalCount }}</b> 次</span>
          <span>有效总额 <b class="red">{{ invoiceSummary.totalAmount }}</b> 万</span>
          <el-button v-if="!readonly" size="small" type="primary" @click="showInvoiceDialog = true">+ 录入开票</el-button>
        </div>
      </div>
      <el-table :data="invoices" stripe size="small" v-if="invoices.length" :row-class-name="ledgerRowClass">
        <el-table-column prop="invoiceDate" label="日期" width="105" />
        <el-table-column prop="invoiceNumber" label="发票号" width="140" />
        <el-table-column prop="invoiceType" label="类型" width="100">
          <template #default="{row}"><el-tag size="small">{{ row.invoiceType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="invoiceCompany" label="开票单位" min-width="130" />
        <el-table-column prop="amount" label="金额(万)" width="85" align="right">
          <template #default="{row}"><b>{{ row.amount }}</b>万</template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{row}">
            <el-tooltip v-if="row.status === 'voided'" :content="'作废原因: ' + (row.voidedReason || '未填写')" placement="top">
              <el-tag size="small" type="info">已作废</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.status === 'pending'" size="small" type="warning">待核对</el-tag>
            <el-tag v-else size="small" type="success">已确认</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!readonly" label="操作" width="150" align="center">
          <template #default="{row}">
            <el-button v-if="row.status === 'pending'" link type="success" size="small" @click="handleConfirmInvoice(row)">确认</el-button>
            <el-button v-if="row.status !== 'voided'" link type="warning" size="small" @click="handleVoidInvoice(row)">作废</el-button>
            <el-button v-if="row.status === 'pending'" link type="danger" size="small" @click="handleDeleteInvoice(row)">删除</el-button>
          </template>
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
          <span>有效总额 <b class="red">{{ paymentSummary.totalAmount }}</b> 万</span>
          <el-button v-if="!readonly" size="small" type="primary" @click="showPaymentDialog = true">+ 录入回款</el-button>
        </div>
      </div>
      <el-table :data="payments" stripe size="small" v-if="payments.length" :row-class-name="ledgerRowClass">
        <el-table-column prop="paymentDate" label="日期" width="105" />
        <el-table-column prop="paymentAmount" label="金额(万)" width="85" align="right">
          <template #default="{row}"><b>{{ row.paymentAmount }}</b>万</template>
        </el-table-column>
        <el-table-column prop="paymentMethod" label="方式" width="95" />
        <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="96">
          <template #default="{row}">
            <el-tooltip v-if="row.status === 'voided'" :content="'作废原因: ' + (row.voidedReason || '未填写')" placement="top">
              <el-tag size="small" type="info">已作废</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.status === 'pending'" size="small" type="warning">待核对</el-tag>
            <el-tag v-else size="small" type="success">已确认</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!readonly" label="操作" width="150" align="center">
          <template #default="{row}">
            <el-button v-if="row.status === 'pending'" link type="success" size="small" @click="handleConfirmPayment(row)">确认</el-button>
            <el-button v-if="row.status !== 'voided'" link type="warning" size="small" @click="handleVoidPayment(row)">作废</el-button>
            <el-button v-if="row.status === 'pending'" link type="danger" size="small" @click="handleDeletePayment(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-text">暂无回款记录</div>
    </div>

    <!-- 【PR-18】口径脚注: 作废留痕不计入 -->
    <div class="ledger-note">
      已作废记录保留展示(悬停查看原因), 不计入上方累计与有效总额、不延长保护期; 仅"待核对"暂录可删除, 已确认记录如需更正请使用"作废"。
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
  handleConfirmInvoice, handleVoidInvoice, handleConfirmPayment, handleVoidPayment,
} = injectCustomerDetail()

// 已作废行置灰(记录保留展示, 金额不计入口径)
const ledgerRowClass = ({ row }) => (row.status === 'voided' ? 'ledger-voided-row' : '')
</script>

<style scoped>
.finance-card { background:#fafbfc; border-radius:10px; padding:18px 20px; margin-bottom:14px; border:1px solid #f0f1f3 }
.fin-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; flex-wrap:wrap; gap:10px }
.fin-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333 }
.fin-summary { display:flex; align-items:center; gap:14px; font-size:13px; color:#666 }
.fin-summary b { color:#333 }.fin-summary .red { color:#f56c6c; font-size:15px }
.empty-text { color:#c0c4cc; text-align:center; padding:24px 0; font-size:13px }
.ledger-note { color:#909399; font-size:12px; padding:2px 4px 10px }
:deep(.ledger-voided-row) { color:#b9bcc2; text-decoration:line-through }
:deep(.ledger-voided-row .el-tag) { text-decoration:none }
</style>
