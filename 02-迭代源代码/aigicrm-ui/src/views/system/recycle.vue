<template>
  <div class="recycle-page">
    <!-- 页头：标题 + 两个实时计数（数据来自列表接口 total） -->
    <div class="recycle-head">
      <div class="head-main">
        <div class="head-title-row">
          <span class="head-accent" aria-hidden="true"></span>
          <h2>回收站</h2>
        </div>
        <p class="head-desc">查看并恢复已删除的客户、人员与文件，删除留痕可追溯</p>
      </div>
      <div class="head-stats">
        <div class="stat">
          <span class="stat-num">{{ counts.customers }}</span>
          <span class="stat-label">已删除客户</span>
        </div>
        <span class="stat-divider" aria-hidden="true"></span>
        <div class="stat">
          <span class="stat-num">{{ counts.users }}</span>
          <span class="stat-label">已删除人员</span>
        </div>
        <span class="stat-divider" aria-hidden="true"></span>
        <div class="stat">
          <span class="stat-num">{{ counts.files }}</span>
          <span class="stat-label">已删除文件</span>
        </div>
      </div>
    </div>

    <div class="section-box">
      <!-- 页签 + 工具栏 -->
      <div class="toolbar-row">
        <div class="seg" role="tablist">
          <button
            v-for="tab in tabs" :key="tab.key" type="button" role="tab"
            :aria-selected="activeTab === tab.key"
            class="seg-item" :class="{ active: activeTab === tab.key }"
            @click="switchTab(tab.key)"
          >
            {{ tab.label }}
            <span class="seg-count">{{ tab.key === 'customers' ? counts.customers : counts.users }}</span>
          </button>
        </div>
        <div class="toolbar-actions">
          <el-input
            v-model="keyword" clearable class="search-input"
            :placeholder="searchPlaceholder"
            :prefix-icon="Search" @keyup.enter="search" @clear="search"
          />
          <el-button
            v-if="activeTab === 'files' && canPurge" type="danger" plain :icon="Delete"
            @click="handlePurgeFiles"
          >清空回收站</el-button>
          <el-tooltip content="刷新列表" placement="top">
            <el-button :icon="Refresh" circle class="refresh-btn" aria-label="刷新列表" @click="loadData" />
          </el-tooltip>
        </div>
      </div>

      <!-- 口径提示 -->
      <div class="info-strip">
        <el-icon class="info-icon"><InfoFilled /></el-icon>
        <span>删除是终态留痕：这里只展示仍可恢复的软删数据。恢复客户会校验同名活跃客户、原负责人与公海残留；恢复人员后需在人员管理重新分配角色；文件删除后先进入回收站，可在保留期内恢复，超过保留期自动清理，管理员也可手动清空。</span>
      </div>

      <!-- 首屏骨架 -->
      <div v-if="firstLoading" class="table-skeleton">
        <el-skeleton :rows="6" animated />
      </div>

      <template v-else>
        <!-- 已删除客户 -->
        <el-table
          v-if="activeTab === 'customers'" v-loading="loading" :data="rows" class="recycle-table"
          style="width:100%" empty-text="暂无已删除客户"
        >
          <el-table-column label="客户全称" min-width="240">
            <template #default="{ row }">
              <div class="name-cell clickable" @click.stop="openCustomerDetail(row)">
                <span class="monogram" aria-hidden="true">{{ (row.customerName || '?').charAt(0) }}</span>
                <span class="name-text">{{ row.customerName || '未命名客户' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="customerCode" label="编号" min-width="140">
            <template #default="{ row }"><span class="mono-text">{{ row.customerCode || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="原负责人" min-width="120">
            <template #default="{ row }">
              <span v-if="row.ownerName">{{ row.ownerName }}</span>
              <el-tag v-else-if="row.ownerId" size="small" type="warning" effect="plain">负责人已删除</el-tag>
              <span v-else class="muted-text">未分配</span>
            </template>
          </el-table-column>
          <el-table-column prop="industry" label="行业" min-width="100">
            <template #default="{ row }"><span class="muted-text">{{ row.industry || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="devType" label="分类" min-width="140">
            <template #default="{ row }"><span class="muted-text">{{ row.devType || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="删除时间" min-width="160">
            <template #default="{ row }"><span class="time-text">{{ formatTime(row.deletedAt) }}</span></template>
          </el-table-column>
          <el-table-column v-if="canRestore || canPurge" label="操作" width="220" align="right" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openCustomerDetail(row)">详情</el-button>
              <el-button v-if="canRestore" link type="primary" size="small" :icon="RefreshLeft" @click.stop="handleRestoreCustomer(row)">
                恢复
              </el-button>
              <el-button v-if="canPurge" link type="danger" size="small" @click.stop="handleHardDeleteCustomer(row)">
                彻底删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 已删除文件 -->
        <el-table
          v-else-if="activeTab === 'files'" v-loading="loading" :data="rows" class="recycle-table"
          style="width:100%" empty-text="暂无已删除文件"
        >
          <el-table-column label="文件名" min-width="260">
            <template #default="{ row }">
              <div class="name-cell clickable" @click.stop="openFileDetail(row)">
                <span class="file-icon" aria-hidden="true"><el-icon><Document /></el-icon></span>
                <span class="name-text" :title="row.fileName">{{ row.fileName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="大小" min-width="100">
            <template #default="{ row }"><span class="muted-text">{{ formatSize(row.fileSize) }}</span></template>
          </el-table-column>
          <el-table-column label="归属" min-width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="scopeTagType(row.fileScope)" effect="plain">{{ scopeLabel(row.fileScope) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上传人" min-width="110">
            <template #default="{ row }">
              <span v-if="row.uploaderName">{{ row.uploaderName }}</span>
              <el-tag v-else-if="row.uploaderId" size="small" type="warning" effect="plain">上传人已删除</el-tag>
              <span v-else class="muted-text">-</span>
            </template>
          </el-table-column>
          <el-table-column label="所在文件夹" min-width="140">
            <template #default="{ row }">
              <span v-if="row.folderName">{{ row.folderName }}</span>
              <span v-else-if="row.folderId" class="muted-text">文件夹已删除</span>
              <span v-else class="muted-text">根目录</span>
            </template>
          </el-table-column>
          <el-table-column label="删除时间" min-width="160">
            <template #default="{ row }"><span class="time-text">{{ formatTime(row.deletedAt) }}</span></template>
          </el-table-column>
          <el-table-column label="内容状态" min-width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.contentAvailable ? 'success' : 'info'" effect="plain">
                {{ row.contentAvailable ? '可恢复' : '已清除' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="canRestore || canPurge" label="操作" width="220" align="right" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openFileDetail(row)">详情</el-button>
              <el-button v-if="canRestore && row.contentAvailable" link type="primary" size="small" :icon="RefreshLeft" @click.stop="handleRestoreFile(row)">
                恢复
              </el-button>
              <el-button v-if="canPurge" link type="danger" size="small" @click.stop="handleHardDeleteFile(row)">
                彻底删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 已删除人员 -->
        <el-table
          v-else v-loading="loading" :data="rows" class="recycle-table"
          style="width:100%" empty-text="暂无已删除人员"
        >
          <el-table-column label="姓名" min-width="140">
            <template #default="{ row }">
              <div class="name-cell clickable" @click.stop="openUserDetail(row)">
                <span class="monogram monogram-user" aria-hidden="true">{{ (row.realName || row.username || '?').charAt(0) }}</span>
                <span class="name-text">{{ row.realName || '未填写姓名' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="username" label="账号" min-width="140">
            <template #default="{ row }"><span class="mono-text">{{ row.username }}</span></template>
          </el-table-column>
          <el-table-column label="原部门" min-width="160">
            <template #default="{ row }">
              <span v-if="row.deptName">{{ row.deptName }}</span>
              <el-tag v-else-if="row.deptId" size="small" type="warning" effect="plain">部门已删除</el-tag>
              <span v-else class="muted-text">未分配</span>
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" min-width="130">
            <template #default="{ row }"><span class="muted-text">{{ row.phone || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="账号状态" min-width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="删除时间" min-width="160">
            <template #default="{ row }"><span class="time-text">{{ formatTime(row.deletedAt) }}</span></template>
          </el-table-column>
          <el-table-column v-if="canRestore || canPurge" label="操作" width="220" align="right" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openUserDetail(row)">详情</el-button>
              <el-button v-if="canRestore" link type="primary" size="small" :icon="RefreshLeft" @click.stop="handleRestoreUser(row)">
                恢复
              </el-button>
              <el-button v-if="canPurge" link type="danger" size="small" @click.stop="handleHardDeleteUser(row)">
                彻底删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager-row">
          <span class="pager-hint">共 {{ total }} 条</span>
          <el-pagination
            v-model:current-page="current" v-model:page-size="size" :total="total"
            :page-sizes="[20, 50, 100]" layout="sizes, prev, pager, next" size="small" background
            @current-change="loadData" @size-change="onSizeChange"
          />
        </div>
      </template>
    </div>

    <!-- 详情抽屉：客户/人员/文件 -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="540px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-body">
        <!-- 客户详情 -->
        <template v-if="detailType === 'customer' && detailData">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="客户全称">{{ detailData.customer.customerName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="编号">{{ detailData.customer.customerCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="开票名称">{{ detailData.customer.invoiceName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="原负责人">{{ detailData.ownerName || (detailData.customer.ownerId ? '负责人已删除' : '未分配') }}</el-descriptions-item>
            <el-descriptions-item label="行业 / 分类">{{ (detailData.customer.industry || '-') + ' / ' + (detailData.customer.devType || '-') }}</el-descriptions-item>
            <el-descriptions-item label="省市县">{{ [detailData.customer.province, detailData.customer.city, detailData.customer.district].filter(Boolean).join(' ') || '-' }}</el-descriptions-item>
            <el-descriptions-item label="厂址">{{ detailData.customer.address || '-' }}</el-descriptions-item>
            <el-descriptions-item label="产能">{{ detailData.customer.capacity || '-' }}</el-descriptions-item>
            <el-descriptions-item label="注册日">{{ detailData.customer.registerDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="最近开票">{{ detailData.customer.lastInvoiceDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="到期日">{{ detailData.customer.expireDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detailData.customer.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="删除时间">{{ formatTime(detailData.customer.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
          <div class="detail-section-title">关联数据</div>
          <div class="related-grid">
            <span v-for="(count, label) in detailData.related" :key="label" class="related-chip" :class="{ zero: !count }">
              {{ label }} <b>{{ count }}</b>
            </span>
          </div>
          <p class="detail-hint">存在发票、回款、激励、指标、日志、阶段金额或审批单时不能彻底删除；分享、提醒、公海残留会随彻底删除一并清理。</p>
        </template>

        <!-- 人员详情 -->
        <template v-else-if="detailType === 'user' && detailData">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="姓名">{{ detailData.user.realName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="账号">{{ detailData.user.username || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detailData.user.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ detailData.user.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="原部门">{{ detailData.deptName || (detailData.user.deptId ? '部门已删除' : '未分配') }}</el-descriptions-item>
            <el-descriptions-item label="账号状态">{{ detailData.user.status === 1 ? '启用' : '停用' }}</el-descriptions-item>
            <el-descriptions-item label="数据范围覆盖">{{ detailData.user.dataScopeOverride || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="离职状态">{{ detailData.user.leaveStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detailData.user.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="删除时间">{{ formatTime(detailData.user.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
          <div class="detail-section-title">关联数据</div>
          <div class="related-grid">
            <span v-for="(count, label) in detailData.related" :key="label" class="related-chip" :class="{ zero: !count }">
              {{ label }} <b>{{ count }}</b>
            </span>
          </div>
          <p class="detail-hint">存在名下客户、审批、激励、文件、日志等业务关联时不能彻底删除；角色/岗位/组织关联会在彻底删除时一并清理，系统日志保留账号名。</p>
        </template>

        <!-- 文件详情 -->
        <template v-else-if="detailType === 'file' && detailData">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="文件名">{{ detailData.file.fileName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="大小">{{ formatSize(detailData.file.fileSize) }}</el-descriptions-item>
            <el-descriptions-item label="文件类型">{{ detailData.fileType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="归属">{{ scopeLabel(detailData.file.fileScope) }}</el-descriptions-item>
            <el-descriptions-item label="上传人">{{ detailData.file.uploaderName || (detailData.file.uploaderId ? '上传人已删除' : '-') }}</el-descriptions-item>
            <el-descriptions-item label="所在文件夹">{{ detailData.file.folderName || (detailData.file.folderId ? '文件夹已删除' : '根目录') }}</el-descriptions-item>
            <el-descriptions-item label="内容状态">
              <el-tag size="small" :type="detailData.file.contentAvailable ? 'success' : 'info'" effect="plain">
                {{ detailData.file.contentAvailable ? '可恢复' : '已清除' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detailData.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="删除时间">{{ formatTime(detailData.file.deletedAt) }}</el-descriptions-item>
          </el-descriptions>
          <div class="detail-actions">
            <el-button v-if="detailData.file.contentAvailable" type="primary" plain :icon="Download" @click="downloadRecycleFile(detailData.file)">
              下载文件
            </el-button>
            <span v-else class="muted-text">文件内容已物理清除，仅保留删除记录</span>
          </div>
          <p class="detail-hint">文件仍被激励记录引用时不能彻底删除；彻底删除会同时清除物理内容与台账记录。</p>
        </template>
      </div>

      <template #footer>
        <div class="detail-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button v-if="canRestore && detailData && detailCanRestore" type="primary" plain @click="restoreFromDetail">
            恢复
          </el-button>
          <el-button v-if="canPurge && detailData" type="danger" @click="hardDeleteFromDetail">
            彻底删除
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Delete, Download, InfoFilled, Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
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
const detailCanRestore = computed(() => {
  if (detailType.value === 'file') return Boolean(detailData.value?.file?.contentAvailable)
  return Boolean(detailData.value)
})

const formatTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const scopeLabel = (scope) => ({ public: '公共', private: '私有', incentive: '激励' }[scope] || scope || '-')
const scopeTagType = (scope) => ({ public: 'primary', private: 'info', incentive: 'warning' }[scope] || 'info')

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
.recycle-page { display: flex; flex-direction: column; gap: 14px; }

/* 页头 */
.recycle-head {
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
  background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md, 8px);
  padding: 18px 22px;
}
.head-main { min-width: 0; }
.head-title-row { display: flex; align-items: center; gap: 10px; }
.head-accent { width: 4px; height: 18px; border-radius: 2px; background: var(--el-color-primary); }
.head-title-row h2 { margin: 0; font-size: 18px; font-weight: 600; color: var(--el-text-color-primary); }
.head-desc { margin: 6px 0 0 14px; font-size: 13px; color: var(--el-text-color-secondary); }
.head-stats { display: flex; align-items: center; gap: 20px; flex-shrink: 0; }
.stat { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.stat-num { font-size: 22px; font-weight: 600; line-height: 1.1; color: var(--el-color-primary); font-variant-numeric: tabular-nums; }
.stat-label { font-size: 12px; color: var(--el-text-color-secondary); }
.stat-divider { width: 1px; height: 28px; background: #e4e7ed; }

/* 主体卡片 */
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md, 8px); padding: 16px 20px 14px; }

/* 工具栏 */
.toolbar-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.seg { display: inline-flex; padding: 3px; gap: 2px; background: #f2f5f9; border-radius: 8px; }
.seg-item {
  display: inline-flex; align-items: center; gap: 6px; border: 0; background: transparent;
  padding: 7px 14px; border-radius: 6px; font-size: 13px; color: #5c6675; cursor: pointer;
  transition: background-color .18s ease, color .18s ease;
}
.seg-item:hover { color: var(--el-color-primary); }
.seg-item.active { background: #fff; color: var(--el-color-primary); font-weight: 600; box-shadow: 0 1px 3px rgba(16, 42, 80, .08); }
.seg-count {
  min-width: 20px; padding: 0 5px; border-radius: 9px; background: #e8eef8; color: #53709a;
  font-size: 11px; line-height: 18px; text-align: center; font-variant-numeric: tabular-nums;
}
.seg-item.active .seg-count { background: var(--el-color-primary-light-9, #ecf2fe); color: var(--el-color-primary); }
.toolbar-actions { display: flex; align-items: center; gap: 8px; }
.search-input { width: 260px; }
.refresh-btn { color: #5c6675; }

/* 口径提示 */
.info-strip {
  display: flex; align-items: flex-start; gap: 8px; margin: 12px 0 4px; padding: 9px 12px;
  background: #f6f9fe; border: 1px solid #e2ebf8; border-radius: 6px;
  font-size: 12px; line-height: 1.6; color: #5c6f88;
}
.info-icon { color: var(--el-color-primary); margin-top: 2px; flex-shrink: 0; }

/* 表格 */
.recycle-table { margin-top: 8px; }
.recycle-table :deep(.el-table__header th) { background: #f7f9fc; color: #1f2329; font-weight: 600; }
.recycle-table :deep(.el-table__row) { transition: background-color .15s ease; }
.name-cell { display: flex; align-items: center; gap: 10px; min-width: 0; }
.monogram {
  display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px;
  border-radius: 8px; background: #e8f0fe; color: var(--el-color-primary);
  font-size: 14px; font-weight: 600; flex-shrink: 0;
}
.monogram-user { border-radius: 50%; background: #eef6f1; color: #2f8f5b; }
.file-icon {
  display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px;
  border-radius: 8px; background: #f1f3f7; color: #5c6675; font-size: 15px; flex-shrink: 0;
}
.restore-unavailable { font-size: 12px; }

/* 详情 */
.name-cell.clickable { cursor: pointer; }
.name-cell.clickable:hover .name-text { color: var(--el-color-primary); }
.detail-body { min-height: 200px; }
.detail-section-title { margin: 16px 0 8px; font-size: 13px; font-weight: 600; color: var(--el-text-color-primary); }
.related-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.related-chip { padding: 4px 10px; border-radius: 6px; background: #f2f5f9; color: #3f4a5a; font-size: 12px; }
.related-chip b { margin-left: 4px; color: var(--el-color-primary); font-variant-numeric: tabular-nums; }
.related-chip.zero { color: #9aa4b2; }
.related-chip.zero b { color: #9aa4b2; }
.detail-hint { margin-top: 12px; font-size: 12px; line-height: 1.6; color: #7a8494; }
.detail-actions { margin-top: 16px; display: flex; align-items: center; gap: 10px; }
.detail-footer { display: flex; justify-content: flex-end; gap: 8px; }
.name-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mono-text { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; color: #3f4a5a; }
.muted-text { color: #7a8494; }
.time-text { color: #7a8494; font-variant-numeric: tabular-nums; font-size: 12.5px; }

/* 分页 */
.pager-row { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; }
.pager-hint { font-size: 12px; color: #7a8494; }

/* 骨架 */
.table-skeleton { padding: 20px 4px 8px; }

@media (max-width: 768px) {
  .recycle-head { flex-direction: column; align-items: flex-start; gap: 12px; }
  .head-stats { align-self: stretch; justify-content: space-between; }
  .stat { align-items: flex-start; }
  .search-input { width: 100%; }
  .toolbar-actions { width: 100%; }
  .toolbar-row { flex-direction: column; align-items: stretch; }
  .seg { width: 100%; }
  .seg-item { flex: 1; justify-content: center; }
}
</style>
