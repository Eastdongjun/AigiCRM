// vitest 4 + Node 26 兼容兜底：jsdom 的 localStorage 未自动暴露到 globalThis
if (typeof window !== 'undefined' && window.localStorage && !globalThis.localStorage) {
  Object.defineProperty(globalThis, 'localStorage', {
    value: window.localStorage,
    configurable: true,
    writable: true
  })
}
