// vitest 4 + Node 26 兼容兜底：jsdom 的 localStorage 未自动暴露到 globalThis
// jsdom 下 window.localStorage 可能整体缺失，用内存实现兜底
function createLocalStorage() {
  let store = {}
  return {
    getItem: (k) => (k in store ? store[k] : null),
    setItem: (k, v) => { store[k] = String(v) },
    removeItem: (k) => { delete store[k] },
    clear: () => { store = {} },
    key: (i) => Object.keys(store)[i] || null,
    get length() { return Object.keys(store).length }
  }
}

if (typeof window !== 'undefined') {
  if (!window.localStorage) {
    try {
      Object.defineProperty(window, 'localStorage', {
        value: createLocalStorage(),
        configurable: true,
        writable: true
      })
    } catch (e) { /* 只读 window 环境下忽略 */ }
  }
  if (!globalThis.localStorage) {
    Object.defineProperty(globalThis, 'localStorage', {
      value: window.localStorage || createLocalStorage(),
      configurable: true,
      writable: true
    })
  }
}
