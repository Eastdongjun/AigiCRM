<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '200px'" class="sidebar">
      <div class="logo">
        <img v-if="userStore.systemLogo" :src="userStore.systemLogo" class="logo-img" />
        <span v-else class="logo-icon">{{ userStore.systemName?.charAt(0) || '运' }}</span>
        <span v-show="!isCollapse" class="logo-text">{{ userStore.systemName }}</span>
      </div>
      <el-scrollbar>
        <!-- 【修复 FEAT-20260804-DYNAMIC-MENU】侧边栏改为按后端 /api/auth/info 下发的 menus 树
             （即"角色管理->菜单权限"里实际勾选的内容）动态渲染，不再是与权限完全脱节的硬编码结构。
             menus 是两层：顶层(menuType=1)是分组标签，子节点(menuType=2)是可点击页面；
             按钮权限点(menuType=3)和隐藏路由(visible=0)后端已经过滤掉，这里不用再处理。 -->
        <el-menu
          :default-active="$route.path"
          :collapse="isCollapse"
          router
          background-color="#001629"
          text-color="#bfcbd9"
          active-text-color="#409eff"
          :collapse-transition="false"
        >
          <template v-for="group in menuGroups" :key="group.id">
            <div class="menu-group-label" v-show="!isCollapse">{{ group.menuName }}</div>
            <el-menu-item v-for="item in group.children" :key="item.id" :index="item.path">
              <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
              <template #title>
                <span class="menu-title-row">
                  <span>{{ item.menuName }}</span>
                  <span class="menu-count" v-if="badgeFor(item.path)">{{ badgeFor(item.path) }}</span>
                </span>
              </template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <!-- 主体 -->
    <el-container>
      <!-- 顶部栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.parentTitle">{{ $route.meta.parentTitle }}</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.title">{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-icon class="header-icon"><Bell /></el-icon>
          <el-icon class="header-icon"><QuestionFilled /></el-icon>
          <el-dropdown @command="handleCommand" trigger="click" class="user-dropdown">
            <div class="user-info">
              <div class="user-avatar-wrap">
                <el-avatar :size="34" class="user-avatar">{{ userStore.realName?.charAt(0) }}</el-avatar>
                <span class="online-dot"></span>
              </div>
              <div class="user-meta">
                <span class="user-name">{{ userStore.realName }}</span>
                <span class="user-dept">{{ userStore.officeName }}<i class="dot-sep">·</i>{{ userStore.roleName }}</span>
              </div>
              <el-icon class="user-caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="user-menu">
                <div class="user-menu-head">
                  <el-avatar :size="40" class="user-avatar-lg">{{ userStore.realName?.charAt(0) }}</el-avatar>
                  <div class="user-menu-meta">
                    <span class="um-name">{{ userStore.realName }}</span>
                    <span class="um-role">{{ userStore.roleName }} · {{ userStore.officeName }}</span>
                  </div>
                </div>
                <el-dropdown-item command="profile"><el-icon class="menu-ico"><User /></el-icon>个人信息</el-dropdown-item>
                <el-dropdown-item command="password"><el-icon class="menu-ico"><Lock /></el-icon>修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout" class="logout-item"><el-icon class="menu-ico"><SwitchButton /></el-icon>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 个人信息弹窗 -->
      <el-dialog v-model="showProfile" title="个人信息" width="420px">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户名">{{ userStore.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ userStore.realName }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ userStore.deptName }}</el-descriptions-item>
          <el-descriptions-item label="办事处">{{ userStore.officeName }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ userStore.roleName }}</el-descriptions-item>
        </el-descriptions>
      </el-dialog>

      <!-- 修改密码弹窗 -->
      <el-dialog v-model="showPassword" title="修改密码" width="400px" :close-on-click-modal="false">
        <el-form :model="pwdForm" label-width="80px">
          <el-form-item label="原密码">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showPassword = false">取消</el-button>
          <el-button type="primary" :loading="pwdLoading" @click="submitPassword">确认修改</el-button>
        </template>
      </el-dialog>

      <!-- 内容区 -->
      <el-main class="main-content">
        <!-- 【修复 20260804 路由切换卡死】原 <transition fade out-in> 包裹 router-view，
             从激励上传页(含 el-dialog/teleport 子树)切出时过渡永不完成 → 新页面组件挂载但 DOM 永远空白
             ("点击菜单没反应")，且任何 duration/超时配置都无法兜底。
             修复：移除 transition 直接渲染（无切换动画，功能优先）。 -->
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { useUserStore } from '@/store/user'
import { changePassword } from '@/api/system'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 【修复 FEAT-20260804-DYNAMIC-MENU】把后端下发的两层 menus 树整理成"分组+可点击页面"，
// 过滤掉没有任何可见子页面的空分组（比如角色没被授权该分组下任何页面时）
const menuGroups = computed(() => {
  return (userStore.menus || [])
    .map(group => ({ ...group, children: (group.children || []).filter(c => c.menuType === 2 && c.path) }))
    .filter(group => group.children.length > 0)
})
// 提醒/审批两个菜单项的角标数量走独立的未读计数接口，不属于菜单树本身的数据
const badgeFor = (path) => {
  if (path === '/reminder') return userStore.badgeReminder > 0 ? userStore.badgeReminder : 0
  if (path === '/approval') return userStore.badgeApproval > 0 ? userStore.badgeApproval : 0
  return 0
}

// 无工作台权限时的兜底落地页(按菜单顺序取首个有权限的页面)
const firstAccessiblePath = () => {
  const candidates = [
    ['customer:view', '/customer'],
    ['file:view', '/file'],
    ['incentive:view', '/incentive'],
    ['reminder:view', '/reminder'],
    ['sea:view', '/sea'],
    ['approval:view', '/approval'],
    ['statistics:view', '/statistics']
  ]
  const hit = candidates.find(([p]) => userStore.hasPermission(p))
  // 学分/指标模块无权限门控,始终可访问,作为最终兜底
  return hit ? hit[1] : '/score/entry'
}
const isCollapse = ref(false)

// 个人信息
const showProfile = ref(false)

// 修改密码
const showPassword = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const submitPassword = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    return ElMessage.warning('请填写完整')
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    return ElMessage.warning('两次输入的新密码不一致')
  }
  if (pwdForm.newPassword.length < 6) {
    return ElMessage.warning('新密码不能少于6位')
  }
  pwdLoading.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    showPassword.value = false
    userStore.logout()
    router.push('/login')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '修改失败，请检查原密码是否正确')
  }
  pwdLoading.value = false
}

// ===== 30分钟无操作自动退出 =====
const INACTIVE_TIMEOUT = 30 * 60 * 1000 // 30分钟
const WARNING_BEFORE = 2 * 60 * 1000   // 提前2分钟提醒
let inactivityTimer = null
let warningTimer = null

const resetInactivityTimer = () => {
  clearTimeout(inactivityTimer)
  clearTimeout(warningTimer)
  warningTimer = setTimeout(() => {
    ElNotification({ title: '即将退出', message: '2分钟无操作将自动退出登录', type: 'warning', duration: 10000 })
  }, INACTIVE_TIMEOUT - WARNING_BEFORE)
  inactivityTimer = setTimeout(() => {
    userStore.logout()
    router.push('/login')
    ElMessage.warning('30分钟无操作，已自动退出')
  }, INACTIVE_TIMEOUT)
}

onMounted(async () => {
  try {
    await userStore.fetchUserInfo()
    if (route.path === '/dashboard' && !userStore.hasPermission('dashboard:view')) {
      router.replace(firstAccessiblePath())
    }
    // 路由级权限守卫：直接输 URL 访问无权限页面 → 跳回首页（菜单已隐藏的页面此处兜底）
    const needPerm = route.meta.permission
    if (needPerm && !userStore.hasPermission(needPerm)) {
      ElMessage.warning('没有访问权限')
      router.replace('/dashboard')
    }
    userStore.refreshBadges()
    userStore.loadSystemConfig()
  } catch (e) {
    router.push('/login')
  }
  // 启动无操作计时
  const events = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart']
  events.forEach(e => window.addEventListener(e, resetInactivityTimer, { passive: true }))
  resetInactivityTimer()
})

onBeforeUnmount(() => {
  const events = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart']
  events.forEach(e => window.removeEventListener(e, resetInactivityTimer))
  clearTimeout(inactivityTimer)
  clearTimeout(warningTimer)
})

const hasPerm = (p) => userStore.hasPermission(p)

const handleCommand = (command) => {
  if (command === 'profile') {
    showProfile.value = true
  } else if (command === 'password') {
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    showPassword.value = true
  } else if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.sidebar {
  background-color: #001629;
  transition: width 0.3s;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.sidebar .el-scrollbar {
  flex: 1;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255,255,255,0.1);
  flex-shrink: 0;
}
.logo-icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  background: #409eff;
  color: #fff;
  border-radius: 6px;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}
.logo-img {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  object-fit: contain;
}
.el-menu {
  border-right: none;
}
.menu-group-label {
  padding: 16px 20px 6px;
  font-size: 12px;
  color: rgba(191,203,217,0.4);
  letter-spacing: 1px;
}
/* 客户管理子菜单缩进 */
.sub-menu-item {
  padding-left: 52px !important;
  font-size: 13px;
  height: 40px;
  line-height: 40px;
}
.sub-menu-item :deep(.el-menu-item) {
  height: 40px;
}
/* 菜单标题行：文字+计数器同行 */
.menu-title-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.menu-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  background: #f56c6c;
  color: #fff;
  border-radius: 9px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  flex-shrink: 0;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eee;
  height: 60px;
  padding: 0 20px;
  background: #fff;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #666;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-icon {
  font-size: 19px;
  cursor: pointer;
  color: #5f6672;
  padding: 7px;
  border-radius: 8px;
  transition: all .2s;
}
.header-icon:hover {
  color: #409eff;
  background: rgba(64,158,255,.1);
}
.user-dropdown {
  outline: none;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  padding: 5px 10px;
  border-radius: 10px;
  transition: background .2s;
}
.user-info:hover {
  background: rgba(64,158,255,.09);
}
.user-avatar-wrap {
  position: relative;
  display: inline-flex;
}
.user-avatar {
  background: linear-gradient(135deg, #409eff, #6366f1);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(64,158,255,.35);
}
.online-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #22c55e;
  border: 2px solid #fff;
}
.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.user-name {
  font-weight: 600;
  color: #1f2329;
  font-size: 13px;
}
.user-dept {
  color: #8f959e;
  font-size: 11px;
  white-space: nowrap;
}
.dot-sep {
  font-style: normal;
  margin: 0 4px;
  color: #c0c4cc;
}
.user-caret {
  font-size: 12px;
  color: #a8abb2;
  transition: transform .2s;
}
.user-dropdown:hover .user-caret {
  transform: rotate(180deg);
}
/* 下拉菜单：账号区 + 图标菜单项 */
.user-menu {
  padding: 6px;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(31,35,41,.12);
}
.user-menu-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 4px;
  background: #f5f7fa;
  border-radius: 8px;
}
.user-avatar-lg {
  background: linear-gradient(135deg, #409eff, #6366f1);
  color: #fff;
  font-weight: 600;
}
.user-menu-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  min-width: 0;
}
.um-name {
  font-weight: 600;
  color: #1f2329;
  font-size: 13px;
}
.um-role {
  color: #8f959e;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-menu .el-dropdown-menu__item {
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
}
.menu-ico {
  margin-right: 8px;
  font-size: 15px;
  vertical-align: -2px;
}
.logout-item {
  color: #f56c6c;
}
.logout-item:hover {
  color: #f56c6c !important;
  background: #fef0f0 !important;
}
.main-content {
  background: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
