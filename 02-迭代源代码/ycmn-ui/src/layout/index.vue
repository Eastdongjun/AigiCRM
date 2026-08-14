<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '200px'" class="sidebar">
      <div class="logo">
        <img :src="userStore.systemLogo || '/favicon.png'" class="logo-img" />
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
            <!-- 二级菜单：页面带子页面时用 el-sub-menu 展开 -->
            <el-sub-menu v-for="item in group.children.filter(c => c.subChildren.length)" :key="item.id" :index="item.path">
              <template #title>
                <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
                <span class="menu-title-row">
                  <span>{{ item.menuName }}</span>
                  <span class="menu-count" v-if="badgeFor(item.path)">{{ badgeFor(item.path) }}</span>
                </span>
              </template>
              <el-menu-item v-for="sub in item.subChildren" :key="sub.id" :index="sub.path">
                <template #title>
                  <span>{{ sub.menuName }}</span>
                </template>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-for="item in group.children.filter(c => !c.subChildren.length)" :key="item.id" :index="item.path">
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
          <el-tooltip content="提醒" placement="bottom" :show-after="300">
            <div class="icon-btn" @click="router.push('/reminder')">
              <el-icon class="header-icon"><Bell /></el-icon>
              <span v-if="userStore.badgeReminder > 0" class="badge-dot">{{ userStore.badgeReminder > 99 ? '99+' : userStore.badgeReminder }}</span>
            </div>
          </el-tooltip>
          <el-tooltip content="帮助" placement="bottom" :show-after="300">
            <div class="icon-btn">
              <el-icon class="header-icon"><QuestionFilled /></el-icon>
            </div>
          </el-tooltip>
          <!-- 【批④ 2026-08-10 多集团会话】集团切换入口(仅多集团用户显示) -->
          <el-dropdown v-if="userStore.canSwitchGroup" trigger="click" class="group-dropdown" @command="handleSwitchGroup">
            <span class="group-switch"><el-icon><OfficeBuilding /></el-icon> 集团:{{ userStore.currentGroupId }} <el-icon><ArrowDown /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="g in userStore.groupIds" :key="g" :command="g" :disabled="g === userStore.currentGroupId">
                  集团 {{ g }}{{ g === userStore.currentGroupId ? '（当前）' : '' }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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
      <el-dialog v-model="showProfile" title="个人信息" width="460px" class="profile-dialog">
        <div class="profile-head">
          <el-avatar :size="56" class="profile-avatar">{{ userStore.realName?.charAt(0) }}</el-avatar>
          <div class="profile-head-meta">
            <div class="profile-name">{{ userStore.realName }}</div>
            <div class="profile-tags">
              <span class="role-tag">{{ userStore.roleName }}</span>
              <span class="scope-tag">{{ scopeLabel(userStore.dataScope) }}</span>
            </div>
          </div>
        </div>
        <div class="profile-group">
          <div class="profile-group-title">基本信息</div>
          <div class="profile-rows">
            <div class="profile-row"><span class="pl">用户名</span><span class="pv">{{ userStore.username }}</span></div>
            <div class="profile-row"><span class="pl">所属部门</span><span class="pv">{{ userStore.deptName || '—' }}</span></div>
            <div class="profile-row"><span class="pl">办事处</span><span class="pv">{{ userStore.officeName || '—' }}</span></div>
          </div>
        </div>
        <div class="profile-group">
          <div class="profile-group-title">账号信息</div>
          <div class="profile-rows">
            <div class="profile-row"><span class="pl">用户ID</span><span class="pv">{{ userStore.userId }}</span></div>
            <div class="profile-row"><span class="pl">角色</span><span class="pv">{{ userStore.roleName || '—' }}</span></div>
            <div class="profile-row"><span class="pl">数据范围</span><span class="pv">{{ scopeLabel(userStore.dataScope) }}</span></div>
            <div class="profile-row"><span class="pl">功能权限</span><span class="pv">{{ userStore.permissions?.size || 0 }} 项</span></div>
          </div>
        </div>
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
import { OfficeBuilding } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 【修复 FEAT-20260804-DYNAMIC-MENU】把后端下发的两层 menus 树整理成"分组+可点击页面"，
// 过滤掉没有任何可见子页面的空分组（比如角色没被授权该分组下任何页面时）
const menuGroups = computed(() => {
  return (userStore.menus || [])
    .map(group => ({
      ...group,
      children: (group.children || [])
        .filter(c => c.menuType === 2 && c.path)
        .map(c => ({
          ...c,
          // 二级子页面：子级中可见的页面级菜单（客户详情 visible=0 不显示）
          subChildren: (c.children || []).filter(s => s.menuType === 2 && s.path && s.visible !== 0)
        }))
    }))
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

// 数据范围中文映射（个人信息展示）
const scopeMap = { self: '仅本人', team: '本人及下级', dept: '本部门及以下', office: '本办事处及以下', region: '本大区及以下', all: '全部数据', custom: '自定义', org: '本部门', org_and_children: '本部门及子级', dept_and_children: '本部门及子级' }
const scopeLabel = (s) => scopeMap[s] || s || '—'

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

// 【批④ 2026-08-10 多集团会话】切换集团(重签 token + 强制刷新)
const handleSwitchGroup = async (groupId) => {
  try {
    await userStore.switchGroup(groupId)
    ElMessage.success(`已切换到集团 ${groupId}`)
  } catch (e) {
    ElMessage.error('集团切换失败')
  }
}

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
.group-switch { cursor: pointer; font-size: 13px; color: #606266; display: inline-flex; align-items: center; gap: 3px; margin-right: 14px; padding: 4px 8px; border: 1px solid #e4e7ed; border-radius: 4px; }
.group-switch:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }

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
  gap: 6px;
}
.icon-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  cursor: pointer;
  transition: all .2s;
}
.icon-btn:hover {
  background: rgba(64,158,255,.1);
}
.icon-btn:hover .header-icon {
  color: #409eff;
  transform: scale(1.08);
}
.header-icon {
  font-size: 19px;
  color: #5f6672;
  transition: all .2s;
}
.badge-dot {
  position: absolute;
  top: 3px;
  right: 3px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
  border: 1.5px solid #fff;
  box-sizing: border-box;
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
/* 个人信息弹窗 */
.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px 16px;
  background: linear-gradient(135deg, rgba(64,158,255,.08), rgba(99,102,241,.06));
  border-radius: 12px;
  margin-bottom: 16px;
}
.profile-avatar {
  background: linear-gradient(135deg, #409eff, #6366f1);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(64,158,255,.35);
}
.profile-head-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.profile-name {
  font-size: 18px;
  font-weight: 700;
  color: #1f2329;
}
.profile-tags {
  display: flex;
  gap: 8px;
}
.role-tag {
  font-size: 11px;
  color: #409eff;
  background: rgba(64,158,255,.12);
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 600;
}
.scope-tag {
  font-size: 11px;
  color: #10b981;
  background: rgba(16,185,129,.12);
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 600;
}
.profile-group {
  margin-bottom: 14px;
}
.profile-group-title {
  font-size: 12px;
  font-weight: 600;
  color: #8f959e;
  margin-bottom: 6px;
  padding-left: 2px;
}
.profile-rows {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
}
.profile-row {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  font-size: 13px;
}
.profile-row + .profile-row {
  border-top: 1px solid #f2f3f5;
}
.pl {
  width: 84px;
  color: #8f959e;
  flex-shrink: 0;
}
.pv {
  color: #1f2329;
  font-weight: 500;
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
