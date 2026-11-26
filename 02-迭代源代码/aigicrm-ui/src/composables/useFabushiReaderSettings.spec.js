import { describe, it, expect, beforeEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useFabushiReaderSettings } from './useFabushiReaderSettings'

// 阅读器版式设置“按书记忆”（useFabushiReaderSettings，只有 PC 在线阅读在用）。
// 2026-11-26 修阅读器“背景”时发现的第二个问题：PC 阅读器打开书时，版式标记（文本版 / 影印版）要等书目详情回来才知道，
// 在此之前按未知（空串）处理。原来把“未知 → 文本版”当成了“管理员改了版式”，于是每次打开文本版的书，
// 用户按书记住的背景、缩放等设置都会被默认值冲掉（影印版不受影响，因为未知时的默认值就是影印版）。

const key = (id) => `fabushi:reader-settings:${id}`

describe('阅读器版式设置按书记忆', () => {
  beforeEach(() => { window.localStorage.clear() })

  it('打开书时版式标记先未知、书目详情回来才知道：这一次不算“管理员改了版式”，按书记住的设置不能丢', async () => {
    window.localStorage.setItem(key('5'), JSON.stringify({ backdrop: 'night', zoom: 130, crop: 6 }))
    const bookId = ref('5')
    const layoutType = ref('') // 还不知道这本书是文本版还是影印版
    const { settings } = useFabushiReaderSettings(bookId, layoutType)
    expect(settings.value.backdrop).toBe('night')

    layoutType.value = 'text' // 书目详情回来了：文本版
    await nextTick()
    expect(settings.value.backdrop).toBe('night')
    expect(settings.value.zoom).toBe(130)
    expect(settings.value.crop).toBe(6)
  })

  it('没有记过设置的文本版：详情回来后按文本版的默认值（纸白、对比度 100）', async () => {
    const layoutType = ref('')
    const { settings } = useFabushiReaderSettings(ref('6'), layoutType)
    // 未知时先按影印版的默认值
    expect(settings.value.backdrop).toBe('sepia')
    expect(settings.value.contrast).toBe(112)

    layoutType.value = 'text'
    await nextTick()
    expect(settings.value.backdrop).toBe('paper')
    expect(settings.value.contrast).toBe(100)
  })

  it('版式标记一开始就知道（影印版）：按影印版的默认值（米黄、对比度 112），记过的设置照读', () => {
    window.localStorage.setItem(key('7'), JSON.stringify({ backdrop: 'paper' }))
    const known = useFabushiReaderSettings(ref('7'), ref('scan'))
    expect(known.settings.value.backdrop).toBe('paper')
    expect(known.settings.value.contrast).toBe(112)
    const fresh = useFabushiReaderSettings(ref('11'), ref('scan'))
    expect(fresh.settings.value.backdrop).toBe('sepia')
  })

  it('管理员后来改了这本书的版式标记：按新默认值重置（用户之后再调的仍然记住）', async () => {
    window.localStorage.setItem(key('8'), JSON.stringify({ backdrop: 'night', zoom: 130 }))
    const layoutType = ref('text')
    const { settings, patch } = useFabushiReaderSettings(ref('8'), layoutType)
    expect(settings.value.backdrop).toBe('night')

    layoutType.value = 'scan'
    await nextTick()
    expect(settings.value.backdrop).toBe('sepia')
    expect(settings.value.zoom).toBe(100)

    patch({ zoom: 120 })
    await nextTick() // 写回本地是在设置变化后的下一个 tick
    expect(JSON.parse(window.localStorage.getItem(key('8')) || '{}').zoom).toBe(120)
  })

  it('换书：读那一书记住的设置', async () => {
    window.localStorage.setItem(key('9'), JSON.stringify({ backdrop: 'night' }))
    window.localStorage.setItem(key('10'), JSON.stringify({ backdrop: 'paper', zoom: 150 }))
    const bookId = ref('9')
    const { settings } = useFabushiReaderSettings(bookId, ref('scan'))
    expect(settings.value.backdrop).toBe('night')
    bookId.value = '10'
    await nextTick()
    expect(settings.value.backdrop).toBe('paper')
    expect(settings.value.zoom).toBe(150)
  })
})
