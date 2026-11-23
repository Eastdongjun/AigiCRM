// 【D19/D24 切片 7】按书目记忆阅读器版式设置：localStorage 持久化 + 换书/版式标记变化时重置默认。
// 注意：这是「阅读偏好」，不写后端、不新增权限码；书签页码才走服务端（FabushiBookmarkController）。
import { ref, watch } from 'vue'
import { normalizeReaderSettings, resolveReaderDefaults, storageKey } from '@/utils/fabushi-reader-settings'

const readStored = (key) => {
  if (typeof window === 'undefined' || !window.localStorage) return null
  try {
    const raw = window.localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch (_) {
    return null // 隐私模式/坏数据不阻断阅读
  }
}

const writeStored = (key, value) => {
  if (typeof window === 'undefined' || !window.localStorage) return
  try {
    window.localStorage.setItem(key, JSON.stringify(value))
  } catch (_) { /* 配额满时静默，本次会话仍然生效 */ }
}

/**
 * @param {import('vue').Ref<string|number>} bookId
 * @param {import('vue').Ref<string>} layoutType 书目版式标记 text/scan
 */
export function useFabushiReaderSettings(bookId, layoutType) {
  const settings = ref(resolveReaderDefaults(layoutType.value))
  let loadedKey = ''
  let loadedLayout = layoutType.value

  const load = () => {
    const key = storageKey(bookId.value)
    if (key === loadedKey) return
    loadedKey = key
    loadedLayout = layoutType.value
    settings.value = normalizeReaderSettings(readStored(key), layoutType.value)
  }
  load()

  // 换书：读该书记忆
  watch(bookId, () => load())

  // 版式标记被管理员改动：以新默认值覆盖（用户后续调整仍会记住）
  watch(layoutType, (value) => {
    if (value === loadedLayout) return
    loadedLayout = value
    settings.value = resolveReaderDefaults(value)
  })

  watch(settings, (value) => writeStored(storageKey(bookId.value), value), { deep: true })

  const patch = (changes) => {
    settings.value = normalizeReaderSettings({ ...settings.value, ...changes }, layoutType.value)
  }

  return { settings, patch }
}
