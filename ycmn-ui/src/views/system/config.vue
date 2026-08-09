<template>
  <div class="config-page">

    <div class="section-box">
      <!-- 2026-08-08 布局重构：左侧导航 + 右侧内容 -->
      <el-tabs v-model="activeTab" tab-position="left" class="settings-tabs">
        <!-- 基本设置 -->
        <el-tab-pane name="basic">
          <template #label><span class="tab-label">⚙️ 基本设置</span></template>
          <BasicSettings />
        </el-tab-pane>

        <!-- 字典管理 -->
        <el-tab-pane name="dict">
          <template #label><span class="tab-label">📋 字典管理</span></template>
          <DictManager />
        </el-tab-pane>
        <!-- 公海配置 -->
        <el-tab-pane name="approvalConfig"><template #label><span class="tab-label">✅ 审批配置</span></template><ApprovalConfig /></el-tab-pane>
        <el-tab-pane name="sea">
          <template #label><span class="tab-label">🌊 公海配置</span></template>
          <SeaConfigManager />
        </el-tab-pane>
        <!-- 激励类别与权限 -->
        <el-tab-pane name="incentiveCategory">
          <template #label><span class="tab-label"><el-icon style="vertical-align:-2px;margin-right:4px"><Coin /></el-icon>激励类别与权限</span></template>
          <!-- 激励类别与权限：区块化布局（2026-08-08 重排，保证两个区块可靠显示） -->
          <div class="perm-page">
            <!-- 区块一：激励类别管理 -->
            <section class="perm-sec">
              <header class="perm-sec-head">
                <div class="perm-sec-title">
                  <span class="perm-sec-icon icon-cat"><el-icon :size="19"><FolderOpened /></el-icon></span>
                  <div class="perm-sec-text">
                    <h3>激励类别管理</h3>
                    <p>维护激励的一级 / 二级分类，配置名称、编码、分值与启用状态</p>
                  </div>
                </div>
                <el-button v-if="userStore.hasPermission('incentive:edit')" type="primary" size="small" :icon="Plus" @click="openCategoryDialog()">新增类别</el-button>
              </header>
              <div class="perm-sec-body">
                <div v-if="catLoadError" class="cat-empty cat-error">无权限查看激励类别（缺少 incentive:view 权限）或加载失败</div>
                <el-empty v-else-if="!categoryTree.length" description="暂无类别，点击「新增类别」创建" :image-size="72" />
                <div v-for="(top, gi) in categoryTree" :key="top.id" class="cat-group" :style="{ '--cat-accent': catAccents[gi % catAccents.length] }">
                  <div class="cat-group-head" @click="toggleCategory(top.id)">
                    <div class="cat-group-title">
                      <span class="cat-name">{{ top.categoryName }}</span>
                      <span class="cat-code">{{ top.categoryCode }}</span>
                      <el-tag :type="top.status === 1 ? 'success' : 'danger'" size="small" effect="light" round>{{ top.status === 1 ? '启用' : '禁用' }}</el-tag>
                      <span class="cat-count"><el-icon><Collection /></el-icon> {{ (top.children || []).length }} 个子项</span>
                    </div>
                    <div class="cat-group-actions" @click.stop>
                      <el-button v-if="userStore.hasPermission('incentive:edit')" link type="primary" size="small" @click="openCategoryDialog(top)">编辑</el-button>
                      <el-button v-if="userStore.hasPermission('incentive:edit')" link type="primary" size="small" @click="openCategoryDialog(null, top.id)">子项</el-button>
                      <el-button v-if="userStore.hasPermission('incentive:edit')" link type="danger" size="small" @click="handleDeleteCategory(top)">删除</el-button>
                    </div>
                    <span class="cat-arrow" :class="{ expanded: !collapsed.has(top.id) }"><el-icon><ArrowDown /></el-icon></span>
                  </div>
                  <div v-show="!collapsed.has(top.id)" class="cat-group-body">
                    <el-table v-if="top.children && top.children.length" :data="top.children" size="small" style="width:100%"
                      :header-cell-style="{ background: '#f7f9fc', color: '#4e5969', fontWeight: 600, borderBottom: '1px solid #eef0f4' }">
                      <el-table-column label="类别名称" min-width="160">
                        <template #default="{ row }">
                          <span class="cat-child-name">{{ row.categoryName }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column prop="categoryCode" label="编码" width="120">
                        <template #default="{ row }"><span class="cat-child-code">{{ row.categoryCode }}</span></template>
                      </el-table-column>
                      <el-table-column label="分值" width="80" align="center">
                        <template #default="{ row }">
                          <span v-if="row.score > 0" class="cat-score-pill">{{ row.score }} 分</span>
                          <span v-else class="cat-score-zero">—</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="排序" width="64" align="center">
                        <template #default="{ row }"><span class="cat-order">{{ row.sortOrder }}</span></template>
                      </el-table-column>
                      <el-table-column label="状态" width="76" align="center">
                        <template #default="{ row }">
                          <span :class="row.status === 1 ? 'cat-status-on' : 'cat-status-off'"><i></i>{{ row.status === 1 ? '启用' : '禁用' }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="操作" width="128" align="center">
                        <template #default="{ row }">
                          <div class="action-btns">
                            <el-button v-if="userStore.hasPermission('incentive:edit')" link type="primary" size="small" @click="openCategoryDialog(row)">编辑</el-button>
                            <el-button v-if="userStore.hasPermission('incentive:edit')" link type="danger" size="small" @click="handleDeleteCategory(row)">删除</el-button>
                          </div>
                        </template>
                      </el-table-column>
                    </el-table>
                    <div v-else class="cat-no-child">暂无子项</div>
                  </div>
                </div>
              </div>
            </section>

            <!-- 区块二：激励专项权限 -->
            <section class="perm-sec">
              <header class="perm-sec-head">
                <div class="perm-sec-title">
                  <span class="perm-sec-icon icon-perm"><el-icon :size="19"><Key /></el-icon></span>
                  <div class="perm-sec-text">
                    <h3>激励专项权限</h3>
                    <p>独立于菜单权限，按人授予：查看已分享 / 查看全部 / 编辑 / 删除</p>
                  </div>
                </div>
                <el-button type="primary" size="small" :icon="Plus" @click="openPermDialog">添加授权</el-button>
              </header>
              <div class="perm-sec-body">
                <el-table :data="permList" size="small" style="width:100%"
                  :header-cell-style="{ background: '#f7f9fc', color: '#4e5969', fontWeight: 600, borderBottom: '1px solid #eef0f4' }"
                  :row-style="{ height: '54px' }">
                  <el-table-column type="index" label="序号" width="56" align="center">
                    <template #default="{ $index }"><span class="perm-index">{{ $index + 1 }}</span></template>
                  </el-table-column>
                  <el-table-column label="人员" min-width="160">
                    <template #default="{ row }">
                      <div class="perm-person">
                        <span class="perm-avatar" :style="avatarStyle(row.realName)">{{ (row.realName || '?').charAt(0) }}</span>
                        <div class="perm-person-text">
                          <span class="perm-name">{{ row.realName }}</span>
                          <span class="perm-username">{{ row.username }}</span>
                        </div>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="组织 / 岗位" min-width="170">
                    <template #default="{ row }">
                      <div class="perm-org">
                        <span class="perm-office">{{ row.officeName || '—' }}</span>
                        <span class="perm-role" :class="roleClass(row.roleName)">{{ row.roleName || '—' }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="查看范围" min-width="150">
                    <template #default="{ row }">
                      <div class="perm-view">
                        <span v-if="row.can_view_shared" class="perm-pill pill-shared">已分享</span>
                        <span v-if="row.can_view_all" class="perm-pill pill-all">全部</span>
                        <span v-if="!row.can_view_shared && !row.can_view_all" class="perm-pill-off">仅本人</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="编辑" width="78" align="center">
                    <template #default="{row}"><span :class="row.can_edit ? 'perm-ok' : 'perm-off'" :title="row.can_edit ? '可编辑激励' : '不可编辑'">{{ row.can_edit ? '✓ 可编辑' : '—' }}</span></template>
                  </el-table-column>
                  <el-table-column label="删除" width="78" align="center">
                    <template #default="{row}"><span :class="row.can_delete ? 'perm-ok perm-ok-danger' : 'perm-off'" :title="row.can_delete ? '可删除激励' : '不可删除'">{{ row.can_delete ? '✓ 可删除' : '—' }}</span></template>
                  </el-table-column>
                  <el-table-column label="操作" width="70" align="center">
                    <template #default="{row}"><el-button link type="danger" size="small" @click="revokePerm(row.id)">撤销</el-button></template>
                  </el-table-column>
                </el-table>
                <el-empty v-if="!permList.length" description="暂无授权人员，点击「添加授权」配置" :image-size="72" />
              </div>
            </section>
          </div>
        </el-tab-pane>

        <!-- 权限体系总览 -->
        <el-tab-pane name="permOverview">
          <template #label><span class="tab-label">🔐 权限体系</span></template>
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
        </el-tab-pane>

        <!-- 权限矩阵 -->
        <el-tab-pane name="perm">
          <template #label><span class="tab-label">🗄️ 权限矩阵</span></template>
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
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 四项激励权限弹窗 -->
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

    <!-- 公海池编辑弹窗 -->
    <!-- 激励类别编辑弹窗 -->
    <el-dialog v-model="categoryDialogVisible" :title="categoryForm.id ? '编辑类别' : '新增类别'" width="500px">
      <el-form :model="categoryForm" label-width="80px">
        <el-form-item label="上级类别">
          <el-tree-select
            v-model="categoryForm.parentId"
            :data="categoryParentOptions"
            :props="{ label: 'categoryName', value: 'id', children: 'children' }"
            check-strictly
            placeholder="无（作为顶级类别）"
            style="width:100%"
            clearable
          />
        </el-form-item>
        <el-form-item label="类别名称">
          <el-input v-model="categoryForm.categoryName" placeholder="如：外出参观、涂层类" />
        </el-form-item>
        <el-form-item label="类别编码">
          <el-input v-model="categoryForm.categoryCode" placeholder="如：visit、trial_coating" />
        </el-form-item>
        <el-form-item label="分值">
          <el-input-number v-model="categoryForm.score" :min="0" :max="100" />
          <span style="color:#999;font-size:12px;margin-left:8px">顶级类别分值为0，子项设置实际分值</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="categoryForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import ApprovalConfig from '@/views/system/approvalConfig.vue'
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '@/api/incentive'
import { usePermissionData } from '@/composables/usePermissionData'
import DictManager from './DictManager.vue'
import SeaConfigManager from './SeaConfigManager.vue'
import BasicSettings from './BasicSettings.vue'

const userStore = useUserStore()
const activeTab = ref('basic')

// ===== 权限矩阵（共享状态已抽至 composables/usePermissionData.js）=====
const {
  permOverview, allRoles, menuTree, rolePermMap, permModules, hasPerm, togglePerm, loadPermData,
  permList, showPermDialog, permForm, allUsers, orgUserTree, userTreeRef, selectedUserCount,
  loadPermList, loadAllUsers, loadOrgUserTree, checkAllUsers, clearUsers, onTreeCheck, openPermDialog, grantPerm, revokePerm,
  permLoadError,
} = usePermissionData()
const token = localStorage.getItem('token')

// ===== 字典类型 =====
// ===== 公海池配置 =====
// ===== 激励类别 =====
const categoryTree = ref([])
const collapsed = ref(new Set())
const categoryDialogVisible = ref(false)
const categoryForm = reactive({ id: null, parentId: 0, categoryName: '', categoryCode: '', score: 0, sortOrder: 0, status: 1 })
// 类别卡片彩色边条
const catAccents = ['#409eff', '#f97316', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444']
// 权限人员头像渐变（按姓名哈希取色）
const avatarPalette = [
  'linear-gradient(135deg,#409eff,#36cfc9)',
  'linear-gradient(135deg,#f59e0b,#f97316)',
  'linear-gradient(135deg,#8b5cf6,#6366f1)',
  'linear-gradient(135deg,#10b981,#34d399)',
  'linear-gradient(135deg,#f43f5e,#f97316)',
]
const avatarStyle = (name) => {
  let h = 0
  for (const c of (name || '?')) h = (h * 31 + c.charCodeAt(0)) % 997
  return { background: avatarPalette[h % avatarPalette.length] }
}
// 岗位样式分类
const roleClass = (role) => {
  const r = role || ''
  if (r.includes('管理员')) return 'role-admin'
  if (r.includes('总监')) return 'role-director'
  if (r.includes('行政')) return 'role-admin'
  return 'role-sale'
}

const categoryParentOptions = computed(() =>
  categoryTree.value.map(c => ({ id: c.id, categoryName: c.categoryName }))
)

const toggleCategory = (id) => {
  const s = new Set(collapsed.value)
  s.has(id) ? s.delete(id) : s.add(id)
  collapsed.value = s
}

const loadCategoryTree = async () => {
  catLoadError.value = false
  try {
    const res = await getCategoryTree()
    categoryTree.value = res.data || []
  } catch (e) { catLoadError.value = true }
}

const openCategoryDialog = (row, parentId) => {
  if (row) {
    Object.assign(categoryForm, { id: row.id, parentId: row.parentId, categoryName: row.categoryName, categoryCode: row.categoryCode, score: row.score, sortOrder: row.sortOrder, status: row.status })
  } else {
    Object.assign(categoryForm, { id: null, parentId: parentId || 0, categoryName: '', categoryCode: '', score: parentId ? 1 : 0, sortOrder: 0, status: 1 })
  }
  categoryDialogVisible.value = true
}

const submitCategory = async () => {
  if (!categoryForm.categoryName.trim()) { ElMessage.warning('请输入类别名称'); return }
  try {
    if (categoryForm.id) {
      await updateCategory(categoryForm.id, categoryForm)
    } else {
      await createCategory(categoryForm)
    }
    ElMessage.success('保存成功')
    categoryDialogVisible.value = false
    loadCategoryTree()
  } catch (e) {}
}

const handleDeleteCategory = async (row) => {
  const hasChildren = row.children && row.children.length > 0
  if (hasChildren) {
    ElMessage.warning(`"${row.categoryName}" 下有 ${row.children.length} 个子项，请先删除子项`)
    return
  }
  try {
    await confirmAction(`确认删除 "${row.categoryName}"？删除后不可恢复。`, '删除激励类别', { confirmText: '确认删除' })
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    loadCategoryTree()
  } catch (e) {}
}

watch(activeTab, (tab) => { if (tab === 'perm') loadPermData() })

onMounted(() => {
  loadPermList()
  loadCategoryTree()
})
</script>

<style scoped>
.config-page { height: calc(100vh - 80px); display: flex; flex-direction: column }
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; padding: 16px; flex: 1; overflow: hidden; display: flex; flex-direction: column }
/* 左侧导航布局（2026-08-08 重构） */
.section-box :deep(.el-tabs) { flex: 1; display: flex; flex-direction: row; overflow: hidden }
.section-box :deep(.el-tabs__header) { width: 200px; flex-shrink: 0; margin: 0; border-right: 1px solid #ebeef5; padding: 8px 0 8px 0 }
.section-box :deep(.el-tabs__nav-wrap) { padding: 0 10px }
.section-box :deep(.el-tabs__nav) { float: none }
.section-box :deep(.el-tabs__active-bar) { display: none }
.section-box :deep(.el-tabs__item) { height: 42px; line-height: 42px; padding: 0 12px; margin: 3px 0; border-radius: 8px; font-size: 14px; color: #4b5563; transition: all .2s }
.section-box :deep(.el-tabs__item.is-active) { background: #ecf5ff; color: #409eff; font-weight: 600 }
.section-box :deep(.el-tabs__item:hover) { color: #409eff; background: #f5f7fa }
.section-box :deep(.el-tabs__content) { flex: 1; overflow: auto; padding: 4px 20px 16px; min-width: 0 }
.tab-label { display: inline-flex; align-items: center; gap: 7px; white-space: nowrap }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }

.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}

/* 激励类别与权限：区块化布局（2026-08-08 重排） */
.perm-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.perm-sec {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.03);
}
.perm-sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  background: linear-gradient(90deg, #f8fafc, #fff);
  border-bottom: 1px solid #eef0f4;
}
.perm-sec-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.perm-sec-icon {
  width: 40px;
  height: 40px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
}
.perm-sec-icon.icon-cat { background: linear-gradient(135deg, #409eff, #3a7afe); box-shadow: 0 4px 10px rgba(64, 158, 255, 0.22); }
.perm-sec-icon.icon-perm { background: linear-gradient(135deg, #8b5cf6, #6366f1); box-shadow: 0 4px 10px rgba(139, 92, 246, 0.22); }
.perm-sec-text h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}
.perm-sec-text p {
  margin: 3px 0 0;
  font-size: 12px;
  color: #86909c;
}
.perm-sec-body {
  padding: 16px 20px 20px;
}
.cat-empty {
  color: #909399;
  font-size: 13px;
  padding: 24px 0;
  text-align: center;
}
.cat-error { color: #f56c6c; }

/* 类别卡片（左侧彩色边条） */
.cat-group {
  position: relative;
  border: 1px solid #e9ecf1;
  border-radius: 10px;
  margin-bottom: 10px;
  overflow: hidden;
  background: #fff;
  transition: border-color 0.18s, box-shadow 0.18s;
}
.cat-group:hover { border-color: #d6dce4; box-shadow: 0 2px 8px rgba(16, 24, 40, 0.05); }
.cat-group::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 4px;
  background: var(--cat-accent, #409eff);
}
.cat-group-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px 12px 20px;
  background: #f8fafc;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.cat-group-head:hover { background: #f2f6fb; }
.cat-group-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
}
.cat-name { font-size: 14px; font-weight: 600; color: #1f2329; }
.cat-code {
  font-size: 11px;
  color: #7c8695;
  background: #e9edf3;
  border-radius: 5px;
  padding: 2px 7px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.cat-count {
  font-size: 12px;
  color: #909399;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.cat-count .el-icon { font-size: 13px; }
.cat-group-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}
.cat-group-head:hover .cat-group-actions { opacity: 1; }
.cat-arrow {
  font-size: 14px;
  color: #a8abb2;
  transition: transform 0.2s, color 0.2s;
  display: inline-flex;
}
.cat-arrow.expanded { transform: rotate(180deg); color: #409eff; }
.cat-group-body { padding: 8px 12px 10px; }
.cat-no-child { color: #c0c4cc; font-size: 13px; text-align: center; padding: 16px 0; }

/* 子类别表格 */
.cat-child-name { font-size: 13px; font-weight: 500; color: #303133; }
.cat-child-code {
  font-size: 11px;
  color: #7c8695;
  background: #f2f4f8;
  border-radius: 5px;
  padding: 2px 7px;
  font-family: ui-monospace, Menlo, monospace;
}
.cat-score-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.12), rgba(64, 158, 255, 0.05));
  color: #2f7fe0;
  font-size: 12px;
  font-weight: 600;
}
.cat-score-zero { color: #c0c4cc; }
.cat-order { color: #86909c; font-size: 12px; }
.cat-status-on, .cat-status-off { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; }
.cat-status-on { color: #10b981; font-weight: 600; }
.cat-status-on i, .cat-status-off i { width: 6px; height: 6px; border-radius: 50%; display: inline-block; }
.cat-status-on i { background: #10b981; box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15); }
.cat-status-off { color: #a8abb2; }
.cat-status-off i { background: #c0c4cc; }

/* 权限人员列表 */
.perm-index { color: #c0c4cc; font-size: 12px; }
.perm-person { display: flex; align-items: center; gap: 10px; }
.perm-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}
.perm-person-text { display: flex; flex-direction: column; min-width: 0; }
.perm-name { font-size: 13px; font-weight: 600; color: #1f2329; }
.perm-username { font-size: 11px; color: #a8abb2; }
.perm-org { display: flex; flex-direction: column; gap: 2px; }
.perm-office { font-size: 12px; color: #4e5969; }
.perm-role { align-self: flex-start; font-size: 11px; padding: 1px 8px; border-radius: 10px; }
.perm-role.role-admin { background: #ecf5ff; color: #409eff; }
.perm-role.role-director { background: #fdf2e6; color: #e6a23c; }
.perm-role.role-sale { background: #f0f9eb; color: #67c23a; }
.perm-view { display: flex; gap: 6px; flex-wrap: wrap; }
.perm-pill { font-size: 11px; padding: 2px 9px; border-radius: 10px; font-weight: 600; }
.pill-shared { background: #ecf5ff; color: #409eff; }
.pill-all { background: #f0f0ff; color: #8b5cf6; }
.perm-pill-off { font-size: 11px; color: #c0c4cc; }
.perm-ok { color: #10b981; font-size: 12px; font-weight: 600; }
.perm-ok-danger { color: #f56c6c; }
.perm-off { color: #d5dbe3; font-size: 13px; }

.action-btns {
  display: flex;
  justify-content: center;
  gap: 2px;
}
.action-btns .el-button {
  min-width: 44px;
}

/* 权限矩阵 */
.perm-table-wrap { overflow-x: auto; max-height: calc(100vh - 280px); overflow-y: auto }
.perm-table { width: 100%; border-collapse: collapse; font-size: 13px }
.perm-table th { position: sticky; top: 0; background: #f5f7fa; padding: 10px 12px; border-bottom: 2px solid #e4e7ed; text-align: center; font-weight: 600; color: #333; z-index: 1 }
.perm-table td { padding: 6px 12px; border-bottom: 1px solid #f0f0f0; text-align: center }
.perm-module { background: #fafbfc; font-weight: 600; color: #1f2329 }
.perm-module-name { text-align: left !important }
.perm-item-col { text-align: left !important; color: #555; min-width: 100px }
.perm-role-col { width: 70px; cursor: pointer; user-select: none }
.perm-role-col:hover { background: #f0f5ff }
.perm-yes { color: #409eff; font-weight: 700; font-size: 16px }
.perm-no { color: #ddd }
.first-in-group td { border-top: 2px solid #e8eaed }

/* 公海池可见范围树 */

/* 激励授权层级人员树 */
.perm-tree-wrap { width: 100%; border: 1px solid #e4e7ed; border-radius: 8px; overflow: hidden }
.perm-tree-tools { display: flex; align-items: center; gap: 8px; padding: 8px 10px; background: #fafbfc; border-bottom: 1px solid #eef0f4 }
.perm-tree-count { font-size: 12px; color: #909399; margin-left: auto }
.perm-tree-count b { color: #409eff; font-size: 14px }
.perm-tree-scroll :deep(.el-tree) { padding: 6px 4px }
.perm-tree-hint { font-size: 11px; color: #a0a6b0; padding: 6px 10px; border-top: 1px solid #eef0f4; background: #fcfcfd }

/* 添加授权弹窗 */
.perm-grant-header { display: flex; align-items: center; gap: 12px; }
.perm-grant-header-icon {
  width: 38px; height: 38px; border-radius: 10px;
  display: inline-flex; align-items: center; justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
  box-shadow: 0 4px 10px rgba(139, 92, 246, 0.22);
  flex-shrink: 0;
}
.perm-grant-header-title { font-size: 15px; font-weight: 600; color: #1f2329; }
.perm-grant-header-sub { font-size: 12px; color: #909399; margin-top: 2px; }
.perm-grant-body { display: flex; flex-direction: column; gap: 14px; padding-top: 4px; }
.perm-grant-sec-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: #303133;
  margin-bottom: 8px;
}
.perm-grant-sec-title .el-icon { color: #409eff; }
.perm-grant-opts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
}
.perm-grant-opt {
  display: flex; align-items: center; gap: 8px;
  padding: 9px 12px;
  border: 1px solid #e9ecf1;
  border-radius: 8px;
  background: #fafbfc;
  transition: border-color 0.15s, background 0.15s;
}
.perm-grant-opt:hover { border-color: #b3d3f8; background: #f5f9ff; }
.perm-grant-opt-text { display: flex; flex-direction: column; min-width: 0; }
.perm-grant-opt-name { font-size: 13px; font-weight: 600; color: #1f2329; }
.perm-grant-opt-desc { font-size: 11px; color: #a0a6b0; margin-top: 1px; }
.perm-grant-footer-count { font-size: 12px; color: #909399; margin-right: auto; }
.perm-grant-footer-count b { color: #409eff; font-size: 14px; }
</style>
