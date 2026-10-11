<template>
  <div>
    <!-- 【裁决 2026-10-10】主体: 阶段性开票回款(半年/全年总额录入制) -->
    <div class="finance-card">
      <div class="fin-head">
        <div class="fin-title"><el-icon><Document /></el-icon> 阶段性开票回款</div>
        <div class="fin-summary">
          <span v-if="stageSummary.baseYear">
            按 <b>{{ stageSummary.baseYear }}</b> 年度：
            开票 <b class="red">{{ fmtWan(stageSummary.annualInvoiceAmount) }}</b> 万 ·
            回款 <b class="red">{{ fmtWan(stageSummary.annualPaymentAmount) }}</b> 万
          </span>
          <el-tag size="small" :type="coreType === 'core' ? 'success' : coreType === 'quasi' ? 'warning' : 'info'" effect="dark">{{ coreTypeLabel }}</el-tag>
          <el-button v-if="!readonly" size="small" type="primary" @click="openStageDialog(null)">+ 录入阶段数据</el-button>
        </div>
      </div>
      <el-table :data="stageRows" stripe size="small" v-if="stageRows.length">
        <el-table-column prop="periodLabel" label="周期" min-width="110">
          <template #default="{row}"><b>{{ row.periodLabel }}</b></template>
        </el-table-column>
        <el-table-column label="阶段内开票额(万)" min-width="120" align="right">
          <template #default="{row}">
            <template v-if="hasAmount(row.invoiceAmount)"><b>{{ formatWan(row.invoiceAmount) }}</b>万</template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="阶段性回款额(万)" min-width="120" align="right">
          <template #default="{row}">
            <template v-if="hasAmount(row.paymentAmount)"><b>{{ formatWan(row.paymentAmount) }}</b>万</template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="阶段差额(开票−回款)" min-width="120" align="right">
          <template #default="{row}">
            <span :class="stageGap(row) < 0 ? 'gap-neg' : ''">{{ formatWan(stageGap(row)) }}万</span>
          </template>
        </el-table-column>
        <el-table-column label="更新" min-width="150">
          <template #default="{row}">
            <span class="upd-meta">{{ row.updatedByName || '—' }} · {{ fmtStamp(row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!readonly" label="操作" width="80" align="center">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openStageDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-text">暂无阶段数据，点击右上角「录入阶段数据」开始记录（半年末 / 年末录入总额）</div>
      <div class="ledger-note">
        录入口径：半年末录「上半年 / 下半年」总额，年末录「全年」总额；全年行与上/下半年行独立录入，核心判定优先取全年行（未录时按上半年+下半年合计）。
        真核心 = 判定年度开票、回款均 ≥ 100 万；争核心 = 有阶段数据但未双达标。录入开票额 &gt; 0 的阶段会按阶段末日期顺延保护期。
      </div>
    </div>

    <!-- 历史逐笔明细(只读): 2026-10-10 前录入的发票/回款流水, 保留可查 -->
    <el-collapse class="history-collapse">
      <el-collapse-item name="history">
        <template #title>
          <span class="history-title"><el-icon><FolderOpened /></el-icon> 历史逐笔明细（只读）</span>
          <span class="history-count">开票 {{ invoiceSummary.totalCount || 0 }} 笔 · 回款 {{ paymentSummary.totalCount || 0 }} 笔</span>
        </template>

        <div class="finance-card history-card">
          <div class="fin-head">
            <div class="fin-title"><el-icon><Document /></el-icon> 开票记录</div>
            <div class="fin-summary">
              <span>累计 <b>{{ invoiceSummary.totalCount }}</b> 次</span>
              <span>有效总额 <b class="red">{{ formatWan(invoiceSummary.totalAmount || 0) }}</b> 万</span>
            </div>
          </div>
          <el-table :data="invoices" stripe size="small" v-if="invoices.length" :row-class-name="ledgerRowClass">
            <el-table-column prop="invoiceDate" label="日期" width="105" />
            <el-table-column prop="invoiceNumber" label="发票号" min-width="170" show-overflow-tooltip :formatter="dashIfEmpty" />
            <el-table-column prop="invoiceType" label="类型" width="100">
              <template #default="{row}"><el-tag size="small">{{ row.invoiceType }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="invoiceCompany" label="开票单位" min-width="130" show-overflow-tooltip :formatter="dashIfEmpty" />
            <el-table-column prop="amount" label="金额(万)" width="85" align="right">
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
          </el-table>
          <div v-else class="empty-text">暂无历史开票记录</div>
        </div>

        <div class="finance-card history-card">
          <div class="fin-head">
            <div class="fin-title"><el-icon color="var(--el-color-success)"><Money /></el-icon> 回款记录</div>
            <div class="fin-summary">
              <span>累计 <b>{{ paymentSummary.totalCount }}</b> 次</span>
              <span>有效总额 <b class="red">{{ formatWan(paymentSummary.totalAmount || 0) }}</b> 万</span>
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
          </el-table>
          <div v-else class="empty-text">暂无历史回款记录</div>
        </div>
        <div class="ledger-note">历史明细为 2026-10-10 逐笔录入制时期的台账（只读留痕）；已作废记录置灰悬停可查原因，不计入有效总额。</div>
      </el-collapse-item>
    </el-collapse>

    <!-- 阶段数据录入/编辑弹窗 -->
    <el-dialog v-model="showStageDialog" :title="isEditingStage ? '修改阶段数据' : '录入阶段数据'" width="500px" :close-on-click-modal="false">
      <el-form :model="stageForm" label-width="130px">
        <el-form-item label="周期" required>
          <div class="period-picker">
            <el-input-number v-model="stageForm.periodYear" :min="2000" :max="maxYear" controls-position="right" style="width:110px" />
            <span class="period-join">年</span>
            <el-select v-model="stageForm.periodType" style="flex:1">
              <el-option label="上半年" value="H1" />
              <el-option label="下半年" value="H2" />
              <el-option label="全年" value="FY" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="阶段内开票额(万)">
          <el-input-number v-model="stageForm.invoiceAmount" :min="0" :precision="2" :controls="false" placeholder="未开票可留空" style="width:100%" />
        </el-form-item>
        <el-form-item label="阶段性回款额(万)">
          <el-input-number v-model="stageForm.paymentAmount" :min="0" :precision="2" :controls="false" placeholder="未回款可留空" style="width:100%" />
        </el-form-item>
        <div class="dialog-tip">两项至少填一项且大于 0；同一周期重复保存即覆盖修改（改动留审计痕迹）。全年行年末录入，可与上/下半年合计不相等——判定优先取全年行。</div>
      </el-form>
      <template #footer>
        <el-button @click="showStageDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveStageAmount">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { injectCustomerDetail } from '@/composables/useCustomerDetail'
import { formatWan, dashIfEmpty } from '@/utils/format'
import { FINANCE_LEDGER_STATUS, statusOf } from '@/utils/status-maps'

const {
  readonly, invoices, invoiceSummary, payments, paymentSummary,
  stageSummary, showStageDialog, stageForm, openStageDialog, handleSaveStageAmount,
  coreType, coreTypeLabel,
} = injectCustomerDetail()

const stageRows = computed(() => stageSummary.value.rows || [])
const maxYear = new Date().getFullYear() + 1
// 弹窗标题: 该周期已有行 = 修改
const isEditingStage = computed(() => stageRows.value.some(
  (r) => r.periodYear === Number(stageForm.periodYear) && r.periodType === stageForm.periodType))

// 已作废历史行置灰(留痕展示, 金额不计入口径)
const ledgerRowClass = ({ row }) => (row.status === 'voided' ? 'ledger-voided-row' : '')
// 金额空值判定：仅 null/undefined/'' 算无数据；0 是有效金额
const hasAmount = (v) => v !== null && v !== undefined && v !== ''
const stageGap = (row) => (Number(row.invoiceAmount) || 0) - (Number(row.paymentAmount) || 0)
const fmtWan = (v) => formatWan(v)
const fmtStamp = (t) => (!t ? '—' : String(t).replace('T', ' ').slice(0, 16))
const financeStatus = (s) => statusOf(FINANCE_LEDGER_STATUS, s)
</script>

<style scoped>
.finance-card { background:#fafbfc; border-radius: var(--radius-md); padding:18px 20px; margin-bottom:14px; border:1px solid #f0f1f3 }
.fin-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; flex-wrap:wrap; gap:10px }
.fin-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333 }
.fin-summary { display:flex; align-items:center; gap:14px; font-size:13px; color:#666; flex-wrap:wrap }
.fin-summary b { color:#333 }.fin-summary .red { color:var(--el-color-danger); font-size:15px }
.empty-text { color: var(--text-placeholder); text-align:center; padding:24px 0; font-size:13px }
.ledger-note { color:var(--el-color-info); font-size:12px; padding:2px 4px 0 }
.upd-meta { color:#999; font-size:12px }
.gap-neg { color: var(--el-color-danger) }
:deep(.ledger-voided-row) { color:#b9bcc2; text-decoration:line-through }
:deep(.ledger-voided-row .el-tag) { text-decoration:none }

.history-collapse { margin-bottom:14px; background:#fff; border:1px solid #f0f1f3; border-radius: var(--radius-md) }
.history-collapse :deep(.el-collapse-item__header) { padding:0 20px; background:transparent; border-bottom-color:#f0f1f3 }
.history-collapse :deep(.el-collapse-item__wrap) { background:transparent }
.history-collapse :deep(.el-collapse-item__content) { padding:14px 20px }
.history-title { display:flex; align-items:center; gap:6px; font-size:14px; font-weight:600; color:#333 }
.history-count { font-size:12px; color:var(--el-color-info); margin-left:12px }
.history-card { margin-bottom:0 }
.history-card + .history-card { margin-top:12px }

.period-picker { display:flex; align-items:center; gap:8px; width:100% }
.period-join { color:#666; font-size:13px }
.dialog-tip { color:var(--el-color-info); font-size:12px; line-height:1.7; padding:4px 2px 0 }
</style>
