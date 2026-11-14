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
// 【WP5】权限版本号拉新在途标记(并发响应同 epoch 变化只触发一次 fetchUserInfo)
let permRefreshing = false
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
    // 【V2026.11.14.1 批量/版本下载】blob 响应直通: 走通用 res.code 判定会被 Blob 误杀(无 code 字段)
    if (response.config?.responseType === 'blob') {
      if (response.status !== 200) return Promise.reject(new Error('下载失败(' + response.status + ')'))
      return response
    }
    // 【2026-11-05 用户选 C"保持登录"】滑动续期: 后端在剩余寿命<半周期时回 X-New-Token, 静默换新
    const newToken = response.headers && (response.headers['x-new-token'] || response.headers['X-New-Token'])
    if (newToken) localStorage.setItem('token', newToken)
    // 【WP5 双端生效 2026-11-13】权限版本号比对: 与本地不一致 → 静默拉新用户信息
    // (管理员保存授权 → 用户不刷新页面, 下一次请求后菜单/按钮/数据范围更新)
    const permEpoch = response.headers && (response.headers['x-perm-epoch'] || response.headers['X-Perm-Epoch'])
    if (permEpoch && isTokenValid()) {
      const KEY = 'permEpoch'
      const prev = localStorage.getItem(KEY)
      if (prev && prev !== String(permEpoch) && !permRefreshing) {
        permRefreshing = true
        import('@/store/user').then(({ useUserStore }) => {
          return useUserStore().fetchUserInfo(true)
        }).catch(() => {}).finally(() => { permRefreshing = false })
      }
      localStorage.setItem(KEY, String(permEpoch))
    }
    // 【C2 2026-10-04】成功响应不再无条件重置 401 去重标记：
    // 过期临界窗口内，过期前发出、服务端在过期前已处理完的在途 200，其响应可能
    // 晚于同一批请求的 401 到达——无条件重置会让去重护栏失效，同批后续 401 再次
    // 触发多条「登录已过期」通知/跳转。仅当本地重新持有有效 token（用户已重新
    // 登录）时才重置，重新武装护栏，保留下一次会话过期仍能正常提示。
    if (sessionExpiredHandled && isTokenValid()) sessionExpiredHandled = false
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
      // 【D21】413 上限由 nginx 配置决定，不在前端硬编码具体数字（与 download.js 口径统一）
      413: '文件过大，超出上传限制',
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

// 【WP5 双端生效】visibilitychange 兜底: 页面重新可见时主动拉一次用户信息
// (覆盖"无 API 流量期间授权被管理员修改"的场景; 与 epoch 响应头比对双保险)
if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && isTokenValid()) {
      import('@/store/user').then(({ useUserStore }) => {
        return useUserStore().fetchUserInfo(true)
      }).catch(() => {})
    }
  })
}

export default request
