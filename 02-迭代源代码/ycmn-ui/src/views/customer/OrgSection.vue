<template>
  <!-- ======== 归属与负责人 ======== -->
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
        <el-select v-model="form.officeId" placeholder="请选择办事处" size="large" filterable :disabled="!form.regionId" @change="onOfficeChange">
          <el-option v-for="o in filteredOffices" :key="o.id" :label="o.deptName" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属部门" prop="deptId">
        <el-select v-model="form.deptId" placeholder="选择部门（可留空）" size="large" filterable clearable :disabled="!form.officeId">
          <el-option v-for="d in filteredDepts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
      </el-form-item>
    </div>

    <div class="form-row two">
      <el-form-item label="第一负责人" prop="ownerId" required>
        <el-select v-model="form.ownerId" placeholder="请选择负责人" size="large" filterable :disabled="isPoolCustomer">
          <el-option v-for="u in users" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
        <div v-if="isPoolCustomer" class="field-hint">公海池内客户负责人不可修改，请通过捞取流程变更</div>
      </el-form-item>
    </div>
  </div>
</template>

<script setup>
import { injectCustomerForm } from '@/composables/useCustomerForm'
import './add-common.css'

const {
  form, regions, filteredOffices, filteredDepts, users,
  onRegionChange, onOfficeChange, isPoolCustomer,
} = injectCustomerForm()
</script>

<style scoped>
.field-hint {
  font-size: 12px;
  line-height: 1.4;
  color: #909399;
  margin-top: 4px;
}
</style>
