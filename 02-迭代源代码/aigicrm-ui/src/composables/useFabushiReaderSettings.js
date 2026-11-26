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

  // 版式标记被管理员改动：以新默认值覆盖（用户后续调整仍会记住）。
  // 【2026-11-26】打开书时标记先未知（空串：PC 阅读器要等书目详情回来才知道是文本版还是影印版），
  // “未知 → 已知”这一次不算改动：按这本书记住的设置重新读一遍。原来把它当成改动，每次打开文本版的书，
  // 都会把用户按书记住的背景、缩放等冲回默认值并写回本地（影印版碰巧不受影响：未知时的默认值就是影印版）。
  watch(layoutType, (value, oldValue) => {
    if (value === loadedLayout) return
    loadedLayout = value
    if (!oldValue) {
      settings.value = normalizeReaderSettings(readStored(storageKey(bookId.value)), value)
      return
    }
    settings.value = resolveReaderDefaults(value)
  })

  watch(settings, (value) => writeStored(storageKey(bookId.value), value), { deep: true })

  const patch = (changes) => {
    settings.value = normalizeReaderSettings({ ...settings.value, ...changes }, layoutType.value)
  }

  return { settings, patch }
}
