import { onMounted, onUnmounted } from 'vue'

/**
 * 【非实时数据 A 方案 2026-11-02 用户裁决】数据页聚焦重拉+30s 轮询。
 * 背景: PC 全页挂载即拉, 开着不动停在打开时刻(切菜单回来才刷新)——排行/统计页
 * 对新录入数据无感知。A 方案补两层轻量刷新:
 *   ① window focus 立即重拉(切回浏览器标签页即最新)
 *   ② 页面可见时每 30s 静默重拉(document.hidden 跳过, 不后台空转)
 * 刷新失败静默(网络抖动不打扰界面), onUnmounted 全清。
 * 后端侧已有 30s 级缓存(scoreboard/dashboard), 轮询命中的多为缓存, 成本可控。
 */
export function useLiveRefresh(refreshFn, intervalMs = 30000) {
  let timer = null
  const isVisible = () => typeof document !== 'undefined' && !document.hidden
  const safeRefresh = () => {
    if (isVisible()) Promise.resolve(refreshFn()).catch(() => {})
  }
  const onFocus = () => safeRefresh()
  onMounted(() => {
    timer = setInterval(safeRefresh, intervalMs)
    window.addEventListener('focus', onFocus)
  })
  onUnmounted(() => {
    if (timer) clearInterval(timer)
    window.removeEventListener('focus', onFocus)
  })
}
