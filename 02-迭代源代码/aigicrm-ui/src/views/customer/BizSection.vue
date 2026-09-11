<template>
  <div class="section-block">
    <div class="section-head">
      <span class="section-icon accent"><el-icon><PriceTag /></el-icon></span>
      <span class="section-label">业务分类</span>
      <span class="section-tip">必填项，决定客户归属的 Tab 分类</span>
    </div>

    <div class="form-row two">
      <el-form-item label="开发类型" prop="devType" required>
        <el-select v-model="form.devType" placeholder="请选择开发类型" size="large">
          <el-option v-for="t in devTypeOptions" :key="t" :label="t" :value="t">
            <span>{{ t }}</span>
            <span class="opt-tag" :style="devTypeTagStyle(t)">{{ devTypeTagText(t) }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="行业" prop="industry" required>
        <el-select v-model="form.industry" placeholder="请选择行业" size="large">
          <el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" />
        </el-select>
      </el-form-item>
    </div>
  </div>
</template>

<script setup>
import { injectCustomerForm } from '@/composables/useCustomerForm'
import './add-common.css'

const { form, devTypeOptions, industryOptions, devTypeTagStyle, devTypeTagText } = injectCustomerForm()
</script>

<style scoped>
.opt-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  margin-left: 8px;
}
</style>
