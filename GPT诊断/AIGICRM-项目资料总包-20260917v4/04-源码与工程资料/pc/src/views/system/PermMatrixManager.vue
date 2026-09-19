<template>
  <div>
    <template v-if="mode === 'overview'">
      <!-- 权限体系总览 -->
      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        <template #title>系统包含5种权限：菜单权限（能进哪个页面）、数据权限（能看到多少数据）、操作权限（能改/删数据）、组织管理权限（能管哪个组织的人）、激励专项权限（能编辑/删除激励）</template>
      </el-alert>

      <el-row :gutter="16">
        <el-col :span="12" v-for="item in permOverview" :key="item.title" style="margin-bottom:16px">
          <el-card shadow="hover">
            <template #header>
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span style="font-weight:600">{{ item.icon }} {{ item.title }}</span>
                <el-tag :type="item.tagType" size="small">{{ item.tag }}</el-tag>
              </div>
            </template>
            <div style="font-size:13px;color:#606266;line-height:1.8;min-height:80px">{{ item.desc }}</div>
            <div style="margin-top:12px">
              <span style="font-size:12px;color:#909399">配置入口：</span>
              <el-button v-for="btn in item.buttons" :key="btn.label" size="small" :type="btn.type || 'primary'" link @click="btn.tab ? activeTab=btn.tab : router.push(btn.href)">{{ btn.label }}</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <template v-else>
      <!-- 权限矩阵 -->
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px">
        <span style="font-size:13px;color:#666">【菜单权限】按角色分配：✓ 表示该角色能访问此页面/功能</span>
        <el-button size="small" @click="loadPermData">刷新</el-button>
      </div>
      <el-empty v-if="permLoadError" description="无权限查看权限矩阵（缺少 system:menu:view / system:role:view 权限）或加载失败" />
      <div v-else class="perm-table-wrap">
        <table class="perm-table">
          <thead>
            <tr>
              <th class="perm-module">模块</th>
              <th class="perm-item-col">权限项</th>
              <th v-for="r in allRoles" :key="r.id" class="perm-role-col">{{ r.roleName }}</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="mod in permModules" :key="mod.name">
              <tr v-for="(item, idx) in mod.items" :key="item.id" :class="{ 'first-in-group': idx === 0 }">
                <td v-if="idx === 0" :rowspan="mod.items.length" class="perm-module perm-module-name">{{ mod.name }}</td>
                <td class="perm-item-col">{{ item.label }}</td>
                <td v-for="r in allRoles" :key="r.id" class="perm-role-col" @click="togglePerm(r, item)">
                  <span v-if="hasPerm(r, item)" class="perm-yes">✓</span>
                  <span v-else class="perm-no">—</span>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { usePermissionData } from '@/composables/usePermissionData'
import './perm-common.css'

const props = defineProps({ mode: { type: String, default: 'matrix' } })
const router = useRouter()
const {
  activeTab, permOverview, allRoles, permModules, hasPerm, togglePerm, loadPermData, permLoadError,
} = usePermissionData()
</script>
