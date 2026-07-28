<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-left">
        <div class="brand">
          <img v-if="systemLogo" :src="systemLogo" class="brand-logo-img" />
          <span v-else class="brand-icon">{{ systemName?.charAt(0) || '运' }}</span>
        </div>
        <h1 class="system-title">{{ systemName }}</h1>
        <p class="system-subtitle">市场资源管理系统</p>
        <ul class="features">
          <li>客户管理</li>
          <li>阶段推进</li>
          <li>数据洞察</li>
        </ul>
      </div>
      <div class="login-right">
        <div class="login-form-wrapper">
          <p class="welcome">WELCOME BACK</p>
          <h2 class="form-title">用户<span class="highlight">登录</span></h2>

          <el-form ref="formRef" :model="form" :rules="rules" size="large">
            <el-form-item prop="username">
              <label class="form-label">账号</label>
              <el-input v-model="form.username" placeholder="请输入账号" prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <label class="form-label">密码</label>
              <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
            </el-form-item>
            <div class="form-options">
              <el-checkbox v-model="form.rememberMe">记住我</el-checkbox>
              <a class="forgot-link" href="javascript:void(0)" @click="forgotPwdVisible = true">忘记密码?</a>
            </div>
            <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">登 录</el-button>
          </el-form>
        </div>
      </div>
    </div>
    <ForgotPasswordDialog v-model="forgotPwdVisible" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { getPublicConfig } from '@/api/system'
import { ElMessage } from 'element-plus'
import ForgotPasswordDialog from './ForgotPasswordDialog.vue'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const forgotPwdVisible = ref(false)
const systemName = ref('运筹马年')
const systemLogo = ref('')

const form = reactive({
  username: '',
  password: '',
  rememberMe: true
})

onMounted(async () => {
  const saved = localStorage.getItem('ycmn_remember_username')
  if (saved) {
    form.username = saved
    form.rememberMe = true
  }
  try {
    const res = await getPublicConfig()
    const cfg = res.data || {}
    if (cfg['system.name']) systemName.value = cfg['system.name']
    if (cfg['system.logo']) systemLogo.value = '/api/system/configs/logo?t=' + Date.now()
  } catch {}
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login(form)
    localStorage.setItem('token', res.data.token)
    if (form.rememberMe) {
      localStorage.setItem('ycmn_remember_username', form.username)
    } else {
      localStorage.removeItem('ycmn_remember_username')
    }
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #022d57 0%, #02438a 40%, #04549c 100%);
}
.login-box {
  display: flex;
  width: 960px;
  min-height: 580px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 20, 40, 0.45);
}
.login-left {
  flex: 0 0 42%;
  background: linear-gradient(135deg, #022d57 0%, #033e7a 60%, #024a94 100%);
  padding: 48px 40px 40px 48px;
  display: flex;
  flex-direction: column;
  color: #fff;
  position: relative;
}
.login-left::after {
  content: '';
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 2px;
  background: linear-gradient(180deg, #1990ff, #47a5ff, #1990ff);
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 48px;
}
.brand-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #1990ff, #47a5ff);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: bold;
  box-shadow: 0 4px 12px rgba(25, 144, 255, 0.35);
}
.brand-logo-img {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: contain;
}
.brand-text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
}
.system-title {
  font-size: 38px;
  font-weight: 700;
  margin-bottom: 8px;
  letter-spacing: 4px;
}
.system-title :deep(.highlight) {
  background: linear-gradient(90deg, #47a5ff, #79bcff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.system-subtitle {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 2px;
  margin-bottom: auto;
}
.features {
  list-style: none;
  padding: 0;
}
.features li {
  padding: 6px 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.65);
  letter-spacing: 0.5px;
}
.features li::before {
  content: '';
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #47a5ff;
  margin-right: 10px;
  vertical-align: middle;
}
.login-right {
  flex: 0 0 58%;
  background: #fff;
  padding: 52px 56px 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
}
.login-right::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: linear-gradient(180deg, #1990ff, #47a5ff, #1990ff);
}
.welcome {
  color: #8a9bb5;
  font-size: 13px;
  letter-spacing: 1px;
  margin-bottom: 4px;
  text-transform: uppercase;
}
.form-title {
  font-size: 24px;
  font-weight: 700;
  color: #1a2a4a;
  letter-spacing: 1px;
  margin-bottom: 36px;
}
.form-title .highlight {
  color: #1990ff;
}
.form-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #1a2a4a;
  margin-bottom: 8px;
  letter-spacing: 0.5px;
}
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
}
.forgot-link {
  color: #1990ff;
  font-size: 13px;
  text-decoration: none;
}
.forgot-link:hover {
  opacity: 0.8;
  text-decoration: underline;
}
.login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  letter-spacing: 6px;
  border-radius: 6px;
  background: linear-gradient(135deg, #06539c 0%, #0d67ba 100%) !important;
  border: none !important;
  box-shadow: 0 4px 14px rgba(6, 83, 156, 0.35);
}
.login-btn:hover {
  background: linear-gradient(135deg, #04467f 0%, #0a59a5 100%) !important;
  box-shadow: 0 6px 20px rgba(6, 83, 156, 0.45);
}
</style>
