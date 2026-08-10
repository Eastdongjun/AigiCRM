<template>
  <div class="perm-module-manager">
    <div class="perm-layout">
      <!-- 左：模块导航 -->
      <div class="perm-sidebar">
        <div class="perm-sidebar-title">权限模块</div>
        <div v-for="m in modules" :key="m.name" class="perm-nav-item"
             :class="{ active: current === m.name }" @click="current = m.name">
          <span>{{ m.name }}</span>
          <span v-if="m.special" class="perm-special-badge">专项</span>
        </div>
      </div>

      <!-- 右：模块配置 -->
      <div class="perm-body">
        <template v-if="curModule">
          <!-- ① 角色权限矩阵 -->
          <div class="perm-block">
            <div class="perm-block-head">
              <b>{{ curModule.name }} · 角色权限</b>
              <span class="perm-block-tip">✓ = 该角色拥有此权限项（按角色分配）</span>
              <el-button size="small" style="margin-left:auto" @click="loadPermData">刷新</el-button>
            </div>
            <el-empty v-if="permLoadError" description="无权限查看权限矩阵或加载失败" />
            <div v-else class="perm-table-wrap">
              <table class="perm-table">
                <thead>
                  <tr><th class="perm-item">权限项</th><th v-for="r in allRoles" :key="r.id">{{ r.roleName }}</th></tr>
                </thead>
                <tbody>
                  <tr v-for="item in curModule.items" :key="item.id">
                    <td class="perm-item">{{ item.label }}</td>
                    <td v-for="r in allRoles" :key="r.id" class="perm-cell" @click="togglePerm(r, item)">
                      <span :class="hasPerm(r, item) ? 'perm-yes' : 'perm-no'">{{ hasPerm(r, item) ? '✓' : '—' }}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- ② 公司级可见（按人员） -->
          <div v-if="curModule.companyFlag" class="perm-block">
            <div class="perm-block-head"><b>{{ curModule.name }} · 公司级可见</b>
              <span class="perm-block-tip">按人员勾选谁能看全公司聚合</span>
            </div>
            <CompanyPermPanel :module="curModule.companyFlag" />
          </div>

          <!-- ③ 激励专项权限（按人员） -->
          <div v-if="curModule.incentivePerm" class="perm-block">
            <div class="perm-block-head"><b>激励专项权限</b>
              <span class="perm-block-tip">看全部=含未分享也可见；编辑/删除含看全部</span>
              <el-button type="primary" size="small" style="margin-left:auto" @click="openPermDialog">添加授权</el-button>
            </div>
            <el-table :data="permList" size="small" stripe>
              <el-table-column prop="realName" label="姓名" width="100" />
              <el-table-column prop="username" label="账号" width="110" />
              <el-table-column prop="officeName" label="所属" min-width="120" />
              <el-table-column label="看全部" width="80" align="center">
                <template #default="{ row }"><span :class="row.can_view_all ? 'perm-yes' : 'perm-off'">{{ row.can_view_all ? '✓' : '—' }}</span></template>
              </el-table-column>
              <el-table-column label="可编辑" width="80" align="center">
                <template #default="{ row }"><span :class="row.can_edit ? 'perm-yes' : 'perm-off'">{{ row.can_edit ? '✓' : '—' }}</span></template>
              </el-table-column>
              <el-table-column label="可删除" width="80" align="center">
                <template #default="{ row }"><span :class="row.can_delete ? 'perm-yes perm-yes-danger' : 'perm-off'">{{ row.can_delete ? '✓' : '—' }}</span></template>
              </el-table-column>
              <el-table-column label="" width="70" align="center">
                <template #default="{ row }"><el-button link type="danger" size="small" @click="revokePerm(row.id)">撤销</el-button></template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!permList.length" description="暂无授权人员，点击「添加授权」配置" :image-size="72" />
          </div>
        </template>
      </div>
    </div>
    <PermGrantDialog />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { usePermissionData } from '@/composables/usePermissionData'
import CompanyPermPanel from './CompanyPermPanel.vue'
import PermGrantDialog from './PermGrantDialog.vue'
import './perm-common.css'

const {
  allRoles, permModules, hasPerm, togglePerm, loadPermData, permLoadError,
  permList, openPermDialog, revokePerm, loadPermList,
} = usePermissionData()

const current = ref('')
const modules = computed(() => permModules.value.map(m => {
  const special = {}
  if (m.name === '四项激励') { special.companyFlag = 'incentive'; special.incentivePerm = true }
  else if (m.name === '学分管理') special.companyFlag = 'score'
  else if (m.name === '核心客户指标') special.companyFlag = 'indicator'
  return { ...m, special: special.companyFlag || special.incentivePerm }
}))
const curModule = computed(() => modules.value.find(m => m.name === current.value))

watch(modules, (list) => { if (!current.value && list.length) current.value = list[0].name }, { immediate: true })
watch(current, (name) => {
  const m = modules.value.find(x => x.name === name)
  if (m?.incentivePerm) loadPermList()
})
</script>

<style scoped>
.perm-module-manager { height: 100%; display: flex; flex-direction: column; }
.perm-layout { display: flex; flex: 1; min-height: 0; gap: 16px; }
.perm-sidebar { width: 180px; flex-shrink: 0; border-right: 1px solid #ebeef5; padding-right: 12px; overflow-y: auto; }
.perm-sidebar-title { font-size: 12px; color: #909399; padding: 4px 8px 8px; }
.perm-nav-item { padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 13px; color: #606266;
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 2px; }
.perm-nav-item:hover { background: #f5f7fa; }
.perm-nav-item.active { background: #ecf5ff; color: #409eff; font-weight: 600; }
.perm-special-badge { font-size: 11px; color: #e6a23c; background: #fdf6ec; border-radius: 3px; padding: 0 4px; }
.perm-body { flex: 1; overflow-y: auto; min-width: 0; }
.perm-block { margin-bottom: 20px; }
.perm-block-head { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; font-size: 14px; }
.perm-block-tip { font-size: 12px; color: #909399; }
.perm-table-wrap { overflow-x: auto; }
.perm-cell { cursor: pointer; text-align: center; }
.perm-yes { color: #67c23a; font-weight: 600; }
.perm-yes-danger { color: #f56c6c; }
.perm-no { color: #c0c4cc; }
.perm-off { color: #c0c4cc; }
</style>
