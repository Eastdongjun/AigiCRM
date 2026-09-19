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

// jsdom 无 ResizeObserver，7 个 views 在 onMounted 里 new ResizeObserver 会直接抛错。
// 组件测试挂载必需的最小 polyfill。
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class ResizeObserver {
    constructor(cb) { this._cb = cb }
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

// 页面里的原生 fetch()（customer/list 的 loadFilterData、incentive/search 的 downloadFile 等）
// 在测试环境会打真实网络。默认 stub 为空实现，各组件测试用 vi.stubGlobal 覆盖具体返回值。
if (typeof globalThis.fetch === 'undefined') {
  globalThis.fetch = async () => {
    throw new Error('fetch not stubbed in test — use vi.stubGlobal("fetch", ...)')
  }
}
