<template>
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
              <!-- 【2026-11-01 用户令: 三级小类不要呈现】产品目录第三级(小类)自管理页撤下:
                   数据保留在分类树(上传弹窗"细分产品"下拉照常派生), 维护走 SQL/迁移 -->
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
          <span style="color: var(--text-muted);font-size:12px;margin-left:8px">顶级类别分值为0，子项设置实际分值</span>
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

    <!-- 【2026-11-03 权限中心收口; 2026-11-05 收敛进系统设置】激励专项授权区块与 PermGrantDialog 现位于 系统设置→权限中心 专项权限页签 -->
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, FolderOpened, Collection, ArrowDown } from '@element-plus/icons-vue'
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '@/api/incentive'
import { confirmAction } from '@/utils/confirm'
import { useUserStore } from '@/store/user'
import './perm-common.css'

const userStore = useUserStore()

const catLoadError = ref(false)
const categoryTree = ref([])
const collapsed = ref(new Set())
const categoryDialogVisible = ref(false)
const categoryForm = reactive({ id: null, parentId: 0, categoryName: '', categoryCode: '', score: 0, sortOrder: 0, status: 1 })
// 类别卡片彩色边条
const catAccents = ['var(--el-color-primary)', 'var(--chart-amber-deep)', '#8b5cf6', '#10b981', 'var(--chart-amber)', '#ef4444']
// 权限人员头像渐变（按姓名哈希取色）
const avatarPalette = [
  'linear-gradient(135deg, var(--el-color-primary), #36cfc9)',
  'linear-gradient(135deg,var(--chart-amber),var(--chart-amber-deep))',
  'linear-gradient(135deg,#8b5cf6,#6366f1)',
  'linear-gradient(135deg,#10b981,#34d399)',
  'linear-gradient(135deg,#f43f5e,var(--chart-amber-deep))',
]

// 【2026-11-01 用户令: 三级小类不要呈现】上级选项只列顶级(三级自管理页撤下, 数据层保留、
// 上传弹窗"细分产品"照常派生; 新增三级仅经 SQL/迁移, 避免 UI 创建不可见节点)
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

onMounted(() => {
  loadCategoryTree()
})
</script>
