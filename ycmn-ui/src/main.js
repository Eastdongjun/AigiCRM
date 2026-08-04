import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import './style.css'

const app = createApp(App)
const pinia = createPinia()

// 注册 Element Plus Icons
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')

// 【需求】页面切换提速(FEAT 0804):路由 chunk 零预加载,首次点击菜单需现场下载(网络往返→卡顿);
// 应用空闲时预加载常用页面 chunk,点击菜单时秒开
window.addEventListener('load', () => {
  if ('requestIdleCallback' in window) {
    requestIdleCallback(() => {
      import('@/views/customer/list.vue')
      import('@/views/incentive/index.vue')
      import('@/views/incentive/stats.vue')
      import('@/views/incentive/search.vue')
      import('@/views/statistics/index.vue')
      import('@/views/system/user.vue')
    }, { timeout: 3000 })
  }
})

// 【需求】版本自动检测(FEAT 0804):部署新版本后旧页面引用的 JS 已被删除,点击菜单会"没反应";
// 启动时对比 version.txt,发现新版本提示刷新,避免用户停留在失效旧版
;(async () => {
  try {
    const res = await fetch('/version.txt?t=' + Date.now(), { cache: 'no-store' })
    if (!res.ok) return
    const v = (await res.text()).trim()
    const old = localStorage.getItem('app_version')
    if (old && old !== v && confirm('系统已更新到新版本，点击"确定"刷新页面')) {
      location.reload()
    }
    localStorage.setItem('app_version', v)
  } catch (e) { /* 网络异常静默 */ }
})()
