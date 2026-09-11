<template>
  <div class="section-block">
    <div class="section-head">
      <span class="section-icon"><el-icon><Document /></el-icon></span>
      <span class="section-label">基本信息</span>
    </div>

    <el-form-item label="订货客户全称" prop="customerName" class="full-width">
      <el-input v-model="form.customerName" placeholder="请输入订货客户全称" size="large" @blur="checkDup" />
      <div v-if="dupInfo.exists" class="dup-warning">
        <el-icon><WarningFilled /></el-icon>
        此客户已在 {{ dupInfo.createdAt }} 由 {{ dupInfo.createdBy }} 创建，请更换名称
      </div>
    </el-form-item>

    <div class="form-row two">
      <el-form-item label="开票名称">
        <el-input v-model="form.invoiceName" placeholder="请输入开票名称" />
      </el-form-item>
      <el-form-item label="产能（万吨）">
        <el-input v-model="form.capacity" placeholder="请输入数字" />
      </el-form-item>
    </div>

    <div class="form-row two">
      <el-form-item label="所属集团">
        <el-input v-model="form.groupName" placeholder="例如：陕煤集团" />
      </el-form-item>
      <el-form-item label="所属分公司">
        <el-input v-model="form.branchCompany" placeholder="例如：榆林化学" />
      </el-form-item>
    </div>

    <div class="form-row two">
      <el-form-item label="所属单位">
        <el-input v-model="form.unitName" placeholder="例如：选煤厂" />
      </el-form-item>
    </div>
  </div>
</template>

<script setup>
import { injectCustomerForm } from '@/composables/useCustomerForm'
import './add-common.css'

const { form, dupInfo, checkDup } = injectCustomerForm()
</script>

<style scoped>
.dup-warning {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #f56c6c;
  font-size: 12px;
  margin-top: 6px;
  padding: 8px 12px;
  background: #fef0f0;
  border-radius: 6px;
}
</style>
