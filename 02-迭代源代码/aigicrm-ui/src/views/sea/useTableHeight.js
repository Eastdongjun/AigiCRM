// 表格高度共享 composable（【D6 2026-10-04】统一三处表格高度方案）
// 此前公海池(sea/index.vue)、捞取记录(sea/records.vue)、客户列表(customer/MainTable.vue)三处并存
// 两份内联 ResizeObserver 实现 + 一套 calc 魔法数 CSS，现收敛到本模块，按页面布局类型二选一：
//   1) useTableHeight()            —— 容器实测：flex 布局页（容器 flex:1 + min-height:0），
//                                     ResizeObserver 实测容器高，el-table 绑 :height 精确填满，
//                                     分页器/筛选栏高度变化自动跟随。使用方：sea/index.vue、sea/records.vue
//   2) useViewportMaxHeight(offset) —— 视口偏移：流式卡片布局页（容器无固定高），
//                                     监听 window resize，el-table 绑 :max-height = 视口高 - offset。
//                                     使用方：customer/MainTable.vue（offset=358 为页面固定占用，沿用原 calc 口径）
// 注：本文件位于 views/sea 下是受本批次文件独占范围限制（仅该目录可新建共享文件），
//     客户列表与公海两页共用；后续如放开目录限制可迁至 src/composables/。
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'

/** 方案一：容器实测（flex 布局页）——返回容器 ref 与实测高度，卸载时自动断开观察 */
export function useTableHeight() {
  const tableWrapRef = ref(null)
  const tableHeight = ref(300)
  let resizeObserver = null
  const updateTableHeight = () => {
    if (tableWrapRef.value) tableHeight.value = tableWrapRef.value.clientHeight
  }
  onMounted(async () => {
    await nextTick()
    updateTableHeight()
    if (tableWrapRef.value && typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(updateTableHeight)
      resizeObserver.observe(tableWrapRef.value)
    }
  })
  onBeforeUnmount(() => {
    if (resizeObserver) resizeObserver.disconnect()
  })
  return { tableWrapRef, tableHeight }
}

/** 方案二：视口偏移（流式卡片布局页）——maxHeight = window.innerHeight - offset，随窗口缩放跟随 */
export function useViewportMaxHeight(offset) {
  const maxHeight = ref(typeof window !== 'undefined' ? Math.max(window.innerHeight - offset, 200) : 600)
  const onWindowResize = () => {
    maxHeight.value = Math.max(window.innerHeight - offset, 200)
  }
  onMounted(() => {
    onWindowResize()
    window.addEventListener('resize', onWindowResize)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('resize', onWindowResize)
  })
  return { tableMaxHeight: maxHeight }
}
