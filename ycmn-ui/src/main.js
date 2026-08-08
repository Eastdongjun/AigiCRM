import { createApp } from 'vue'
// 【性能 2026-08-08】Element Plus 按需引入：组件由 unplugin-vue-components 按模板自动注入，
// 样式按组件自动引入；此处仅保留全局图标注册与 v-loading 指令注册。
import { ElLoading } from 'element-plus'
// v-loading 指令样式（按需模式不会随组件自动引入，手动补样式）
import 'element-plus/es/components/loading/style/css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import './style.css'

const app = createApp(App)
const pinia = createPinia()

// 注册 Element Plus Icons（菜单/角标等动态图标按名称渲染，需全局注册）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
// v-loading 指令（按需模式不会自动注册指令，手动挂载 ElLoading）
app.directive('loading', ElLoading.directive)

app.use(pinia)
app.use(router)
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
    if (old && old !== v) {
      // 版本不一致 = 当前页面引用已删除的旧 chunk,点击菜单会"没反应/空白";
      // 必须刷新到新版才能恢复。用户取消则不写入版本号,下次打开继续提示
      if (confirm('系统已更新到新版本，点击"确定"刷新页面（不刷新将无法正常使用）')) {
        localStorage.setItem('app_version', v)
        location.reload()
      }
    } else {
      localStorage.setItem('app_version', v)
    }
  } catch (e) { /* 网络异常静默 */ }
})()
