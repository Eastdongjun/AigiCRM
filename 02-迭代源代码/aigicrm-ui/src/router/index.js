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
  // 【手机文件上传 2026-11-02】小程序 web-view 容器页: 微信小程序无法直接访问手机文件系统,
  // 经本 H5 页 <input type="file"> 调起手机文件选择器后直传后端。
  // 鉴权自治: 登录 token 由小程序 URL 参数带入(见下方守卫白名单), 不依赖 PC 端 localStorage。
  {
    path: '/mp-file-picker',
    name: 'MpFilePicker',
    component: () => import('@/views/mp/FilePicker.vue'),
    meta: { title: '手机文件上传' }
  },
  // 【C12 2026-10-08 二轮】404 兜底：深链错误路径不再白屏（catchAll 放最后，不拦截已定义路由）
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在' }
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
      // 【A1 三轮 2026-10-09】客户组四条路由补 parentTitle「客户管理」：此前客户列表/新增客户面包屑只有两级，
      // 而同构的公海池/激励统计/统计分析为三级，同构页面信息路径深浅不一
      {
        path: 'customer',
        name: 'CustomerList',
        component: () => import('@/views/customer/list.vue'),
        meta: { title: '客户列表', parentTitle: '客户管理', icon: 'User', permission: 'customer:view' }
      },
      {
        path: 'customer/add',
        name: 'CustomerAdd',
        component: () => import('@/views/customer/add.vue'),
        // 【A1 三轮】侧栏「新增客户」常驻菜单项已隐藏（与列表页「+ 新客户」主按钮完全重复，动作型入口不入导航），
        // 路由保留作页面载体；进入时高亮归位到客户列表
        meta: { title: '新增客户', parentTitle: '客户管理', activeMenu: '/customer', permission: 'customer:add' }
      },
      {
        path: 'customer/edit/:id',
        name: 'CustomerEdit',
        component: () => import('@/views/customer/add.vue'),
        // activeMenu：详情/编辑类路由不在侧栏菜单中，高亮归位到所属列表菜单（layout 按 meta.activeMenu 计算当前态）
        meta: { title: '编辑客户', parentTitle: '客户管理', activeMenu: '/customer', permission: 'customer:edit' }
      },
      {
        path: 'customer/detail/:id',
        name: 'CustomerDetail',
        component: () => import('@/views/customer/detail.vue'),
        meta: { title: '客户详情', parentTitle: '客户管理', activeMenu: '/customer', permission: 'customer:view' }
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
        // 【A1 三轮 2026-10-09】meta.title 四项服务→激励上传：生产侧栏菜单项名为「激励上传」（菜单名来自后端 menuName，
        // 不受此影响），原 title 使面包屑显示「首页/四项服务」与高亮菜单项不同词；补 parentTitle 与激励查询/统计同构三级
        // 【2026-11-05 用户令"PC端还是叫四项激励"】parentTitle 归位四项激励(V2026.11.05.1 组名纠偏)
        meta: { title: '激励上传', parentTitle: '四项激励', icon: 'Trophy', permission: 'incentive:add' }
      },
      {
        path: 'incentive/stats',
        name: 'IncentiveStats',
        component: () => import('@/views/incentive/stats.vue'),
        // 【2026-11-04 逐功能建码+拆组归位】激励统计=四项激励模块: 菜单节点(sys_menu /incentive/stats)已改绑
        // incentive:stats:view(V2026.11.04.1), 此处路由守卫同步归位(存量角色已由迁移回填, 无人失权);
        // parentTitle 四项服务→四项激励(侧栏拆组 V2026.11.04.2, 面包屑与侧栏高亮组同词)
        meta: { title: '激励统计', parentTitle: '四项激励', icon: 'TrendCharts', permission: 'incentive:stats:view' }
      },
      {
        // 【2026-09-13】激励统计表格行的下钻明细页（子页面）；侧边栏来自后端菜单树，本路由不会出现在菜单里
        path: 'incentive/stats/detail/:childId',
        name: 'IncentiveStatsDetail',
        component: () => import('@/views/incentive/StatsDetail.vue'),
        // 【2026-11-04 归位】下钻明细页跟主页同码 incentive:stats:view(原 incentive:view 为 11-04 拆码前的旧口径)
        meta: { title: '激励明细', parentTitle: '激励统计', activeMenu: '/incentive/stats', icon: 'Document', permission: 'incentive:stats:view' }
      },
      {
        path: 'incentive/search',
        name: 'IncentiveSearch',
        component: () => import('@/views/incentive/search.vue'),
        // 【2026-11-05 用户令"PC端还是叫四项激励"】parentTitle 归位四项激励(V2026.11.05.1 组名纠偏)
        meta: { title: '激励查询', parentTitle: '四项激励', icon: 'Search', permission: 'incentive:view' }
      },
      {
        // 【2026-11-05 用户令"四项服务的定位是给客户展示案例库的"】PC 端四项服务=案例库:
        // 照片墙案例展示面(照片墙卡片+筛选+大图预览), 数据走 /incentive/customer-records
        // (incentive:view 同码守护, 三方同源零新码零接口改动); 菜单节点由 V2026.11.05.1 seed
        path: 'case-library',
        name: 'CaseLibrary',
        component: () => import('@/views/incentive/case-library.vue'),
        meta: { title: '案例库', parentTitle: '四项服务', icon: 'Picture', permission: 'incentive:view' }
      },
      {
        path: 'incentive/ranking',
        name: 'LegacyIncentiveRanking',
        component: () => import('@/views/score/ranking.vue'),
        meta: { title: '学分排行', parentTitle: '学分管理', activeMenu: '/score/ranking', icon: 'Medal', permission: 'score:view' }
      },
      // 提醒管理
      {
        path: 'task',
        name: 'TaskList',
        component: () => import('@/views/task/index.vue'),
        // 【A1 2026-10-08 二轮】工作台"待办任务"卡直达的本页此前无任何侧栏入口（断头页），
        // layout 会把「我的任务」合成注入"业务核心"分组菜单（后端菜单树不含该页，前端补位）
        meta: { title: '我的任务', parentTitle: '业务核心', icon: 'List', requiresAuth: true, permission: 'task:view' } // 【工作流复核确认修复】路由守卫补权限(对齐后端 task:view)
      },
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
        meta: { title: '提醒详情', parentTitle: '提醒管理', activeMenu: '/reminder', icon: 'Document', permission: 'reminder:view' }
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
        // 【A1 2026-10-08 二轮】补 parentTitle：与学分录入/学分排行同挂「考核与分析」组，同组面包屑深度一致
        meta: { title: '统计分析', parentTitle: '统计分析', icon: 'TrendCharts', permission: 'statistics:view' }
      },
      {
        path: 'score/entry',
        name: 'ScoreEntry',
        component: () => import('@/views/score/entry.vue'),
        meta: { title: '学分录入', parentTitle: '学分管理', icon: 'Edit', permission: 'score:entry' }
      },
      {
        path: 'score/ranking',
        name: 'ScoreRanking',
        component: () => import('@/views/score/ranking.vue'),
        meta: { title: '学分排行', parentTitle: '学分管理', icon: 'Medal', permission: 'score:view' }
      },
      {
        path: 'ranking/customer',
        name: 'CustomerRanking',
        component: () => import('@/views/ranking/customer.vue'),
        // 【A1 2026-10-08 二轮】parentTitle 校正：原写「统计分析」是把同组兄弟页当父级展示，
        // 统一改挂后端菜单组「考核与分析」，同组页面面包屑深度一致
        meta: { title: '客户排行', parentTitle: '客户管理', icon: 'DataLine', permission: 'statistics:view' }
      },
      // 核心客户六项指标
      // 标题统一为「核心客户六项指标」：生产菜单(id=108)与页面 <h3> 均用此名，
      // 原 meta.title 写作「统计总览」会造成"点进去看到的和菜单不一致"。
      {
        path: 'indicator',
        name: 'Indicator',
        component: () => import('@/views/indicator/index.vue'),
        meta: { title: '核心客户六项指标', parentTitle: '客户管理', icon: 'Aim', permission: 'statistics:view' }
      },
      // 【2026-11-05 用户令模块归属对齐】核心客户六项指标/客户排行→客户管理; 学分录入/排行→学分管理(新组);
// 统计分析→独立「统计分析」组; 考核与分析组撤销(V2026.11.05.4)。parentTitle 与菜单树同词。
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
        meta: { title: '组织架构', activeMenu: '/system/org', icon: 'OfficeBuilding', permission: 'system:dept:view' }
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
      // 【统一权限配置中心 2026-11-03 收口单入口 → 2026-11-05 用户令"权限中心收敛到系统设置里面"】
      // 权限中心不再是独立侧栏菜单(库节点已删 V2026.11.05.1), 页面转为 系统设置 内「权限中心」页签;
      // 本路由仅作旧链接自愈重定向(旧书签/历史审计链接/外部直达), 侧栏不再出现。
      // meta 仅保证路由块结构完整(审计抽取器要求每块带 meta, 无 meta 会吞并下一路由块);
      // vue-router 重定向后守卫读的是目标路由(/system/config)的 meta, 此处不参与鉴权。
      {
        path: 'system/perm-center',
        name: 'PermCenterLegacy',
        redirect: '/system/config',
        meta: { title: '权限中心(旧)', permission: 'system:config:view' }
      },
      // 【2026-11-05 用户令"日志管理也放到系统设置里面"】日志管理不再是独立侧栏菜单
      // (库节点已删 V2026.11.05.2), 页面转为 系统设置 内「日志管理」页签(码仍=system:log:view);
      // 本路由仅作旧链接自愈重定向(旧书签/历史审计链接), 侧栏不再出现。
      // meta 仅保证路由块结构完整(审计抽取器要求每块带 meta), 重定向后守卫读目标路由 meta。
      {
        path: 'system/log',
        name: 'SystemLogLegacy',
        redirect: '/system/config',
        meta: { title: '日志管理(旧)', permission: 'system:log:view' }
      },
      // 【回收站 2026-10-18】已删除客户/人员查看与恢复：独立页面，默认仅系统管理员可见，
      // 其他角色由管理员在角色管理勾选 system:recycle:view / system:recycle:restore 后可见
      {
        path: 'system/recycle',
        name: 'SystemRecycle',
        component: () => import('@/views/system/recycle.vue'),
        meta: { title: '回收站', icon: 'Delete', permission: 'system:recycle:view' }
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
// 品牌名唯一来源：登录前用默认品牌；登录后跟随 userStore.systemName（由后端配置下发，见 store/user.js loadSystemConfig）
// 【2026-10-13】品牌回归 'AIGICRM'（A4 三轮曾统一为运筹马年·市场资源管理系统，已按用户指令撤销）
const BRAND_NAME = 'AIGICRM'
router.beforeEach(async (to, from, next) => {
  document.title = (to.meta.title ? to.meta.title + ' - ' : '') + BRAND_NAME
  // /mp-file-picker 为小程序 web-view 容器页(鉴权自治: URL 带 token 直连后端), 不走 PC 登录态
  if (to.path === '/login' || to.path === '/mp-file-picker') {
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
    if (userStore.systemName) document.title = (to.meta.title ? to.meta.title + ' - ' : '') + userStore.systemName
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
