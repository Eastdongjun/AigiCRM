<template>
  <el-dialog v-model="showPermDialog" width="540px" class="perm-grant-dlg">
    <template #header>
      <div class="perm-grant-header">
        <span class="perm-grant-header-icon"><el-icon :size="18"><Key /></el-icon></span>
        <div>
          <div class="perm-grant-header-title">添加激励专项授权</div>
          <div class="perm-grant-header-sub">可见性与 编辑/删除 分别配置名单（对齐公海权限形态）</div>
        </div>
      </div>
    </template>
    <div class="perm-grant-body">
      <!-- ① 可见性权限(共享区): 角色批量+人员树+两个查看开关 -->
      <!-- 【2026-11-03 权限中心 P3】按角色批量: 勾角色=其启用成员全体获得下方开关(提交时前端展开) -->
      <div class="perm-grant-sec">
        <div class="perm-grant-sec-title"><el-icon><View /></el-icon> 可见性权限</div>
        <el-select v-model="grantRoleIds" multiple collapse-tags collapse-tags-tooltip filterable clearable
                   placeholder="如「全员互看」= 勾销售员+行政+销售总监；人员与角色可同时勾选（并集）" style="width:100%">
          <el-option v-for="r in sectionRoleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
        </el-select>
        <div class="perm-tree-wrap" style="margin-top:10px">
          <div class="perm-tree-tools">
            <el-button size="small" :icon="Plus" @click="checkAllUsers">全选</el-button>
            <el-button size="small" @click="clearUsers">清空</el-button>
            <span class="perm-tree-count">已选 <b>{{ selectedUserCount }}</b> 人</span>
          </div>
          <el-scrollbar height="200px" class="perm-tree-scroll">
            <el-tree ref="userTreeRef" :data="orgUserTree" show-checkbox node-key="key"
              :props="{ label: 'label', children: 'children' }"
              :expand-on-click-node="false" default-expand-all
              @check="onTreeCheck" />
          </el-scrollbar>
          <div class="perm-tree-hint">按 大区 → 办事处 → 部门 → 人员 勾选，勾选上级自动选中其下所有人</div>
        </div>
        <div class="perm-grant-opts" style="margin-top:10px">
          <div class="perm-grant-opt">
            <el-checkbox v-model="permForm.canViewShared" />
            <div class="perm-grant-opt-text">
              <span class="perm-grant-opt-name">查看已分享</span>
              <span class="perm-grant-opt-desc">默认全员可见，取消勾选=收窄</span>
            </div>
          </div>
          <div class="perm-grant-opt">
            <el-checkbox v-model="permForm.canViewAll" />
            <div class="perm-grant-opt-text">
              <span class="perm-grant-opt-name">查看全部</span>
              <span class="perm-grant-opt-desc">含未分享记录</span>
            </div>
          </div>
        </div>
        <div class="perm-grant-hint">编辑 / 删除他人记录在下方折叠区单独配置名单</div>
      </div>

      <!-- ② 操作权限: 公海式独立折叠区(2026-11-05 用户选 A 对齐公海池级操作权限形态) -->
      <!-- 【2026-08-16 授权表化】编辑/删除按人勾选（替代 admin∨销售总监 角色判定），现按区独立名单 -->
      <el-collapse v-model="permOpenSections" class="perm-grant-collapse">
        <el-collapse-item name="edit">
          <template #title>
            <span class="perm-grant-collapse-title"><el-icon><EditPen /></el-icon> 编辑权限</span>
            <el-tag size="small" :type="editUserCount ? 'warning' : 'info'" style="margin-left:8px">{{ editUserCount }} 人</el-tag>
            <el-tag v-if="editRoleIds.length" size="small" type="success" style="margin-left:4px">{{ editRoleIds.length }} 角色</el-tag>
          </template>
          <el-select v-model="editRoleIds" multiple collapse-tags collapse-tags-tooltip filterable clearable size="small"
                     placeholder="勾选角色=其成员获得编辑权限（换届免改名单）" style="width:100%">
            <el-option v-for="r in sectionRoleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
          <div class="perm-tree-wrap" style="margin-top:8px">
            <div class="perm-tree-tools">
              <span class="perm-tree-count">已选 <b>{{ editUserCount }}</b> 人</span>
              <el-button size="small" link type="danger" style="margin-left:auto" @click="clearEditUsers">清空</el-button>
            </div>
            <el-scrollbar height="180px" class="perm-tree-scroll">
              <el-tree ref="editUserTreeRef" :data="orgUserTree" show-checkbox node-key="key"
                :props="{ label: 'label', children: 'children' }"
                :expand-on-click-node="false" default-expand-all
                @check="onEditTreeCheck" />
            </el-scrollbar>
          </div>
          <div class="perm-grant-hint">可追加/修改他人上传的激励记录（只加不减：已授予的查看权限不受影响）</div>
        </el-collapse-item>
        <el-collapse-item name="delete">
          <template #title>
            <span class="perm-grant-collapse-title"><el-icon><Delete /></el-icon> 删除权限</span>
            <el-tag size="small" :type="deleteUserCount ? 'warning' : 'info'" style="margin-left:8px">{{ deleteUserCount }} 人</el-tag>
            <el-tag v-if="deleteRoleIds.length" size="small" type="success" style="margin-left:4px">{{ deleteRoleIds.length }} 角色</el-tag>
          </template>
          <el-select v-model="deleteRoleIds" multiple collapse-tags collapse-tags-tooltip filterable clearable size="small"
                     placeholder="勾选角色=其成员获得删除权限（换届免改名单）" style="width:100%">
            <el-option v-for="r in sectionRoleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
          <div class="perm-tree-wrap" style="margin-top:8px">
            <div class="perm-tree-tools">
              <span class="perm-tree-count">已选 <b>{{ deleteUserCount }}</b> 人</span>
              <el-button size="small" link type="danger" style="margin-left:auto" @click="clearDeleteUsers">清空</el-button>
            </div>
            <el-scrollbar height="180px" class="perm-tree-scroll">
              <el-tree ref="deleteUserTreeRef" :data="orgUserTree" show-checkbox node-key="key"
                :props="{ label: 'label', children: 'children' }"
                :expand-on-click-node="false" default-expand-all
                @check="onDeleteTreeCheck" />
            </el-scrollbar>
          </div>
          <div class="perm-grant-hint">可删除他人上传的激励记录（只加不减：已授予的查看/编辑权限不受影响）</div>
        </el-collapse-item>
      </el-collapse>
    </div>
    <template #footer>
      <span class="perm-grant-footer-count">已选 <b>{{ totalPicked }}</b> 人</span>
      <el-button @click="showPermDialog=false">取消</el-button>
      <el-button type="primary" :icon="Check" @click="grantPerm">确认授权</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 共享"激励专项授权"弹窗（C4: 激励类别 tab 与权限矩阵共用 composable 单例状态，跨组件联动）
// 【2026-11-05 公海式分区授权】可见性共享区 + 编辑/删除独立折叠区(各自角色+人员名单);
//   提交侧逐人合并终态再分组调用(后端 grant 为四 flag 全量覆盖 upsert, 见 usePermissionData.grantPerm)
import { ref, computed, onMounted } from 'vue'
import { Plus, Key, View, EditPen, Delete, Check } from '@element-plus/icons-vue'
import { usePermissionData } from '@/composables/usePermissionData'
import { getRoleList } from '@/api/system'
import './perm-common.css'

const {
  showPermDialog, permForm, grantRoleIds, orgUserTree, userTreeRef, selectedUserCount,
  permOpenSections, editRoleIds, deleteRoleIds, editUserTreeRef, deleteUserTreeRef, editUserCount, deleteUserCount,
  checkAllUsers, clearUsers, onTreeCheck, grantPerm,
  onEditTreeCheck, onDeleteTreeCheck, clearEditUsers, clearDeleteUsers,
} = usePermissionData()

// 【2026-11-03 P3】按角色批量的数据源(角色列表, 挂载时一次拉取)
const roleOptions = ref([])
onMounted(async () => {
  try { roleOptions.value = (await getRoleList()).data || [] } catch (e) {}
})
// incentive_% 载体角色是 08-14 RBAC 收敛保留的内部绑定(分享可见/全量查看), 非批量授权目标:
// 其成员在 sys_user_role 是"附加行", 主角色展开取不到, 必须从三处角色选项中排除
const sectionRoleOptions = computed(() =>
  (roleOptions.value || []).filter(r => !(r.roleCode || '').startsWith('incentive_')))

// 页脚合计 = 三区勾选人员数(角色成员提交时展开, 不计入口)
const totalPicked = computed(() => Number(selectedUserCount.value || 0) + Number(editUserCount.value || 0) + Number(deleteUserCount.value || 0))
</script>
