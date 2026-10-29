<template>
  <!-- 筛选栏 -->
  <div class="filter-strip">
    <div class="filter-left">
      <div class="filter-group">
        <span class="filter-label">部门</span>
        <el-tree-select
          v-model="selectedDeptId"
          :data="deptTree"
          clearable
          check-strictly
          node-key="id"
          :props="{ label: 'deptName', children: 'children' }"
          placeholder="选择部门（含全部子部门）"
          style="width:180px"
          @change="onDeptChange" />
      </div>
      <div class="filter-group">
        <span class="filter-label">人员</span>
        <el-select v-model="selectedUser" size="default" style="width:130px" clearable placeholder="全部" @change="selectedUser=$event||null">
          <el-option v-for="u in userList" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
      </div>
      <div class="filter-group">
        <span class="filter-label">周期</span>
        <el-select v-model="selectedPeriod" size="default" style="width:120px">
          <el-option v-for="p in periodOptions" :key="p.value" :label="p.label" :value="p.value" />
        </el-select>
      </div>
      <div class="filter-group">
        <span class="filter-label">行业</span>
        <el-select v-model="filterIndustry" size="default" style="width:130px" clearable placeholder="全部">
          <el-option v-for="ind in industryOptions" :key="ind" :label="ind" :value="ind" />
        </el-select>
      </div>
      <div class="filter-group">
        <span class="filter-label">时间</span>
        <el-date-picker
          v-model="filterDateRange"
          type="daterange"
          size="default"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="上传开始"
          end-placeholder="上传结束"
          style="width:230px"
        />
      </div>
      <div class="filter-divider"></div>
      <el-input v-model="searchText" placeholder="搜索文件名..." clearable size="default" style="width:160px" :prefix-icon="Search" />
      <el-input v-model="filterCustomer" placeholder="客户名称" clearable size="default" style="width:140px" />
    </div>
    <div class="filter-right">
      <!-- 左侧子分类选中联动标签: 面板收起时它是唯一的状态指示,可一键关闭恢复全部 -->
      <el-tag v-if="selectedCategoryName" closable size="small" effect="light" class="cat-filter-tag" @close="selectedCategoryId = null">
        分类：{{ selectedCategoryName }}
      </el-tag>
      <span class="file-count"><el-icon><Folder /></el-icon> {{ filteredRecords.length }} 个文件</span>
    </div>
  </div>
</template>

<script setup>
import { Search, Folder } from '@element-plus/icons-vue'
import { injectIncentiveUpload } from '@/composables/useIncentiveUpload'

const {
  selectedDeptId, deptTree, onDeptChange, selectedUser, userList,
  selectedPeriod, periodOptions, searchText, filterCustomer, filteredRecords,
  industryOptions, filterIndustry, filterDateRange,
  selectedCategoryId, selectedCategoryName,
} = injectIncentiveUpload()
</script>

<style scoped>
.filter-strip { display:flex; align-items:center; justify-content:space-between; background:#fff; border:1px solid #e8ecf1; border-radius: var(--radius-md); padding:10px 16px; flex-shrink:0 }
.filter-left { display:flex; align-items:center; gap:8px; flex-wrap:wrap }
.filter-group { display:flex; align-items:center; gap:6px }
.filter-label { font-size:12px; font-weight:600; color:#4e5969; white-space:nowrap }
.filter-divider { width:1px; height:22px; background:#e8ecf1; margin:0 4px }
.filter-right { display:flex; align-items:center; gap:10px }
.cat-filter-tag { font-weight:600 }
.file-count { display:flex; align-items:center; gap:4px; font-size:13px; font-weight:600; color:var(--el-color-primary); white-space:nowrap }
</style>
