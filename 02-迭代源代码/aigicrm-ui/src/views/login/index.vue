<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-left">
        <div class="brand">
          <img :src="systemLogo || '/favicon.png'" class="brand-logo-img" />
        </div>
        <h1 class="system-title">{{ systemName }}</h1>
        <p class="system-subtitle">{{ systemSubtitle }}</p>
        <ul class="features">
          <li>客户 360° 经营台账</li>
          <li>公海流转与到期看护</li>
          <li>四项服务 · 数据洞察</li>
        </ul>
      </div>
      <div class="login-right">
        <div class="login-form-wrapper">
          <p class="welcome">WELCOME BACK</p>
          <h2 class="form-title">用户<span class="highlight">登录</span></h2>

          <el-form ref="formRef" :model="form" :rules="rules" size="large">
            <el-form-item prop="username">
              <label class="form-label">账号</label>
              <el-input ref="userInputRef" v-model="form.username" placeholder="请输入账号" prefix-icon="User" @keyup.enter="focusPassword" />
            </el-form-item>
            <el-form-item prop="password">
              <label class="form-label">密码</label>
              <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
            </el-form-item>
            <div class="form-options">
              <div class="remember-group">
                <el-checkbox v-model="form.rememberUser">记住账号</el-checkbox>
                <!-- 【2026-11-05 用户令"恢复记住密码"】按 10-21 审计自己的建议恢复: WebCrypto 设备密钥
                     加密(HTTP 档降级弱混淆)+7 天有效期, 勾选即 7 天档令牌; 勾选自动联动"记住账号" -->
                <el-checkbox v-model="form.rememberPassword" @change="onRememberPwdChange">记住密码</el-checkbox>
                <!-- 【2026-11-05 用户选 C"保持登录"】不落盘口令的等价体验: 勾选=签发 7 天档令牌+活跃滑动续期, 掉线窗口=7 天不活跃 -->
                <el-checkbox v-model="form.rememberLogin">7 天内保持登录</el-checkbox>
              </div>
              <a class="forgot-link" href="javascript:void(0)" @click="forgotPwdVisible = true">忘记密码?</a>
            </div>
            <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">登 录</el-button>
          </el-form>
        </div>
      </div>
    </div>
    <!-- ICP 备案(工信部要求: 底部展示并可跳转备案系统) -->
    <footer class="icp-footer">
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">陕ICP备2024024412号</a>
    </footer>
    <ForgotPasswordDialog v-model="forgotPwdVisible" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { getPublicConfig } from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'
import ForgotPasswordDialog from './ForgotPasswordDialog.vue'
import { REMEMBER_KEY_USER, clearRememberedPassword, clearRememberedAuth, loadRememberedAuth, saveRememberedAuth } from './remembered-auth'

const router = useRouter()
const formRef = ref(null)
const userInputRef = ref(null)
const loading = ref(false)
const forgotPwdVisible = ref(false)
// 默认品牌与页内展示一致（配置加载后由后端 system.name 覆写）
const systemName = ref('AIGICRM')
const systemSubtitle = ref('市场资源管理系统')
const systemLogo = ref('')

const form = reactive({
  username: '',
  password: '',
  // 【2026-10-17 用户口径】"记住账号"默认勾选（隐私保守默认）。
  // 【B1 安全修复 2026-10-21】"记住密码"勾选项下线：口令不再以任何形式写入 localStorage，
  // 原 base64 可逆落盘属 high 级安全问题（退出/改密/重置均不清除），安全优先移除。
  rememberUser: true,
  rememberLogin: true,
  rememberPassword: false
})

// 【B1 安全修复 2026-10-21】口令持久化整体移除，只保留"记住账号"。
// 旧版本遗留在浏览器里的 aigicrm_remember_password 由 clearRememberedPassword()
// 在登录页挂载、登录成功（含强制改密两条分支）时统一清除（见 remembered-auth.js）。
/** 只落盘/清除"记住账号"键（口令记忆由 save/clearRememberedAuth 异步承载）*/
function persistRemembered() {
  try {
    if (form.rememberUser) localStorage.setItem(REMEMBER_KEY_USER, form.username)
    else localStorage.removeItem(REMEMBER_KEY_USER)
    clearRememberedPassword()
  } catch { /* 存储不可用(隐私模式等)时静默 */ }
}

// 【2026-11-05 恢复记住密码】级联(镜像 2026-10-08 旧行为): 勾密码自动勾账号+保持登录;
// 取消密码 → 清加密记忆(下次不再回填)
async function onRememberPwdChange(v) {
  if (v) {
    form.rememberUser = true
    form.rememberLogin = true
  } else {
    clearRememberedAuth()
    form.password = ''
  }
}

onMounted(async () => {
  // 【B1 安全修复 2026-10-21】只回填"记住账号"；旧版本遗留的 base64 口令键清除且不回填
  // （覆盖管理员重置等场景：用户下次打开登录页时，浏览器里过期的口令残留被清掉）
  const saved = localStorage.getItem(REMEMBER_KEY_USER)
  if (saved) {
    form.username = saved
    form.rememberUser = true
  }
  // 【2026-11-05 用户令"恢复记住密码"】读取加密记忆(WebCrypto 设备密钥档/xor 降级档), 7 天过期
  const remembered = await loadRememberedAuth()
  if (remembered) {
    form.username = remembered.username
    form.password = remembered.password
    form.rememberUser = true
    form.rememberPassword = true
    form.rememberLogin = true
  }
  clearRememberedPassword()
  try {
    const res = await getPublicConfig()
    const cfg = res.data || {}
    if (cfg['system.name']) systemName.value = cfg['system.name']
    if (cfg['system.subtitle']) systemSubtitle.value = cfg['system.subtitle']
    if (cfg['system.logo']) systemLogo.value = '/api/system/configs/logo?t=' + Date.now()
  } catch {}
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const focusPassword = () => {
  const pwdInput = document.querySelector('input[type="password"]')
  if (pwdInput) pwdInput.focus()
}

const handleLogin = async () => {
  if (loading.value) return
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) { ElMessage.warning('请填写完整信息'); return }

  loading.value = true
  try {
    form.username = form.username.trim()
    // 【2026-11-05 保持登录/恢复记住密码】记住密码或保持登录任一勾选 → 7 天档 JWT(滑动续期)
    const rememberMe = form.rememberLogin || form.rememberPassword
    const res = await login({ ...form, rememberMe })
    localStorage.setItem('token', res.data.token)
    // 【批④ 2026-08-10 多集团会话】登录响应带回集团信息(多集团用户顶部可切换)
    const { useUserStore } = await import('@/store/user')
    useUserStore().applyGroupInfo(res.data.groupIds, res.data.currentGroupId)
    // 【2026-08-14 安全 P0】首次登录强制改密：初始/重置口令为随机值，后端标记 pwdResetRequired=1
    // 改密后当前 token 即失效（DB 级 pwd_changed_at 撤销），须用新密码重新登录
    if (res.data.pwdResetRequired === 1) {
      try {
        const { value } = await ElMessageBox.prompt('首次登录必须修改初始密码', '修改密码', {
          inputType: 'password',
          inputPlaceholder: '输入新密码（至少 8 位）',
          inputValidator: (v) => (v && v.length >= 8) || '密码至少 8 位',
          confirmButtonText: '确认修改',
          cancelButtonText: '退出登录'
        })
        const { changePassword } = await import('@/api/system')
        await changePassword({ oldPassword: form.password, newPassword: value })
        // 【B1 安全修复 2026-10-21】强制改密成功后不再把"新口令"写入 localStorage
        // （原 persistRemembered(value) 属 high 级问题：新口令重新落盘）；只保留"记住账号"
        // 【2026-11-05】旧口令的加密记忆一并清除(改密后旧记忆无效)
        clearRememberedAuth()
        persistRemembered()
        localStorage.removeItem('token')
        ElMessage.success('密码已修改，请用新密码重新登录')
        return
      } catch (e) {
        // 【B1 安全修复 2026-10-21】取消强制改密分支不再把"旧口令"写回 localStorage
        persistRemembered()
        localStorage.removeItem('token')
        ElMessage.warning('未完成密码修改，请重新登录')
        return
      }
    }
    persistRemembered()
    // 【2026-11-05 恢复记住密码】按勾选保存(设备密钥加密)或清除口令记忆
    if (form.rememberPassword) await saveRememberedAuth(form.username, form.password)
    else await clearRememberedAuth()
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // 显示后端返回的具体错误（如"用户名或密码错误"）
    const msg = e?.response?.data?.message || e?.message
    if (msg && msg !== 'Error') ElMessage.error(msg)
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
  background: linear-gradient(180deg, #0d1733 0%, #13255e 40%, #1d4ed8 100%);
  position: relative;
}
.icp-footer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 14px;
  text-align: center;
}
/* 【对比度 2026-10-17】底部深蓝色(#1d4ed8)：白字 .55 仅 3.10:1 → .92 为 5.93:1，达 AA */
.icp-footer a {
  font-size: 12px;
  color: rgba(255, 255, 255, .92);
  letter-spacing: .5px;
}
.icp-footer a:hover { color: #fff }
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
  background: linear-gradient(135deg, #0d1733 0%, #16337a 60%, #1d4ed8 100%);
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
  background: linear-gradient(180deg, #2563eb, #6083e4, #2563eb);
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 48px;
}
.brand-logo-img {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: contain;
}
.system-title {
  font-size: 38px;
  font-weight: 700;
  margin-bottom: 8px;
  letter-spacing: 4px;
}
/* 【对比度 2026-10-17】登录左栏深蓝底：.7 → .92，最亮端 #1d4ed8 上 5.93:1，达 AA */
.system-subtitle {
  font-size: 15px;
  color: rgba(255, 255, 255, .92);
  letter-spacing: 2px;
  margin-bottom: auto;
}
.features {
  list-style: none;
  padding: 0;
}
/* 【对比度 2026-10-17】深蓝底 .72 → .92：最亮端 #1d4ed8 上 5.93:1，达 AA */
.features li {
  padding: 7px 0;
  font-size: 14px;
  color: rgba(255, 255, 255, .92);
  letter-spacing: 0.5px;
}
.features li::before {
  content: '✓';
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(96, 131, 228, .35);
  font-size: 12px;
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
  background: linear-gradient(180deg, #2563eb, #6083e4, #2563eb);
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
  color: #2563eb;
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
.remember-group { display: flex; gap: 18px; align-items: center; }
.forgot-link {
  color: #2563eb;
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
  border-radius: var(--radius-sm);
  background: var(--hero-gradient) !important;
  border: none !important;
  box-shadow: 0 4px 14px rgba(29, 78, 216, 0.35);
}
.login-btn:hover {
  background: linear-gradient(135deg, #173ead 0%, #1d4ed8 100%) !important;
  box-shadow: 0 6px 20px rgba(29, 78, 216, 0.45);
}
</style>
