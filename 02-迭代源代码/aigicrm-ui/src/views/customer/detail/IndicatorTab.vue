<template>
  <div class="indicator-panel">
    <!-- 【裁决 2026-10-10】硬性门槛改年度口径; 判定三态(真核心/争核心/非核心)由后端按阶段总额算好回传 -->
    <div class="ind-row main-threshold">
      <span class="ind-num">01</span>
      <span class="ind-name">
        开票回款 · 年度硬性门槛 ≥ ¥100万/年
        <span class="ind-detail" v-if="stageSummary.baseYear">
          按 {{ stageSummary.baseYear }} 年度：开票 {{ annualInvoiceAmount }} 万 · 回款 {{ annualPaymentAmount }} 万
        </span>
      </span>
      <span class="ind-status" :class="coreType === 'core' ? 'pass' : coreType === 'quasi' ? 'half' : 'fail'">
        {{ coreType === 'core' ? '✓ 真核心' : coreType === 'quasi' ? '◐ 争核心' : '— 非核心' }}
      </span>
    </div>
    <div class="ind-row" v-for="(ind, idx) in indicatorDefs" :key="ind.code">
      <span class="ind-num">{{ ind.code }}</span>
      <span class="ind-name">{{ ind.label }}</span>
      <el-select v-model="indicatorForm[ind.field]" size="small" style="width:170px" :disabled="readonly">
        <el-option v-for="opt in ind.options" :key="opt.dictValue" :label="`${opt.dictValue}分 · ${opt.dictLabel}`" :value="parseInt(opt.dictValue)" />
      </el-select>
    </div>
    <div class="ind-actions">
      <span>五项总分 <b class="ind-total">{{ indicatorTotal }}</b> / 15（客情参考，不参与核心判定）</span>
      <el-button v-if="!readonly" type="primary" size="small" @click="handleSaveIndicator">保存指标</el-button>
    </div>
  </div>
</template>

<script setup>
import { injectCustomerDetail } from '@/composables/useCustomerDetail'

const {
  readonly, indicatorDefs, indicatorForm, indicatorTotal,
  coreType, stageSummary, annualInvoiceAmount, annualPaymentAmount,
  handleSaveIndicator,
} = injectCustomerDetail()
</script>

<style scoped>
.indicator-panel { }
.ind-row { display:flex; align-items:center; gap:14px; padding:12px 0; border-bottom:1px solid #f5f5f5 }
.ind-row:last-child { border-bottom:none }
.ind-num { width:28px; height:28px; border-radius:50%; background:var(--el-color-primary); color:#fff; display:flex; align-items:center; justify-content:center; font-size:12px; font-weight:700; flex-shrink:0 }
.ind-name { flex:1; font-size:14px; color:#333 }
.ind-detail { display:block; font-size:12px; color:var(--el-color-info); margin-top:2px }
.ind-status { font-size:13px; font-weight:600 }.ind-status.pass { color:#52c41a }.ind-status.half { color:#fa8c16 }.ind-status.fail { color:#999 }
.ind-actions { display:flex; justify-content:space-between; align-items:center; margin-top:16px; padding-top:14px; border-top:2px solid #f0f0f0; font-size:14px; color:#666 }
.ind-total { font-size:24px; color:var(--el-color-primary) }
.main-threshold { background:#fafbfc; border-radius:8px; padding:14px 16px; margin-bottom:4px }
</style>
