<template>
  <div class="log-panel">
    <div class="log-write" v-if="!readonly">
      <el-input v-model="newLog" type="textarea" :rows="3" placeholder="输入跟进记录..." resize="vertical" />
      <!-- 【PR-10】跟进日志生成任务:明确承诺形成待办(总册口径) -->
      <div class="task-gen" v-if="newLog.trim()">
        <el-checkbox v-model="generateTask" size="small">生成下一步任务</el-checkbox>
        <template v-if="generateTask">
          <el-input v-model="taskTitle" placeholder="任务标题(如:提交选型方案)" size="small" style="width:220px" />
          <el-date-picker v-model="taskDueDate" type="date" placeholder="截止时间" size="small"
            value-format="YYYY-MM-DD" :disabled-date="(d) => d < new Date()" style="width:150px" />
        </template>
      </div>
      <div class="log-submit">
        <el-button type="primary" size="small" @click="handleSubmit" :loading="submitting">
          <el-icon><Promotion /></el-icon> 记录
        </el-button>
      </div>
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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { injectCustomerDetail } from '@/composables/useCustomerDetail'
import { createTask } from '@/api/task'
import { addCustomerLog, getCustomerLogs } from '@/api/customer'

const { readonly, newLog, logs, submitLog, customerId } = injectCustomerDetail()

// 【PR-10】任务生成状态
const generateTask = ref(false)
const taskTitle = ref('')
const taskDueDate = ref('')
const submitting = ref(false)

const handleSubmit = async () => {
  if (!newLog.value.trim() || submitting.value) return
  if (generateTask.value && !taskTitle.value.trim()) {
    ElMessage.warning('请填写任务标题')
    return
  }
  submitting.value = true
  try {
    // 先记录跟进日志(submitLog 返回是否成功:失败时其内部已弹错误提示,此处不建任务、不弹成功、保留表单内容供重试)
    const logSaved = await submitLog()
    if (!logSaved) return

    // 如果勾选了生成任务,创建关联任务
    if (generateTask.value && taskTitle.value.trim()) {
      const uid = await import('@/store/user').then(m => m.useUserStore().userId)
      await createTask({
        title: taskTitle.value.trim(),
        customerId: customerId.value,
        dueDate: taskDueDate.value || null,
        assigneeId: uid,
      })
      ElMessage.success('跟进已记录,任务已创建')
    }
    // 重置任务表单
    generateTask.value = false
    taskTitle.value = ''
    taskDueDate.value = ''
  } catch (e) {
    // 错误提示由 request 拦截器处理
  } finally { submitting.value = false }
}
</script>

<style scoped>
.log-panel { }
.log-write { margin-bottom:16px }
.log-submit { display:flex; justify-content:flex-end; margin-top:8px }
.task-gen { display:flex; gap:8px; align-items:center; margin-top:8px; flex-wrap:wrap }
.timeline { padding-left:8px }
.timeline-item { position:relative; padding:0 0 18px 24px }
.tl-dot { position:absolute; left:-2px; top:8px; width:10px; height:10px; border-radius:50%; background:var(--el-color-primary); border:2px solid #fff; box-shadow:0 0 0 2px var(--el-color-primary) }
.timeline-item::before { content:''; position:absolute; left:2px; top:18px; bottom:0; width:2px; background:#e8e8e8 }
.timeline-item:last-child::before { display:none }
.tl-card { background:#fafbfc; border-radius:8px; padding:14px 16px; border:1px solid #f0f1f3 }
.tl-text { font-size:14px; color:#333; line-height:1.6; margin-bottom:6px }
.tl-meta { display:flex; gap:16px; font-size:12px }.tl-user { color:var(--el-color-primary) }.tl-time { color: var(--text-muted) }
.empty-text { color: var(--text-placeholder); text-align:center; padding:24px 0; font-size:13px }
</style>
