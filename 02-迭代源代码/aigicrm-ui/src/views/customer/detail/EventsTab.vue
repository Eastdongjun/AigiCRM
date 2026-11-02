<template>
  <div class="events-panel">
    <div class="events-legend">
      客户关键日期事件全记录：建档 / 开票推进 / 人工维护开票日 / 延期 / 释放进公海 / 捞取 / 转移，按时间倒序（最新在上）。
    </div>
    <div class="timeline" v-if="events.length">
      <div v-for="ev in events" :key="ev.id" class="timeline-item">
        <div class="tl-dot" :class="`tl-${ev.eventType}`"></div>
        <div class="tl-card">
          <div class="tl-head">
            <span class="tl-type" :class="`tag-${ev.eventType}`">{{ typeLabel(ev.eventType) }}</span>
            <span class="tl-date">{{ ev.eventDate }}</span>
          </div>
          <div class="tl-text">{{ ev.detail }}</div>
          <div class="tl-changes" v-if="ev.oldValue || ev.newValue">
            <span v-if="ev.oldValue">变更前：{{ ev.oldValue }}</span>
            <span v-if="ev.newValue">变更后：{{ ev.newValue }}</span>
          </div>
          <div class="tl-meta">
            <span class="tl-user">{{ ev.createdByName || '系统' }}</span>
            <span class="tl-time">{{ ev.createdAt }}</span>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty-text">暂无生命线事件</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCustomerEvents } from '@/api/customer'
import { injectCustomerDetail } from '@/composables/useCustomerDetail'

const { customerId } = injectCustomerDetail()
const events = ref([])

const TYPE_LABELS = {
  created: '建档',
  invoice_adv: '开票推进',
  invoice_manual: '人工维护开票日',
  extend: '延期',
  released: '释放进公海',
  claimed: '捞取',
  transferred: '转移',
}
const typeLabel = (t) => TYPE_LABELS[t] || t

onMounted(async () => {
  try {
    const res = await getCustomerEvents(customerId.value)
    events.value = res.data || []
  } catch (e) {
    // 错误提示由 request 拦截器处理; 生命线加载失败不阻断详情页其余页签
  }
})
</script>

<style scoped>
.events-legend { font-size: 12px; color: var(--text-muted); margin-bottom: 14px; line-height: 1.5 }
.timeline { padding-left: 8px }
.timeline-item { position: relative; padding: 0 0 18px 24px }
.tl-dot { position: absolute; left: -2px; top: 10px; width: 10px; height: 10px; border-radius: 50%; background: var(--el-color-primary); border: 2px solid #fff; box-shadow: 0 0 0 2px var(--el-color-primary) }
/* 语义色: 释放=橙(离场), 捞取=绿(回归), 延期=蓝(延长), 其余=主色 */
.tl-dot.tl-released { background: var(--el-color-warning); box-shadow: 0 0 0 2px var(--el-color-warning) }
.tl-dot.tl-claimed { background: var(--el-color-success); box-shadow: 0 0 0 2px var(--el-color-success) }
.tl-dot.tl-extend { background: #7c9cff; box-shadow: 0 0 0 2px #7c9cff }
.timeline-item::before { content: ''; position: absolute; left: 2px; top: 20px; bottom: 0; width: 2px; background: #e8e8e8 }
.timeline-item:last-child::before { display: none }
.tl-card { background: #fafbfc; border-radius: 8px; padding: 12px 16px; border: 1px solid #f0f1f3 }
.tl-head { display: flex; align-items: center; gap: 10px; margin-bottom: 6px }
.tl-type { font-size: 12px; padding: 1px 8px; border-radius: 10px; background: var(--el-color-primary-light-9); color: var(--el-color-primary) }
.tag-released { background: var(--el-color-warning-light-9); color: var(--el-color-warning) }
.tag-claimed { background: var(--el-color-success-light-9); color: var(--el-color-success) }
.tl-date { font-size: 13px; font-weight: 500; color: var(--text-primary) }
.tl-text { font-size: 13px; color: #333; line-height: 1.6 }
.tl-changes { display: flex; gap: 14px; font-size: 12px; color: var(--text-muted); margin-top: 4px }
.tl-meta { display: flex; gap: 16px; font-size: 12px; margin-top: 6px }
.tl-user { color: var(--el-color-primary) }
.tl-time { color: var(--text-muted) }
.empty-text { color: var(--text-placeholder); text-align: center; padding: 24px 0; font-size: 13px }
</style>
