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
        if (!response.config.silent) notify(res.message || '请求失败')
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
    // 【2026-08-13 全局中文化】HTTP 状态码 → 中文提示；后端 message 优先。
    // 覆盖 nginx 413(HTML body 无 message)/500 空 body 等此前显示 "Request failed with status code xxx" 的场景
    const STATUS_MSG = {
      400: '请求参数有误',
      403: '没有操作权限',
      404: '请求的接口不存在',
      413: '文件过大，超出上传限制（最大 500MB）',
      429: '操作太频繁，请稍后再试',
      500: '服务器繁忙，请稍后再试',
      502: '服务暂时不可用，请稍后再试',
      503: '服务维护中，请稍后再试',
      504: '请求超时，请稍后再试'
    }
    let msg = error.response?.data?.message
    if (!msg) {
      msg = STATUS_MSG[status]
        || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络异常，请稍后重试')
    }
    // 【2026-08-15 审计 M2】角标接口(提醒未读/审批统计)对无对应权限用户 403 属预期:
    // 每次进系统都会静默失败并由 store 空 catch 兜底, 全局弹"没有操作权限"是假错误提示, 抑制之
    const url = error.config?.url || ''
    const isBadgeEndpoint = status === 403 && (url.includes('/reminder/pending-count') || url.includes('/approval/statistics'))
    if (!isBadgeEndpoint && !error.config?.silent) notify(msg)
    // 重包装为中文 Error：各处 catch 的 e.message 自动中文，无需逐个页面改
    const wrapped = new Error(msg)
    wrapped.response = error.response
    return Promise.reject(wrapped)
  }
)

export default request
