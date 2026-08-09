<template>
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
</template>

<script setup>
import { injectCustomerDetail } from '@/composables/useCustomerDetail'

const {
  readonly, indicatorDefs, indicatorForm, indicatorTotal, bothThresholdMet,
  onIndicatorChange, handleSaveIndicator,
} = injectCustomerDetail()
</script>

<style scoped>
.indicator-panel { }
.ind-row { display:flex; align-items:center; gap:14px; padding:12px 0; border-bottom:1px solid #f5f5f5 }
.ind-row:last-child { border-bottom:none }
.ind-num { width:28px; height:28px; border-radius:50%; background:#409eff; color:#fff; display:flex; align-items:center; justify-content:center; font-size:12px; font-weight:700; flex-shrink:0 }
.ind-name { flex:1; font-size:14px; color:#333 }
.ind-status { font-size:13px; font-weight:600 }.ind-status.pass { color:#52c41a }.ind-status.fail { color:#f56c6c }
.ind-actions { display:flex; justify-content:space-between; align-items:center; margin-top:16px; padding-top:14px; border-top:2px solid #f0f0f0; font-size:14px; color:#666 }
.ind-total { font-size:24px; color:#409eff }.ind-warn { color:#fa8c16; font-size:12px; margin-left:8px }
.main-threshold { background:#fafbfc; border-radius:8px; padding:14px 16px; margin-bottom:4px }
</style>
