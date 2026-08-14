<template>
  <el-dialog v-model="colPickerVisible" title="列设置" width="520px" :close-on-click-modal="false">
    <div style="margin-bottom:8px;display:flex;gap:8px">
      <el-button size="small" @click="setAllCols">全选</el-button>
      <el-button size="small" @click="clearCols">全不选</el-button>
    </div>
    <el-row :gutter="12">
      <el-col :span="8" v-for="col in allCols" :key="col.key" style="margin-bottom:6px">
        <el-checkbox v-model="visibleCols" :label="col.key" :value="col.key" border size="small" style="width:100%">{{ col.label }}</el-checkbox>
      </el-col>
    </el-row>
    <template #footer><el-button @click="colPickerVisible = false">取消</el-button><el-button type="primary" @click="colPickerVisible = false">确定</el-button></template>
  </el-dialog>
</template>

<script setup>
import { injectCustomerList } from '@/composables/useCustomerList'

const { colPickerVisible, allCols, visibleCols } = injectCustomerList()

const setAllCols = () => { visibleCols.value = allCols.map(c => c.key) }
const clearCols = () => { visibleCols.value = [] }
</script>
