<template>
  <div class="customer-detail" v-if="customer" v-loading="loading">
    <!-- 导航行 -->
    <div class="top-bar">
      <span class="back-link" @click="$router.push(readonly ? '/sea' : '/customer')">
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
        <div class="hero-badge" :class="isCore ? 'core' : 'non'">
          <el-icon><Medal /></el-icon> {{ isCore ? '核心客户' : '非核心' }}
        </div>
        <div class="hero-expire">
          <div class="expire-date">{{ customer.expireDate }}</div>
          <div class="expire-tag" :class="'lvl-'+expireLevel">
            <el-icon><Warning /></el-icon> {{ statusLabel }}
          </div>
        </div>
      </div>
    </div>

    <!-- 数据卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon-box gold"><el-icon><Trophy /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ incentiveScore }}</div>
          <div class="stat-label">激励积分</div>
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
          <div class="stat-num">{{ invoiceSummary.totalCount + paymentSummary.totalCount }}</div>
          <div class="stat-label">开票+回款</div>
        </div>
      </div>
    </div>

    <!-- Tab 内容区（各 tab 为子组件，共享 useCustomerDetail 上下文） -->
    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="基本信息" name="info"><InfoTab /></el-tab-pane>
      <el-tab-pane v-if="canViewFinance" name="finance">
        <template #label>
          <span>开票回款 <el-tag size="small" :type="bothThresholdMet?'success':'danger'">{{ bothThresholdMet?'✓达标':'✗未达标'}}</el-tag></span>
        </template>
        <FinanceTab />
      </el-tab-pane>
      <el-tab-pane name="indicator">
        <template #label>
          <span>核心指标 <el-tag size="small" :type="indicatorTotal >= 12 ? 'success' : 'warning'">{{ indicatorTotal }}/15</el-tag></span>
        </template>
        <IndicatorTab />
      </el-tab-pane>
      <el-tab-pane name="incentive">
        <template #label>
          <span>四项激励 <el-tag size="small" type="warning">{{ incentiveRecords.length }}</el-tag></span>
        </template>
        <IncentiveTab />
      </el-tab-pane>
      <!-- 【2026-09-13 移除「客户文件」Tab】ADR adr-20260909-customer-file-association 的需求
           （小程序为公司内部使用、不面向客户 ⇒ 不再需要"按客户关联文件"的能力）已作废；
           后端端点与关联表 biz_customer_file 由 V2026.09.13.5 迁移一并删除。 -->
      <el-tab-pane label="跟进日志" name="logs"><LogsTab /></el-tab-pane>
    </el-tabs>

    <!-- 分享弹窗 -->
    <el-dialog v-model="showShareDialog" title="分享客户" width="480px">
      <p style="color:#909399;margin-bottom:12px;font-size:13px">选择用户后，该用户可查看此客户信息（只读）</p>
      <div v-if="shares.length" style="margin-bottom:12px;display:flex;flex-wrap:wrap;gap:6px">
        <el-tag v-for="s in shares" :key="s.id" closable size="small" @close="handleRemoveShare(s)">{{ s.sharedToUserName }}</el-tag>
      </div>
      <el-select v-model="shareUserIds" multiple filterable placeholder="选择用户..." style="width:100%">
        <el-option v-for="u in allUsers" :key="u.id" :label="u.realName" :value="u.id" />
      </el-select>
      <template #footer><el-button @click="showShareDialog=false">取消</el-button><el-button type="primary" @click="handleAddShare">确认分享</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { provide, onMounted, watch } from 'vue'
import { useCustomerDetail, CustomerDetailKey } from '@/composables/useCustomerDetail'
import { useUserStore } from '@/store/user'
import InfoTab from './detail/InfoTab.vue'
import FinanceTab from './detail/FinanceTab.vue'
import IndicatorTab from './detail/IndicatorTab.vue'
import IncentiveTab from './detail/IncentiveTab.vue'
import LogsTab from './detail/LogsTab.vue'

// 共享上下文: 父级实例化一次并 provide,各 tab 子组件 inject
const detail = useCustomerDetail()
provide(CustomerDetailKey, detail)
const userStore = useUserStore()
const canEditCustomer = userStore.hasPermission('customer:edit')
const canShareCustomer = userStore.hasPermission('customer:edit')

const {
  customer, loading, activeTab, readonly, readonlyTag, handleEdit,
  showShareDialog, shares, shareUserIds, allUsers, handleAddShare, handleRemoveShare,
  isCore, expireLevel, statusLabel, incentiveScore, indicator, totalScore,
  invoiceSummary, paymentSummary, avatarColor, bothThresholdMet, indicatorTotal, incentiveRecords,
  canViewFinance,
  canViewContacts,
  loadAll, loadDicts, loadUsers, loadIncentiveRecords,
} = detail

onMounted(() => { loadAll(); loadDicts(); loadUsers() })
watch(customer, (v) => { if (v?.customerName) loadIncentiveRecords() })
</script>

<style scoped>
.top-bar { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px }
.back-link { color:#409eff; font-size:14px; cursor:pointer; display:flex; align-items:center; gap:4px }
.back-link:hover { color:#66b1ff }
.top-actions { display:flex; gap:8px }
.hero-card { background:linear-gradient(135deg,#1d39c4,#409eff); border-radius:12px; padding:28px 32px; color:#fff; margin-bottom:16px; display:flex; justify-content:space-between; align-items:center; box-shadow:0 4px 20px rgba(64,158,255,.25) }
.hero-left { display:flex; align-items:center; gap:20px }
.hero-avatar { width:60px; height:60px; border-radius:14px; display:flex; align-items:center; justify-content:center; font-size:24px; font-weight:800; border:2px solid rgba(255,255,255,.3); flex-shrink:0 }
.hero-main h1 { font-size:22px; font-weight:700; margin:0 0 6px; letter-spacing:.5px }
.hero-meta { display:flex; gap:10px; flex-wrap:wrap }
.meta-tag { display:inline-flex; align-items:center; gap:4px; font-size:12px; padding:3px 10px; background:rgba(255,255,255,.15); border-radius:6px; opacity:.9 }
.hero-right { text-align:right; display:flex; flex-direction:column; align-items:flex-end; gap:8px }
.hero-badge { display:inline-flex; align-items:center; gap:4px; padding:6px 16px; border-radius:20px; font-size:13px; font-weight:600 }
.hero-badge.core { background:rgba(82,196,26,.3) }.hero-badge.non { background:rgba(255,255,255,.12) }
.hero-expire { text-align:right }.expire-date { font-size:28px; font-weight:700 }
.expire-tag { display:inline-flex; align-items:center; gap:4px; padding:2px 10px; border-radius:10px; font-size:12px; margin-top:2px }
.expire-tag.lvl-red { background:rgba(245,108,108,.3) }.expire-tag.lvl-orange { background:rgba(230,162,60,.3) }.expire-tag.lvl-blue { background:rgba(255,255,255,.2) }
.stat-cards { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; margin-bottom:20px }
.stat-card { background:#fff; border-radius:10px; padding:18px 16px; display:flex; align-items:center; gap:14px; box-shadow:0 1px 3px rgba(0,0,0,.04); transition:box-shadow .2s }
.stat-card:hover { box-shadow:0 4px 12px rgba(0,0,0,.08) }
.stat-icon-box { width:44px; height:44px; border-radius:12px; display:flex; align-items:center; justify-content:center; font-size:20px }
.stat-icon-box.gold { background:#fef3e2; color:#e6a23c }.stat-icon-box.red { background:#fef0f0; color:#f56c6c }
.stat-icon-box.blue { background:#ecf5ff; color:#409eff }.stat-icon-box.green { background:#f0f9eb; color:#67c23a }
.stat-num { font-size:22px; font-weight:700; color:#1f2329; line-height:1.2 }
.stat-label { font-size:12px; color:#909399; margin-top:2px }
.detail-tabs { background:#fff; border-radius:10px; padding:4px 20px 20px; box-shadow:0 1px 3px rgba(0,0,0,.04) }
.detail-tabs :deep(.el-tabs__header) { margin-bottom:16px }
.detail-tabs :deep(.el-tabs__item) { font-size:14px; font-weight:500 }
</style>
