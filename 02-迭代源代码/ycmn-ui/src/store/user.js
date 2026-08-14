import { defineStore } from 'pinia'
import { getUserInfo } from '@/api/auth'
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
    systemName: 'AigiCRM',
    systemLogo: '',
    // 【批④ 2026-08-10 多集团会话】
    groupIds: [],
    currentGroupId: null,
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
    // 【批④】从登录响应写入集团信息
    applyGroupInfo(groupIds, currentGroupId) {
      this.groupIds = groupIds || []
      this.currentGroupId = currentGroupId ?? null
    },
    // 【批④】切换当前集团(重签 token 后强制刷新用户信息)
    async switchGroup(groupId) {
      const { switchGroup: api } = await import('@/api/auth')
      const res = await api(groupId)
      localStorage.setItem('token', res.data.token)
      this.applyGroupInfo(res.data.groupIds, res.data.currentGroupId)
      await this.fetchUserInfo(true)
    },
    isAdmin() {
      return this.roles.includes('admin') || this.username === 'admin'
    },
    logout() {
      this.$reset()
      localStorage.removeItem('token')
    }
  }
})
