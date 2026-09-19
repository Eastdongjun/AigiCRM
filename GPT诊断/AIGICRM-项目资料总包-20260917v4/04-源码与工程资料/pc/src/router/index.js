import { createRouter, createWebHistory } from 'vue-router'
import { isTokenValid, clearToken } from '@/utils/token'
import { firstAccessiblePath } from './access'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/403',
    name: 'NoPermission',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限' }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'Monitor', permission: 'dashboard:view' }
      },
      // 客户管理
      {
        path: 'customer',
        name: 'CustomerList',
        component: () => import('@/views/customer/list.vue'),
        meta: { title: '客户列表', icon: 'User', permission: 'customer:view' }
      },
      {
        path: 'customer/add',
        name: 'CustomerAdd',
        component: () => import('@/views/customer/add.vue'),
        meta: { title: '新增客户', permission: 'customer:add' }
      },
      {
        path: 'customer/edit/:id',
        name: 'CustomerEdit',
        component: () => import('@/views/customer/add.vue'),
        meta: { title: '编辑客户', permission: 'customer:edit' }
      },
      {
        path: 'customer/detail/:id',
        name: 'CustomerDetail',
        component: () => import('@/views/customer/detail.vue'),
        meta: { title: '客户详情', permission: 'customer:view' }
      },
      // 文件管理
      {
        path: 'file',
        name: 'FileManage',
        component: () => import('@/views/file/index.vue'),
        meta: { title: '文件管理', icon: 'Folder', permission: 'file:view' }
      },
      // 激励模块
      {
        path: 'incentive',
        name: 'Incentive',
        component: () => import('@/views/incentive/index.vue'),
        // 【2026-09-13 口径统一】本路由即「激励上传」页：菜单码、后端上传接口 @PreAuthorize、
        // 小程序上传入口用的都是 incentive:add（此前路由写 incentive:view，与菜单码不一致）
        meta: { title: '四项激励', icon: 'Trophy', permission: 'incentive:add' }
      },
      {
        path: 'incentive/stats',
        name: 'IncentiveStats',
        component: () => import('@/views/incentive/stats.vue'),
        meta: { title: '激励统计', parentTitle: '四项激励', icon: 'TrendCharts', permission: 'incentive:view' }
      },
      {
        // 【2026-09-13】激励统计表格行的下钻明细页（子页面）；侧边栏来自后端菜单树，本路由不会出现在菜单里
        path: 'incentive/stats/detail/:childId',
        name: 'IncentiveStatsDetail',
        component: () => import('@/views/incentive/StatsDetail.vue'),
        meta: { title: '激励明细', parentTitle: '激励统计', icon: 'Document', permission: 'incentive:view' }
      },
      {
        path: 'incentive/search',
        name: 'IncentiveSearch',
        component: () => import('@/views/incentive/search.vue'),
        meta: { title: '激励查询', parentTitle: '四项激励', icon: 'Search', permission: 'incentive:view' }
      },
      {
        path: 'incentive/ranking',
        name: 'LegacyIncentiveRanking',
        component: () => import('@/views/score/ranking.vue'),
        meta: { title: '积分排行', parentTitle: '积分管理', icon: 'Medal', permission: 'score:view' }
      },
      // 提醒管理
      {
        path: 'reminder',
        name: 'Reminder',
        component: () => import('@/views/reminder/index.vue'),
        meta: { title: '提醒管理', icon: 'Bell', permission: 'reminder:view' }
      },
      {
        // 【2026-09-13】提醒管理列表行的下钻详情页（子页面）；侧边栏来自后端菜单树，本路由不会出现在菜单里
        path: 'reminder/detail/:id',
        name: 'ReminderDetail',
        component: () => import('@/views/reminder/detail.vue'),
        meta: { title: '提醒详情', parentTitle: '提醒管理', icon: 'Document', permission: 'reminder:view' }
      },
      // 公海管理
      {
        path: 'sea',
        name: 'SeaPool',
        component: () => import('@/views/sea/index.vue'),
        meta: { title: '公海池', parentTitle: '公海管理', icon: 'Ship', permission: 'sea:view' }
      },
      {
        path: 'sea/records',
        name: 'SeaRecords',
        component: () => import('@/views/sea/records.vue'),
        meta: { title: '捞取记录', parentTitle: '公海管理', icon: 'Ship', permission: 'sea:view' }
      },
      // 审批管理
      {
        path: 'approval',
        name: 'Approval',
        component: () => import('@/views/approval/index.vue'),
        meta: { title: '审批管理', icon: 'CircleCheck', permission: 'approval:view' }
      },
      // 统计分析
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: { title: '统计分析', icon: 'TrendCharts', permission: 'statistics:view' }
      },
      {
        path: 'score/entry',
        name: 'ScoreEntry',
        component: () => import('@/views/score/entry.vue'),
        meta: { title: '积分录入', parentTitle: '积分管理', icon: 'Edit', permission: 'score:entry' }
      },
      {
        path: 'score/ranking',
        name: 'ScoreRanking',
        component: () => import('@/views/score/ranking.vue'),
        meta: { title: '积分排行', parentTitle: '积分管理', icon: 'Medal', permission: 'score:view' }
      },
      {
        path: 'ranking/customer',
        name: 'CustomerRanking',
        component: () => import('@/views/ranking/customer.vue'),
        meta: { title: '客户排行', parentTitle: '统计分析', icon: 'DataLine', permission: 'statistics:view' }
      },
      // 核心客户六项指标
      // 标题统一为「核心客户六项指标」：生产菜单(id=108)与页面 <h3> 均用此名，
      // 原 meta.title 写作「统计总览」会造成"点进去看到的和菜单不一致"。
      {
        path: 'indicator',
        name: 'Indicator',
        component: () => import('@/views/indicator/index.vue'),
        meta: { title: '核心客户六项指标', icon: 'Aim', permission: 'statistics:view' }
      },
      // 系统管理
      {
        path: 'system/user',
        name: 'UserManage',
        component: () => import('@/views/system/user.vue'),
        meta: { title: '人员管理', icon: 'UserFilled', permission: 'system:user:view' }
      },
      {
        path: 'system/org',
        name: 'DeptManage',
        component: () => import('@/views/system/dept.vue'),
        meta: { title: '组织架构', icon: 'OfficeBuilding', permission: 'system:dept:view' }
      },
      // 兼容旧菜单、旧书签与历史审计链接：真实菜单已切到 system/org，但旧路径仍应打开同一组织页面。
      // 这不是侧边栏入口，只是让已下发过的 legacy route 可自愈。
      {
        path: 'system/dept',
        name: 'LegacyDeptManage',
        component: () => import('@/views/system/dept.vue'),
        meta: { title: '组织架构', icon: 'OfficeBuilding', permission: 'system:dept:view' }
      },
      {
        path: 'system/role',
        name: 'RoleManage',
        component: () => import('@/views/system/role.vue'),
        meta: { title: '角色管理', icon: 'Key', permission: 'system:role:view' }
      },
      {
        path: 'system/position',
        name: 'PositionManage',
        component: () => import('@/views/system/position.vue'),
        meta: { title: '岗位管理', icon: 'Avatar', permission: 'system:position:view' }
      },
      {
        path: 'system/config',
        name: 'SystemConfig',
        component: () => import('@/views/system/config.vue'),
        meta: { title: '系统设置', icon: 'Tools', permission: 'system:config:view' }
      },
      {
        path: 'system/log',
        name: 'SystemLog',
        component: () => import('@/views/system/log.vue'),
        meta: { title: '日志管理', icon: 'Notebook', permission: 'system:log:view' }
      },
      {
        path: 'system/rule',
        name: 'PermissionRule',
        component: () => import('@/views/system/rule.vue'),
        meta: { title: '权限规则', icon: 'SetUp', permission: 'system:rule:view' }
      },
      {
        // 【2026-09-13】本月激励查询阅读排行榜：入口在系统设置下，默认只绑系统管理员角色，
        // 管理员在「角色管理 → 权限分配」勾选该菜单后其它角色才可见（菜单/路由/接口同一权限码）
        path: 'system/incentive-read-ranking',
        name: 'IncentiveReadRanking',
        component: () => import('@/views/system/incentive-read-ranking.vue'),
        meta: {
          title: '本月激励查询阅读排行榜',
          parentTitle: '系统设置',
          icon: 'DataAnalysis',
          permission: 'incentive:read-ranking:view'
        }
      },
      {
        path: 'system/group',
        name: 'GroupManage',
        component: () => import('@/views/system/group.vue'),
        meta: { title: '集团管理', icon: 'OfficeBuilding', permission: 'system:group:view' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  document.title = (to.meta.title ? to.meta.title + ' - ' : '') + 'AIGICRM'
  if (to.path === '/login') {
    next()
  } else if (!isTokenValid()) {
    // token 缺失或已过期：提前清掉，避免带坏 token 进入页面触发一堆 403
    clearToken()
    next('/login')
  } else {
    // 【修复 2026-08-10 P1-09】页面级权限在守卫层检查(此前在 layout onMounted 才检查,
    // 页面已渲染后才踢回; 且多数模块路由无 meta.permission 完全无前端门控)。
    // 动态 import 避免 router ↔ store 循环依赖。
    const { useUserStore } = await import('@/store/user')
    const userStore = useUserStore()
    if (!userStore.userId || userStore.permissions.size === 0) {
      try {
        await userStore.fetchUserInfo(true)
      } catch (e) {
        clearToken()
        next('/login')
        return
      }
    }
    if (to.meta.permission && !userStore.hasPermission(to.meta.permission)) {
      const fallback = firstAccessiblePath(routes, userStore)
      next(to.path === fallback ? '/403' : fallback)
      return
    }
    next()
  }
})

// 【需求】旧版页面自愈(FEAT 0804):浏览器停留在旧版时,点击菜单动态加载已删除的 chunk 会失败
// (表现:点击没反应/内容空白)。捕获懒加载失败后强制刷新——no-cache 保证刷新加载新版,无需用户清缓存
router.onError((error) => {
  if (String(error).includes('Failed to fetch dynamically imported module')
      || String(error).includes('dynamically imported module')
      || String(error).includes('Importing a module script failed')) {
    try { localStorage.removeItem('app_version') } catch (e) {}
    location.reload()
  }
})

export default router
