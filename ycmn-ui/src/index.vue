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
        <el-menu
          :default-active="$route.path"
          :collapse="isCollapse"
          router
          background-color="#001629"
          text-color="#bfcbd9"
          active-text-color="#409eff"
          :collapse-transition="false"
        >
          <!-- 首页 -->
          <template v-if="hasPerm('dashboard:view')">
            <div class="menu-group-label" v-show="!isCollapse">首页</div>
            <el-menu-item index="/dashboard">
              <el-icon><Monitor /></el-icon>
              <template #title>工作台</template>
            </el-menu-item>
          </template>

          <!-- 业务核心 -->
          <div class="menu-group-label" v-show="!isCollapse">业务核心</div>
          <el-sub-menu v-if="hasPerm('customer:view')" index="customer-group">
            <template #title>
              <el-icon><User /></el-icon>
              <span>客户管理</span>
            </template>
            <el-menu-item index="/customer">客户列表</el-menu-item>
            <el-menu-item v-if="hasPerm('customer:add')" index="/customer/add">新增客户</el-menu-item>
          </el-sub-menu>
          <el-menu-item v-if="hasPerm('file:view')" index="/file">
            <el-icon><Folder /></el-icon>
            <template #title>文件管理</template>
          </el-menu-item>

          <el-sub-menu v-if="hasPerm('incentive:view')" index="incentive-group">
            <template #title>
              <el-icon><Document /></el-icon>
              <span>四项激励</span>
            </template>
            <el-menu-item index="/incentive">
              <el-icon><Upload /></el-icon>
              <span>激励上传</span>
            </el-menu-item>
            <el-menu-item index="/incentive/stats">
              <el-icon><TrendCharts /></el-icon>
              <span>激励统计</span>
            </el-menu-item>
            <el-menu-item index="/incentive/search">
              <el-icon><Search /></el-icon>
              <span>激励查询</span>
            </el-menu-item>
          </el-sub-menu>

          <el-menu-item v-if="hasPerm('reminder:view')" index="/reminder">
            <el-icon><Bell /></el-icon>
            <template #title>
              <span class="menu-title-row">
                <span>提醒管理</span>
                <span class="menu-count" v-if="userStore.badgeReminder > 0">{{ userStore.badgeReminder }}</span>
              </span>
            </template>
          </el-menu-item>
          <el-sub-menu v-if="hasPerm('sea:view')" index="sea-group">
            <template #title>
              <el-icon><Ship /></el-icon>
              <span>公海管理</span>
            </template>
            <el-menu-item index="/sea">公海池</el-menu-item>
            <el-menu-item index="/sea/records">捞取记录</el-menu-item>
          </el-sub-menu>
          <el-menu-item v-if="hasPerm('approval:view')" index="/approval">
            <el-icon><CircleCheck /></el-icon>
            <template #title>
              <span class="menu-title-row">
                <span>审批管理</span>
                <span class="menu-count" v-if="userStore.badgeApproval > 0">{{ userStore.badgeApproval }}</span>
              </span>
            </template>
          </el-menu-item>

          <!-- 考核与分析 -->
          <div class="menu-group-label" v-show="!isCollapse">考核与分析</div>
          <el-menu-item v-if="hasPerm('statistics:view')" index="/statistics">
            <el-icon><TrendCharts /></el-icon>
            <template #title>统计分析</template>
          </el-menu-item>
          <el-sub-menu index="score-group">
            <template #title>
              <el-icon><Medal /></el-icon>
              <span>学分管理</span>
            </template>
            <el-menu-item index="/score/entry">学分录入</el-menu-item>
            <el-menu-item index="/score/ranking">学分排行</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="indicator-group">
            <template #title>
              <el-icon><Aim /></el-icon>
              <span>核心客户六项指标</span>
            </template>
            <el-menu-item index="/indicator">统计总览</el-menu-item>
          </el-sub-menu>

          <!-- 系统管理（仅管理员可见） -->
          <template v-if="hasPerm('system:user:view')">
            <div class="menu-group-label" v-show="!isCollapse">系统管理</div>
            <el-menu-item index="/system/user">
              <el-icon><UserFilled /></el-icon>
              <template #title>人员管理</template>
            </el-menu-item>
            <el-menu-item v-if="hasPerm('system:role:view')" index="/system/role">
              <el-icon><Key /></el-icon>
              <template #title>角色管理</template>
            </el-menu-item>
            <el-menu-item v-if="hasPerm('system:dept:view')" index="/system/org">
              <el-icon><OfficeBuilding /></el-icon>
              <template #title>组织架构</template>
            </el-menu-item>
            <el-menu-item v-if="hasPerm('system:config:view')" index="/system/config">
              <el-icon><Tools /></el-icon>
              <template #title>系统设置</template>
            </el-menu-item>
            <el-menu-item v-if="hasPerm('system:log:view')" index="/system/log">
              <el-icon><Notebook /></el-icon>
              <template #title>日志管理</template>
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
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" class="user-avatar">{{ userStore.realName?.charAt(0) }}</el-avatar>
              <span class="user-name">{{ userStore.realName }}</span>
              <span class="user-dept">· {{ userStore.officeName }} {{ userStore.roleName }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
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
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { useUserStore } from '@/store/user'
import { changePassword } from '@/api/system'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

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
  gap: 16px;
}
.header-icon {
  font-size: 20px;
  cursor: pointer;
  color: #666;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.user-avatar {
  background: #409eff;
  color: #fff;
}
.user-name {
  font-weight: 500;
  color: #333;
}
.user-dept {
  color: #999;
  font-size: 12px;
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
