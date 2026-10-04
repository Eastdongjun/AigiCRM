import { createApp } from 'vue'
import { getDownloadToken } from '@/utils/token'
// 【性能 2026-08-08】Element Plus 按需引入：组件由 unplugin-vue-components 按模板自动注入，
// 样式按组件自动引入；此处仅保留全局图标注册与 v-loading 指令注册。
import { ElLoading } from 'element-plus'
// 按需模式下 JS 调用组件（非模板）样式需手动补：v-loading 指令 + 消息/确认弹窗/通知
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import './style.css'
import { shouldPromptForVersionChange } from './utils/app-version'

const app = createApp(App)
const pinia = createPinia()

// 动态菜单只使用此处列出的图标白名单，避免把整套图标打入主包。
// 【D22 2026-10-04】消除「import 列表 + menuIcons 映射」双清单（此前漏改 map 侧的图标会静默不渲染）：
// 图标名只在 menuIconList 里出现一次，全局注册 key 由组件自身 name（与导出名一致）生成；
// 成员访问均为静态（Icons.Xxx），构建端 tree-shaking 仍只打包白名单内图标，不会打入整套图标包；
// 名单里若出现包未导出的图标名，启动即抛错提示排查（而非静默丢图标）。
const menuIconList = [
  Icons.Aim,
  Icons.AlarmClock,
  Icons.Avatar,
  Icons.ArrowLeft,
  Icons.ArrowDown,
  Icons.ArrowRight,
  Icons.Back,
  Icons.Bell,
  Icons.Box,
  Icons.Briefcase,
  Icons.Calendar,
  Icons.ChatDotSquare,
  Icons.Check,
  Icons.CircleCheck,
  Icons.CircleClose,
  Icons.Close,
  Icons.Coin,
  Icons.Collection,
  Icons.Connection,
  Icons.DataAnalysis,
  Icons.DataLine,
  Icons.Delete,
  Icons.Document,
  Icons.Download,
  Icons.Edit,
  Icons.EditPen,
  Icons.Expand,
  Icons.Filter,
  Icons.Files,
  Icons.Fold,
  Icons.Folder,
  Icons.FolderAdd,
  Icons.FolderOpened,
  Icons.Grid,
  Icons.HomeFilled,
  Icons.InfoFilled,
  Icons.Iphone,
  Icons.Key,
  Icons.List,
  Icons.Location,
  Icons.Lock,
  Icons.MapLocation,
  Icons.Medal,
  Icons.Money,
  Icons.Monitor,
  Icons.Notebook,
  Icons.OfficeBuilding,
  Icons.Picture,
  Icons.Position,
  Icons.Plus,
  Icons.Postcard,
  Icons.PriceTag,
  Icons.Promotion,
  Icons.QuestionFilled,
  Icons.Refresh,
  Icons.Right,
  Icons.Search,
  Icons.SetUp,
  Icons.Setting,
  Icons.Share,
  Icons.Ship,
  Icons.Star,
  Icons.StarFilled,
  Icons.SwitchButton,
  Icons.Tickets,
  Icons.Timer,
  Icons.Tools,
  Icons.TrendCharts,
  Icons.Trophy,
  Icons.Upload,
  Icons.User,
  Icons.UserFilled,
  Icons.VideoPlay,
  Icons.View,
  Icons.Warning,
  Icons.WarningFilled,
  Icons.ZoomIn,
]
const menuIcons = Object.fromEntries(
  menuIconList.map((icon) => {
    if (!icon) throw new Error('main.js: menuIconList 存在未导出的图标名（拼写错误），请检查图标白名单')
    return [icon.name, icon]
  })
)
// 生产菜单历史值里存在小写/旧命名；这里做兼容别名，避免图标缺失。
menuIcons.apartment = Icons.OfficeBuilding
menuIcons.lock = Icons.Lock
menuIcons.post = Icons.Postcard
for (const [key, component] of Object.entries(menuIcons)) {
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
  getDownloadToken() // F2: 预热下载专用 token，列表缩略图用短时 token 而非主 JWT
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
    if (shouldPromptForVersionChange(old, v, import.meta.env.DEV)) {
      // 版本不一致 = 当前页面引用已删除的旧 chunk,点击菜单会"没反应/空白";
      // 必须刷新到新版才能恢复。用户取消则不写入版本号,下次打开继续提示
      if (confirm('系统已更新到新版本，点击"确定"刷新页面（不刷新将无法正常使用）')) {
        localStorage.setItem('app_version', v)
        location.reload()
      }
    } else { localStorage.setItem('app_version', v) }
  } catch (e) { /* 网络异常静默 */ }
})()
