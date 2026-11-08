<template>
  <div class="reminder-manage">
    <div class="page-header">
      <h2>提醒管理</h2>
      <span class="page-hint">点击任意一行查看提醒详情</span>
    </div>

    <!-- 搜索栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="query.keyword" placeholder="搜索客户名称..." clearable prefix-icon="Search" style="width: 200px" @change="search" />
        <el-tree-select
          v-model="query.deptId"
          :data="offices"
          check-strictly
          clearable
          node-key="id"
          :props="{ label: 'deptName', children: 'children' }"
          placeholder="选择部门（含全部子部门）"
          style="width: 190px"
          @change="search"
        />
        <el-select v-model="query.ownerId" placeholder="全部负责人" clearable filterable style="width: 140px" @change="search">
          <el-option v-for="u in owners" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <!-- 【2026-11-08 审计修复】按钮文案「一键清空」改「清除本页」: 实际只处理当前页行,
             原文案误导用户以为清空全部提醒(小程序端同名契约钉的是 miniprogram 文件, 与 PC 无关) -->
        <el-button type="danger" plain size="small" @click="handleClearAll">清除本页</el-button>
      </div>
    </div>

    <!-- 表格区域 -->
    <div class="table-area">
      <div class="table-wrap" ref="tableWrapRef">
      <el-table v-loading="listLoading" :data="tableData" stripe style="width:100%" size="default" :height="tableHeight"
        @row-click="openDetail" :row-style="{ cursor: 'pointer' }" title="点击查看提醒详情">
        <el-table-column label="类型" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row._type === 'share'" type="warning" size="small" effect="dark">分享</el-tag>
            <el-tag v-else-if="row._type === 'invoice'" :type="row._invoiceTagType" size="small" effect="dark">开票</el-tag>
            <el-tag v-else type="info" size="small">到期</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="客户名称" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || '—' }}</template>
        </el-table-column>
        <!-- 【UI 整改 2026-10-08】到期日合并进提示内容：分享/开票行无到期日，原先独立"到期日"列整列渲染
             「—」连片且白占 100px 列宽；现到期日期只在有值时随提示内容展示 -->
        <el-table-column label="提示内容" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row._type === 'share'">{{ row._shareLabel }}</span>
            <span v-else-if="row._type === 'invoice'">
              {{ row._invoiceLabel }}<template v-if="row.expireDate"> · {{ row.expireDate }}</template>
            </span>
            <span v-else>{{ row.expireDate || '—' }} 到期 · 剩 {{ row.remainingDays }} 天</span>
          </template>
        </el-table-column>
        <!-- 【C10 三轮 2026-10-09】空值占位统一 dashIfEmpty（此前负责人列空白与同表客户名 '—' 两种口径） -->
        <el-table-column prop="ownerName" label="负责人" width="80" :formatter="dashIfEmpty" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <!-- 状态列改中性色：此前与「类型」列同色同形，两列无法区分（浏览器视觉走查发现） -->
            <el-tag v-if="row._type === 'share'" type="info" size="small">待查看</el-tag>
            <el-tag v-else-if="row._type === 'invoice'" type="info" size="small">待处理</el-tag>
            <el-tag v-else :type="getStatusType(row.remainingDays)" size="small">{{ getStatusText(row.remainingDays) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
            <template v-if="row._type === 'share'">
              <!-- 【INV-11 术语统一】"已读"→"已处理"(与详情页同口径) -->
              <el-button link type="primary" size="small" @click.stop="handleProcessShare(row)">已处理</el-button>
            </template>
            <template v-else-if="row._type === 'invoice'">
              <el-button link type="primary" size="small" @click.stop="handleProcessShare(row)">已处理</el-button>
            </template>
            <template v-else>
              <el-button v-if="!row.todayFollowedUp" link type="primary" size="small" @click.stop="handleFollowUp(row)">已跟进</el-button>
              <el-button v-else link type="info" size="small" disabled>已跟进</el-button>
              <el-button link type="warning" size="small" @click.stop="handleExtend(row)">申请延期</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="rem-pager">
        <span class="rem-pager-info">{{ total > 0 ? (query.current - 1) * query.size + 1 : 0 }}-{{ Math.min(query.current * query.size, total) }} / 共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="pageSizes(query.size)"
          layout="sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="handleSizeChange"
          :pager-count="5"
          size="small"
          background
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExpiringList, getReminderList, processReminder, ensureExpiring } from '@/api/reminder'
import { submitApproval } from '@/api/approval'
import { getScopeDepts, getScopeUsers } from '@/api/system'
import { useUserStore } from '@/store/user'
import { pageSizes } from '@/utils/pagination'
import { buildReminderDetailLocation, remainingDaysTagType, remainingDaysText } from '@/utils/reminder-detail'
import { confirmAction } from '@/utils/confirm'
// 【C10 三轮 2026-10-09】表格空值占位唯一 formatter
import { dashIfEmpty } from '@/utils/format'

const userStore = useUserStore()
const router = useRouter()

const tableData = ref([])
const total = ref(0)
const offices = ref([])
const owners = ref([])
// 【UI 整改 2026-10-08】列表加载态：三路提醒并发拉取期间表格遮罩
const listLoading = ref(false)

const query = reactive({
  current: 1,
  size: 15,
  keyword: '',
  deptId: null,
  ownerId: null
})

// 【B2 2026-10-21 二轮·服务端分页】合并列表的六段固定拼接序（分享3类 → 开票2类 → 到期）。
// 每段都是独立的服务端分页源（/reminder/list 或 /reminder/expiring-list，均返回 MyBatis-Plus
// IPage 的真实 total，keyword/ownerId/deptId 与展示同口径叠加）；段内服务端排序稳定
// （reminder: orderByDesc(createdAt)，expiring: orderByAsc(expireDate)），因此
// "全局第 k 条 = 某段第 k-段偏移 条"的映射在翻页间不重不漏。
const SEGMENTS = [
  { key: 'share_to', kind: 'share' },
  { key: 'share_by', kind: 'share' },
  { key: 'share_owner', kind: 'share' },
  { key: 'invoice_overdue', kind: 'invoice' },
  { key: 'invoice_never', kind: 'invoice' },
  { key: 'expiring', kind: 'expiring' },
]
const SHARE_LABELS = { share_to: '客户已分享给你', share_by: '你分享了客户', share_owner: '你的客户被分享' }

// 【B2 二轮】单段按服务端分页取一页，返回映射后的行 + 该段服务端真实 total。
// 三路统一携带 keyword（/reminder/list 自 C1 2026-10-21 起服务端消费 keyword，
// ReminderService.applyKeywordScope：客户名/编码/类型标签匹配，与 owner/dept 范围 AND 叠加，
// total 与筛选同口径）——客户端不再做合并后二次过滤。
const fetchSegmentPage = async (seg, current, size) => {
  if (seg.kind === 'expiring') {
    const res = await getExpiringList({ current, size, keyword: query.keyword, deptId: query.deptId, ownerId: query.ownerId })
    const rows = (res.data?.records || []).map(r => ({ ...r, _type: 'expiring' }))
    return { rows, total: Number(res.data?.total ?? rows.length), current, size }
  }
  const res = await getReminderList({ current, size, type: seg.key, status: 'pending', keyword: query.keyword, ownerId: query.ownerId, deptId: query.deptId })
  const rows = (res.data?.records || []).map(item => ({
    reminderId: item.id,
    customerId: item.customerId,
    customerName: item.customerName,
    ownerName: item.ownerName,
    officeName: item.officeName || '',
    reminderType: item.reminderType,
    ...(seg.kind === 'share'
      ? { _type: 'share', _shareLabel: SHARE_LABELS[seg.key] || seg.key }
      : {
          _type: 'invoice',
          _invoiceType: seg.key,
          _invoiceTagType: seg.key === 'invoice_overdue' ? 'danger' : 'warning',
          _invoiceLabel: seg.key === 'invoice_overdue'
            ? `已超期 ${(item.overdueDays || 0)} 天未开票`
            : '待首次开票',
        }),
  }))
  return { rows, total: Number(res.data?.total ?? rows.length), current, size }
}

// 【B2 二轮】取段内行区间 [a,b)：按页大小对齐成块逐块请求（区间长度≤页大小，最多跨 2 块），
// 拼接后本地精确切片。probe 为探测阶段已取回的该段第 1 块（存在且页大小一致时直接复用免请求）。
const fetchSegmentRange = async (seg, a, b, size, probe) => {
  const firstBlock = Math.floor(a / size)
  const lastBlock = Math.floor((b - 1) / size)
  const blocks = []
  for (let blk = firstBlock; blk <= lastBlock; blk++) {
    if (blk === 0 && probe && probe.current === 1 && probe.size === size && probe.rows.length >= a) {
      blocks.push(probe.rows)
    } else {
      const r = await fetchSegmentPage(seg, blk + 1, size).catch(() => null)
      if (!r) break
      blocks.push(r.rows)
      if (!r.rows.length) break // 段越界（total 比取数时变小）：截断循环防死循环
    }
  }
  const merged = blocks.flat()
  const base = firstBlock * size
  return merged.slice(a - base, b - base)
}

// 【B2 二轮】请求序号守卫：翻页/筛选快速切换时旧响应不覆盖新页（同 recycle.vue 的 listSeq 模式）
let loadSeq = 0

const search = () => { query.current = 1; loadData() }
const handleSizeChange = () => { query.current = 1; loadData() }

const loadData = async () => {
  const seq = ++loadSeq
  listLoading.value = true
  try {
    const size = Math.max(1, Number(query.size) || 1)
    const page = Math.max(1, Number(query.current) || 1)
    const start = (page - 1) * size
    const end = start + size
    // 阶段一：并发探测六段的服务端真实 total（首段直接取整页，页 1 时可复用为当前页块）
    const probes = await Promise.all(SEGMENTS.map((seg, i) =>
      fetchSegmentPage(seg, 1, i === 0 ? size : 1).catch(() => null)))
    const totals = probes.map(p => (p && p.total) || 0)
    // 阶段二：按段拼接序计算全局偏移，把当前页 [start,end) 映射到触及段的段内区间并取块
    const rows = []
    let offset = 0
    for (let i = 0; i < SEGMENTS.length && offset < end; i++) {
      const segLen = totals[i]
      const segStart = offset
      const segEnd = offset + segLen
      if (end > segStart && start < segEnd && segLen > 0) {
        const a = Math.max(start, segStart) - segStart
        const b = Math.min(end, segEnd) - segStart
        rows.push(...await fetchSegmentRange(SEGMENTS[i], a, b, size, probes[i]))
      }
      offset += segLen
    }
    if (seq !== loadSeq) return
    // 服务端分页口径：当前页行 + 分页器 total = 六段服务端 total 之和（真实、与筛选同口径），
    // 翻页/筛选每次重新取数；不再存在"只取 15 条/200 条当全量"的客户端假分页
    tableData.value = rows
    total.value = totals.reduce((s, t) => s + t, 0)
  } catch (e) {
  } finally {
    if (seq === loadSeq) listLoading.value = false
  }
}

const handleClearAll = async () => {
  // 【B2 二轮 2026-10-21】服务端分页后 tableData=当前页行：批量处理只覆盖当前页，
  // 弹窗文案如实标注"当前页 N 条 / 筛选共 M 条"（与 C3 建议口径一致），不再声称"所有提醒"
  const targets = tableData.value.filter(r => r.reminderId)
  try {
    // 【UI 整改 2026-10-08】确认弹窗收敛到 confirmAction：批量清空属批量不可逆操作，红色确认钮
    // 【2026-11-08 审计修复】弹窗文案随按钮由「一键清空」改「清除本页」口径（逻辑不动，仅文案）
    await confirmAction(`确认清除本页 ${targets.length} 条提醒？（当前筛选共 ${total.value} 条，其余页需翻页逐页处理）分享/开票提醒标记已处理，到期提醒标记已跟进。`, '清除本页提醒', { confirmText: '清除' })
  } catch { return }
  let done = 0
  for (const r of targets) {
    try { await processReminder(r.reminderId); done++ } catch {}
  }
  ElMessage.success(`已处理 ${done} 条提醒`)
  // 【工作流复核确认修复】处理后重置回第一页: 列表缩短后当前页可能越界(出现"16-15/共15条"区间错乱+空表)
  query.current = 1
  loadData()
  userStore.refreshBadges()
}

const handleProcessShare = async (row) => {
  try {
    await processReminder(row.reminderId)
    ElMessage.success('已标记处理')
    query.current = 1 // 处理后列表缩短, 防当前页越界
    loadData()
    userStore.refreshBadges()
  } catch (e) {}
}

const handleFollowUp = async (row) => {
  // 【工作流复核确认修复】到期行提醒实体未物化(reminderId=null)时不可处理, 明确提示而非静默失败
  // 【体检 P2 2026-10-12】指引改为真实出路(INV-10): 原文案指向不存在的「今日跟进」页,
  // 降级详情页又无处理按钮——实际出路是次日 2AM 凌晨任务自动物化, 或请管理员在提醒页触发补生成
  if (!row.reminderId) {
    ElMessage.warning('该客户今日提醒实体尚未生成（每日 2:00 自动补建）；如需立即处理，请联系管理员在提醒管理页触发补生成，或明日再来跟进')
    return
  }
  try {
    await processReminder(row.reminderId)
    row.todayFollowedUp = true
    ElMessage.success('已标记今日跟进')
    userStore.refreshBadges()
  } catch (e) {}
}

const handleExtend = async (row) => {
  // 【工作流复核确认修复】提交审批前加确认(原直接提交审批单, 误触即发起流程)
  try {
    await confirmAction(`确定为「${row.customerName || '该客户'}」提交保护期延期审批？`, '申请延期', { confirmText: '提交审批' })
  } catch { return }
  try {
    await submitApproval({ type: 'extend', customerId: String(row.customerId), actionAfterApprove: '保护期延长(天数以审批单为准)' })
    ElMessage.success('延期申请已提交，等待审批')
  } catch (e) {}
}

const getStatusType = (days) => remainingDaysTagType(days)

const getStatusText = (days) => remainingDaysText(days)

// 2026-09-13: 点击行（或"详情"按钮）进入提醒详情页；无提醒实体的到期行以客户信息降级展示
const openDetail = (row) => {
  router.push(buildReminderDetailLocation(row))
}

// 表格高度：动态计算可用高度，保证分页器完整显示、不被裁切
const tableWrapRef = ref(null)
const tableHeight = ref(300)
let resizeObserver = null
const updateTableHeight = () => {
  if (tableWrapRef.value) tableHeight.value = tableWrapRef.value.clientHeight
}

onMounted(async () => {
  // 【2026-09-23 缺陷修复】ensure-expiring 后端已收紧为 admin-only(P1-09 批次): 普通用户调用必 403
  // 弹"仅管理员可批量生成到期提醒"(生产实测 NM005 每次进页必弹)。小程序 9-22 已修,PC 漏改。
  // 到期提醒由服务端 CustomerExpireTask 每晚物化兜底,客户端补生成仅对 admin 有意义。
  const catchUp = userStore.isAdmin() ? ensureExpiring().catch(() => {}) : Promise.resolve()
  catchUp.finally(loadData)
  try {
    const res = await getScopeDepts()
    offices.value = res.data || []
  } catch (e) {}
  try {
    // 负责人筛选=范围感知人员(选范围外人只会得到空列表);2026-09-23 前误用选人接口(集团全量)
    const res = await getScopeUsers()
    owners.value = res.data || []
  } catch (e) {}
  nextTick(updateTableHeight)
  if (tableWrapRef.value) {
    resizeObserver = new ResizeObserver(updateTableHeight)
    resizeObserver.observe(tableWrapRef.value)
  }
})

onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect()
})
</script>

<style scoped>
.page-header { margin-bottom: 12px; display: flex; align-items: baseline; gap: 10px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #333; }
.page-hint { font-size: 12px; color: var(--el-color-info); }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar-left { display: flex; gap: 8px; }
.toolbar-right { display: flex; gap: 8px; }
.reminder-manage { height: calc(100vh - 124px); display: flex; flex-direction: column; }
.table-area { flex: 1; min-height: 0; display: flex; flex-direction: column; background: #fff; border: 1px solid #e4e7ed; border-radius: 8px; overflow: hidden }
.table-wrap { flex: 1; min-height: 0; }
.rem-pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  box-sizing: border-box;
  width: 100%;
  max-width: 100%;
  padding: 10px 16px;
  border-top: 1px solid #f0f2f5;
  color: var(--el-color-info);
  font-size: 12px;
  flex-shrink: 0;
  background: #fafbfc;
  overflow-x: auto;
}
.rem-pager-info { color: var(--el-color-info); white-space: nowrap; margin-right: auto; }
.rem-pager .el-pagination { flex-shrink: 0; }
.table-area :deep(.el-table td.el-table__cell) { font-size: 14px; padding: 10px 0 }
.table-area :deep(.el-table th.el-table__cell) { font-size: 14px; font-weight: 600; padding: 10px 0 }
</style>
