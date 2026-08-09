<template>
  <el-dialog v-model="showPermDialog" width="540px" class="perm-grant-dlg">
    <template #header>
      <div class="perm-grant-header">
        <span class="perm-grant-header-icon"><el-icon :size="18"><Key /></el-icon></span>
        <div>
          <div class="perm-grant-header-title">添加激励专项授权</div>
          <div class="perm-grant-header-sub">选择人员并配置可见范围与操作权限</div>
        </div>
      </div>
    </template>
    <div class="perm-grant-body">
      <!-- 选择人员 -->
      <div class="perm-grant-sec">
        <div class="perm-grant-sec-title"><el-icon><User /></el-icon> 选择人员</div>
        <div class="perm-tree-wrap">
          <div class="perm-tree-tools">
            <el-button size="small" :icon="Plus" @click="checkAllUsers">全选</el-button>
            <el-button size="small" @click="clearUsers">清空</el-button>
            <span class="perm-tree-count">已选 <b>{{ selectedUserCount }}</b> 人</span>
          </div>
          <el-scrollbar height="240px" class="perm-tree-scroll">
            <el-tree ref="userTreeRef" :data="orgUserTree" show-checkbox node-key="key"
              :props="{ label: 'label', children: 'children' }"
              :expand-on-click-node="false" default-expand-all
              @check="onTreeCheck" />
          </el-scrollbar>
          <div class="perm-tree-hint">按 大区 → 办事处 → 部门 → 人员 勾选，勾选上级自动选中其下所有人</div>
        </div>
      </div>
      <!-- 权限配置 -->
      <div class="perm-grant-sec">
        <div class="perm-grant-sec-title"><el-icon><Lock /></el-icon> 权限配置</div>
        <div class="perm-grant-opts">
          <div class="perm-grant-opt">
            <el-checkbox v-model="permForm.canViewShared" />
            <div class="perm-grant-opt-text">
              <span class="perm-grant-opt-name">查看已分享</span>
              <span class="perm-grant-opt-desc">默认全员可见，可单独收窄</span>
            </div>
          </div>
          <div class="perm-grant-opt">
            <el-checkbox v-model="permForm.canViewAll" />
            <div class="perm-grant-opt-text">
              <span class="perm-grant-opt-name">查看全部</span>
              <span class="perm-grant-opt-desc">含未分享记录</span>
            </div>
          </div>
          <div class="perm-grant-opt">
            <el-switch v-model="permForm.canEdit" size="small" />
            <div class="perm-grant-opt-text">
              <span class="perm-grant-opt-name">编辑</span>
              <span class="perm-grant-opt-desc">可编辑他人激励记录</span>
            </div>
          </div>
          <div class="perm-grant-opt">
            <el-switch v-model="permForm.canDelete" size="small" />
            <div class="perm-grant-opt-text">
              <span class="perm-grant-opt-name">删除</span>
              <span class="perm-grant-opt-desc">可删除他人激励记录</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <span class="perm-grant-footer-count">已选 <b>{{ selectedUserCount }}</b> 人</span>
      <el-button @click="showPermDialog=false">取消</el-button>
      <el-button type="primary" :icon="Check" @click="grantPerm">确认授权</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 共享"激励专项授权"弹窗（C4: 激励类别 tab 与权限矩阵共用 composable 单例状态，跨组件联动）
import { Plus, Key, User, Lock, Check } from '@element-plus/icons-vue'
import { usePermissionData } from '@/composables/usePermissionData'
import './perm-common.css'

const {
  showPermDialog, permForm, orgUserTree, userTreeRef, selectedUserCount,
  checkAllUsers, clearUsers, onTreeCheck, grantPerm,
} = usePermissionData()
</script>
