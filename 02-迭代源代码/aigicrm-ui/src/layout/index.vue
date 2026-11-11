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
        <!-- el-menu 三个颜色 prop 需字面色值参与 hover 混色,取值须与 style.css :root 的
             --sidebar-bg/--sidebar-text/--sidebar-active 令牌保持一致;当前态高亮按 meta.activeMenu 计算 -->
        <el-menu
          :default-active="activeMenuPath"
          :collapse="isCollapse"
          router
          background-color="#001629"
          text-color="#bfcbd9"
          active-text-color="#6083e4"
          :collapse-transition="false"
        >
          <template v-for="group in menuGroups" :key="group.id">
            <div class="menu-group-label" v-show="!isCollapse">{{ group.menuName }}</div>
            <!-- 按后端菜单 sort_order 原顺序渲染；不能把有子菜单的项统一提前，否则生产菜单顺序会变形。 -->
            <template v-for="item in group.children" :key="item.id">
              <el-sub-menu v-if="item.subChildren.length" :index="item.path">
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
              <el-menu-item v-else :index="item.path">
                <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
                <template #title>
                  <span class="menu-title-row">
                    <span>{{ item.menuName }}</span>
                    <span class="menu-count" v-if="badgeFor(item.path)">{{ badgeFor(item.path) }}</span>
                  </span>
                </template>
              </el-menu-item>
            </template>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <!-- 主体 -->
    <el-container>
      <!-- 顶部栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse">
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
          <!-- 【批④ 2026-08-10 多集团会话】集团切换入口(仅多集团用户显示);
               2026-10-08 显示集团名而非裸 ID(名称缺失回退"集团 <id>") -->
          <el-dropdown v-if="userStore.canSwitchGroup" trigger="click" class="group-dropdown" @command="handleSwitchGroup">
            <span class="group-switch"><el-icon><OfficeBuilding /></el-icon> {{ userStore.currentGroupName || ('集团 ' + userStore.currentGroupId) }} <el-icon><ArrowDown /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="g in userStore.groupIds" :key="g" :command="g" :disabled="g === userStore.currentGroupId">
                  {{ userStore.groupNames[g] || ('集团 ' + g) }}{{ g === userStore.currentGroupId ? '（当前）' : '' }}
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
        <!-- 【2026-09-20 全局页面骨架】所有菜单页统一：灰画布(main-content) → 白色整体容器(page-shell) → 页内小布局 -->
        <div class="page-shell">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
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
// 【2026-08-15 审计 H4】后端已按 path 去重; 前端再兜底一次, 防历史冗余菜单行导致侧边栏同页渲染两次
// 【A1 三轮 2026-10-09】动作型入口不入导航：侧栏「新增客户」与客户列表页右上「+ 新客户」主按钮功能完全重复
// （常见 CRM 惯例是仅保留列表入口 + 按钮）。路由 /customer/add 保留作页面载体（按钮跳转/面包屑不受影响），
// 仅从侧栏菜单树中滤除；后端将来若停发该菜单行，此过滤为空操作
const HIDDEN_ACTION_PATHS = new Set(['/customer/add'])
const menuGroups = computed(() => {
  const seenPaths = new Set()
  const groups = (userStore.menus || [])
    .map(group => ({
      ...group,
      children: (group.children || [])
        .filter(c => c.menuType === 2 && c.path)
        .filter(c => !HIDDEN_ACTION_PATHS.has(c.path))
        .filter(c => {
          if (seenPaths.has(c.path)) return false
          seenPaths.add(c.path)
          return true
        })
        .map(c => ({
          ...c,
          // 二级子页面：子级中可见的页面级菜单（客户详情 visible=0 不显示），同 path 也去重
          subChildren: (c.children || [])
            .filter(s => s.menuType === 2 && s.path && s.visible !== 0)
            .filter((s, idx, arr) => arr.findIndex(x => x.path === s.path) === idx)
        }))
    }))
    .filter(group => group.children.length > 0)
  // 【A1 2026-10-08 二轮】「我的任务」(/task) 是工作台"待办任务"卡直达页，但后端菜单树没有该页，
  // 用户进入后无法从菜单回到（断头页）。前端合成注入"业务核心"分组（该页仅要求登录态，无独立权限码）；
  // 后端菜单将来下发同名菜单时按 path 去重不会重复出现
  const bizGroup = groups.find(g => g.menuName === '业务核心')
  // 【工作流复核确认修复】注入加 task:view 权限门控: 后端 /api/task/* 均要求该权限码,
  // 无权限角色(如行政 05)原可见菜单, 点入即 403 报错+空数据页(死入口)
  if (bizGroup && userStore.hasPermission('task:view') && !bizGroup.children.some(c => c.path === '/task')) {
    bizGroup.children.push({
      id: 'fe-task-entry', menuType: 2, path: '/task', menuName: '我的任务', icon: 'List', subChildren: [],
    })
  }
  return groups
})
// 提醒/审批两个菜单项的角标数量走独立的未读计数接口，不属于菜单树本身的数据
const badgeFor = (path) => {
  if (path === '/reminder') return userStore.badgeReminder > 0 ? userStore.badgeReminder : 0
  if (path === '/approval') return userStore.badgeApproval > 0 ? userStore.badgeApproval : 0
  return 0
}

// 无工作台权限时的兜底落地页(按菜单顺序取首个有权限的页面)
const firstAccessiblePath = () => {
  const firstMenu = menuGroups.value
    .flatMap(group => group.children || [])
    .find(item => item.path)
  return firstMenu?.path || '/403'
}
// 【2026-10-08 UI 整改 A1】当前态高亮：detail/edit 等深链路由不在侧栏菜单中(如 /customer/detail/358)，
// 按 meta.activeMenu 归位到所属列表菜单，修复深入两级后侧栏无任何高亮的问题
const activeMenuPath = computed(() => route.meta?.activeMenu || route.path)

// 【A1 2026-10-08 二轮】当前菜单项滚出侧栏视口时自动滚动定位（如 /system/user 在"系统管理"
// 长菜单深处），路由切换后对 .is-active 菜单项执行 scrollIntoView，保证当前态对用户始终可见
watch([activeMenuPath, menuGroups], () => {
  nextTick(() => {
    document.querySelector('.sidebar .el-menu-item.is-active')
      ?.scrollIntoView?.({ block: 'nearest' })
  })
})

// 【2026-10-08 UI 整改 D16】侧栏折叠态持久化：刷新/重登保持用户偏好
const SIDEBAR_COLLAPSE_KEY = 'aigicrm_sidebar_collapsed'
const readStoredCollapse = () => {
  try { return localStorage.getItem(SIDEBAR_COLLAPSE_KEY) === '1' } catch { return false }
}
const isCollapse = ref(readStoredCollapse())
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
  try { localStorage.setItem(SIDEBAR_COLLAPSE_KEY, isCollapse.value ? '1' : '0') } catch { /* 存储不可用(隐私模式等)时静默 */ }
}

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
  if (pwdForm.newPassword.length < 8 || pwdForm.newPassword.length > 30) {
    return ElMessage.warning('新密码长度须为 8-30 位')
  }
  pwdLoading.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    showPassword.value = false
    userStore.logout()
    router.push('/login')
  } catch (e) {
    // 【工作流复核确认修复】错误详情由 request 拦截器统一弹出(原双弹同文案)
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
    // 【2026-11-12 裁决A】dashboard:view 退役, 工作台兜底判断与路由门禁同改 statistics:view
    if (route.path === '/dashboard' && !userStore.hasPermission('statistics:view')) {
      router.replace(firstAccessiblePath())
    }
    // 路由级权限守卫：直接输 URL 访问无权限页面 → 跳回首页（菜单已隐藏的页面此处兜底）
    const needPerm = route.meta.permission
    if (needPerm && !userStore.hasPermission(needPerm)) {
      ElMessage.warning('没有访问权限')
      router.replace(firstAccessiblePath())
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
    // 【工作流复核确认修复】切换成功后刷新角标(数据域已变, 旧集团角标是错值) + 提示显示集团名(INV-11)
    userStore.refreshBadges()
    ElMessage.success(`已切换到${userStore.currentGroupName || '集团 ' + groupId}`)
  } catch (e) {
    // 失败详情由拦截器弹出(原双弹: 真实原因+笼统文案各一条)
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
.group-switch { cursor: pointer; font-size: 13px; color: var(--text-secondary); display: inline-flex; align-items: center; gap: 3px; margin-right: 14px; padding: 4px 8px; border: 1px solid #e4e7ed; border-radius: 4px; }
.group-switch:hover { color: var(--el-color-primary); border-color: #bfdbfe; background: #eef4ff; }

.layout-container {
  height: 100vh;
}
.sidebar {
  background-color: var(--sidebar-bg);
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
  background: #1d4ed8;
  color: #fff;
  border-radius: var(--radius-sm);
  align-items: center;
  justify-content: center;
  font-size: 16px;
}
.logo-img {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  object-fit: contain;
}
.el-menu {
  border-right: none;
}
/* 【2026-10-08 UI 整改①】侧栏当前态配色接线到 :root 令牌（与上方 el-menu prop 同值，令牌为唯一来源） */
.sidebar :deep(.el-menu-item.is-active) { color: var(--sidebar-active); }
.menu-group-label {
  padding: 16px 20px 6px;
  font-size: 12px;
  color: var(--sidebar-text);
  opacity: .4;
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
  background: var(--el-color-danger);
  color: #fff;
  border-radius: 9px;
  font-size: 12px;
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
  color: var(--text-secondary);
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
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all .2s;
}
.icon-btn:hover {
  background: var(--el-color-primary-light-9);
}
.icon-btn:hover .header-icon {
  color: var(--el-color-primary);
  transform: scale(1.08);
}
.header-icon {
  font-size: 20px;
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
  background: var(--el-color-danger);
  color: #fff;
  font-size: 12px;
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
  border-radius: var(--radius-md);
  transition: background .2s;
}
.user-info:hover {
  background: var(--el-color-primary-light-9);
}
.user-avatar-wrap {
  position: relative;
  display: inline-flex;
}
.user-avatar {
  background: var(--hero-gradient);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(29,78,216,.35);
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
  color: var(--text-primary);
  font-size: 13px;
}
.user-dept {
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}
.dot-sep {
  font-style: normal;
  margin: 0 4px;
  color: var(--text-placeholder);
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
  background: var(--hero-gradient);
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
  color: var(--text-primary);
  font-size: 13px;
}
.um-role {
  color: var(--text-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-menu .el-dropdown-menu__item {
  border-radius: var(--radius-sm);
  padding: 8px 12px;
  font-size: 13px;
}
.menu-ico {
  margin-right: 8px;
  font-size: 15px;
  vertical-align: -2px;
}
.logout-item {
  color: var(--el-color-danger);
}
.logout-item:hover {
  color: var(--el-color-danger) !important;
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
  background: var(--hero-gradient);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(29,78,216,.35);
}
.profile-head-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.profile-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}
.profile-tags {
  display: flex;
  gap: 8px;
}
.role-tag {
  font-size: 12px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  padding: 2px 10px;
  border-radius: var(--radius-md);
  font-weight: 600;
}
.scope-tag {
  font-size: 12px;
  color: #10b981;
  background: rgba(16,185,129,.12);
  padding: 2px 10px;
  border-radius: var(--radius-md);
  font-weight: 600;
}
.profile-group {
  margin-bottom: 14px;
}
.profile-group-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 6px;
  padding-left: 2px;
}
.profile-rows {
  border: 1px solid #ebeef5;
  border-radius: var(--radius-md);
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
  color: var(--text-muted);
  flex-shrink: 0;
}
.pv {
  color: var(--text-primary);
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
