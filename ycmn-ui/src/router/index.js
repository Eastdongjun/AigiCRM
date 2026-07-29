import { createRouter, createWebHistory } from 'vue-router'
import { isTokenValid, clearToken } from '@/utils/token'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
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
        meta: { title: '工作台', icon: 'Monitor' }
      },
      // 客户管理
      {
        path: 'customer',
        name: 'CustomerList',
        component: () => import('@/views/customer/list.vue'),
        meta: { title: '客户列表', icon: 'User' }
      },
      {
        path: 'customer/add',
        name: 'CustomerAdd',
        component: () => import('@/views/customer/add.vue'),
        meta: { title: '新增客户' }
      },
      {
        path: 'customer/edit/:id',
        name: 'CustomerEdit',
        component: () => import('@/views/customer/add.vue'),
        meta: { title: '编辑客户' }
      },
      {
        path: 'customer/detail/:id',
        name: 'CustomerDetail',
        component: () => import('@/views/customer/detail.vue'),
        meta: { title: '客户详情' }
      },
      // 文件管理
      {
        path: 'file',
        name: 'FileManage',
        component: () => import('@/views/file/index.vue'),
        meta: { title: '文件管理', icon: 'Folder' }
      },
      // 激励模块
      {
        path: 'incentive',
        name: 'Incentive',
        component: () => import('@/views/incentive/index.vue'),
        meta: { title: '四项激励Plus版', parentTitle: '四项激励Plus', icon: 'Trophy' }
      },
      {
        path: 'incentive/stats',
        name: 'IncentiveStats',
        component: () => import('@/views/incentive/stats.vue'),
        meta: { title: '激励统计', parentTitle: '四项激励Plus', icon: 'TrendCharts' }
      },
      {
        path: 'incentive/search',
        name: 'IncentiveSearch',
        component: () => import('@/views/incentive/search.vue'),
        meta: { title: '激励查询', parentTitle: '四项激励Plus', icon: 'Search' }
      },
      // 提醒管理
      {
        path: 'reminder',
        name: 'Reminder',
        component: () => import('@/views/reminder/index.vue'),
        meta: { title: '提醒管理', icon: 'Bell' }
      },
      // 公海管理
      {
        path: 'sea',
        name: 'SeaPool',
        component: () => import('@/views/sea/index.vue'),
        meta: { title: '公海池', parentTitle: '公海管理', icon: 'Ship' }
      },
      {
        path: 'sea/records',
        name: 'SeaRecords',
        component: () => import('@/views/sea/records.vue'),
        meta: { title: '捞取记录', parentTitle: '公海管理', icon: 'Ship' }
      },
      // 审批管理
      {
        path: 'approval',
        name: 'Approval',
        component: () => import('@/views/approval/index.vue'),
        meta: { title: '审批管理', icon: 'CircleCheck' }
      },
      // 统计分析
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: { title: '统计分析', icon: 'TrendCharts' }
      },
      {
        path: 'score/entry',
        name: 'ScoreEntry',
        component: () => import('@/views/score/entry.vue'),
        meta: { title: '学分录入', parentTitle: '学分管理', icon: 'Edit' }
      },
      {
        path: 'score/ranking',
        name: 'ScoreRanking',
        component: () => import('@/views/score/ranking.vue'),
        meta: { title: '学分排行', parentTitle: '学分管理', icon: 'Medal' }
      },
      // 核心客户六项指标
      {
        path: 'indicator',
        name: 'Indicator',
        component: () => import('@/views/indicator/index.vue'),
        meta: { title: '统计总览', parentTitle: '核心客户六项指标', icon: 'Aim' }
      },
      // 系统管理
      {
        path: 'system/user',
        name: 'UserManage',
        component: () => import('@/views/system/user.vue'),
        meta: { title: '人员管理', icon: 'UserFilled' }
      },
      {
        path: 'system/dept',
        name: 'DeptManage',
        component: () => import('@/views/system/dept.vue'),
        meta: { title: '组织架构', icon: 'OfficeBuilding' }
      },
      {
        path: 'system/role',
        name: 'RoleManage',
        component: () => import('@/views/system/role.vue'),
        meta: { title: '角色管理', icon: 'Key' }
      },
      {
        path: 'system/config',
        name: 'SystemConfig',
        component: () => import('@/views/system/config.vue'),
        meta: { title: '系统设置', icon: 'Tools' }
      },
      {
        path: 'system/log',
        name: 'SystemLog',
        component: () => import('@/views/system/log.vue'),
        meta: { title: '日志管理', icon: 'Notebook' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  document.title = (to.meta.title ? to.meta.title + ' - ' : '') + '运筹马年'
  if (to.path === '/login') {
    next()
  } else if (!isTokenValid()) {
    // token 缺失或已过期：提前清掉，避免带坏 token 进入页面触发一堆 403
    clearToken()
    next('/login')
  } else {
    next()
  }
})

export default router
