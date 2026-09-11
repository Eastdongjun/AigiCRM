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
        <el-date-picker v-model="form.lastInvoiceDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" :disabled="!canEditProtectedDates" />
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
.protect-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #409eff;
  font-size: 13px;
  height: 32px;
  padding: 0 12px;
  background: #ecf5ff;
  border-radius: 6px;
}
</style>
