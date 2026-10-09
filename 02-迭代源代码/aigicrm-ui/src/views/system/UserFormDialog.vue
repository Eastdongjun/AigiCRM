<template>
  <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑用户' : '新增用户'" width="680px" :close-on-click-modal="false">
    <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="90px" class="user-form">
      <el-form-item label="所属组织">
        <el-cascader
          v-model="orgPath"
          :options="deptTree"
          :props="cascaderProps"
          placeholder="选择组织(总部 / 大区 / 办事处)"
          clearable filterable style="width:100%"
          @change="onOrgChange"
        />
        <div class="org-hint">
          <template v-if="editForm.officeId">已归属 {{ orgHintText }}</template>
          <template v-else>选到办事处层级才会归属公海池</template>
        </div>
      </el-form-item>
      <el-row :gutter="24">
        <el-col :span="12">
          <el-form-item label="账号" prop="username" v-if="!editForm.id">
            <div class="acct-hint" v-if="latestAccountHint">{{ latestAccountHint }}</div>
            <el-input v-model="editForm.username" placeholder="大区缩写+流水号" />
          </el-form-item>
          <el-form-item label="账号" v-else>
            <el-input :model-value="editForm.username" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="姓名" prop="realName">
            <el-input v-model="editForm.realName" placeholder="真实姓名" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="24">
        <el-col :span="12">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="editForm.phone" placeholder="手机号" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <!-- 【工作流复核确认修复】挂 prop=email: useUserList 的 email 校验规则已定义但 form-item 未挂 prop 从未生效 -->
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="editForm.email" placeholder="邮箱地址" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="24">
        <el-col :span="12">
          <el-form-item label="角色" prop="roleId">
            <el-select v-model="editForm.roleId" style="width:100%" placeholder="选择角色">
              <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="直接上级">
            <el-select v-model="editForm.managerId" style="width:100%" placeholder="选择上级" clearable filterable>
              <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <!-- 【2026-08-11 岗位绑定】岗位多选(第一个为主岗位), 岗位数据范围经权限范围管理器"按岗位"配置 -->
      <el-form-item label="岗位">
        <el-select v-model="editForm.positionIds" multiple style="width:100%" placeholder="选择岗位(可多选, 第一个为主岗位)">
          <el-option v-for="p in allPositions" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <div class="org-hint">岗位数据范围请在「系统设置 → 权限管理 → 按岗位」配置</div>
      </el-form-item>
      <el-row :gutter="24">
        <el-col :span="12">
          <!-- 【C11 2026-10-08】星标=真校验：补 prop 接入 formRules.dataScopeOverride，未选时字段下方红字（不再只靠保存时全局 toast） -->
          <el-form-item label="数据范围" prop="dataScopeOverride">
            <el-select v-model="editForm.dataScopeOverride" style="width:100%" placeholder="请选择数据范围">
              <el-option label="仅本人" value="self" />
              <el-option label="本人及下级" value="team" />
              <el-option label="本部门及以下" value="dept" />
              <el-option label="本办事处及以下" value="office" />
              <el-option label="本大区及以下" value="region" />
              <el-option label="全部(本集团)" value="all" />
              <el-option label="自定义组织" value="custom" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="禁用" />
          </el-form-item>
        </el-col>
      </el-row>
      <!-- 2026-08-08 用户级自定义授权组织：数据范围=custom 时勾选可见组织 -->
      <el-form-item v-if="editForm.dataScopeOverride === 'custom'" label="授权组织">
        <el-tree-select
          v-model="editForm.orgIds"
          :data="deptTree"
          :props="{ label: 'deptName', value: 'id', children: 'children' }"
          placeholder="勾选该人员可见的组织（含子级）"
          multiple check-strictly show-checkbox collapse-tags collapse-tags-tooltip
          style="width:100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { injectUserList } from '@/composables/useUserList'

const {
  dialogVisible, editForm, formRules, formRef, orgPath, deptTree, cascaderProps,
  onOrgChange, orgHintText, latestAccountHint, roles, userOptions, handleSave, saving,
  allPositions,
} = injectUserList()
</script>

<style scoped>
.org-hint { font-size: 12px; color: var(--el-color-info); line-height: 1.5; margin-top: 2px; }
.acct-hint { font-size: 12px; color: var(--el-color-success); margin-bottom: 6px }
.user-form :deep(.el-form-item) { margin-bottom: 18px }
</style>
