import { defineStore } from 'pinia'
import { getUserInfo, logout as apiLogout } from '@/api/auth'
import { getPendingCount } from '@/api/reminder'
import { getApprovalStatistics } from '@/api/approval'
import { getPublicConfig } from '@/api/system'

export const useUserStore = defineStore('user', {
  state: () => ({
    userId: null,
    username: '',
    realName: '',
    avatar: '',
    deptName: '',
    officeName: '',
    roles: [],
    roleName: '',
    dataScope: 'self',
    permissions: new Set(),
    menus: [],
    managedOrgIds: [],
    // 侧边栏角标
    badgeReminder: 0,
    badgeApproval: 0,
    // 系统配置
    // 【A4 三轮 2026-10-09】默认值与页内品牌一致（此前 'AIGICRM' 与登录页/侧栏品牌双名并存）；
    // 生产由后端 system.name 配置覆写，此处仅影响配置加载前的首帧与 document.title 跟随
    systemName: '运筹马年·市场资源管理系统',
    systemLogo: '',
    // 【批④ 2026-08-10 多集团会话】
    groupIds: [],
    currentGroupId: null,
    // 【2026-10-08】集团名(展示用): 顶栏"集团:{{id}}"渲染裸数字的根修
    groupNames: {},
    currentGroupName: '',
    // 公共配置缓存（布局级：避免每页重复请求 configs/public）
    publicConfig: null,
    _userLoadedAt: 0,
    _configLoadedAt: 0
  }),
  getters: {
    // 多集团用户才显示切换入口
    canSwitchGroup: (state) => (state.groupIds || []).length > 1,
  },
  actions: {
    async fetchUserInfo(force = false) {
      // 布局级缓存：30s 内重复调用直接复用（页面共用 auth/info，避免每页重复请求）
      const now = Date.now()
      if (!force && this._userLoadedAt && now - this._userLoadedAt < 30000 && this.userId) return
      const res = await getUserInfo()
      const data = res.data
      this.userId = data.userId
      this.username = data.username
      this.realName = data.realName
      this.avatar = data.avatar
      this.deptName = data.deptName
      this.officeName = data.officeName
      this.roles = data.roles
      this.roleName = data.roleName
      this.dataScope = data.dataScope || 'self'
      this.permissions = new Set(data.permissions)
      this.menus = data.menus
      this.managedOrgIds = data.managedOrgIds || []
      this.applyGroupInfo(data.groupIds, data.currentGroupId, data.groupNames, data.currentGroupName)
      this._userLoadedAt = now
    },
    async loadSystemConfig(force = false) {
      // 布局级缓存：60s 内重复调用直接复用（登录页/客户列表等共用 configs/public）
      const now = Date.now()
      if (!force && this._configLoadedAt && now - this._configLoadedAt < 60000 && this.publicConfig) return
      try {
        const res = await getPublicConfig()
        this.publicConfig = res.data || {}
        const cfg = this.publicConfig
        if (cfg['system.name']) this.systemName = cfg['system.name']
        if (cfg['system.logo']) this.systemLogo = '/api/system/configs/logo?t=' + Date.now()
        this._configLoadedAt = now
      } catch {}
    },
    async refreshBadges() {
      try {
        const res = await getPendingCount()
        this.badgeReminder = res.data || 0
      } catch (e) {}
      try {
        const res = await getApprovalStatistics()
        this.badgeApproval = res.data?.pending || 0
      } catch (e) {}
    },
    hasPermission(permission) {
      return this.permissions.has(permission)
    },
    // 【批④】从登录响应写入集团信息(2026-10-08 起带名称: 展示层不再渲染裸 ID)
    applyGroupInfo(groupIds, currentGroupId, groupNames, currentGroupName) {
      this.groupIds = groupIds || []
      this.currentGroupId = currentGroupId ?? null
      this.groupNames = groupNames || {}
      this.currentGroupName = currentGroupName || this.groupNames[this.currentGroupId] || ''
    },
    // 【批④】切换当前集团(重签 token 后强制刷新用户信息)
    async switchGroup(groupId) {
      const { switchGroup: api } = await import('@/api/auth')
      const res = await api(groupId)
      localStorage.setItem('token', res.data.token)
      this.applyGroupInfo(res.data.groupIds, res.data.currentGroupId, res.data.groupNames, res.data.currentGroupName)
      await this.fetchUserInfo(true)
    },
    isAdmin() {
      return this.roles.includes('admin') || this.username === 'admin'
    },
    logout() {
      // 【工作流复核确认修复】先调后端 /auth/logout 注销拉黑当前 token(原只本地清除,
      // 后端"注销即拉黑"机制成死代码, rememberMe 7 天有效期内退出的 token 服务端仍可用)
      // fire-and-forget: 本地清理不因注销请求失败而阻塞
      apiLogout().catch(() => {})
      this.$reset()
      localStorage.removeItem('token')
    }
  }
})
