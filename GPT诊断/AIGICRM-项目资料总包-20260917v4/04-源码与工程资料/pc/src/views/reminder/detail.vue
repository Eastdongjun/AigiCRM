<template>
  <div class="reminder-detail">
    <div class="page-header">
      <el-button link type="primary" @click="goBack">← 返回提醒管理</el-button>
      <h2>提醒详情</h2>
      <el-tag type="info" size="small" effect="plain">{{ typeLabel }}</el-tag>
      <el-tag v-if="hasReminder" :type="statusTagType" size="small" effect="dark">{{ statusText }}</el-tag>
      <el-tag v-else type="warning" size="small">未生成提醒</el-tag>
    </div>

    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      class="mb12"
    />
    <el-alert
      v-else-if="!hasReminder"
      title="该客户今日到期提醒尚未生成（可能已处理或尚未物化），以下为客户当前信息"
      type="info"
      :closable="false"
      show-icon
      class="mb12"
    />

    <div v-if="loading" class="card"><el-skeleton :rows="4" animated /></div>

    <template v-else>
      <div class="card">
        <div class="card-title">提醒信息</div>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="提醒类型">{{ typeLabel }}</el-descriptions-item>
          <el-descriptions-item label="提醒编号">{{ hasReminder ? reminder.id : '—' }}</el-descriptions-item>
          <el-descriptions-item label="处理状态">
            <el-tag v-if="hasReminder" :type="statusTagType" size="small">{{ statusText }}</el-tag>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="到期/超期口径">{{ timingText }}</el-descriptions-item>
          <el-descriptions-item label="提醒生成时间">{{ formatDateTime(reminder.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="处理时间">{{ formatDateTime(reminder.processedAt) }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ reminder.ownerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ reminder.officeName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="最后更新">{{ formatDateTime(reminder.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="card">
        <div class="card-title">
          客户信息
          <el-button v-if="customerId" link type="primary" size="small" class="title-action" @click="goCustomer">查看客户完整档案 ›</el-button>
        </div>
        <el-descriptions v-if="customer" :column="3" border size="small">
          <el-descriptions-item label="客户名称">{{ customer.customerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="客户编号">{{ customer.customerCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="客户状态">
            <el-tag :type="customerTagType" size="small">{{ customerStatusLabel(customer.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="行业">{{ customer.industry || '—' }}</el-descriptions-item>
          <el-descriptions-item label="地区">{{ customer.region || '—' }}</el-descriptions-item>
          <el-descriptions-item label="客户负责人">{{ customer.ownerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="保护期到期日">{{ customer.expireDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="剩余天数">
            <span :class="remainingClass">{{ remainingDaysLabel }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="最后开票日">{{ customer.lastInvoiceDate || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else :description="customerHint" :image-size="60" />
      </div>

      <div class="actions">
        <el-button
          v-if="hasReminder && statusText === '待处理'"
          type="primary"
          :loading="processing"
          @click="markProcessed"
        >标记已处理</el-button>
        <el-button @click="goBack">返回列表</el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCustomer } from '@/api/customer'
import { getReminderDetail, processReminder } from '@/api/reminder'
import { useUserStore } from '@/store/user'
import { customerStatusTagType } from '@/utils/customer-status'
import {
  NO_REMINDER_ID,
  customerStatusLabel,
  daysUntil,
  normalizeCustomerBrief,
  reminderStatusTagType,
  reminderStatusText,
  reminderTimingText,
  reminderTypeLabel
} from '@/utils/reminder-detail'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const reminder = ref({})
const fallbackCustomer = ref(null)
const loading = ref(true)
const processing = ref(false)
const loadError = ref('')
const customerHint = ref('暂无客户信息')

const routeId = computed(() => String(route.params.id || ''))
const hasReminder = computed(() => routeId.value !== NO_REMINDER_ID && /^\d+$/.test(routeId.value))
const customerId = computed(() => {
  const fromQuery = route.query.customerId
  if (fromQuery) return String(fromQuery)
  const fromDetail = reminder.value?.customerId
  return fromDetail == null ? '' : String(fromDetail)
})
const typeLabel = computed(() => reminderTypeLabel(reminder.value?.reminderType || route.query.type))
const statusText = computed(() => reminderStatusText(reminder.value?.status))
const statusTagType = computed(() => reminderStatusTagType(reminder.value?.status))
const customer = computed(() => normalizeCustomerBrief(reminder.value?.customer || fallbackCustomer.value))
const customerTagType = computed(() => customerStatusTagType(customer.value?.status))
const timingText = computed(() => reminderTimingText(reminder.value, customer.value))
const remainingDays = computed(() => daysUntil(customer.value?.expireDate))
const remainingDaysLabel = computed(() => {
  const days = remainingDays.value
  if (days === null) return '—'
  return days < 0 ? `已过期 ${Math.abs(days)} 天` : `${days} 天`
})
const remainingClass = computed(() => {
  const days = remainingDays.value
  if (days === null) return ''
  if (days < 0 || days <= 7) return 'days-danger'
  if (days <= 30) return 'days-warning'
  return 'days-normal'
})

function formatDateTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    if (hasReminder.value) {
      const res = await getReminderDetail(routeId.value)
      reminder.value = res.data || {}
    } else {
      reminder.value = { reminderType: route.query.type || 'expiring' }
    }
    // 无提醒实体时（或详情未带客户块）用客户接口补齐客户信息，权限不足则降级提示，不阻断页面
    if (!reminder.value.customer && customerId.value) {
      try {
        const res = await getCustomer(customerId.value)
        fallbackCustomer.value = res.data || null
      } catch (e) {
        customerHint.value = '无客户查看权限，仅显示提醒信息'
      }
    }
    if (!reminder.value.customer && !fallbackCustomer.value && customerHint.value === '暂无客户信息') {
      customerHint.value = '未关联到客户信息'
    }
  } catch (e) {
    loadError.value = e?.response?.data?.message || '提醒详情加载失败（可能已被清理或无权限查看）'
    reminder.value = {}
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push({ name: 'Reminder' })
}

function goCustomer() {
  router.push({ name: 'CustomerDetail', params: { id: customerId.value } })
}

async function markProcessed() {
  processing.value = true
  try {
    await processReminder(routeId.value)
    ElMessage.success('已标记处理')
    userStore.refreshBadges()
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '标记失败')
  } finally {
    processing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.reminder-detail { padding-bottom: 16px; }
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #333; }
.mb12 { margin-bottom: 12px; }
.card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 12px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
}
.title-action { margin-left: auto; }
.actions { display: flex; gap: 8px; }
.days-danger { color: #f56c6c; font-weight: 600; }
.days-warning { color: #e6a23c; font-weight: 600; }
.days-normal { color: #67c23a; font-weight: 600; }
</style>
