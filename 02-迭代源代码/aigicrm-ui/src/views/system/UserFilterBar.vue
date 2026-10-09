<template>
  <div class="section-box filter-box">
    <div class="filter-head">
      <div class="filter-title">
        <span class="filter-title-text">人员管理</span>
        <span class="filter-title-count">共 {{ total }} 人</span>
      </div>
      <el-button v-if="userStore.hasPermission('system:user:add')" type="primary" :icon="Plus" @click="openDialog()">新增用户</el-button>
    </div>
    <div class="filter-row">
      <el-input v-model="query.keyword" placeholder="搜索姓名 / 账号 / 手机号..." clearable prefix-icon="Search" class="filter-search" @keyup.enter="search" />
      <el-tree-select v-model="query.deptId" :data="deptTree" :props="{ label: 'deptName', value: 'id', children: 'children' }" check-strictly :render-after-expand="false" default-expand-all clearable filterable placeholder="全部部门" class="filter-select" @change="onDeptFilterChange" />
      <el-select v-model="query.status" placeholder="全部状态" clearable class="filter-select filter-status" @change="search">
        <el-option label="正常" :value="1" /><el-option label="禁用" :value="0" />
      </el-select>
    </div>
  </div>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import { injectUserList } from '@/composables/useUserList'

const { userStore, query, total, deptTree, search, onDeptFilterChange, openDialog } = injectUserList()
</script>

<style scoped>
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md); padding: 14px 20px; flex-shrink: 0 }
.filter-box { display: flex; flex-direction: column; gap: 12px; padding: 16px 20px }
.filter-head { display: flex; align-items: center; justify-content: space-between }
.filter-title { display: flex; align-items: baseline; gap: 10px }
.filter-title-text { font-size: 16px; font-weight: 700; color: #1f2329; letter-spacing: 0.5px }
.filter-title-count { font-size: 12px; color: var(--el-color-info); background: #f4f4f5; border-radius: 20px; padding: 2px 10px }
.filter-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap }
.filter-search { width: 260px }
.filter-select { width: 200px }
.filter-status { width: 120px }
.filter-box :deep(.el-input__wrapper) { border-radius: 8px }
.filter-box :deep(.el-select__wrapper) { border-radius: 8px }
</style>
