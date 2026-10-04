<template>
  <el-dialog v-model="showAdvFilter" title="高级筛选" width="680px" :close-on-click-modal="false" destroy-on-close>
    <el-tabs v-model="advFilterTab" type="border-card">
      <el-tab-pane label="基本筛选" name="basic">
        <el-form label-width="70px" label-position="right">
          <el-row :gutter="16">
            <el-col :span="8"><el-form-item label="行业"><el-select v-model="advFilter.industry" clearable placeholder="全部" style="width:100%"><el-option v-for="ind in allIndustries" :key="ind" :label="ind" :value="ind"/></el-select></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="省"><el-input v-model="advFilter.province" placeholder="如：山西" clearable/></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="市"><el-input v-model="advFilter.city" placeholder="如：太原" clearable/></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="状态"><el-select v-model="advFilter.status" clearable placeholder="全部" style="width:100%"><el-option label="正常" value="normal"/><el-option label="预警" value="warning"/><el-option label="超期" value="expired"/></el-select></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="保护"><el-select v-model="advFilter.protect" clearable placeholder="全部" style="width:100%"><el-option label="保护中" value="active"/><el-option label="即将到期" value="expiring"/><el-option label="已到期" value="expired"/></el-select></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="负责人"><el-select v-model="advFilter.owner" clearable filterable placeholder="全部" style="width:100%"><el-option v-for="u in filterUserOptions" :key="u.id" :label="u.realName" :value="u.realName"/></el-select></el-form-item></el-col>
          </el-row>
          <el-divider content-position="left">日期范围</el-divider>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="注册日"><el-date-picker v-model="advFilter.regDate" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="到期日"><el-date-picker v-model="advFilter.expDate" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          </el-row>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="组织/人员" name="org">
        <el-form label-width="70px">
          <el-form-item label="所属部门" v-if="canFilterByOrg">
            <el-tree-select
              v-model="advFilter.deptId"
              :data="filterOrgTree"
              clearable
              check-strictly
              node-key="id"
              :props="deptTreeProps"
              placeholder="选择部门（含全部子部门）"
              style="width:100%"
              @change="onDeptFilterChange" />
          </el-form-item>
          <el-form-item label="组织" v-if="canFilterByOrg">
            <el-tree-select
              v-model="advFilter.orgIds"
              :data="filterOrgTree"
              multiple
              check-strictly
              show-checkbox
              node-key="id"
              :props="deptTreeProps"
              placeholder="选择组织（可多选）"
              style="width:100%"
              clearable
              collapse-tags
            />
            <div class="form-tip">选中组织后，将显示该组织下所有人员的客户</div>
          </el-form-item>
          <el-form-item label="人员" v-if="canFilterByUsers">
            <el-select v-model="advFilter.userIds" multiple filterable placeholder="选择人员（可多选）" style="width:100%" clearable collapse-tags>
              <el-option v-for="u in filterUserOptions" :key="u.id" :label="u.realName" :value="u.id"/>
            </el-select>
          </el-form-item>
          <el-empty v-if="!canFilterByOrg && !canFilterByUsers" description="当前账号无组织/人员筛选权限，仅可查看自己数据" :image-size="60"/>
        </el-form>
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button @click="clearAdvFilter">清空全部</el-button>
      <el-button @click="showAdvFilter = false">取消</el-button>
      <el-button type="primary" @click="showAdvFilter = false">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { injectCustomerList } from '@/composables/useCustomerList'

const {
  showAdvFilter, advFilterTab, advFilter, allIndustries,
  canFilterByOrg, canFilterByUsers, filterOrgTree, filterUserOptions,
  onDeptFilterChange, clearAdvFilter,
} = injectCustomerList()

// 【M3 2026-10-04】部门树与组织树同源（filterOrgTree 来自 /system/depts/tree，节点是后端 SysDept），
// label 统一取 deptName：后端 SysDept 无 orgName 字段，此前组织树按 orgName 取 label 节点全部空白。
// 两棵树 props 相同，合并为一个常量去重。
const deptTreeProps = { label: 'deptName', children: 'children' }
</script>
