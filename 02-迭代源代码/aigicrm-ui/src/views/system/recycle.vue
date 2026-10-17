<template>
  <div class="recycle-page">
    <!-- 深蓝渐变主视觉：标题 + 三类删除数据概览 -->
    <section class="recycle-hero">
      <div class="hero-copy">
        <span class="hero-eyebrow">系统管理 · 数据治理</span>
        <h2 class="hero-title">回收站</h2>
        <p class="hero-sub">查看、恢复或彻底清理已删除的客户、人员与文件</p>
      </div>
      <div class="hero-stats">
        <div v-for="item in statItems" :key="item.key" class="hero-metric" :class="{ active: activeTab === item.key }" @click="switchTab(item.key)">
          <span class="hm-icon"><el-icon><component :is="item.icon" /></el-icon></span>
          <span class="hm-body">
            <span class="hm-num">{{ counts[item.key] }}</span>
            <span class="hm-label">{{ item.label }}</span>
          </span>
        </div>
      </div>
    </section>

    <!-- 列表主卡 -->
    <section class="recycle-panel">
      <header class="panel-head">
        <div class="seg" role="tablist">
          <button
            v-for="tab in tabs" :key="tab.key" type="button" role="tab"
            :aria-selected="activeTab === tab.key"
            class="seg-item" :class="{ active: activeTab === tab.key }"
            @click="switchTab(tab.key)"
          >
            {{ tab.label }}
            <span class="seg-count">{{ tab.key === 'customers' ? counts.customers : tab.key === 'users' ? counts.users : counts.files }}</span>
          </button>
        </div>
        <div class="panel-actions">
          <el-input
            v-model="keyword" clearable class="search-input"
            :placeholder="searchPlaceholder"
            :prefix-icon="Search" @keyup.enter="search" @clear="search"
          />
          <el-button
            v-if="activeTab === 'files' && canPurge" type="danger" plain :icon="Delete"
            class="purge-btn" @click="handlePurgeFiles"
          >清空回收站</el-button>
          <el-tooltip content="刷新列表" placement="top">
            <el-button :icon="Refresh" circle class="refresh-btn" aria-label="刷新列表" @click="loadData" />
          </el-tooltip>
        </div>
      </header>

      <div class="panel-note">
        <el-icon class="note-icon"><InfoFilled /></el-icon>
        <span>删除为软删除留痕：客户恢复校验同名活跃卡、负责人与公海残留；人员恢复后需重新分配角色；文件删除先入回收站，保留期内可恢复，超期自动清理。</span>
      </div>

      <div v-if="firstLoading" class="table-skeleton"><el-skeleton :rows="6" animated /></div>

      <template v-else>
        <!-- 已删除客户 -->
        <el-table
          v-if="activeTab === 'customers'" v-loading="loading" :data="rows" class="recycle-table"
          style="width:100%" empty-text="暂无已删除客户"
        >
          <el-table-column label="客户全称" min-width="250">
            <template #default="{ row }">
              <div class="entity-cell clickable" @click.stop="openCustomerDetail(row)">
                <span class="entity-avatar avatar-customer" aria-hidden="true">{{ (row.customerName || '?').charAt(0) }}</span>
                <span class="entity-main">
                  <span class="entity-name">{{ row.customerName || '未命名客户' }}</span>
                  <span class="entity-meta">{{ row.customerCode || '-' }}</span>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="原负责人" min-width="130">
            <template #default="{ row }">
              <span v-if="row.ownerName" class="text-strong">{{ row.ownerName }}</span>
              <el-tag v-else-if="row.ownerId" size="small" type="warning" effect="light">负责人已删除</el-tag>
              <span v-else class="muted-text">未分配</span>
            </template>
          </el-table-column>
          <el-table-column prop="industry" label="行业" min-width="100">
            <template #default="{ row }"><span class="muted-text">{{ row.industry || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="devType" label="分类" min-width="140">
            <template #default="{ row }"><span class="type-chip">{{ row.devType || '未分类' }}</span></template>
          </el-table-column>
          <el-table-column label="删除时间" min-width="165">
            <template #default="{ row }"><span class="time-text">{{ formatTime(row.deletedAt) }}</span></template>
          </el-table-column>
          <el-table-column v-if="canRestore || canPurge" label="操作" width="225" align="right" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openCustomerDetail(row)">详情</el-button>
              <el-button v-if="canRestore" link type="primary" size="small" :icon="RefreshLeft" @click.stop="handleRestoreCustomer(row)">恢复</el-button>
              <el-button v-if="canPurge" link type="danger" size="small" @click.stop="handleHardDeleteCustomer(row)">彻底删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 已删除人员 -->
        <el-table
          v-else-if="activeTab === 'users'" v-loading="loading" :data="rows" class="recycle-table"
          style="width:100%" empty-text="暂无已删除人员"
        >
          <el-table-column label="人员" min-width="200">
            <template #default="{ row }">
              <div class="entity-cell clickable" @click.stop="openUserDetail(row)">
                <span class="entity-avatar avatar-user" aria-hidden="true">{{ (row.realName || row.username || '?').charAt(0) }}</span>
                <span class="entity-main">
                  <span class="entity-name">{{ row.realName || '未填写姓名' }}</span>
                  <span class="entity-meta">{{ row.username }}</span>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="原部门" min-width="160">
            <template #default="{ row }">
              <span v-if="row.deptName" class="text-strong">{{ row.deptName }}</span>
              <el-tag v-else-if="row.deptId" size="small" type="warning" effect="light">部门已删除</el-tag>
              <span v-else class="muted-text">未分配</span>
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" min-width="130">
            <template #default="{ row }"><span class="muted-text">{{ row.phone || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="账号状态" min-width="100" align="center">
            <template #default="{ row }">
              <span class="status-chip" :class="row.status === 1 ? 'on' : 'off'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="删除时间" min-width="165">
            <template #default="{ row }"><span class="time-text">{{ formatTime(row.deletedAt) }}</span></template>
          </el-table-column>
          <el-table-column v-if="canRestore || canPurge" label="操作" width="225" align="right" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openUserDetail(row)">详情</el-button>
              <el-button v-if="canRestore" link type="primary" size="small" :icon="RefreshLeft" @click.stop="handleRestoreUser(row)">恢复</el-button>
              <el-button v-if="canPurge" link type="danger" size="small" @click.stop="handleHardDeleteUser(row)">彻底删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 已删除文件 -->
        <el-table
          v-else v-loading="loading" :data="rows" class="recycle-table"
          style="width:100%" empty-text="暂无已删除文件"
        >
          <el-table-column label="文件" min-width="280">
            <template #default="{ row }">
              <div class="entity-cell clickable" @click.stop="openFileDetail(row)">
                <span class="entity-avatar avatar-file" aria-hidden="true"><el-icon><Document /></el-icon></span>
                <span class="entity-main">
                  <span class="entity-name" :title="row.fileName">{{ row.fileName }}</span>
                  <span class="entity-meta">{{ formatSize(row.fileSize) }}</span>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="归属" min-width="90" align="center">
            <template #default="{ row }">
              <span class="scope-chip" :class="'scope-' + (row.fileScope || 'other')">{{ scopeLabel(row.fileScope) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="上传人" min-width="120">
            <template #default="{ row }">
              <span v-if="row.uploaderName" class="text-strong">{{ row.uploaderName }}</span>
              <el-tag v-else-if="row.uploaderId" size="small" type="warning" effect="light">上传人已删除</el-tag>
              <span v-else class="muted-text">-</span>
            </template>
          </el-table-column>
          <el-table-column label="所在文件夹" min-width="150">
            <template #default="{ row }">
              <span v-if="row.folderName" class="muted-text">{{ row.folderName }}</span>
              <span v-else-if="row.folderId" class="muted-text">文件夹已删除</span>
              <span v-else class="muted-text">根目录</span>
            </template>
          </el-table-column>
          <el-table-column label="删除时间" min-width="165">
            <template #default="{ row }"><span class="time-text">{{ formatTime(row.deletedAt) }}</span></template>
          </el-table-column>
          <el-table-column label="内容状态" min-width="105" align="center">
            <template #default="{ row }">
              <span class="status-chip" :class="row.contentAvailable ? 'on' : 'off'">
                {{ row.contentAvailable ? '可恢复' : '已清除' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="canRestore || canPurge" label="操作" width="225" align="right" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openFileDetail(row)">详情</el-button>
              <el-button v-if="canRestore && row.contentAvailable" link type="primary" size="small" :icon="RefreshLeft" @click.stop="handleRestoreFile(row)">恢复</el-button>
              <el-button v-if="canPurge" link type="danger" size="small" @click.stop="handleHardDeleteFile(row)">彻底删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager-row">
          <span class="pager-hint">共 <b>{{ total }}</b> 条</span>
          <el-pagination
            v-model:current-page="current" v-model:page-size="size" :total="total"
            :page-sizes="[20, 50, 100]" layout="sizes, prev, pager, next" size="small" background
            @current-change="loadData" @size-change="onSizeChange"
          />
        </div>
      </template>
    </section>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" size="560px" destroy-on-close class="recycle-detail-drawer">
      <template #header>
        <div class="drawer-head">
          <span class="drawer-icon" :class="'drawer-icon-' + detailType">
            <el-icon><component :is="detailIcon" /></el-icon>
          </span>
          <span class="drawer-copy">
            <span class="drawer-title">{{ detailTitle }}</span>
            <span class="drawer-sub">{{ detailSubtitle }}</span>
          </span>
        </div>
      </template>

      <div v-loading="detailLoading" class="detail-body">
        <!-- 客户详情 -->
        <template v-if="detailType === 'customer' && detailData">
          <div class="detail-grid">
            <div class="detail-item"><span class="di-label">客户全称</span><span class="di-value">{{ detailData.customer.customerName || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">编号</span><span class="di-value mono-text">{{ detailData.customer.customerCode || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">开票名称</span><span class="di-value">{{ detailData.customer.invoiceName || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">原负责人</span><span class="di-value">{{ detailData.ownerName || (detailData.customer.ownerId ? '负责人已删除' : '未分配') }}</span></div>
            <div class="detail-item"><span class="di-label">行业</span><span class="di-value">{{ detailData.customer.industry || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">分类</span><span class="di-value">{{ detailData.customer.devType || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">省市县</span><span class="di-value">{{ [detailData.customer.province, detailData.customer.city, detailData.customer.district].filter(Boolean).join(' ') || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">厂址</span><span class="di-value">{{ detailData.customer.address || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">产能</span><span class="di-value">{{ detailData.customer.capacity || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">注册日</span><span class="di-value">{{ detailData.customer.registerDate || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">最近开票</span><span class="di-value">{{ detailData.customer.lastInvoiceDate || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">到期日</span><span class="di-value">{{ detailData.customer.expireDate || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">创建时间</span><span class="di-value">{{ formatTime(detailData.customer.createdAt) }}</span></div>
            <div class="detail-item"><span class="di-label">删除时间</span><span class="di-value">{{ formatTime(detailData.customer.updatedAt) }}</span></div>
          </div>
          <div class="detail-section-title">关联数据</div>
          <div class="related-grid">
            <span v-for="(count, label) in detailData.related" :key="label" class="related-chip" :class="{ zero: !count }">
              {{ label }} <b>{{ count }}</b>
            </span>
          </div>
          <div class="detail-warn">存在发票、回款、激励、指标、日志、阶段金额或审批单时不能彻底删除；分享、提醒、公海残留会随彻底删除一并清理。</div>
        </template>

        <!-- 人员详情 -->
        <template v-else-if="detailType === 'user' && detailData">
          <div class="detail-grid">
            <div class="detail-item"><span class="di-label">姓名</span><span class="di-value">{{ detailData.user.realName || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">账号</span><span class="di-value mono-text">{{ detailData.user.username || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">手机号</span><span class="di-value">{{ detailData.user.phone || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">邮箱</span><span class="di-value">{{ detailData.user.email || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">原部门</span><span class="di-value">{{ detailData.deptName || (detailData.user.deptId ? '部门已删除' : '未分配') }}</span></div>
            <div class="detail-item"><span class="di-label">账号状态</span><span class="di-value">{{ detailData.user.status === 1 ? '启用' : '停用' }}</span></div>
            <div class="detail-item"><span class="di-label">数据范围覆盖</span><span class="di-value">{{ detailData.user.dataScopeOverride || '未设置' }}</span></div>
            <div class="detail-item"><span class="di-label">离职状态</span><span class="di-value">{{ detailData.user.leaveStatus || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">创建时间</span><span class="di-value">{{ formatTime(detailData.user.createdAt) }}</span></div>
            <div class="detail-item"><span class="di-label">删除时间</span><span class="di-value">{{ formatTime(detailData.user.updatedAt) }}</span></div>
          </div>
          <div class="detail-section-title">关联数据</div>
          <div class="related-grid">
            <span v-for="(count, label) in detailData.related" :key="label" class="related-chip" :class="{ zero: !count }">
              {{ label }} <b>{{ count }}</b>
            </span>
          </div>
          <div class="detail-warn">存在名下客户、审批、激励、文件、日志等业务关联时不能彻底删除；角色、岗位、组织关联会在彻底删除时一并清理，系统日志保留账号名。</div>
        </template>

        <!-- 文件详情 -->
        <template v-else-if="detailType === 'file' && detailData">
          <div class="detail-grid">
            <div class="detail-item"><span class="di-label">文件名</span><span class="di-value">{{ detailData.file.fileName || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">大小</span><span class="di-value">{{ formatSize(detailData.file.fileSize) }}</span></div>
            <div class="detail-item"><span class="di-label">文件类型</span><span class="di-value">{{ detailData.fileType || '-' }}</span></div>
            <div class="detail-item"><span class="di-label">归属</span><span class="di-value">{{ scopeLabel(detailData.file.fileScope) }}</span></div>
            <div class="detail-item"><span class="di-label">上传人</span><span class="di-value">{{ detailData.file.uploaderName || (detailData.file.uploaderId ? '上传人已删除' : '-') }}</span></div>
            <div class="detail-item"><span class="di-label">所在文件夹</span><span class="di-value">{{ detailData.file.folderName || (detailData.file.folderId ? '文件夹已删除' : '根目录') }}</span></div>
            <div class="detail-item"><span class="di-label">内容状态</span><span class="di-value">{{ detailData.file.contentAvailable ? '可恢复' : '已清除' }}</span></div>
            <div class="detail-item"><span class="di-label">创建时间</span><span class="di-value">{{ formatTime(detailData.createdAt) }}</span></div>
            <div class="detail-item"><span class="di-label">删除时间</span><span class="di-value">{{ formatTime(detailData.file.deletedAt) }}</span></div>
          </div>
          <div class="detail-actions">
            <el-button v-if="detailData.file.contentAvailable" type="primary" plain :icon="Download" @click="downloadRecycleFile(detailData.file)">下载文件</el-button>
            <span v-else class="muted-text">文件内容已物理清除，仅保留删除记录</span>
          </div>
          <div class="detail-warn">文件仍被激励记录引用时不能彻底删除；彻底删除会同时清除物理内容与台账记录。</div>
        </template>
      </div>

      <template #footer>
        <div class="detail-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button v-if="canRestore && detailData && detailCanRestore" type="primary" plain @click="restoreFromDetail">恢复</el-button>
          <el-button v-if="canPurge && detailData" type="danger" @click="hardDeleteFromDetail">彻底删除</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Avatar, Document, Delete, Download, InfoFilled, Refresh, RefreshLeft, Search, User,
} from '@element-plus/icons-vue'
import {
  getDeletedCustomers, getDeletedUsers, getDeletedFiles,
  restoreDeletedCustomer, restoreDeletedUser, restoreDeletedFile, purgeDeletedFiles,
  getDeletedCustomerDetail, getDeletedUserDetail, getDeletedFileDetail,
  hardDeleteCustomer, hardDeleteUser, hardDeleteFile,
} from '@/api/recycle'
import { downloadFile } from '@/utils/download'
import { getToken } from '@/utils/token'
import { formatSize } from '@/utils/format'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const canRestore = computed(() => userStore.hasPermission('system:recycle:restore'))
const canPurge = computed(() => userStore.hasPermission('system:recycle:purge'))

const tabs = [
  { key: 'customers', label: '已删除客户' },
  { key: 'users', label: '已删除人员' },
  { key: 'files', label: '已删除文件' },
]
const statItems = [
  { key: 'customers', label: '已删除客户', icon: User },
  { key: 'users', label: '已删除人员', icon: Avatar },
  { key: 'files', label: '已删除文件', icon: Document },
]
const activeTab = ref('customers')
const keyword = ref('')
const current = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref([])
const loading = ref(false)
const firstLoading = ref(true)
const counts = reactive({ customers: 0, users: 0, files: 0 })

// 详情抽屉
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailType = ref('')
const detailData = ref(null)
const detailRow = ref(null)
const detailTitle = computed(() => ({ customer: '客户详情', user: '人员详情', file: '文件详情' }[detailType.value] || '详情'))
const detailIcon = computed(() => ({ customer: User, user: Avatar, file: Document }[detailType.value] || Document))
const detailSubtitle = computed(() => {
  if (!detailData.value) return ''
  if (detailType.value === 'customer') return detailData.value.customer?.customerCode || ''
  if (detailType.value === 'user') return detailData.value.user?.username || ''
  return detailData.value.file?.fileName || ''
})
const detailCanRestore = computed(() => {
  if (detailType.value === 'file') return Boolean(detailData.value?.file?.contentAvailable)
  return Boolean(detailData.value)
})

const formatTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const scopeLabel = (scope) => ({ public: '公共', private: '私有', incentive: '激励' }[scope] || scope || '-')

const searchPlaceholder = computed(() => ({
  customers: '搜索客户名称或编号',
  users: '搜索姓名或账号',
  files: '搜索文件名或存储路径',
}[activeTab.value] || '搜索'))

async function loadData() {
  loading.value = true
  try {
    const params = { current: current.value, size: size.value, keyword: keyword.value.trim() }
    const res = activeTab.value === 'customers'
      ? await getDeletedCustomers(params)
      : activeTab.value === 'users'
        ? await getDeletedUsers(params)
        : await getDeletedFiles(params)
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
    counts[activeTab.value] = total.value
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  } finally {
    loading.value = false
    firstLoading.value = false
  }
}

async function loadCounts() {
  try {
    const [customers, users, files] = await Promise.all([
      getDeletedCustomers({ current: 1, size: 1 }),
      getDeletedUsers({ current: 1, size: 1 }),
      getDeletedFiles({ current: 1, size: 1 }),
    ])
    counts.customers = customers.data?.total || 0
    counts.users = users.data?.total || 0
    counts.files = files.data?.total || 0
  } catch (e) {
    // 计数失败不阻断列表
  }
}

function search() {
  current.value = 1
  loadData()
}

function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
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
      `确认恢复客户“${row.customerName}”（编号 ${row.customerCode}）？恢复后回到原负责人名下；若存在同名活跃客户、负责人已删除或公海残留，系统将拒绝恢复。`,
      '恢复客户',
      { confirmButtonText: '确认恢复', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    const res = await restoreDeletedCustomer(row.id)
    ElMessage.success(res.data?.message || '客户已恢复')
    await Promise.all([loadData(), loadCounts()])
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  }
}

async function handleRestoreUser(row) {
  try {
    await ElMessageBox.confirm(
      `确认恢复人员“${row.realName || row.username}”（${row.username}）？恢复后需到人员管理重新分配角色，删除时角色、岗位与组织归属已清除。`,
      '恢复人员',
      { confirmButtonText: '确认恢复', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    const res = await restoreDeletedUser(row.id)
    ElMessage.success(res.data?.message || '人员已恢复')
    await Promise.all([loadData(), loadCounts()])
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  }
}

async function handleRestoreFile(row) {
  try {
    await ElMessageBox.confirm(
      `确认恢复文件“${row.fileName}”？系统会先校验文件内容是否仍在存储中、原文件夹是否存在；私有文件还会校验上传者配额。`,
      '恢复文件',
      { confirmButtonText: '确认恢复', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    const res = await restoreDeletedFile(row.id)
    ElMessage.success(res.data?.message || '文件已恢复')
    await Promise.all([loadData(), loadCounts()])
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  }
}

async function handlePurgeFiles() {
  try {
    await ElMessageBox.confirm(
      `确认清空文件回收站？将物理删除当前 ${counts.files} 个已删除文件的存储内容与记录，此操作不可恢复。`,
      '清空文件回收站',
      { confirmButtonText: '确认清空', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    const res = await purgeDeletedFiles()
    ElMessage.success(res.data?.message || '已清空文件回收站')
    current.value = 1
    await Promise.all([loadData(), loadCounts()])
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  }
}

// ===== 详情 =====
async function openDetail(type, row, fetcher) {
  detailType.value = type
  detailRow.value = row
  detailData.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await fetcher(row.id)
    detailData.value = res.data || null
  } catch (e) {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const openCustomerDetail = (row) => openDetail('customer', row, getDeletedCustomerDetail)
const openUserDetail = (row) => openDetail('user', row, getDeletedUserDetail)
const openFileDetail = (row) => openDetail('file', row, getDeletedFileDetail)

// ===== 彻底删除 =====
const HARD_DELETE_LABEL = { customer: '客户', user: '人员', file: '文件' }

async function hardDelete(kind, row) {
  const label = HARD_DELETE_LABEL[kind]
  const name = kind === 'customer' ? row.customerName : kind === 'user' ? (row.realName || row.username) : row.fileName
  try {
    await ElMessageBox.confirm(
      `确认彻底删除${label}“${name}”？此操作不可恢复；如存在业务关联（发票、回款、激励、日志等）系统会拒绝并说明原因。`,
      `彻底删除${label}`,
      { confirmButtonText: '彻底删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    const res = kind === 'customer' ? await hardDeleteCustomer(row.id)
      : kind === 'user' ? await hardDeleteUser(row.id)
        : await hardDeleteFile(row.id)
    ElMessage.success(res.data?.message || `${label}已彻底删除`)
    detailVisible.value = false
    await Promise.all([loadData(), loadCounts()])
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出（业务关联拒绝原因在 message 中）
  }
}

const handleHardDeleteCustomer = (row) => hardDelete('customer', row)
const handleHardDeleteUser = (row) => hardDelete('user', row)
const handleHardDeleteFile = (row) => hardDelete('file', row)
const hardDeleteFromDetail = () => {
  if (!detailRow.value) return
  hardDelete(detailType.value, detailRow.value)
}

async function restoreFromDetail() {
  if (!detailRow.value) return
  const row = detailRow.value
  try {
    if (detailType.value === 'customer') {
      const res = await restoreDeletedCustomer(row.id)
      ElMessage.success(res.data?.message || '客户已恢复')
    } else if (detailType.value === 'user') {
      const res = await restoreDeletedUser(row.id)
      ElMessage.success(res.data?.message || '人员已恢复')
    } else {
      const res = await restoreDeletedFile(row.id)
      ElMessage.success(res.data?.message || '文件已恢复')
    }
    detailVisible.value = false
    await Promise.all([loadData(), loadCounts()])
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  }
}

async function downloadRecycleFile(row) {
  await downloadFile(`/api/system/recycle/files/${row.id}/content`, {
    name: row.fileName || '文件',
    headers: { Authorization: `Bearer ${getToken()}` },
  })
}

onMounted(() => {
  loadData()
  loadCounts()
})
</script>

<style scoped>
.recycle-page { display: flex; flex-direction: column; gap: 16px; }

/* ===== 主视觉 ===== */
.recycle-hero {
  position: relative; overflow: hidden;
  display: flex; align-items: center; justify-content: space-between; gap: 28px;
  background: var(--hero-gradient);
  border-radius: var(--radius-lg, 12px);
  padding: 26px 30px;
  box-shadow: var(--hero-shadow, 0 10px 28px rgba(16, 42, 80, .18));
}
.recycle-hero::after {
  content: ''; position: absolute; right: -60px; top: -80px; width: 260px; height: 260px;
  background: radial-gradient(circle, rgba(255, 255, 255, .16), rgba(255, 255, 255, 0));
  pointer-events: none;
}
.hero-copy { position: relative; z-index: 1; min-width: 0; }
.hero-eyebrow { display: inline-block; font-size: 12px; letter-spacing: 1.2px; color: rgba(255, 255, 255, .66); margin-bottom: 8px; }
.hero-title { margin: 0; font-size: 24px; font-weight: 700; line-height: 1.2; color: #fff; letter-spacing: .5px; }
.hero-sub { margin: 8px 0 0; font-size: 13px; line-height: 1.5; color: rgba(255, 255, 255, .78); }
.hero-stats { position: relative; z-index: 1; display: flex; align-items: stretch; gap: 10px; flex-shrink: 0; }
.hero-metric {
  display: flex; align-items: center; gap: 12px; min-width: 132px;
  padding: 12px 16px; border-radius: 12px;
  background: rgba(255, 255, 255, .10);
  border: 1px solid rgba(255, 255, 255, .16);
  cursor: pointer;
  transition: background-color .18s ease, border-color .18s ease, transform .18s ease;
}
.hero-metric:hover { background: rgba(255, 255, 255, .16); }
.hero-metric.active { background: rgba(255, 255, 255, .20); border-color: rgba(255, 255, 255, .38); }
.hero-metric:active { transform: scale(.98); }
.hm-icon {
  display: inline-flex; align-items: center; justify-content: center; width: 38px; height: 38px;
  border-radius: 10px; background: rgba(255, 255, 255, .16); color: #fff; font-size: 19px; flex-shrink: 0;
}
.hm-body { display: flex; flex-direction: column; gap: 2px; }
.hm-num { font-size: var(--font-num-hero, 26px); font-weight: 700; line-height: 1.1; color: #fff; font-variant-numeric: tabular-nums; }
.hm-label { font-size: 12px; color: rgba(255, 255, 255, .76); white-space: nowrap; }

/* ===== 列表主卡 ===== */
.recycle-panel {
  background: #fff; border: 1px solid #e6edf7; border-radius: var(--radius-lg, 12px);
  box-shadow: 0 10px 30px rgba(16, 42, 80, .06);
  padding: 18px 22px 14px;
}
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.seg { display: inline-flex; padding: 4px; gap: 2px; background: #f3f6fb; border-radius: 10px; }
.seg-item {
  display: inline-flex; align-items: center; gap: 7px; border: 0; background: transparent;
  padding: 8px 15px; border-radius: 8px; font-size: 13px; color: #5c6675; cursor: pointer;
  transition: background-color .18s ease, color .18s ease, box-shadow .18s ease;
}
.seg-item:hover { color: var(--el-color-primary); }
.seg-item.active { background: #fff; color: var(--el-color-primary); font-weight: 600; box-shadow: 0 2px 6px rgba(16, 42, 80, .10); }
.seg-count {
  min-width: 22px; padding: 0 6px; border-radius: 10px; background: #e7eef9; color: #53709a;
  font-size: 11px; line-height: 18px; text-align: center; font-variant-numeric: tabular-nums;
}
.seg-item.active .seg-count { background: var(--el-color-primary-light-9, #ecf2fe); color: var(--el-color-primary); }
.panel-actions { display: flex; align-items: center; gap: 8px; }
.search-input { width: 250px; }
.refresh-btn { color: #5c6675; }

.panel-note {
  display: flex; align-items: flex-start; gap: 8px; margin: 14px 0 6px; padding: 10px 14px;
  background: #f5f9ff; border: 1px solid #e3edfb; border-left: 3px solid var(--el-color-primary);
  border-radius: 8px; font-size: 12px; line-height: 1.65; color: #5c6f88;
}
.note-icon { color: var(--el-color-primary); margin-top: 2px; flex-shrink: 0; }

/* ===== 表格 ===== */
.recycle-table { margin-top: 8px; }
.recycle-table :deep(.el-table__inner-wrapper::before) { display: none; }
.recycle-table :deep(.el-table__header th) {
  background: #f8fafc; color: #1f2329; font-weight: 600; height: 42px;
  border-bottom: 1px solid #eef2f8;
}
.recycle-table :deep(.el-table__row) { transition: background-color .15s ease; }
.recycle-table :deep(.el-table__row:hover > td) { background: #f6f9ff !important; }
.recycle-table :deep(td) { border-bottom: 1px solid #f1f4f9; }
.recycle-table :deep(.el-table__row) { height: 56px; }

.entity-cell { display: flex; align-items: center; gap: 11px; min-width: 0; }
.entity-cell.clickable { cursor: pointer; }
.entity-cell.clickable:hover .entity-name { color: var(--el-color-primary); }
.entity-avatar {
  display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px;
  border-radius: 10px; font-size: 15px; font-weight: 600; flex-shrink: 0;
}
.avatar-customer { background: linear-gradient(140deg, #e8f0fe, #d7e6ff); color: #1d4ed8; }
.avatar-user { border-radius: 50%; background: linear-gradient(140deg, #e9f7ef, #d6efe0); color: #2f8f5b; }
.avatar-file { background: linear-gradient(140deg, #f1f4f9, #e6ebf3); color: #5c6675; }
.entity-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.entity-name { font-size: 13.5px; color: #1f2329; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; transition: color .15s ease; }
.entity-meta { font-size: 11.5px; color: #8a94a6; font-variant-numeric: tabular-nums; }
.type-chip {
  display: inline-block; padding: 2px 9px; border-radius: 5px; background: #f2f6fc;
  color: #44618d; font-size: 12px;
}
.scope-chip { display: inline-block; padding: 2px 9px; border-radius: 5px; font-size: 12px; }
.scope-public { background: #eaf1fe; color: #1d4ed8; }
.scope-private { background: #f1f4f9; color: #5c6675; }
.scope-incentive { background: #fff5e6; color: #b26a00; }
.status-chip { display: inline-block; padding: 2px 9px; border-radius: 10px; font-size: 12px; }
.status-chip.on { background: #eaf7ef; color: #2f8f5b; }
.status-chip.off { background: #f2f4f7; color: #8a94a6; }
.text-strong { color: #1f2329; }
.mono-text { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; color: #3f4a5a; }
.muted-text { color: #7a8494; font-size: 12.5px; }
.time-text { color: #7a8494; font-variant-numeric: tabular-nums; font-size: 12.5px; }

.pager-row { display: flex; align-items: center; justify-content: space-between; margin-top: 14px; padding-top: 12px; border-top: 1px solid #f1f4f9; }
.pager-hint { font-size: 12px; color: #7a8494; }
.pager-hint b { color: var(--el-color-primary); font-variant-numeric: tabular-nums; }
.table-skeleton { padding: 20px 4px 8px; }

/* ===== 详情抽屉 ===== */
.drawer-head { display: flex; align-items: center; gap: 12px; }
.drawer-icon {
  display: inline-flex; align-items: center; justify-content: center; width: 40px; height: 40px;
  border-radius: 11px; font-size: 20px; color: #fff; flex-shrink: 0;
  background: linear-gradient(140deg, #1d4ed8, #3e8df3);
}
.drawer-icon-user { background: linear-gradient(140deg, #2f8f5b, #4fb37b); }
.drawer-icon-file { background: linear-gradient(140deg, #4b5b70, #7a8ba3); }
.drawer-copy { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.drawer-title { font-size: 16px; font-weight: 600; color: #1f2329; }
.drawer-sub { font-size: 12px; color: #8a94a6; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detail-body { min-height: 220px; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.detail-item {
  display: flex; flex-direction: column; gap: 3px; padding: 9px 12px;
  background: #f8fafc; border: 1px solid #eef2f8; border-radius: 8px; min-width: 0;
}
.di-label { font-size: 11.5px; color: #8a94a6; }
.di-value { font-size: 13px; color: #1f2329; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detail-section-title { margin: 18px 0 9px; font-size: 13px; font-weight: 600; color: #1f2329; }
.related-grid { display: flex; flex-wrap: wrap; gap: 7px; }
.related-chip { padding: 4px 10px; border-radius: 7px; background: #f2f6fc; color: #44618d; font-size: 12px; }
.related-chip b { margin-left: 4px; color: var(--el-color-primary); font-variant-numeric: tabular-nums; }
.related-chip.zero { background: #f5f6f8; color: #9aa4b2; }
.related-chip.zero b { color: #9aa4b2; }
.detail-warn {
  margin-top: 14px; padding: 10px 12px; border-radius: 8px; font-size: 12px; line-height: 1.6;
  background: #fff8ec; border: 1px solid #f6e3c2; color: #8a6520;
}
.detail-actions { margin-top: 16px; display: flex; align-items: center; gap: 10px; }
.detail-footer { display: flex; justify-content: flex-end; gap: 8px; }

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .recycle-hero { flex-direction: column; align-items: stretch; }
  .hero-stats { justify-content: space-between; }
  .hero-metric { flex: 1; min-width: 0; }
  .detail-grid { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .recycle-hero { padding: 20px; }
  .hero-stats { flex-direction: column; }
  .recycle-panel { padding: 14px; }
  .panel-head { flex-direction: column; align-items: stretch; }
  .seg { width: 100%; }
  .seg-item { flex: 1; justify-content: center; }
  .panel-actions { width: 100%; flex-wrap: wrap; }
  .search-input { width: 100%; }
}
@media (prefers-reduced-motion: reduce) {
  .hero-metric, .seg-item, .entity-name, .recycle-table :deep(.el-table__row) { transition: none !important; }
}
</style>
