<template>
  <div class="recycle-page">
    <div class="page-header">
      <div>
        <h2>回收站</h2>
        <p class="page-desc">查看并恢复已删除的客户与人员（逻辑删除数据，仅管理员默认可见）</p>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon class="recycle-hint">
      <template #title>
        删除是终态留痕：这里列出的是仍可恢复的软删数据。恢复客户会校验同名活跃客户、原负责人是否在册、
        公海池是否残留；恢复人员后其角色/岗位/组织归属需重新分配（删除时已清除）。
      </template>
    </el-alert>

    <div class="filter-bar">
      <el-tabs v-model="activeTab" class="recycle-tabs" @tab-change="onTabChange">
        <el-tab-pane name="customers" label="已删除客户" />
        <el-tab-pane name="users" label="已删除人员" />
      </el-tabs>
    </div>

    <div class="toolbar">
      <el-input v-model="keyword" :placeholder="activeTab === 'customers' ? '搜索客户名称/编号' : '搜索姓名/账号'"
        clearable size="small" style="width: 240px" @keyup.enter="search" @clear="search" />
      <el-button size="small" @click="search">查询</el-button>
      <span class="count-hint">共 {{ total }} 条</span>
    </div>

    <div class="table-area">
      <el-table v-if="activeTab === 'customers'" v-loading="loading" :data="rows" stripe size="small"
        style="width:100%" empty-text="暂无已删除客户">
        <el-table-column prop="customerName" label="客户全称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="customerCode" label="编号" min-width="130" />
        <el-table-column label="原负责人" min-width="100">
          <template #default="{ row }">
            {{ row.ownerName || (row.ownerId ? `已删除用户(#${row.ownerId})` : '—') }}
          </template>
        </el-table-column>
        <el-table-column prop="industry" label="行业" min-width="90" />
        <el-table-column prop="devType" label="分类" min-width="130" />
        <el-table-column label="删除时间" min-width="140">
          <template #default="{ row }">{{ formatTime(row.deletedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="canRestore" label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleRestoreCustomer(row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else v-loading="loading" :data="rows" stripe size="small"
        style="width:100%" empty-text="暂无已删除人员">
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column label="原部门" min-width="140">
          <template #default="{ row }">{{ row.deptName || (row.deptId ? `已删除部门(#${row.deptId})` : '—') }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="120" />
        <el-table-column label="状态" min-width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="删除时间" min-width="140">
          <template #default="{ row }">{{ formatTime(row.deletedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="canRestore" label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleRestoreUser(row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="current" v-model:page-size="size" :total="total"
        :page-sizes="[20, 50, 100]" layout="sizes, prev, pager, next, jumper" size="small" background
        style="justify-content: flex-end; margin-top: 12px" @current-change="loadData" @size-change="onSizeChange" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDeletedCustomers, getDeletedUsers, restoreDeletedCustomer, restoreDeletedUser } from '@/api/recycle'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const canRestore = computed(() => userStore.hasPermission('system:recycle:restore'))

const activeTab = ref('customers')
const keyword = ref('')
const current = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref([])
const loading = ref(false)

const formatTime = (value) => {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const params = { current: current.value, size: size.value, keyword: keyword.value.trim() }
    const res = activeTab.value === 'customers'
      ? await getDeletedCustomers(params)
      : await getDeletedUsers(params)
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

function search() {
  current.value = 1
  loadData()
}

function onTabChange() {
  current.value = 1
  keyword.value = ''
  rows.value = []
  total.value = 0
  loadData()
}

function onSizeChange(val) {
  size.value = val
  current.value = 1
  loadData()
}

async function handleRestoreCustomer(row) {
  try {
    await ElMessageBox.confirm(
      `确认恢复客户“${row.customerName}”（编号 ${row.customerCode}）？恢复后回到原负责人名下，若存在同名活跃客户/负责人已删除/公海残留将被拒绝。`,
      '恢复客户',
      { confirmButtonText: '确认恢复', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    const res = await restoreDeletedCustomer(row.id)
    ElMessage.success(res.data?.message || '客户已恢复')
    loadData()
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  }
}

async function handleRestoreUser(row) {
  try {
    await ElMessageBox.confirm(
      `确认恢复人员“${row.realName || row.username}”（${row.username}）？恢复后需到「人员管理」重新分配角色（删除时角色/岗位/组织归属已清除）。`,
      '恢复人员',
      { confirmButtonText: '确认恢复', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    const res = await restoreDeletedUser(row.id)
    ElMessage.success(res.data?.message || '人员已恢复')
    loadData()
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  }
}

watch(activeTab, () => { rows.value = [] })
onMounted(loadData)
</script>

<style scoped>
.recycle-page { padding: 4px 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #333; }
.page-desc { color: var(--text-muted); font-size: 13px; font-weight: normal; margin-left: 8px; }
.recycle-hint { margin-bottom: 12px; }
.filter-bar { display: flex; align-items: center; justify-content: space-between; }
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.count-hint { color: var(--el-text-color-secondary); font-size: 12px; margin-left: 4px; }
.table-area { background: #fff; border-radius: 8px; padding: 12px; }
</style>
