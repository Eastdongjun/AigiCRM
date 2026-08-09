<template>
  <div class="top-bar">
    <el-input v-model="searchText" placeholder="搜索客户名称..." clearable size="default" style="width:240px" @change="search" />
    <el-button @click="showAdvFilter = true"><el-icon><Filter /></el-icon> 高级筛选<el-tag v-if="advFilterCount > 0" size="small" type="warning" style="margin-left:4px">{{ advFilterCount }}</el-tag></el-button>
    <div class="top-bar-right">
      <el-button :icon="Upload" @click="showImportDialog = true">批量导入</el-button>
      <el-button :icon="Download" @click="handleExport">导出</el-button>
      <el-button type="primary" :icon="Plus" @click="$router.push('/customer/add')">新客户</el-button>
    </div>
  </div>
</template>

<script setup>
import { Upload, Download, Plus } from '@element-plus/icons-vue'
import { injectCustomerList } from '@/composables/useCustomerList'

const {
  searchText, search, showAdvFilter, advFilterCount, showImportDialog, handleExport,
} = injectCustomerList()
</script>

<style scoped>
.top-bar { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.top-bar-right { margin-left: auto; display: flex; gap: 8px; }
</style>
