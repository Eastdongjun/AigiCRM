<template>
  <el-dialog v-model="storageApplyVisible" title="存储扩容申请" width="480px">
    <!-- 深蓝摘要条：与文件页存储概览/工作台主视觉同一语言 -->
    <div class="apply-summary">
      <div class="summary-item">
        <span class="summary-label">当前配额</span>
        <span class="summary-value">{{ currentQuotaText }}</span>
      </div>
      <el-icon class="summary-arrow"><Right /></el-icon>
      <div class="summary-item">
        <span class="summary-label">可申请上限</span>
        <span class="summary-value">{{ maxQuotaText }}</span>
      </div>
    </div>

    <el-form label-width="110px" class="apply-form" @submit.prevent>
      <el-form-item label="申请后总配额" required>
        <div class="quota-field">
          <div class="quota-input-row">
            <el-input-number
              v-model="form.quotaGb"
              :min="minQuotaGb"
              :max="maxQuotaGb"
              :step="10"
              :precision="0"
              controls-position="right"
              style="width: 180px"
              aria-label="申请后总配额（GB）"
            />
            <span class="quota-unit">GB</span>
          </div>
          <span class="apply-hint">需大于当前配额，且不超过可申请上限</span>
        </div>
      </el-form-item>
      <el-form-item label="申请理由" required>
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="4"
          maxlength="200"
          show-word-limit
          placeholder="请说明扩容用途，如项目资料归档、大文件交付等"
        />
      </el-form-item>
    </el-form>

    <div class="apply-note">提交后将进入审批流程，审批通过后配额自动调整。</div>

    <template #footer>
      <el-button @click="storageApplyVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Right } from '@element-plus/icons-vue'
import { submitApproval } from '@/api/approval'
import { injectFilePage } from '@/composables/useFilePage'
import { bytesToGb, formatStorageQuota, gbToBytes } from '@/utils/storage-apply'

const { storageApplyVisible, storageApplyInfo, loadStorageApplyInfo } = injectFilePage()

const submitting = ref(false)
const form = reactive({ quotaGb: 0, reason: '' })

const currentQuotaGb = computed(() => bytesToGb(storageApplyInfo.value?.quota))
// 上限按整数 GB 输入，向下取整确保申请值不会越过 maxQuota(bytes)
const maxQuotaGb = computed(() => Math.floor(bytesToGb(storageApplyInfo.value?.maxQuota)))
const minQuotaGb = computed(() => Math.max(1, Math.floor(currentQuotaGb.value) + 1))
const currentQuotaText = computed(() => formatStorageQuota(storageApplyInfo.value?.quota))
const maxQuotaText = computed(() => formatStorageQuota(storageApplyInfo.value?.maxQuota))

const resetForm = () => {
  const current = currentQuotaGb.value
  const max = maxQuotaGb.value
  const suggested = Math.max(minQuotaGb.value, Math.min(max || minQuotaGb.value, Math.floor(current) + 10))
  form.quotaGb = suggested
  form.reason = ''
}

watch(storageApplyVisible, (visible) => {
  if (visible) resetForm()
})

const handleSubmit = async () => {
  const targetGb = Number(form.quotaGb)
  if (!Number.isInteger(targetGb) || targetGb <= currentQuotaGb.value) {
    ElMessage.warning(`申请后总配额需大于当前配额（${currentQuotaText.value}）`)
    return
  }
  if (targetGb > maxQuotaGb.value) {
    ElMessage.warning(`申请后总配额不能超过上限（${maxQuotaText.value}）`)
    return
  }
  const reason = form.reason.trim()
  if (!reason) {
    ElMessage.warning('请填写申请理由')
    return
  }
  if (reason.length > 200) {
    ElMessage.warning('申请理由不能超过 200 字')
    return
  }
  submitting.value = true
  try {
    await submitApproval({
      type: 'storage',
      extraData: JSON.stringify({ quota: gbToBytes(targetGb), reason })
    })
    ElMessage.success('已提交审批')
    storageApplyVisible.value = false
    await loadStorageApplyInfo()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理（含“已有在途申请”等后端校验）
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* 深蓝摘要条：白字对比度 ≥ .92，与存储概览条同口径 */
.apply-summary {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px 18px;
  margin-bottom: 20px;
  background: var(--hero-gradient);
  border-radius: 10px;
  box-shadow: var(--hero-shadow);
}
.summary-item { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.summary-label { font-size: 12px; color: rgba(255, 255, 255, .92); }
.summary-value { font-size: 20px; font-weight: 700; color: #fff; line-height: 1.2; letter-spacing: .2px; }
.summary-arrow { color: rgba(255, 255, 255, .92); font-size: 16px; flex-shrink: 0; }
.apply-form { padding-right: 4px; }
.quota-field { display: flex; flex-direction: column; gap: 4px; width: 100%; }
.quota-input-row { display: flex; align-items: center; gap: 8px; }
.quota-unit { font-size: 13px; color: var(--el-text-color-regular); }
.apply-hint { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.5; }
.apply-note {
  margin-top: 2px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: #f4f7fb;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
}
</style>
