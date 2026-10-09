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
    <div class="form-row two">
      <!-- 【2026-09-13】终端：此前只有 Excel 导入能写，页面无任何入口（库内该列 596 行全为 0） -->
      <el-form-item label="终端" prop="hasTerminal">
        <el-select v-model="form.hasTerminal" placeholder="请选择" size="large">
          <el-option v-for="t in TERMINAL_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <div class="field-hint">是否终端客户（与导入模板「终端」同口径：1=是 0=否）</div>
      </el-form-item>
    </div>
  </div>
</template>

<script setup>
import { injectCustomerForm } from '@/composables/useCustomerForm'
import { TERMINAL_OPTIONS } from '@/utils/customer-fields'
import './add-common.css'

const { form, devTypeOptions, industryOptions, devTypeTagStyle, devTypeTagText } = injectCustomerForm()
</script>

<style scoped>
.opt-tag {
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 4px;
  margin-left: 8px;
}
.field-hint {
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-info);
  margin-top: 4px;
}
</style>
