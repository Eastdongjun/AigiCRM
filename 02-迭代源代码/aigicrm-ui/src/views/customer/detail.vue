<template>
  <!-- 【B5 2026-10-21】v-loading 从 v-if="customer" 同元素拆到外层容器：首屏加载期间遮罩可渲染；
       客户主体缺失时不再整页空白——首屏失败渲染页面级错误态（重试/返回），分区失败渲染警示条 -->
  <div class="customer-detail" v-loading="loading">
    <!-- 【B5 2026-10-21】分区单路失败警示条：allSettled 后仅失败分区记录在此，其余内容照常展示 -->
    <el-alert
      v-if="customer && sectionErrors.length"
      class="section-error-bar" type="warning" :closable="false" show-icon
      :title="`部分数据加载失败：${sectionErrors.map(s => s.label).join('、')}，对应内容可能缺失`">
      <el-button link type="primary" @click="loadAll">重试加载</el-button>
    </el-alert>

    <template v-if="customer">
    <!-- 导航行 -->
    <div class="top-bar">
      <!-- 【UI 整改 2026-10-08】返回改 goBack：从列表进来时携带 tab/搜索词/页码带参回跳，返回不再丢筛选 -->
      <span class="back-link" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> {{ readonly ? '返回公海池' : '返回客户列表' }}
      </span>
      <div class="top-actions" v-if="!readonly">
        <el-button v-if="canEditCustomer" size="small" @click="handleEdit"><el-icon><Edit /></el-icon> 编辑</el-button>
        <el-button v-if="canShareCustomer" size="small" @click="showShareDialog = true"><el-icon><Share /></el-icon> 分享</el-button>
      </div>
      <el-tag v-else type="info" size="large">{{ readonlyTag }}</el-tag>
    </div>

    <!-- 头部卡片 -->
    <div class="hero-card">
      <div class="hero-left">
        <div class="hero-avatar" :style="{background: avatarColor}">{{ customer.customerName?.charAt(0) }}</div>
        <div class="hero-main">
          <h1>{{ customer.customerName }}</h1>
          <div class="hero-meta">
            <span class="meta-tag"><el-icon><OfficeBuilding /></el-icon> {{ customer.industry || '—' }}</span>
            <span class="meta-tag"><el-icon><PriceTag /></el-icon> {{ customer.devType || '—' }}</span>
            <span class="meta-tag"><el-icon><User /></el-icon> {{ customer.ownerName }}</span>
            <span v-if="customer.groupName" class="meta-tag"><el-icon><Connection /></el-icon> {{ customer.groupName }}</span>
          </div>
        </div>
      </div>
      <div class="hero-right">
        <!-- 【裁决 2026-10-10 + 2026-11-03 双门槛配置化】核心三态: 真核心(年度双>=真核心门槛)/争核心(双>=争核心门槛未双达)/非核心 -->
        <div class="hero-badge" :class="coreType || 'non'">
          <el-icon><Medal /></el-icon> {{ coreTypeLabel }}
        </div>
        <div class="hero-expire">
          <div class="expire-date">{{ customer.expireDate }}</div>
          <div class="expire-tag" :class="'lvl-'+expireLevel">
            <el-icon><Warning /></el-icon> {{ statusLabel }}
          </div>
          <!-- PR-11 首批:保护剩余天数集中呈现(负数=已过期 N 天) -->
          <div class="expire-days" v-if="expireDaysLeft !== null">
            {{ expireDaysLeft >= 0 ? `保护剩余 ${expireDaysLeft} 天` : `已过期 ${-expireDaysLeft} 天` }}
          </div>
        </div>
      </div>
    </div>

    <!-- 【PR-11 首批】统一作业概览条:归属/最近跟进/下一任务 集中呈现(不切 Tab 即可掌握状态) -->
    <div class="overview-strip">
      <div class="overview-item">
        <span class="overview-label">归属</span>
        <span class="overview-value">负责人 {{ customer.ownerName }}<template v-if="customer.deptName"> · {{ customer.deptName }}</template><template v-if="customer.groupName"> · {{ customer.groupName }}</template></span>
      </div>
      <div class="overview-item">
        <span class="overview-label">最近跟进</span>
        <span class="overview-value" v-if="latestLog">{{ (latestLog.createdAt || '').slice(0, 10) }} {{ latestLog.content }}</span>
        <span class="overview-value muted" v-else>暂无跟进记录</span>
      </div>
      <div class="overview-item">
        <span class="overview-label">下一任务</span>
        <!-- 【UI 整改 2026-10-08·⑫】不向用户暴露"任务模块规划中"路线图信息，改用户视角引导话术 -->
        <span class="overview-value muted">暂无，可在跟进日志中安排</span>
      </div>
    </div>

    <!-- 数据卡片 -->
    <div class="stat-cards">
      <!-- 【2026-10-27 深度检查修复】激励学分卡随 incentive:view 门控(无权限不显示 0 分误导) -->
      <div v-if="canViewIncentive" class="stat-card">
        <div class="stat-icon-box gold"><el-icon><Trophy /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ incentiveScore }}</div>
          <div class="stat-label">激励学分</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box red"><el-icon><StarFilled /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ indicator?.relationshipScore || 0 }}</div>
          <div class="stat-label">客情关系</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box blue"><el-icon><TrendCharts /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ totalScore }}</div>
          <div class="stat-label">综合得分</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box green"><el-icon><CircleCheck /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ fmtWanShort(annualInvoiceAmount) }}<span class="stat-unit">万</span></div>
          <div class="stat-label">年度开票{{ stageSummary.baseYear ? `(${stageSummary.baseYear})` : '' }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box gold"><el-icon><Money /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ fmtWanShort(annualPaymentAmount) }}<span class="stat-unit">万</span></div>
          <div class="stat-label">年度回款{{ stageSummary.baseYear ? `(${stageSummary.baseYear})` : '' }}</div>
        </div>
      </div>
    </div>

    <!-- Tab 内容区（各 tab 为子组件，共享 useCustomerDetail 上下文） -->
    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="基本信息" name="info"><InfoTab /></el-tab-pane>
      <el-tab-pane v-if="canViewFinance" name="finance">
        <template #label>
          <span>开票回款 <el-tag size="small" :type="coreType === 'core' ? 'success' : coreType === 'quasi' ? 'warning' : 'info'">{{ coreTypeLabel }}</el-tag></span>
        </template>
        <FinanceTab />
      </el-tab-pane>
      <el-tab-pane name="indicator">
        <template #label>
          <!-- 五项评分=客情参考(2026-10-10 起不参与核心判定), 展示保持低噪 -->
          <span>核心指标 <el-tag size="small" type="info">{{ indicatorTotal }}/15</el-tag></span>
        </template>
        <IndicatorTab />
      </el-tab-pane>
      <!-- 【2026-10-27 深度检查修复】四项服务 Tab 随 incentive:view 门控: 无权限不发请求、不渲染误导性空态 -->
      <el-tab-pane v-if="canViewIncentive" name="incentive">
        <template #label>
          <span>四项服务 <el-tag size="small" type="warning">{{ incentiveRecords.length }}</el-tag></span>
        </template>
        <IncentiveTab />
      </el-tab-pane>
      <!-- 【2026-09-13 移除「客户文件」Tab】ADR adr-20260909-customer-file-association 的需求
           （小程序为公司内部使用、不面向客户 ⇒ 不再需要"按客户关联文件"的能力）已作废；
           后端端点与关联表 biz_customer_file 由 V2026.09.13.5 迁移一并删除。 -->
      <el-tab-pane label="跟进日志" name="logs"><LogsTab /></el-tab-pane>
      <!-- 【生命线 2026-11-02 裁决D】客户关键日期事件全记录(建档/开票/延期/释放/捞取/转移) -->
      <el-tab-pane label="生命线" name="events"><EventsTab /></el-tab-pane>
    </el-tabs>

    <!-- 分享弹窗 -->
    <el-dialog v-model="showShareDialog" title="分享客户" width="480px">
      <p style="color:var(--el-color-info);margin-bottom:12px;font-size:13px">选择用户后，该用户可查看此客户信息（只读）</p>
      <div v-if="shares.length" style="margin-bottom:12px;display:flex;flex-wrap:wrap;gap:6px">
        <el-tag v-for="s in shares" :key="s.id" closable size="small" @close="handleRemoveShare(s)">{{ s.sharedToUserName }}</el-tag>
      </div>
      <el-select v-model="shareUserIds" multiple filterable placeholder="选择用户..." style="width:100%">
        <el-option v-for="u in allUsers" :key="u.id" :label="u.realName" :value="u.id" />
      </el-select>
      <template #footer><el-button @click="showShareDialog=false">取消</el-button><el-button type="primary" @click="handleAddShare">确认分享</el-button></template>
    </el-dialog>
    </template>

    <!-- 【B5 2026-10-21】首屏 getCustomer 失败：页面级错误态 + 重试/返回（原先只剩 toast、页面空白无出路） -->
    <div v-else-if="loadError" class="detail-error">
      <el-icon class="detail-error-icon"><Warning /></el-icon>
      <div class="detail-error-title">客户详情加载失败</div>
      <div class="detail-error-msg">{{ loadError }}</div>
      <div class="detail-error-actions">
        <el-button type="primary" @click="loadAll">重试</el-button>
        <el-button @click="goBack">{{ readonly ? '返回公海池' : '返回客户列表' }}</el-button>
      </div>
    </div>
    <div v-else class="detail-empty">未找到该客户</div>
  </div>
</template>

<script setup>
import { provide, onMounted, watch, computed } from 'vue'
import { useCustomerDetail, CustomerDetailKey } from '@/composables/useCustomerDetail'
import { useUserStore } from '@/store/user'
import InfoTab from './detail/InfoTab.vue'
import FinanceTab from './detail/FinanceTab.vue'
import IndicatorTab from './detail/IndicatorTab.vue'
import IncentiveTab from './detail/IncentiveTab.vue'
import LogsTab from './detail/LogsTab.vue'
import EventsTab from './detail/EventsTab.vue'

// 共享上下文: 父级实例化一次并 provide,各 tab 子组件 inject
const detail = useCustomerDetail()
provide(CustomerDetailKey, detail)
const userStore = useUserStore()
const canEditCustomer = userStore.hasPermission('customer:edit')
// 【L4 2026-10-12 测试报告】平台 admin 无集团归属(GroupScopeUtil 对 isAdmin 恒 null),分享必 403
// 「无有效集团归属，禁止共享」——入口隐藏防误导(业务账号不受影响)。
// isAdmin 是 store action 须调用取值, computed 包装保持响应式(与 useCustomerDetail 同款)
const canShareCustomer = computed(() => userStore.hasPermission('customer:edit') && !userStore.isAdmin())

const {
  customer, loading, loadError, sectionErrors, activeTab, readonly, readonlyTag, handleEdit,
  showShareDialog, shares, shareUserIds, allUsers, handleAddShare, handleRemoveShare,
  expireLevel, statusLabel, incentiveScore, indicator, totalScore, expireDaysLeft, latestLog,
  avatarColor, indicatorTotal, incentiveRecords,
  coreType, coreTypeLabel, stageSummary, annualInvoiceAmount, annualPaymentAmount,
  canViewFinance,
  canViewContacts,
  canViewIncentive,
  route, router,
  loadAll, loadDicts, loadUsers, loadIncentiveRecords,
} = detail

// 年度金额短格式: 130.50 → "130.5"; 无数据时 computed 已兜底 0
const fmtWanShort = (v) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0'
  return String(Math.round(n * 100) / 100)
}

// 【UI 整改 2026-10-08·③列表态保持】返回客户列表时携带来源列表的 tab/搜索词/页码
//（列表页 viewDetail 跳转时写入 query），消除"进详情返回即丢筛选条件"；
// 非列表来源（公海/分享只读）无这些参数，回列表落默认态
const goBack = () => {
  if (readonly.value) {
    router.push('/sea')
    return
  }
  const query = {}
  if (route.query.tab) query.tab = String(route.query.tab)
  if (route.query.kw) query.kw = String(route.query.kw)
  if (route.query.page) query.page = String(route.query.page)
  router.push({ path: '/customer', query })
}

onMounted(() => { loadAll(); loadDicts(); loadUsers() })
watch(customer, (v) => { if (v?.customerName) loadIncentiveRecords() })
</script>

<style scoped>
/* 【B5 2026-10-21】首屏失败页面级错误态与分区失败警示条 */
.section-error-bar { margin-bottom: 12px }
.detail-error { min-height: 60vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: var(--text-muted) }
.detail-error-icon { font-size: 48px; color: var(--el-color-danger) }
.detail-error-title { font-size: 18px; font-weight: 600; color: #1f2329 }
.detail-error-msg { font-size: 13px; word-break: break-all }
.detail-error-actions { margin-top: 12px; display: flex; gap: 8px }
.detail-empty { min-height: 60vh; display: flex; align-items: center; justify-content: center; color: var(--text-muted) }
.top-bar { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px }
/* 【UI 整改 2026-10-08】返回链接改主题变量，不再硬编码 var(--el-color-primary) */
.back-link { color:var(--el-color-primary); font-size:14px; cursor:pointer; display:flex; align-items:center; gap:4px }
.back-link:hover { color:var(--el-color-primary-light-3) }
.top-actions { display:flex; gap:8px }
/* 【UI 整改 2026-10-08·⑨】hero 渐变/阴影对齐品牌深蓝体系（#16337a→#1d4ed8→#2563eb，同 StatCards.vue 客户条带），
   替换偏亮蓝紫的 #1d39c4→var(--el-color-primary) 旧渐变 */
.hero-card { background:var(--hero-gradient); border-radius:12px; padding:28px 32px; color:#fff; margin-bottom:16px; display:flex; justify-content:space-between; align-items:center; box-shadow:var(--hero-shadow) }
.hero-left { display:flex; align-items:center; gap:20px }
.hero-avatar { width:60px; height:60px; border-radius:14px; display:flex; align-items:center; justify-content:center; font-size:24px; font-weight:800; border:2px solid rgba(255,255,255,.3); flex-shrink:0 }
.hero-main h1 { font-size:22px; font-weight:700; margin:0 0 6px; letter-spacing:.5px }
.hero-meta { display:flex; gap:10px; flex-wrap:wrap }
.meta-tag { display:inline-flex; align-items:center; gap:4px; font-size:12px; padding:3px 10px; background:rgba(255,255,255,.15); border-radius: var(--radius-sm); opacity:.9 }
.hero-right { text-align:right; display:flex; flex-direction:column; align-items:flex-end; gap:8px }
.hero-badge { display:inline-flex; align-items:center; gap:4px; padding:6px 16px; border-radius:20px; font-size:13px; font-weight:600 }
.hero-badge.core { background:rgba(82,196,26,.3) }.hero-badge.quasi { background:rgba(250,173,20,.35) }.hero-badge.non { background:rgba(255,255,255,.12) }
.hero-expire { text-align:right }.expire-date { font-size:28px; font-weight:700 }
.expire-tag { display:inline-flex; align-items:center; gap:4px; padding:2px 10px; border-radius: var(--radius-md); font-size:12px; margin-top:2px }
.expire-tag.lvl-red { background:rgba(245,108,108,.3) }.expire-tag.lvl-orange { background:rgba(230,162,60,.3) }.expire-tag.lvl-blue { background:rgba(255,255,255,.2) }
/* 【ui-1 2026-10-17】5 张卡固定 4 列会在第二行留 3 个空列；改为自适应：
   ≥1280 一行 5 张（每张约 190px），1024-1279 收为 3+2，≤900 收为 2 列且末张奇数卡跨满行，避免空洞/挤压 */
.stat-cards { display:grid; grid-template-columns:repeat(auto-fit,minmax(190px,1fr)); gap:12px; margin-bottom:20px }
@media (max-width:1279px) { .stat-cards { grid-template-columns:repeat(3,minmax(0,1fr)) } }
@media (max-width:900px) {
  .stat-cards { grid-template-columns:repeat(2,minmax(0,1fr)) }
  .stat-card:last-child:nth-child(odd) { grid-column:1 / -1 }
}
/* 【PR-11 首批】统一作业概览条 */
.overview-strip { display:flex; gap:24px; flex-wrap:wrap; background:#f8fafc; border:1px solid #e2e8f0; border-radius: var(--radius-md); padding:12px 16px; margin-bottom:14px }
.overview-item { min-width:220px; flex:1 }
/* 【对比度 2026-10-17】浅色条上的辅助字改用 --text-muted(#6b7280)：在 #f8fafc 上 4.62:1，
   原 #94a3b8=2.45、#cbd5e1=1.42 均不达 AA */
.overview-label { display:block; font-size:12px; color:var(--text-muted); margin-bottom:4px }
.overview-value { font-size:13px; color:#334155; word-break:break-all }
.overview-value.muted { color:var(--text-muted) }
/* 【UI 整改 2026-10-08·⑨】蓝底 hero 上的辅助字改近白色（原 #64748b 对比度约 1.6:1 不可读，现 ≥4.5:1） */
.expire-days { font-size:12px; color:rgba(255,255,255,.92); text-align:right; margin-top:4px }
.stat-card { background:#fff; border-radius: var(--radius-md); padding:18px 16px; display:flex; align-items:center; gap:14px; box-shadow:0 1px 3px rgba(0,0,0,.04); transition:box-shadow .2s }
.stat-card:hover { box-shadow:0 4px 12px rgba(0,0,0,.08) }
.stat-icon-box { width:44px; height:44px; border-radius:12px; display:flex; align-items:center; justify-content:center; font-size:20px }
.stat-icon-box.gold { background:#fef3e2; color:var(--el-color-warning) }.stat-icon-box.red { background:#fef0f0; color:var(--el-color-danger) }
.stat-icon-box.blue { background:var(--el-color-primary-light-9); color:var(--el-color-primary) }.stat-icon-box.green { background:#f0f9eb; color:var(--el-color-success) }
.stat-num { font-size:22px; font-weight:700; color:#1f2329; line-height:1.2 }
.stat-unit { font-size:12px; font-weight:500; margin-left:2px }
/* 【对比度 2026-10-17】--el-color-info(#909399) 白底仅 3.08:1，改 --text-muted(4.83:1) 达 AA */
.stat-label { font-size:12px; color:var(--text-muted); margin-top:2px }
.detail-tabs { background:#fff; border-radius: var(--radius-md); padding:4px 20px 20px; box-shadow:0 1px 3px rgba(0,0,0,.04) }
.detail-tabs :deep(.el-tabs__header) { margin-bottom:16px }
.detail-tabs :deep(.el-tabs__item) { font-size:14px; font-weight:500 }
</style>
