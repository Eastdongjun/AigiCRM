import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getToken, clearToken, isTokenValid } from './token'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// grouping:true 会把相同文案的提示合并成一条，避免多个请求同时失败堆一排
const notify = (message) => ElMessage({ message, type: 'error', grouping: true })

// 登录失效只处理一次（多个在途请求同时 401/403 时避免弹多条 / 多次跳转）
let sessionExpiredHandled = false
const handleSessionExpired = () => {
  clearToken()
  if (sessionExpiredHandled) return
  sessionExpiredHandled = true
  // 【修复 2026-08-10 P1-08】会话过期必须重置 user store——
  // 否则 30s TTL 内换账号重登, fetchUserInfo 命中缓存渲染上一账号身份/菜单/权限。
  // 动态 import 避免 request.js ↔ store/user.js 循环依赖。
  import('@/store/user').then(({ useUserStore }) => {
    try { useUserStore().$reset() } catch (e) { /* store 未初始化时忽略 */ }
  }).catch(() => {})
  if (router.currentRoute.value.path !== '/login') {
    notify('登录已过期，请重新登录')
    router.push('/login')
  }
}

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    sessionExpiredHandled = false // 有成功响应说明会话正常，重置标记
    const res = response.data
    if (res.code !== 200) {
      // 登录接口的 401 是密码错误，不是会话过期
      const isLoginApi = response.config.url?.includes('/auth/login')
      if (res.code === 401 && !isLoginApi) {
        handleSessionExpired()
      } else {
        notify(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    const status = error.response?.status
    // 401，或 403 但本地 token 已失效 → 判定为登录过期
    if (status === 401 || (status === 403 && !isTokenValid())) {
      handleSessionExpired()
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message
    if (status === 403) {
      // token 仍有效却 403 → 确实是没有该操作权限
      notify(msg || '没有操作权限')
    } else {
      notify(msg || error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
