<template>
  <div class="section-block">
    <div class="section-head">
      <span class="section-icon info"><el-icon><Location /></el-icon></span>
      <span class="section-label">地址信息</span>
    </div>

    <div class="form-row three">
      <el-form-item label="省/市/区">
        <el-cascader v-model="regionValue" :options="regionData" :props="{ expandTrigger: 'hover' }" placeholder="请选择省/市/区" clearable filterable style="width:100%" @change="handleRegionChange" />
      </el-form-item>
      <el-form-item label="厂址">
        <el-input v-model="form.address" placeholder="如：榆神工业区" />
      </el-form-item>
      <el-form-item label="保护期">
        <div class="protect-hint"><el-icon><Timer /></el-icon> 注册日/开票日 + 360天</div>
      </el-form-item>
    </div>

    <div class="form-row two">
      <el-form-item label="注册日">
        <el-date-picker v-model="form.registerDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" :disabled="!canEditProtectedDates" />
      </el-form-item>
      <el-form-item label="最近开票日">
        <!-- 裁决 2026-09-24: 纯推导字段(最后一次非作废开票),任何角色不可直改;补录/作废走客户财务页 -->
        <el-input :model-value="form.lastInvoiceDate || '尚未开票'" disabled class="derived-input" />
        <div class="field-hint">由开票记录自动推导（较晚于注册日者 + 360 天计保护期）；如需修正请到客户财务页补录或作废发票</div>
      </el-form-item>
    </div>
  </div>
</template>

<script setup>
import { injectCustomerForm } from '@/composables/useCustomerForm'
import './add-common.css'

const { form, regionValue, regionData, canEditProtectedDates, handleRegionChange } = injectCustomerForm()
</script>

<style scoped>
.field-hint {
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-info);
  margin-top: 4px;
}
.protect-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-color-primary);
  font-size: 13px;
  height: 32px;
  padding: 0 12px;
  background: var(--el-color-primary-light-9);
  border-radius: var(--radius-sm);
}
</style>
