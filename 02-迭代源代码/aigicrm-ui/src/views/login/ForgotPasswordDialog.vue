<template>
  <el-dialog v-model="visible" title="" width="480px" :close-on-click-modal="false"
             class="forgot-pwd-dialog" @closed="handleClosed" :show-close="true" append-to-body>
    <template #header>
      <div class="dialog-header">
        <h3 class="dialog-title">找回密码</h3>
        <div class="steps">
          <div class="step" :class="{ active: step >= 1, current: step === 1 }">
            <span class="step-num">1</span>
            <span class="step-label">验证账号</span>
          </div>
          <div class="step-line" :class="{ done: step >= 2 }"></div>
          <div class="step" :class="{ active: step >= 2, current: step === 2 }">
            <span class="step-num">2</span>
            <span class="step-label">验证身份</span>
          </div>
          <div class="step-line" :class="{ done: step >= 3 }"></div>
          <div class="step" :class="{ active: step >= 3, current: step === 3 }">
            <span class="step-num">3</span>
            <span class="step-label">重置密码</span>
          </div>
        </div>
      </div>
    </template>

    <!-- 步骤1: 输入账号 -->
    <div v-show="step === 1" class="step-body">
      <div class="step-icon-wrap">
        <el-icon :size="40" color="#1990ff"><User /></el-icon>
      </div>
      <p class="step-desc">请输入您的登录账号，查找可用的验证方式</p>
      <el-form ref="step1Ref" :model="form" :rules="step1Rules">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" prefix-icon="User" size="large"
                    @keyup.enter="handleNext" />
        </el-form-item>
      </el-form>
    </div>

    <!-- 步骤2: 选择验证方式 + 输入验证码 -->
    <div v-show="step === 2" class="step-body">
      <p class="step-desc">选择验证方式，获取并输入验证码</p>
      <div class="method-cards">
        <div v-if="lookup.hasPhone" class="method-card" :class="{ selected: form.method === 'phone' }"
             @click="form.method = 'phone'">
          <div class="method-icon phone-icon">
            <el-icon :size="22"><Iphone /></el-icon>
          </div>
          <div class="method-info">
            <span class="method-title">手机验证</span>
            <span class="method-detail">{{ lookup.maskedPhone }}</span>
          </div>
          <div v-if="form.method === 'phone'" class="method-check">
            <el-icon :size="18"><Check /></el-icon>
          </div>
        </div>
      </div>
      <div class="code-row">
        <el-input v-model="form.code" placeholder="请输入6位验证码" size="large" maxlength="6"
                  @keyup.enter="handleNext" />
        <el-button :type="countdown > 0 ? 'info' : 'primary'" :disabled="countdown > 0 || !form.method"
                   :loading="sendingCode" @click="handleSendCode" size="large" class="send-btn">
          {{ countdown > 0 ? countdown + 's' : '获取验证码' }}
        </el-button>
      </div>
    </div>

    <!-- 步骤3: 设置新密码 -->
    <div v-show="step === 3" class="step-body">
      <div class="step-icon-wrap">
        <el-icon :size="40" color="#1990ff"><Lock /></el-icon>
      </div>
      <p class="step-desc">请设置您的新密码（8-30位）</p>
      <el-form ref="step3Ref" :model="form" :rules="step3Rules">
        <el-form-item prop="newPassword">
          <el-input v-model="form.newPassword" type="password" placeholder="请输入新密码"
                    prefix-icon="Lock" show-password size="large" />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请确认新密码"
                    prefix-icon="Lock" show-password size="large" @keyup.enter="handleNext" />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="step > 1" @click="handleBack">上一步</el-button>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleNext" class="next-btn">
          {{ step === 3 ? '确认重置' : '下一步' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Iphone, Check, User, Lock } from '@element-plus/icons-vue'
import { forgotPasswordLookup, forgotPasswordSendCode, forgotPasswordReset } from '@/api/auth'
// 【B1 安全修复 2026-10-21】重置成功后清除"记住账号/口令"遗留键(口令已变更，旧口令键不可留)
import { clearRemembered } from './remembered-auth'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: val => emit('update:modelValue', val)
})

const step = ref(1)
const step1Ref = ref(null)
const step3Ref = ref(null)
const submitting = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let timer = null

const form = reactive({
  username: '', method: '', code: '',
  newPassword: '', confirmPassword: ''
})

const lookup = reactive({
  maskedPhone: '', maskedEmail: '',
  hasPhone: false, hasEmail: false
})

const step1Rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }]
}
const confirmValidator = (_rule, value, callback) => {
  if (value !== form.newPassword) callback(new Error('两次密码不一致'))
  else callback()
}
const step3Rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    // 【D19 修复】密码强度统一 ≥8 位（对齐首登强制改密口径，login/index.vue 同为 ≥8）
    // 后端 ForgotPasswordResetRequest 为 @Size(min=6)（较松），前后端口径不一致时以严格者(8)为准
    { min: 8, max: 30, message: '密码长度8-30位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: confirmValidator, trigger: 'blur' }
  ]
}

function handleClosed() {
  step.value = 1
  Object.assign(form, { username: '', method: '', code: '', newPassword: '', confirmPassword: '' })
  Object.assign(lookup, { maskedPhone: '', maskedEmail: '', hasPhone: false, hasEmail: false })
  countdown.value = 0
  clearInterval(timer)
}

async function handleNext() {
  if (step.value === 1) {
    const valid = await step1Ref.value.validate().catch(() => false)
    if (!valid) return
    submitting.value = true
    try {
      const res = await forgotPasswordLookup({ username: form.username })
      Object.assign(lookup, res.data)
      if (!lookup.hasPhone) {
        ElMessage.warning('该账号未绑定手机号，请联系管理员')
        return
      }
      form.method = 'phone'
      step.value = 2
    } catch (_e) { /* interceptor handles */ }
    finally { submitting.value = false }
  } else if (step.value === 2) {
    if (!form.method) { ElMessage.warning('请选择验证方式'); return }
    if (!form.code || form.code.length !== 6) { ElMessage.warning('请输入6位验证码'); return }
    step.value = 3
  } else if (step.value === 3) {
    const valid = await step3Ref.value.validate().catch(() => false)
    if (!valid) return
    submitting.value = true
    try {
      await forgotPasswordReset({
        username: form.username,
        code: form.code.trim(),
        newPassword: form.newPassword
      })
      ElMessage.success('密码重置成功，请重新登录')
      // 【B1 安全修复 2026-10-21】重置成功后清除"记住账号/口令"键：
      // 口令已作废不可留，账号键一并清除(重置流程可能在他人/他机操作，保守全清)
      clearRemembered()
      visible.value = false
    } catch (_e) { /* interceptor handles */ }
    finally { submitting.value = false }
  }
}

function handleBack() {
  step.value = step.value - 1
}

async function handleSendCode() {
  if (!form.method) { ElMessage.warning('请选择验证方式'); return }
  sendingCode.value = true
  try {
    const res = await forgotPasswordSendCode({ username: form.username, method: form.method })
    ElMessage.success('验证码已发送到您的手机')
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (_e) { /* interceptor handles */ }
  finally { sendingCode.value = false }
}

onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
/* ── 对话框整体 ── */
:deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
}
:deep(.el-dialog__header) {
  padding: 24px 24px 0;
  margin-right: 0;
}
:deep(.el-dialog__body) {
  padding: 12px 32px 20px;
}
:deep(.el-dialog__footer) {
  padding: 12px 32px 24px;
}

/* ── 头部 ── */
.dialog-header {
  text-align: center;
}
.dialog-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a2a4a;
  margin: 0 0 24px;
  letter-spacing: 1px;
}

/* ── 步骤条 ── */
.steps {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  margin-bottom: 4px;
}
.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 70px;
}
.step-num {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  background: #edf0f5;
  color: #a0aec0;
  transition: all 0.3s;
}
.step.active .step-num {
  background: linear-gradient(135deg, #06539c, #1990ff);
  color: #fff;
  box-shadow: 0 2px 10px rgba(25, 144, 255, 0.3);
}
.step.current .step-num {
  box-shadow: 0 2px 12px rgba(25, 144, 255, 0.45);
  transform: scale(1.05);
}
.step-label {
  font-size: 12px;
  color: #a0aec0;
  white-space: nowrap;
}
.step.active .step-label {
  color: #1a2a4a;
  font-weight: 500;
}
.step-line {
  width: 60px;
  height: 2px;
  background: #edf0f5;
  margin: 0 4px;
  margin-bottom: 22px;
  border-radius: 1px;
  transition: background 0.3s;
}
.step-line.done {
  background: linear-gradient(90deg, #1990ff, #47a5ff);
}

/* ── 步骤内容 ── */
.step-body {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.step-icon-wrap {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(25,144,255,0.08), rgba(71,165,255,0.15));
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}
.step-desc {
  font-size: 14px;
  color: #606266;
  margin-bottom: 20px;
  text-align: center;
}
.step-body :deep(.el-form) {
  width: 100%;
}

/* ── 验证方式卡片 ── */
.method-cards {
  display: flex;
  gap: 12px;
  width: 100%;
  margin-bottom: 20px;
}
.method-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 2px solid #e8ecf1;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.25s;
  position: relative;
}
.method-card:hover {
  border-color: #b3d8ff;
  background: #f8fbff;
}
.method-card.selected {
  border-color: #1990ff;
  background: linear-gradient(135deg, #f0f7ff, #e8f4ff);
}
.method-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.phone-icon {
  background: linear-gradient(135deg, #e6f4ff, #d0ebff);
  color: #1990ff;
}
.method-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.method-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a2a4a;
}
.method-detail {
  font-size: 13px;
  color: #8a9bb5;
  margin-top: 1px;
}
.method-check {
  position: absolute;
  right: 10px;
  top: 10px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #1990ff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── 验证码输入行 ── */
.code-row {
  display: flex;
  gap: 12px;
  width: 100%;
}
.code-row .el-input {
  flex: 1;
}
.send-btn {
  min-width: 120px;
  white-space: nowrap;
  font-weight: 500;
}

/* ── 底部按钮 ── */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.next-btn {
  min-width: 100px;
  font-weight: 500;
}
</style>
