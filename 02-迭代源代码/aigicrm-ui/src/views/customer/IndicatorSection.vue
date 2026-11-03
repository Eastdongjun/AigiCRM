<template>
  <div class="section-block">
    <div class="section-head">
      <span class="section-icon warn"><el-icon><StarFilled /></el-icon></span>
      <span class="section-label">客情 · 五项指标</span>
      <span class="section-tip">选填，可稍后在客户详情补充</span>
    </div>

    <div class="form-row three">
      <el-form-item v-for="f in indicatorFields" :key="f.field" :label="f.label">
        <el-select v-model="indicatorForm[f.field]" placeholder="请选择" size="large">
          <el-option
            v-for="opt in f.options"
            :key="opt.dictValue"
            :label="`${opt.dictValue}分 · ${opt.dictLabel}`"
            :value="Number(opt.dictValue)"
          />
        </el-select>
      </el-form-item>
    </div>

    <div class="indicator-sum">
      五项合计 <span class="num">{{ indicatorTotal }}</span> / {{ INDICATOR_MAX_SCORE }} 分 ·
      <!-- 【2026-11-03 门槛配置化】门槛由管理员在系统配置设定, 此处不再写死数字 -->
      客情参考，不参与核心判定（核心判定 = 年度开票、回款均达到系统设定的门槛，按阶段总额自动判定）
    </div>
  </div>
</template>

<script setup>
import { INDICATOR_FIELDS, INDICATOR_MAX_SCORE } from '@/utils/customer-fields'
import { injectCustomerForm } from '@/composables/useCustomerForm'
import './add-common.css'

// 字段定义与客户详情页共用同一份口径（utils/customer-fields.js），避免两处标签漂移
const indicatorFields = INDICATOR_FIELDS
const { indicatorForm, indicatorTotal } = injectCustomerForm()
</script>

<style scoped>
.indicator-sum {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-color-info);
  padding: 8px 12px;
  background: #fafbfc;
  border-radius: var(--radius-sm);
}
.indicator-sum .num {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-color-warning);
}
</style>
