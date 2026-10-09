<template>
  <el-card shadow="never" class="main-card">
    <el-tabs v-model="activeTab" @tab-change="onTabChange" class="customer-tabs">
      <el-tab-pane v-for="tab in tabs" :key="tab.type" :label="tab.label" :name="tab.type">
        <template #label>
          <span class="tab-label">
            <span class="tab-dot" :style="{ background: tab.color }"></span>
            {{ tab.label }}
            <span class="tab-count">{{ tab.count }}</span>
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 筛选栏(搜索已合并至顶部主搜索框,此处只留维度筛选) -->
    <div class="filter-bar">
      <!-- 【C1 2026-10-04】行业/状态筛选变化必须重置页码（否则过滤结果变少时停在旧页码可能越界白屏，同 search() 修复） -->
      <el-select v-model="filters.industry" placeholder="行业" size="small" clearable style="width:110px" @change="onQuickFilterChange">
        <el-option v-for="ind in allIndustries" :key="ind" :label="ind" :value="ind" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" size="small" clearable style="width:90px" @change="onQuickFilterChange">
        <el-option label="正常" value="normal" /><el-option label="预警" value="warning" /><el-option label="已超期" value="expired" /><el-option label="已归档" value="archived" />
      </el-select>
      <!-- 【议题五.4】显示已归档: 后端 include_archived(默认口径+含归档), 勾选即重载 -->
      <el-checkbox v-model="showArchived" size="small" @change="toggleArchived">显示已归档</el-checkbox>
      <el-button size="small" @click="colPickerVisible = true">列设置</el-button>
      <!-- 【UI 整改 2026-10-08·D15】批量操作: 勾选行后出现, 释放/归档按可操作口径显示条数, 删除走 confirmAction 红色确认 -->
      <template v-if="selectedRows.length > 0">
        <el-button v-if="canRelease" size="small" type="warning" plain @click="handleBatchRelease">释放({{ batchReleasable.length }})</el-button>
        <el-button v-if="canRelease" size="small" plain @click="handleBatchArchive">归档({{ batchArchivable.length }})</el-button>
        <el-button v-if="canDelete" size="small" type="danger" plain @click="handleBatchDelete">删除({{ batchDeletable.length }})</el-button>
      </template>
      <!-- 【容量加固·服务端分页 2026-10-05】条数=服务端 total(原为本地过滤后切片长度) -->
      <span class="filter-count">共 {{ total }} 条</span>
    </div>

    <!-- 表格 -->
    <!-- 【D6 2026-10-04】表格高度统一走共享 composable（:max-height=视口-358，替代原 CSS calc 魔法数） -->
    <!-- 【UI 整改 2026-10-08·D13】v-loading 覆盖服务端分页/筛选请求间隙（参照 sea/index.vue 既有写法）；
         空态改 #empty 插槽（带"清空搜索与筛选"引导按钮，比 :empty-text 纯文案多一步出路） -->
    <!-- 【C12 2026-10-08 二轮】加载失败页面级错误态（区别于空态）：错误条+重试 -->
    <el-alert v-if="listError" type="error" :closable="false" class="list-error-bar" show-icon>
      <template #title>
        客户列表加载失败
        <el-button size="small" type="primary" plain style="margin-left:10px" @click="loadData">重试</el-button>
      </template>
    </el-alert>
    <!-- 【工作流复核确认修复】移除列头排序: 服务端分页下裸 sortable 只重排当前页 30 条且默认箭头与真实顺序(createdAt desc)不符,
         待后端 order 参数支持后再启用(与公海池排序遗留同队列) -->
<!-- 【C10 二轮 2026-10-08】内容列一律 min-width：窄视口下 el-table 出横向滚动条，
         列不再被压到单字宽不可读；文本列统一 dashIfEmpty 空值占位（同表一种口径） -->
    <el-table ref="tableRef" v-loading="listLoading" :data="filteredList" :max-height="tableMaxHeight" stripe
      @row-click="viewDetail" @selection-change="onSelectionChange" :row-style="{ cursor: 'pointer' }" :row-class-name="archiveRowClass" style="width:100%">
      <template #empty>
        <div class="table-empty">
          <p class="table-empty-text">{{ emptyTableText }}</p>
          <el-button v-if="hasActiveFilter" size="small" type="primary" plain @click="clearSearch">清空搜索与筛选</el-button>
        </div>
      </template>
      <el-table-column type="selection" width="42" :selectable="(row) => !isShared(row)" />
      <el-table-column type="index" width="50" label="#" :index="(i) => (page - 1) * pageSize + i + 1" />
      <el-table-column v-if="vis('customerCode')" prop="customerCode" label="编号" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="vis('customerName')" prop="customerName" label="客户全称" min-width="220" show-overflow-tooltip />
      <el-table-column v-if="vis('invoiceName')" prop="invoiceName" label="开票名称" min-width="130" show-overflow-tooltip :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('groupName')" prop="groupName" label="集团" min-width="100" show-overflow-tooltip :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('branchCompany')" prop="branchCompany" label="分公司" min-width="100" show-overflow-tooltip :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('unitName')" prop="unitName" label="所属单位" min-width="100" show-overflow-tooltip :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('industry')" prop="industry" label="行业" min-width="70" :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('hasTerminal')" prop="hasTerminal" label="终端" width="60" align="center">
        <template #default="{ row }">{{ terminalLabel(row.hasTerminal) }}</template>
      </el-table-column>
      <!-- 【C10】数值列右对齐（与 FinanceTab 金额列惯例一致）+ 空值占位 -->
      <el-table-column v-if="vis('capacity')" prop="capacity" label="产能" min-width="70" align="right" header-align="right" :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('province')" label="省市" min-width="70" show-overflow-tooltip prop="province">
        <template #default="{ row }">{{ (row.province || row.city) ? (row.province || '') + (row.city || '') : '—' }}</template>
      </el-table-column>
      <el-table-column v-if="vis('address')" prop="address" label="厂址" min-width="130" show-overflow-tooltip :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('registerDate')" prop="registerDate" label="注册日" min-width="90" :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('lastInvoiceDate')" prop="lastInvoiceDate" label="最近开票" min-width="90" :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('transferDate')" prop="transferDate" label="转移日" min-width="90" :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('expireDate')" prop="expireDate" label="到期日" min-width="90">
        <template #default="{ row }">
          <span :style="{ color: getExpireColor(row), fontWeight: 'bold' }">{{ row.expireDate || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="vis('adjustDate')" prop="adjustDate" label="调整日" min-width="90">
        <template #default="{ row }"><span v-if="row.adjustDate">{{ row.adjustDate }}</span><span v-else>—</span></template>
      </el-table-column>
      <el-table-column v-if="vis('regionName')" prop="regionName" label="大区" min-width="70" :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('officeName')" prop="officeName" label="办事处" min-width="80" :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('deptName')" prop="deptName" label="部门" min-width="80" :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('ownerName')" prop="ownerName" label="负责人" min-width="70" :formatter="dashIfEmpty" />
      <el-table-column v-if="vis('managerName')" prop="managerName" label="上级" min-width="70" :formatter="dashIfEmpty" />
      <el-table-column label="状态" width="90" align="center" prop="status">
        <template #default="{ row }">
          <el-tag v-if="isShared(row)" type="warning" size="small" effect="dark" round>被分享</el-tag>
          <el-tag v-else :type="getStatusType(row)" size="small" effect="dark" round>{{ getStatusText(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="保护" width="80" align="center" prop="protectionStatus">
        <template #default="{ row }">
          <span class="protect-tag" :class="getProtectClass(row)">{{ getProtectText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canRelease || canDelete" label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="!isShared(row)">
            <!-- 【议题五.4】归档行只可恢复; 未归档行可归档(归档=隐藏+不参与统计/提醒, 可恢复) -->
            <el-button v-if="canRelease && row.status === 'archived'" type="success" link size="small" @click.stop="handleRestore(row)">恢复</el-button>
            <template v-else>
              <!-- 【B5 三轮 2026-10-09】行内「释放」与批量栏释放按钮统一 warning 橙（原行内 danger 红：与「删除」同为红，危险分级被抹平；对齐人员管理 编辑蓝/重置橙/删除红 三级惯例） -->
              <el-button v-if="canRelease" type="warning" link size="small" @click.stop="openRelease(row)">释放</el-button>
              <el-button v-if="canRelease" type="info" link size="small" @click.stop="handleArchive(row)">归档</el-button>
            </template>
            <el-button v-if="canDelete" type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
          <span v-else style="color: var(--text-placeholder);font-size:12px">—</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { injectCustomerList } from '@/composables/useCustomerList'
import { useViewportMaxHeight } from '@/views/sea/useTableHeight'
import { terminalLabel } from '@/utils/customer-fields'
// 【A2/C10 二轮 2026-10-08】空值占位唯一 formatter（utils/format.js 契约）
import { dashIfEmpty } from '@/utils/format'

const {
  activeTab, onTabChange, tabs, filters, allIndustries,
  colPickerVisible, filteredList, total, emptyTableText, page, pageSize,
  canDelete, canRelease, vis, isShared, getStatusType, getStatusText,
  getProtectClass, getProtectText, getExpireColor,
  viewDetail, openRelease, handleDelete,
  showArchived, toggleArchived, handleArchive, handleRestore,
  onQuickFilterChange,
  listLoading, listError, tableRef, selectedRows, onSelectionChange, loadData,
  batchReleasable, batchArchivable, batchDeletable,
  handleBatchRelease, handleBatchArchive, handleBatchDelete,
  hasActiveFilter, clearSearch,
} = injectCustomerList()

// 已归档行置灰(默认隐藏, 勾选"显示已归档"才出现)
const archiveRowClass = ({ row }) => (row.status === 'archived' ? 'archived-row' : '')

// 【D6 2026-10-04】表格高度统一走共享 composable（与公海两页同源）：
// 358 = 顶部栏+统计卡+Tab+筛选栏+分页等页面固定占用（沿用原 calc(100vh - 358px) 的同一口径），
// 由官方 :max-height 接管，替代此前 .el-table/.el-table__body-wrapper 双处 calc + 57px 表头二次扣减
const { tableMaxHeight } = useViewportMaxHeight(358)
</script>

<style scoped>
.main-card { border-radius: var(--radius-md); }
.main-card :deep(.el-card__body) { padding: 0 16px 16px; }
.customer-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
/* Tab 激活主色对齐全局深蓝(统一走主题变量,不再写字面值 #1d4ed8) */
.customer-tabs :deep(.el-tabs__item.is-active),
.customer-tabs :deep(.el-tabs__active-bar) { color: var(--el-color-primary); }
.tab-label { display: flex; align-items: center; gap: 6px; }
.tab-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.tab-count {
  font-size: 12px; padding: 0 8px; border-radius: var(--radius-pill);
  background: #eef4ff; color: var(--el-color-primary); font-weight: 600; line-height: 18px;
}
.filter-bar { display: flex; gap: 8px; align-items: center; padding: 8px 0; flex-wrap: wrap; }
.list-error-bar { margin: 10px 0 0; }
.filter-count { font-size: 12px; color: var(--el-color-info); margin-left: auto; }
/* 【UI 整改 2026-10-08·⑦空态】#empty 插槽空态: 文案 + "清空搜索与筛选"引导按钮 */
.table-empty { padding: 24px 0; display: flex; flex-direction: column; align-items: center; gap: 10px; }
.table-empty-text { margin: 0; color: var(--el-color-info); font-size: 13px; }
:deep(.archived-row) { color: #b9bcc2; }
:deep(.archived-row .cell) { color: #b9bcc2; }
/* 【D6 2026-10-04】表格高度改由 :max-height 属性驱动（见 useTableHeight），
   原双处 calc 魔法数（100vh-358px / 再扣 57px 表头）已移除 */

.main-card :deep(.el-table th) { white-space: nowrap; }
.main-card :deep(.el-table th .cell) { display: flex !important; align-items: center !important; gap: 2px; white-space: nowrap; }
.main-card :deep(.el-table .caret-wrapper) { flex-shrink: 0; margin-left: 0; }
</style>
