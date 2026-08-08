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
    // 侧边栏角标
    badgeReminder: 0,
    badgeApproval: 0,
    // 系统配置
    systemName: 'AigiCRM',
    systemLogo: ''
  }),
  actions: {
    async fetchUserInfo() {
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
    },
    async loadSystemConfig() {
      try {
        const res = await getPublicConfig()
        const cfg = res.data || {}
        if (cfg['system.name']) this.systemName = cfg['system.name']
        if (cfg['system.logo']) this.systemLogo = '/api/system/configs/logo?t=' + Date.now()
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
    isAdmin() {
      return this.roles.includes('admin') || this.username === 'admin'
    },
    logout() {
      this.$reset()
      localStorage.removeItem('token')
    }
  }
})
