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
    <el-form-item label="别名/简称" prop="alias" class="full-width">
      <el-input v-model="form.alias" placeholder="简称、曾用名（可选，搜索可命中）" maxlength="200" />
    </el-form-item>

    <div class="form-row two">
      <el-form-item label="开票名称">
        <el-input v-model="form.invoiceName" placeholder="请输入开票名称" />
      </el-form-item>
      <el-form-item label="产能（万吨）">
        <el-input v-model="form.capacity" type="number" placeholder="请输入数字，如 120" />
      </el-form-item>
    </div>

    <div class="form-row two">
      <!-- 【2026-09-13】所属集团：原为纯自由文本，同一集团易出现多种写法（"陕煤集团"/"陕煤"）；
           改为按数据范围返回的历史集团名可选可新建下拉。人工核对：sys_group 是「集团/法人租户配置」
           （运营方自己的集团，权限体系顶层单元），与本字段「客户所属集团」语义不同，不能混用。 -->
      <el-form-item label="所属集团">
        <el-select
          v-model="form.groupName"
          placeholder="选择或输入集团名称"
          size="large"
          filterable
          allow-create
          default-first-option
          clearable
        >
          <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
        </el-select>
        <div class="field-hint">优先选择已有集团名，避免同一集团多种写法；新集团名可直接输入</div>
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
import { computed } from 'vue'
import { injectCustomerForm } from '@/composables/useCustomerForm'
import './add-common.css'

const { form, dupInfo, checkDup, groupNameOptions } = injectCustomerForm()

// 历史集团名候选 + 当前值：历史自由文本值可能不在候选列表里，
// 补进选项以保证编辑旧客户时下拉能正常回显（el-select 对无匹配 option 的值不回显标签）
const groupOptions = computed(() => {
  const names = new Set(groupNameOptions.value)
  const current = String(form.groupName || '').trim()
  if (current) names.add(current)
  return [...names]
})
</script>

<style scoped>
.field-hint {
  font-size: 12px;
  line-height: 1.4;
  color: #909399;
  margin-top: 4px;
}
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
