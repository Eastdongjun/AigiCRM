<template>
  <div class="section-block">
    <div class="section-head">
      <span class="section-icon warn"><el-icon><MapLocation /></el-icon></span>
      <span class="section-label">归属与负责人</span>
    </div>

    <div class="form-row two">
      <el-form-item label="所属大区" prop="regionId">
        <el-select v-model="form.regionId" placeholder="请选择大区" size="large" @change="onRegionChange">
          <el-option v-for="d in regions" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属办事处" prop="officeId">
        <el-select v-model="form.officeId" placeholder="请选择办事处" size="large" filterable :disabled="!form.regionId">
          <el-option v-for="o in filteredOffices" :key="o.id" :label="o.deptName" :value="o.id" />
        </el-select>
      </el-form-item>
      <!-- 【2026-09-13】移除「所属部门」下拉：后端 create/update 均按「第一负责人所在部门」
           重算 biz_customer.dept_id（ORG 数据范围锚点），用户选择会被覆盖 → 原下拉是误导性死字段 -->
      <el-form-item label="归属部门">
        <div class="field-static">自动：第一负责人所在部门</div>
        <div class="field-hint">客户归属部门由负责人所属部门自动确定，用于数据范围与部门统计</div>
      </el-form-item>
    </div>

    <div class="form-row two">
      <el-form-item label="第一负责人" prop="ownerId" required>
        <el-select v-model="form.ownerId" placeholder="请选择负责人" size="large" filterable :disabled="isPoolCustomer">
          <el-option v-for="u in users" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
        <div v-if="isPoolCustomer" class="field-hint">公海池内客户负责人不可修改，请通过捞取流程变更</div>
      </el-form-item>
      <!-- 【2026-09-13】管理上级：此前库/实体/列表列都在，但无任何录入入口，列表「上级」列恒为空 -->
      <el-form-item label="管理上级" prop="managerId">
        <el-select v-model="form.managerId" placeholder="选择上级（选填）" size="large" filterable clearable>
          <el-option v-for="u in users" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
        <div class="field-hint">客户管理上级（选填），列表「上级」列与客户详情据此显示</div>
      </el-form-item>
    </div>
  </div>
</template>

<script setup>
import { injectCustomerForm } from '@/composables/useCustomerForm'
import './add-common.css'

const {
  form, regions, filteredOffices, users,
  onRegionChange, isPoolCustomer,
} = injectCustomerForm()
</script>

<style scoped>
.field-hint {
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-info);
  margin-top: 4px;
}
.field-static {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 12px;
  font-size: 13px;
  color: #606266;
  background: #f7f8fa;
  border: 1px solid #e5e6eb;
  border-radius: var(--radius-sm);
}
</style>
