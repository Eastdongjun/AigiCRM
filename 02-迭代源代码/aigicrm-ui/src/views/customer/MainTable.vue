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
        <el-option label="正常" value="normal" /><el-option label="预警" value="warning" /><el-option label="超期" value="expired" /><el-option label="已归档" value="archived" />
      </el-select>
      <!-- 【议题五.4】显示已归档: 后端 include_archived(默认口径+含归档), 勾选即重载 -->
      <el-checkbox v-model="showArchived" size="small" @change="toggleArchived">显示已归档</el-checkbox>
      <el-button size="small" @click="colPickerVisible = true">列设置</el-button>
      <span class="filter-count">共 {{ filteredList.length }} 条</span>
    </div>

    <!-- 表格 -->
    <!-- 【D6 2026-10-04】表格高度统一走共享 composable（:max-height=视口-358，替代原 CSS calc 魔法数） -->
    <el-table :data="filteredList" :max-height="tableMaxHeight" stripe :empty-text="emptyTableText" :default-sort="{ prop: 'customerCode', order: 'descending' }"
      @row-click="viewDetail" :row-style="{ cursor: 'pointer' }" :row-class-name="archiveRowClass" style="width:100%">
      <el-table-column type="index" width="50" label="#" sortable :index="(i) => (page - 1) * pageSize + i + 1" />
      <el-table-column v-if="vis('customerCode')" prop="customerCode" label="编号" min-width="140" sortable />
      <el-table-column v-if="vis('customerName')" prop="customerName" label="客户全称" min-width="220" sortable />
      <el-table-column v-if="vis('invoiceName')" prop="invoiceName" label="开票名称" width="130" show-overflow-tooltip sortable />
      <el-table-column v-if="vis('groupName')" prop="groupName" label="集团" width="100" show-overflow-tooltip sortable />
      <el-table-column v-if="vis('branchCompany')" prop="branchCompany" label="分公司" width="100" show-overflow-tooltip sortable />
      <el-table-column v-if="vis('unitName')" prop="unitName" label="所属单位" width="100" show-overflow-tooltip sortable />
      <el-table-column v-if="vis('industry')" prop="industry" label="行业" width="70" sortable />
      <el-table-column v-if="vis('hasTerminal')" prop="hasTerminal" label="终端" width="60" align="center" sortable>
        <template #default="{ row }">{{ terminalLabel(row.hasTerminal) }}</template>
      </el-table-column>
      <el-table-column v-if="vis('capacity')" prop="capacity" label="产能" width="70" sortable />
      <el-table-column v-if="vis('province')" label="省市" width="70" show-overflow-tooltip sortable prop="province">
        <template #default="{ row }">{{ row.province }}{{ row.city }}</template>
      </el-table-column>
      <el-table-column v-if="vis('address')" prop="address" label="厂址" width="130" show-overflow-tooltip sortable />
      <el-table-column v-if="vis('registerDate')" prop="registerDate" label="注册日" width="90" sortable />
      <el-table-column v-if="vis('lastInvoiceDate')" prop="lastInvoiceDate" label="最近开票" width="90" sortable />
      <el-table-column v-if="vis('transferDate')" prop="transferDate" label="转移日" width="90" sortable />
      <el-table-column v-if="vis('expireDate')" prop="expireDate" label="到期日" width="90" sortable>
        <template #default="{ row }">
          <span :style="{ color: getExpireColor(row), fontWeight: 'bold' }">{{ row.expireDate || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="vis('adjustDate')" prop="adjustDate" label="调整日" width="90" sortable>
        <template #default="{ row }"><span v-if="row.adjustDate">{{ row.adjustDate }}</span><span v-else>—</span></template>
      </el-table-column>
      <el-table-column v-if="vis('regionName')" prop="regionName" label="大区" width="70" sortable />
      <el-table-column v-if="vis('officeName')" prop="officeName" label="办事处" width="80" sortable />
      <el-table-column v-if="vis('deptName')" prop="deptName" label="部门" width="80" sortable />
      <el-table-column v-if="vis('ownerName')" prop="ownerName" label="负责人" width="70" sortable />
      <el-table-column v-if="vis('managerName')" prop="managerName" label="上级" width="70" sortable />
      <el-table-column label="状态" width="90" align="center" sortable prop="status">
        <template #default="{ row }">
          <el-tag v-if="isShared(row)" type="warning" size="small" effect="dark" round>被分享</el-tag>
          <el-tag v-else :type="getStatusType(row)" size="small" effect="dark" round>{{ getStatusText(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="保护" width="80" align="center" sortable prop="protectionStatus">
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
              <el-button v-if="canRelease" type="danger" link size="small" @click.stop="openRelease(row)">释放</el-button>
              <el-button v-if="canRelease" type="info" link size="small" @click.stop="handleArchive(row)">归档</el-button>
            </template>
            <el-button v-if="canDelete" type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
          <span v-else style="color:#c0c4cc;font-size:12px">—</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { injectCustomerList } from '@/composables/useCustomerList'
import { useViewportMaxHeight } from '@/views/sea/useTableHeight'
import { terminalLabel } from '@/utils/customer-fields'

const {
  activeTab, onTabChange, tabs, filters, allIndustries,
  colPickerVisible, filteredList, emptyTableText, page, pageSize,
  canDelete, canRelease, vis, isShared, getStatusType, getStatusText,
  getProtectClass, getProtectText, getExpireColor,
  viewDetail, openRelease, handleDelete,
  showArchived, toggleArchived, handleArchive, handleRestore,
  onQuickFilterChange,
} = injectCustomerList()

// 已归档行置灰(默认隐藏, 勾选"显示已归档"才出现)
const archiveRowClass = ({ row }) => (row.status === 'archived' ? 'archived-row' : '')

// 【D6 2026-10-04】表格高度统一走共享 composable（与公海两页同源）：
// 358 = 顶部栏+统计卡+Tab+筛选栏+分页等页面固定占用（沿用原 calc(100vh - 358px) 的同一口径），
// 由官方 :max-height 接管，替代此前 .el-table/.el-table__body-wrapper 双处 calc + 57px 表头二次扣减
const { tableMaxHeight } = useViewportMaxHeight(358)
</script>

<style scoped>
.main-card { border-radius: 10px; }
.main-card :deep(.el-card__body) { padding: 0 16px 16px; }
.customer-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
/* Tab 激活主色对齐全局深蓝(主题变量已统一,此处兜底旧版样式覆盖) */
.customer-tabs :deep(.el-tabs__item.is-active),
.customer-tabs :deep(.el-tabs__active-bar) { color: #1d4ed8; }
.tab-label { display: flex; align-items: center; gap: 6px; }
.tab-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.tab-count {
  font-size: 11px; padding: 0 8px; border-radius: 999px;
  background: #eef4ff; color: #1d4ed8; font-weight: 600; line-height: 18px;
}
.filter-bar { display: flex; gap: 8px; align-items: center; padding: 8px 0; flex-wrap: wrap; }
.filter-count { font-size: 12px; color: #909399; margin-left: auto; }
:deep(.archived-row) { color: #b9bcc2; }
:deep(.archived-row .cell) { color: #b9bcc2; }
/* 【D6 2026-10-04】表格高度改由 :max-height 属性驱动（见 useTableHeight），
   原双处 calc 魔法数（100vh-358px / 再扣 57px 表头）已移除 */

.main-card :deep(.el-table th) { white-space: nowrap; }
.main-card :deep(.el-table th .cell) { display: flex !important; align-items: center !important; gap: 2px; white-space: nowrap; }
.main-card :deep(.el-table .caret-wrapper) { flex-shrink: 0; margin-left: 0; }
</style>
