import { computed, ref, watch } from 'vue'

/**
 * 法布施列表通用前端分页（P-20261124）。
 * 用于后端仍返回完整数组、但页面必须提供翻页的列表；不改变接口契约。
 * @param {import('vue').Ref|import('vue').ComputedRef} source
 * @param {{pageSize?: number}} options
 */
export function usePagedList(source, options = {}) {
  const size = ref(options.pageSize || 10)
  const current = ref(1)
  const total = computed(() => (source.value || []).length)
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
  const paged = computed(() => {
    const start = (current.value - 1) * size.value
    return (source.value || []).slice(start, start + size.value)
  })
  const reset = () => { current.value = 1 }
  watch(totalPages, (v) => { if (current.value > v) current.value = v })
  watch(size, () => { current.value = 1 })
  return { current, size, total, totalPages, paged, reset }
}
