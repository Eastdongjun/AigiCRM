<template>
  <div class="log-panel">
    <div class="log-write" v-if="!readonly">
      <el-input v-model="newLog" type="textarea" :rows="3" placeholder="输入跟进记录..." resize="vertical" />
      <div class="log-submit"><el-button type="primary" size="small" @click="submitLog"><el-icon><Promotion /></el-icon> 记录</el-button></div>
    </div>
    <div class="timeline" v-if="logs.length">
      <div v-for="log in logs" :key="log.id" class="timeline-item">
        <div class="tl-dot"></div>
        <div class="tl-card">
          <div class="tl-text">{{ log.content }}</div>
          <div class="tl-meta"><span class="tl-user">{{ log.userName }}</span><span class="tl-time">{{ log.createdAt }}</span></div>
        </div>
      </div>
    </div>
    <div v-else class="empty-text">暂无跟进记录</div>
  </div>
</template>

<script setup>
import { injectCustomerDetail } from '@/composables/useCustomerDetail'

const { readonly, newLog, logs, submitLog } = injectCustomerDetail()
</script>

<style scoped>
.log-panel { }
.log-write { margin-bottom:16px }
.log-submit { display:flex; justify-content:flex-end; margin-top:8px }
.timeline { padding-left:8px }
.timeline-item { position:relative; padding:0 0 18px 24px }
.tl-dot { position:absolute; left:-2px; top:8px; width:10px; height:10px; border-radius:50%; background:#409eff; border:2px solid #fff; box-shadow:0 0 0 2px #409eff }
.timeline-item::before { content:''; position:absolute; left:2px; top:18px; bottom:0; width:2px; background:#e8e8e8 }
.timeline-item:last-child::before { display:none }
.tl-card { background:#fafbfc; border-radius:8px; padding:14px 16px; border:1px solid #f0f1f3 }
.tl-text { font-size:14px; color:#333; line-height:1.6; margin-bottom:6px }
.tl-meta { display:flex; gap:16px; font-size:12px }.tl-user { color:#409eff }.tl-time { color:#999 }
.empty-text { color:#c0c4cc; text-align:center; padding:24px 0; font-size:13px }
</style>
